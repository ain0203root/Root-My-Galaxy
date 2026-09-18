package dev.busung.s25uroot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect

/**
 * The automatic install after a reboot, in its own process.
 *
 * It is a gate rather than a run: what it decides is *whether* this boot gets an automatic attempt,
 * and it answers that with the kernel's boot id, the stored receipt, and the app's own settings. The
 * run itself is the same code the install screen drives, started here with the cached payload and the
 * standalone transport, so an automatic install cannot drift from a manual one.
 *
 * It runs in `:autoroot_gate` so it survives the app's process being started, killed and restarted
 * around it at boot, which is exactly when that happens. Every step is bounded: the whole gate has a
 * deadline, the run has its own cut-offs, and the boot's single attempt is claimed before the run
 * starts so a second `BOOT_COMPLETED` cannot spend it twice.
 */
class AutoRootService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gateJob: Job? = null
    private var progressJob: Job? = null
    private var stopping = false
    private var viewModel: InstallViewModel? = null

    /**
     * Whether this boot's attempt comes from a one-shot retry rather than from root on boot.
     *
     * Read once, before the gate runs, because running it is what consumes the retry: by the time
     * anything is reported there is no retry left to ask about. It matters because the two are
     * different promises - one is a setting the user can see and one is a request they made once - and a
     * notification headed "Root on boot" for a boot that had it switched off reads as the setting being
     * ignored rather than as the request being honoured.
     */
    private var retryTriggeredThisBoot = false

    /**
     * Whether this boot's attempt is a one-shot retry, whichever way it also happens to be asked for.
     *
     * Distinct from [retryTriggeredThisBoot], which is about what the notification should call itself.
     * This one decides which payload the run gets: a retry runs the attempt it was armed for, and that is
     * true whether or not root on boot also wanted this boot's attempt.
     */
    private var retryArmedThisBoot = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopWithoutResult()
            return START_NOT_STICKY
        }
        if (gateJob?.isActive == true) return START_NOT_STICKY
        // Read here rather than in the gate, because running the gate is what consumes the retry: by the
        // time a run is starting there is none left to ask about.
        retryArmedThisBoot = AutoRootSupport.currentBootToken()
            ?.let { bootToken -> AppPreferences.retryPendingForBoot(this, bootToken) }
            ?: false
        retryTriggeredThisBoot = retryArmedThisBoot && !AppPreferences.bootRootMode(this)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.autoroot_stabilizing), ongoing = true),
        )
        gateJob = scope.launch {
            try {
                runGate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // The last line of defence, and the reason it is here rather than inside the gate's own
                // try: everything the gate does *before* that try - reading the boot id, the decision,
                // claiming the attempt - can throw too, and an uncaught throw here takes the process
                // down. On this path that is a crash dialog after a reboot and no notification at all,
                // which reads as the app being broken rather than the automatic install not happening.
                AppLog.error(AppLogTags.BOOT, "Root on boot aborted before it could report", error)
                runCatching {
                    finish(
                        getString(
                            R.string.autoroot_failed,
                            error.message ?: error.javaClass.simpleName,
                        ),
                    )
                }.onFailure { teardownQuietly() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopping = true
        progressJob?.cancel()
        scope.cancel()
        stopForegroundCompat()
        super.onDestroy()
    }

    private suspend fun runGate() {
        val initialBootToken = AutoRootSupport.currentBootToken()
        if (initialBootToken == null) {
            AppLog.warn(AppLogTags.BOOT, "Root on boot skipped: the kernel boot id could not be read")
            stopWithoutResult()
            return
        }
        // The authoritative reading, not the native one: this is the decision that spends the boot's
        // single install attempt, and on this hardware the native paths can be denied by policy while
        // root is live. Asking twice costs a process; asking wrongly costs a doomed install.
        val kernelSuActive = RootStatusProbe.isActive()
        // Bound to a name so the line below can say *which* rule stood down: "skipped before starting"
        // is four different situations, and only one of them is worth doing anything about.
        val decision = AutoRootSupport.decision(this, initialBootToken, kernelSuActive)
        AppLog.info(
            AppLogTags.BOOT,
            "Gate decision ${decision.name} (KernelSU active=$kernelSuActive)",
        )
        when (decision) {
            // Root already active means this boot needs nothing, recorded against the boot id so the
            // rest of the boot does not ask again either.
            AutoRootDecision.SkipAlreadyRooted -> {
                AutoRootSupport.markVerifiedForBoot(this, initialBootToken)
                stopWithoutResult()
                return
            }
            AutoRootDecision.SkipDisabled,
            AutoRootDecision.SkipKernelSuLoadingOff,
            AutoRootDecision.SkipAlreadyVerified,
            AutoRootDecision.SkipAttempted,
            -> {
                stopWithoutResult()
                return
            }
            AutoRootDecision.NeedsPriorInstall -> {
                AppLog.warn(AppLogTags.BOOT, "Root on boot needs one online install first")
                finish(getString(R.string.autoroot_prior_install_required))
                return
            }
            AutoRootDecision.Run -> Unit
        }
        // The attempt is claimed before the run rather than after, so two components racing the same
        // boot cannot both spend it.
        if (!AutoRootSupport.claimAttempt(this, initialBootToken)) {
            AppLog.warn(AppLogTags.BOOT, "Root on boot skipped: this boot's attempt is already spent")
            stopWithoutResult()
            return
        }

        val wakeLock = acquireGateWakeLock()
        try {
            withTimeout(GATE_LIMIT_MILLIS) {
                awaitSettledFloor()
                require(
                    AppPreferences.bootRootMode(this@AutoRootService) ||
                        AppPreferences.retryArmed(this@AutoRootService),
                ) {
                    // Either way of asking this boot for an install was withdrawn while the gate waited.
                    "The automatic install was turned off while waiting"
                }
                val bootToken = AutoRootSupport.currentBootToken()
                    ?: error(getString(R.string.error_boot_id))
                require(bootToken == initialBootToken) { getString(R.string.autoroot_boot_changed) }
                if (RootStatusProbe.isActive()) {
                    AutoRootSupport.markVerifiedForBoot(this@AutoRootService, bootToken)
                    AppLog.info(AppLogTags.BOOT, "Root on boot skipped after the wait: KernelSU is active")
                    return@withTimeout
                }
                // Shizuku first, and before the run rather than inside it: this is the one caller that
                // runs unattended. What it can do is wait, and then say what stopped it - with the two
                // answers the run screen asks the same question with, because the person reading this
                // notification is the person who would otherwise have had to start the app and press one
                // of them. What it must not do is spend the boot on a transport the payload does not
                // need: see [bootShizukuPlan], which runs the standard way instead of holding when the
                // payload can carry itself, so a boot with no Wi-Fi and no root still makes progress.
                val payloadNeedsShell = AutoRootSupport.bootPayloadNeedsShell(
                    context = this@AutoRootService,
                    preferAttempted = retryArmedThisBoot,
                )
                when (val step = shizukuStep(payloadNeedsShell)) {
                    is ShizukuStep.Refuse -> {
                        finish(step.refusal.message, answers = step.refusal.answers)
                        return@withTimeout
                    }
                    is ShizukuStep.Run -> runInstall(bootToken, withoutShell = step.withoutShell)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            AppLog.error(
                AppLogTags.BOOT,
                "Root on boot gave up after ${GATE_LIMIT_MILLIS / 1000} s without finishing",
            )
            finish(getString(R.string.autoroot_failed, getString(R.string.autoroot_timed_out)))
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            AppLog.error(AppLogTags.BOOT, "Root on boot failed: $detail", error)
            finish(getString(R.string.autoroot_failed, detail))
        } finally {
            releaseQuietly(wakeLock)
        }
    }

    /**
     * Holds the CPU awake for the length of the gate, or reports that it could not.
     *
     * A wake lock is an optimisation here - without one the wait simply becomes suspendable - so it is
     * never allowed to decide whether the boot gets its install. Acquiring one is a binder call that
     * enforces `WAKE_LOCK`, and an ungranted permission throws `SecurityException` *inside the gate's
     * own process*, which kills it: on a device that withholds the permission, an unattended boot would
     * never reach the run and the failure would surface only as the app crashing after a reboot.
     */
    private fun acquireGateWakeLock(): PowerManager.WakeLock? = runCatching {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:AutoRootGate")
            .also { it.acquire(GATE_LIMIT_MILLIS) }
    }.onFailure {
        AppLog.warn(
            AppLogTags.BOOT,
            "No wake lock for the gate (${it.javaClass.simpleName}: ${it.message}); carrying on without one",
        )
    }.getOrNull()

    private fun releaseQuietly(wakeLock: PowerManager.WakeLock?) {
        runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
    }

    /**
     * Waits out this path's own boot-settle floor, reporting the countdown.
     *
     * It is a separate setting from the manual one because the two are waiting out different amounts.
     * By the time this service is running, `BOOT_COMPLETED` has already passed, so part of the boot is
     * spent and the remaining wait is shorter; a person who tunes the automatic floor is deciding how
     * much unattended risk to take, not how long a manual run pauses. Reading one setting for both
     * would make the second decision silently rewrite the first.
     */
    private suspend fun awaitSettledFloor() {
        val required = AppPreferences.autoRootSettleSeconds(this)
        while (true) {
            val left = BootSettle.remainingMillis(required, BootSettle.elapsedMillis())
            if (left <= 0L) return
            notifyOngoing(getString(R.string.status_boot_settle, BootSettle.formatRemaining(left)))
            delay(SETTLE_TICK_MILLIS)
        }
    }

    /**
     * What stopped the gate, and which answers the notification should offer it with.
     *
     * The two travel together because the second is a property of the first: "nothing here can start
     * Shizuku" must not be reported with a button that tries to, and the two are decided by the same
     * reading of the device - see [bootShizukuPlan] and [ShizukuRefusalActions].
     */
    private data class GateRefusal(val message: String, val answers: ShizukuRefusalActions)

    /**
     * What the gate decided about Shizuku before the run: start it, or say what stopped it.
     *
     * A refusal had to stay distinguishable from a start, and [withoutShell] is the third answer - the
     * run may go ahead, but not the way the setting asked for. It is carried to the run rather than
     * implied, because the run refuses an unattended start it was promised Shizuku for, and it must: the
     * promise is what the gate is allowed to break, not something a run may quietly decide for itself.
     */
    private sealed interface ShizukuStep {
        data class Run(val withoutShell: Boolean) : ShizukuStep
        data class Refuse(val refusal: GateRefusal) : ShizukuStep
    }

    /**
     * Holds the boot run for Shizuku when the run asked for it, says what stopped it if it never
     * arrives, and goes the standard way when the payload never needed it.
     *
     * The setting decides here rather than inside the run because the two transports are not
     * interchangeable: the app's own process is a different execution context, not a degraded one, and
     * for a profile that wants a shell it is not available at all. A boot that quietly took it would be a
     * boot that did not do what the user asked, with nobody watching to notice - so what may be taken
     * quietly is decided by [bootShizukuPlan] from the payload's own policy, and everything else is
     * handed to the notification as two answers.
     *
     * The plan is logged before it is acted on, including the shell reading that produced it: this is the
     * one place where a boot can decide to install without Shizuku, and a device whose payload was read
     * as not needing a shell has to be able to see that it was read that way.
     */
    private suspend fun shizukuStep(shellRequired: Boolean): ShizukuStep {
        val plan = bootShizukuPlan(
            requested = AppPreferences.shizukuMode(this),
            usable = shizukuUsable(),
            startable = shizukuStartable(),
            shellRequired = shellRequired,
        )
        AppLog.info(
            AppLogTags.BOOT,
            "Shizuku for this boot: ${plan.name} (the payload needs a shell=$shellRequired)",
        )
        return when (plan) {
            BootShizukuPlan.AsAsked -> ShizukuStep.Run(withoutShell = false)
            // The one outcome a boot reaches by itself, and the one the notification has to explain
            // afterwards: the run is the same code as the screen's "run without Shizuku", so what it
            // installs is exactly what that answer would have installed.
            BootShizukuPlan.WithoutShell -> ShizukuStep.Run(withoutShell = true)
            BootShizukuPlan.Wait ->
                if (awaitShizuku()) ShizukuStep.Run(withoutShell = false)
                else ShizukuStep.Refuse(
                    GateRefusal(
                        getString(
                            R.string.autoroot_shizuku_unavailable,
                            BootSettle.formatRemaining(SHIZUKU_WAIT_MILLIS),
                        ),
                        ShizukuRefusalActions.RetryOrStandard,
                    ),
                )
            BootShizukuPlan.Unstartable -> ShizukuStep.Refuse(
                GateRefusal(
                    getString(R.string.autoroot_shizuku_unstartable),
                    ShizukuRefusalActions.StandardOnly,
                ),
            )
        }
    }

    /**
     * Whether Shizuku is up and this app may use it.
     *
     * Both halves are needed and they are not the same thing: a running Shizuku this app has no
     * permission for is a binder it cannot send the payload through, and a grant with nothing running is
     * nothing at all.
     */
    private fun shizukuUsable(): Boolean =
        ShizukuController.isRunning() && ShizukuController.isGranted()

    /**
     * Whether anything on this device can start Shizuku.
     *
     * Root is deliberately not counted here: a gate that reached this point has already read KernelSU as
     * inactive, and the run it is holding back is the very thing that would give the device root. The
     * Shizuku app's own boot start does count, because on that device Shizuku is coming up by itself and
     * all this app has to do is wait for it.
     */
    private fun shizukuStartable(): Boolean =
        shizukuBootStartWorthAttempting(
            rootAlreadyActive = false,
            localAdbPaired = AdbCredentialStore.hasStoredKey(this) && AppPreferences.adbPaired(this),
            tokenConfigured = AppPreferences.shizukuAutomationToken(this).isNotBlank(),
        ) || runCatching { ShizukuIntentStarter.ownBootReceiverEnabled(this) }.getOrDefault(false)

    /**
     * Waits for Shizuku to become usable, starting it as often as is worth trying.
     *
     * The wait is bounded and reports itself, on the same terms as the settle floor above: an unattended
     * boot has nobody to tell its progress to, and a silent two minutes behind a notification that says
     * "starting the install" looks exactly like a run that has hung.
     *
     * Starting it here can race the boot service, which is doing the same thing on a device with Shizuku
     * on boot - and that is fine rather than a problem: [ShizukuStarter] serializes the attempts across
     * processes and re-probes the binder before each launch, so the second caller concludes "already
     * running" instead of starting a second server.
     */
    private suspend fun awaitShizuku(): Boolean {
        val startedAt = BootSettle.elapsedMillis()
        var attempts = 0
        // Pushed one interval into the past so the first attempt is immediate: by the time the gate is
        // here, the boot has already waited out the settle floor.
        var lastAttemptAt = startedAt - SHIZUKU_ATTEMPT_SPACING_MILLIS
        while (true) {
            // The setting can be turned off while this waits - it is two minutes in which somebody may
            // well open the app - and waiting for something no longer wanted is only a delay.
            if (!AppPreferences.shizukuMode(this)) return true
            if (shizukuUsable()) return true
            val left = SHIZUKU_WAIT_MILLIS - (BootSettle.elapsedMillis() - startedAt)
            if (left <= 0L) {
                AppLog.warn(
                    AppLogTags.BOOT,
                    "Shizuku did not arrive within ${SHIZUKU_WAIT_MILLIS / 1000} s; the install cannot go through it",
                )
                return false
            }
            if (attempts < SHIZUKU_START_ATTEMPTS &&
                BootSettle.elapsedMillis() - lastAttemptAt >= SHIZUKU_ATTEMPT_SPACING_MILLIS
            ) {
                attempts++
                lastAttemptAt = BootSettle.elapsedMillis()
                AppLog.info(AppLogTags.BOOT, "Starting Shizuku for the gate, attempt $attempts")
                runCatching { ShizukuStarter.start(context = this, shell = kernelSuRootShell(this)) }
                    .onFailure {
                        AppLog.warn(
                            AppLogTags.BOOT,
                            "A Shizuku start attempt for the gate failed: " +
                                "${it.javaClass.simpleName}: ${it.message}",
                        )
                    }
            }
            notifyOngoing(getString(R.string.autoroot_shizuku_waiting, BootSettle.formatRemaining(left)))
            delay(SETTLE_TICK_MILLIS)
        }
    }

    /**
     * Drives the ordinary install with the cached payload; there is no network at boot to rely on.
     *
     * [withoutShell] is the gate's own decision, passed on rather than re-derived: it is the difference
     * between running as the settings ask and running the standard way, and the run is told which one it
     * is because it cannot ask.
     */
    private suspend fun runInstall(bootToken: String, withoutShell: Boolean = false) {
        val model = InstallViewModel(application)
        viewModel = model
        progressJob = scope.launch {
            model.state.collect { state ->
                if (!state.busy) return@collect
                val line = state.log.lineSequence().lastOrNull()?.take(MAX_NOTIFICATION_DETAIL)
                notifyOngoing(state.message.ifBlank { line.orEmpty() })
            }
        }
        notifyOngoing(getString(R.string.autoroot_starting))
        model.runToCompletion(
            unattended = true,
            payloadOffline = true,
            // A retry runs the payload the failed attempt was for, from the files that attempt left on
            // the device. Root on boot has no attempt to honour by definition - it is not repeating
            // anything - so it keeps resolving from the cache.
            preferAttemptedPayload = retryArmedThisBoot,
            // The promise the gate could not keep, dropped before the run rather than inside it: the run
            // refuses an unattended start that was told to use Shizuku and has none, and that refusal is
            // what makes this an explicit decision instead of a silent fallback.
            withoutShizuku = withoutShell,
        )
        progressJob?.cancel()
        progressJob = null

        val state = model.state.value
        if (state.phase == InstallPhase.Installed) {
            AutoRootSupport.markVerifiedForBoot(this, bootToken)
            if (AppPreferences.shizukuBootMode(this)) ShizukuBootService.start(this)
            // KernelSU is loaded and its modules are not: this run happened in a userspace that was
            // already built, so the offer to build it again is the difference between a phone that is
            // rooted and a phone whose modules do anything.
            //
            // A run that went the standard way says so here, because this notification is the whole of
            // what anyone sees of a boot install: with Use Shizuku on, a rooted phone and a message that
            // reads as if nothing was different is the setting being ignored without saying so.
            finish(
                getString(
                    if (withoutShell) R.string.autoroot_succeeded_without_shell
                    else R.string.autoroot_succeeded,
                ),
                offerSoftReboot = true,
            )
            return
        }
        val failure = state.failure
        val reason = failure?.reason?.takeIf(String::isNotBlank)
            ?: state.message.ifBlank { getString(R.string.status_install_failed) }
        val stage = failure?.let { getString(it.stage.label) }
        finish(
            if (stage == null) getString(R.string.autoroot_failed, reason)
            else getString(R.string.autoroot_failed_at, stage, reason),
        )
    }

    private fun notifyOngoing(message: String) {
        if (stopping) return
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(message, ongoing = true),
        )
    }

    /**
     * The run is over: the notification stops being ongoing and says how it went.
     *
     * [answers] is only for a gate that stood down over Shizuku, and it is a parameter rather than
     * something added where the notification is built because the actions have to be the *right* two:
     * a result that was not about Shizuku at all must not carry "use the standard method".
     */
    private fun finish(
        message: String,
        offerSoftReboot: Boolean = false,
        answers: ShizukuRefusalActions? = null,
    ) {
        if (stopping) return
        stopping = true
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(
                message,
                ongoing = false,
                offerSoftReboot = offerSoftReboot,
                answers = answers,
            ),
        )
        stopForegroundCompat()
        stopSelf()
    }

    /** Nothing to report, and nothing to leave behind: used when the gate decides not to run. */
    private fun stopWithoutResult() {
        if (stopping) return
        stopping = true
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopForegroundCompat()
        stopSelf()
    }

    /**
     * The same teardown, for when reporting the failure was itself the thing that threw.
     *
     * It ignores [stopping] on purpose: that flag is what stops this from cancelling a result the user
     * is meant to read, and by the time this runs there is no result - the notification was never
     * posted. What is left to do is only make sure the foreground notification does not outlive the
     * gate, since a half-finished [finish] would otherwise leave it standing with nothing behind it.
     */
    private fun teardownQuietly() {
        stopping = true
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
        runCatching { stopForegroundCompat() }
        runCatching { stopSelf() }
    }

    /**
     * [offerSoftReboot] adds the action a boot run needs after it succeeds, and only then.
     *
     * A boot run loads KernelSU into a userspace that was already built, so its modules are inert until
     * that userspace is built again. The offer is the only way to do it without asking the user to know
     * which of the two restarts is the one that loads modules - and it is offered rather than performed
     * because it closes everything that is open, which is not something a background run gets to decide.
     */
    private fun buildNotification(
        message: String,
        ongoing: Boolean,
        offerSoftReboot: Boolean = false,
        answers: ShizukuRefusalActions? = null,
    ) = NotificationCompat
        .Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle(
            getString(
                if (retryTriggeredThisBoot) R.string.autoroot_retry_title
                else R.string.settings_boot_root,
            ),
        )
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setContentIntent(launcherPendingIntent())
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .setAutoCancel(!ongoing)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            0,
            getString(R.string.autoroot_disable),
            PendingIntent.getBroadcast(
                this,
                1,
                Intent(this, AutoRootActionReceiver::class.java)
                    .setAction(AutoRootActionReceiver.ACTION_DISABLE_ROOT_ON_BOOT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .apply {
            if (offerSoftReboot) {
                addAction(
                    0,
                    getString(R.string.autoroot_apply_modules),
                    PendingIntent.getBroadcast(
                        this@AutoRootService,
                        2,
                        Intent(this@AutoRootService, AutoRootActionReceiver::class.java)
                            .setAction(AutoRootActionReceiver.ACTION_APPLY_MODULES),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            // The two answers this boot could not ask for itself, in the same order and with the same
            // meaning as the run screen's dialog. They open the install screen rather than starting a
            // foreground service from here: an action that quietly ran an exploit behind a notification
            // would be the unattended behaviour this refusal exists to avoid, and the screen is also
            // where the reason a start failed is shown.
            if (answers?.offersRetry == true) {
                addAction(
                    0,
                    getString(R.string.autoroot_answer_retry_shizuku),
                    answerPendingIntent(3, RunAnswer.RetryShizuku),
                )
            }
            if (answers != null) {
                addAction(
                    0,
                    getString(R.string.autoroot_answer_standard),
                    answerPendingIntent(4, RunAnswer.StandardMethod),
                )
            }
        }
        .build()

    /** An answer the notification offers, handed to the screen that can act on it. */
    private fun answerPendingIntent(requestCode: Int, answer: RunAnswer): PendingIntent =
        PendingIntent.getActivity(
            this,
            requestCode,
            Intent(this, InstallActivity::class.java)
                .putExtra(InstallActivity.EXTRA_RUN_ANSWER, answer.extra),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun launcherPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.autoroot_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.autoroot_channel_description) },
        )
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    companion object {
        const val ACTION_CANCEL = "dev.busung.s25uroot.action.CANCEL_AUTO_ROOT"

        private const val CHANNEL_ID = "auto_root"
        /** Also read by the notification's own action, so the offer can clear the result it acted on. */
        internal const val NOTIFICATION_ID = 0x42554f55

        /** How long the whole gate may take, including the run's own cut-offs. */
        private const val GATE_LIMIT_MILLIS = 15 * 60 * 1_000L

        /**
         * How long the gate holds for Shizuku before it gives up on this boot.
         *
         * Longer than the boot service's own schedule - a 20 s settle and up to two retries 15 s apart -
         * because the two run in parallel and this one is the last to give up, not the first to try.
         */
        private const val SHIZUKU_WAIT_MILLIS = 120_000L

        /** Spacing between this gate's own start attempts inside that window. */
        private const val SHIZUKU_ATTEMPT_SPACING_MILLIS = 30_000L

        /** The most attempts that can be said to be different attempts at the same thing. */
        private const val SHIZUKU_START_ATTEMPTS = 3
        private const val SETTLE_TICK_MILLIS = 1_000L
        private const val MAX_NOTIFICATION_DETAIL = 120

        fun start(context: Context) {
            val intent = Intent(context, AutoRootService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoRootService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
