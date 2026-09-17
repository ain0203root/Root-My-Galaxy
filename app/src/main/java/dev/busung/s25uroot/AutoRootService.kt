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
import android.util.Log
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
        retryTriggeredThisBoot =
            AppPreferences.retryArmed(this) && !AppPreferences.bootRootMode(this)
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
                Log.e(TAG, "Root on boot aborted before it could report", error)
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
            Log.i(TAG, "Root on boot skipped: the kernel boot id could not be read")
            stopWithoutResult()
            return
        }
        // The authoritative reading, not the native one: this is the decision that spends the boot's
        // single install attempt, and on this hardware the native paths can be denied by policy while
        // root is live. Asking twice costs a process; asking wrongly costs a doomed install.
        val kernelSuActive = RootStatusProbe.isActive()
        when (AutoRootSupport.decision(this, initialBootToken, kernelSuActive)) {
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
                Log.i(TAG, "Root on boot skipped before starting")
                stopWithoutResult()
                return
            }
            AutoRootDecision.NeedsPriorInstall -> {
                finish(getString(R.string.autoroot_prior_install_required))
                return
            }
            AutoRootDecision.Run -> Unit
        }
        // The attempt is claimed before the run rather than after, so two components racing the same
        // boot cannot both spend it.
        if (!AutoRootSupport.claimAttempt(this, initialBootToken)) {
            Log.i(TAG, "Root on boot skipped: this kernel boot's attempt is already spent")
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
                    Log.i(TAG, "Root on boot skipped after the wait: KernelSU is already active")
                    return@withTimeout
                }
                runInstall(bootToken)
            }
        } catch (timeout: TimeoutCancellationException) {
            finish(getString(R.string.autoroot_failed, getString(R.string.autoroot_timed_out)))
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "Root on boot failed", error)
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
        Log.w(TAG, "Root on boot: no wake lock for the gate, continuing without one", it)
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

    /** Drives the ordinary install with the cached payload; there is no network at boot to rely on. */
    private suspend fun runInstall(bootToken: String) {
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
        model.runToCompletion(forceStandalone = true, payloadOffline = true)
        progressJob?.cancel()
        progressJob = null

        val state = model.state.value
        if (state.phase == InstallPhase.Installed) {
            AutoRootSupport.markVerifiedForBoot(this, bootToken)
            if (AppPreferences.shizukuBootMode(this)) ShizukuBootService.start(this)
            // KernelSU is loaded and its modules are not: this run happened in a userspace that was
            // already built, so the offer to build it again is the difference between a phone that is
            // rooted and a phone whose modules do anything.
            finish(getString(R.string.autoroot_succeeded), offerSoftReboot = true)
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

    /** The run is over: the notification stops being ongoing and says how it went. */
    private fun finish(message: String, offerSoftReboot: Boolean = false) {
        if (stopping) return
        stopping = true
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(message, ongoing = false, offerSoftReboot = offerSoftReboot),
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
            if (!offerSoftReboot) return@apply
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
        .build()

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

        private const val TAG = "RootMyGalaxyAutoRoot"
        private const val CHANNEL_ID = "auto_root"
        /** Also read by the notification's own action, so the offer can clear the result it acted on. */
        internal const val NOTIFICATION_ID = 0x42554f55

        /** How long the whole gate may take, including the run's own cut-offs. */
        private const val GATE_LIMIT_MILLIS = 15 * 60 * 1_000L
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
