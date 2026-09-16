package dev.busung.s25uroot

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class InstallPhase {
    Checking,
    Ready,
    Settling,
    Downloading,
    Exploiting,
    LoadingKernelSu,
    Installed,
    Failed,
}

data class InstallUiState(
    val phase: InstallPhase = InstallPhase.Checking,
    val message: String = "",
    val probeOutput: String = "",
    val log: String = "",
    /** Set when [phase] is [InstallPhase.Failed], so the screen can name the stage and the cause. */
    val failure: RunFailure? = null,
) {
    val busy: Boolean
        get() = phase in setOf(
            InstallPhase.Checking,
            InstallPhase.Settling,
            InstallPhase.Downloading,
            InstallPhase.Exploiting,
            InstallPhase.LoadingKernelSu,
        )

}

/**
 * What a run is handed, for the run-plan screen: the variables the app sets for the payload, the
 * arguments only the Shizuku transport adds, and the cut-offs the app itself enforces. Keeping it
 * a value type means the screen shows what a run would use rather than a second copy of the rules,
 * and the rules stay testable without a device.
 */
internal data class ExploitPlan(
    /**
     * The boot-uptime floor a run waits for before the exploit starts; part of the plan because it is
     * a cut-off the app enforces on the run, and the plan is where those are shown.
     */
    val bootSettleSeconds: Int = 0,
    val environment: Map<String, String>,
    val shizukuArguments: Map<String, String>,
    /** Null when no stall watchdog applies, which is the case for a fresh session. */
    val stallLimitMillis: Long?,
    val totalLimitMillis: Long,
    val helperLimitMillis: Long,
)

data class TargetCatalogUiState(
    val loading: Boolean = false,
    val profiles: List<TargetProfile> = emptyList(),
    val error: String? = null,
    val sourceFailures: List<String> = emptyList(),
)

private data class CommandResult(val code: Int, val output: String)

/**
 * Payloads are truncated to a fixed release size, so a rebuild of a target --
 * or a different target padded to the same size -- has exactly the length of
 * whatever is already staged, and would keep running in its place.
 */
internal fun stagedFileIsCurrent(staged: File, source: File): Boolean {
    if (!staged.exists()) return false
    val stagedDigest = sha256OrNull(staged) ?: return false
    return stagedDigest == sha256OrNull(source)
}

private fun sha256OrNull(file: File): String? = runCatching {
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}.getOrNull()

class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = PayloadRepository(application)
    private val historyStore = InstallHistoryStore(application)
    private val mutableState = MutableStateFlow(InstallUiState())
    private val mutableHistory = MutableStateFlow(historyStore.closeInterruptedRuns())
    private val mutableTargetCatalog = MutableStateFlow(TargetCatalogUiState())
    private var discoveryJob: Job? = null
    private var installJob: Job? = null
    private var activeHistoryEntry: InstallHistoryEntry? = null

    /** Which stage the run is in, for the failure report. */
    private var activeStage = RunStage.Target

    @Volatile
    private var activeRunShizuku: Boolean? = null

    /** Which transport this run's payload goes through, frozen when the run starts. */
    @Volatile
    private var activeRunTransport: RunTransport? = null

    /** Set by the run screen's override while a boot-settle wait is in progress. */
    @Volatile
    private var bootSettleOverridden = false
    val state: StateFlow<InstallUiState> = mutableState.asStateFlow()
    val history: StateFlow<List<InstallHistoryEntry>> = mutableHistory.asStateFlow()
    val targetCatalog: StateFlow<TargetCatalogUiState> = mutableTargetCatalog.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (installJob?.isActive == true) return
        mutableHistory.value = historyStore.load()
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            val probe = NativeProbe.run()
            if (detectInstalled()) {
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Installed,
                    message = app.getString(R.string.status_ksu_active),
                    probeOutput = probe,
                    log = probe,
                )
                return@launch
            }
            try {
                val profile = repository.resolveTarget(DeviceSnapshot.current())
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Ready,
                    message = app.getString(R.string.status_not_installed),
                    probeOutput = probe,
                    log = "$probe\n${app.getString(R.string.log_profile, profile.profileId)}",
                )
            } catch (error: Throwable) {
                mutableState.value = InstallUiState(
                    phase = InstallPhase.Failed,
                    message = app.getString(R.string.status_support_failed),
                    probeOutput = probe,
                    log = "$probe\n[-] ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun deleteHistoryEntries(ids: Collection<String>) {
        val runningId = activeHistoryEntry?.id
        val toDelete = ids.filterNot { it == runningId }
        if (toDelete.isEmpty()) return
        toDelete.forEach(historyStore::delete)
        mutableHistory.value = mutableHistory.value.filterNot { it.id in toDelete }
    }

    fun loadTargetCatalog() {
        if (mutableTargetCatalog.value.loading) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableTargetCatalog.value = TargetCatalogUiState(loading = true)
            mutableTargetCatalog.value = try {
                val catalog = repository.loadCatalog()
                TargetCatalogUiState(
                    profiles = catalog.targets.sortedWith(
                        compareBy(
                            TargetProfile::displayName,
                            TargetProfile::profileId,
                            TargetProfile::sourceLabel,
                        ),
                    ),
                    sourceFailures = catalog.sourceFailures,
                )
            } catch (error: Throwable) {
                TargetCatalogUiState(error = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    /**
     * Runs an install and returns once it reaches a terminal phase, for callers outside the
     * install screen that have to keep a foreground service alive for exactly as long as the run
     * takes — the boot gate, which cannot wait on the UI state itself.
     */
    suspend fun runToCompletion(
        selectionId: String? = null,
        forceStandalone: Boolean = false,
        payloadOffline: Boolean = false,
    ) {
        install(selectionId, forceStandalone, payloadOffline)
        installJob?.join()
    }

    /**
     * Ends a boot-settle wait on the user's word.
     *
     * A plain flag rather than a cancellation, because the wait is not the run: what is being skipped
     * is the pause in front of it, and the run continues from there with nothing else changed.
     */
    fun skipBootSettle() {
        bootSettleOverridden = true
    }

    fun install(
        selectionId: String? = null,
        forceStandalone: Boolean = false,
        /**
         * Forces this run to use the cached payload.
         *
         * A boot-time run sets it because at boot there may be no network to download from and
         * nobody to wait for one, so the run has to be able to say "cached, or not at all" without
         * changing the mode the user chose for their own runs.
         */
        payloadOffline: Boolean = false,
    ) {
        if (installJob?.isActive == true || mutableState.value.phase == InstallPhase.Installed) return
        discoveryJob?.cancel()
        bootSettleOverridden = false
        installJob = viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = InstallUiState(
                phase = InstallPhase.Checking,
                probeOutput = mutableState.value.probeOutput,
            )
            startHistory()
            // First line of every run, and part of its stored history with the rest of the log: a
            // result is only reproducible if the build that produced it is on the record. The
            // version code is part of that identity, not decoration: the name only names the
            // commit, and several local builds share one commit.
            appendLog(
                app.getString(
                    R.string.version_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
            )
            // Freeze the transport for the whole run so a mid-run preference
            // change cannot mix Shizuku and standalone execution between the
            // exploit and the KernelSU staging steps. The boot service asks for
            // standalone explicitly rather than by toggling the stored
            // preference, which would leave it wrong if the run never finished.
            try {
                activeStage = RunStage.Target
                setPhase(InstallPhase.Checking, app.getString(R.string.status_checking_github))
                // Offline mode is what makes a run possible with no network at all, so it resolves
                // nothing: the cached payload already names its target, and asking the catalog would
                // be the very thing this mode exists to avoid.
                val offline = payloadOffline || AppPreferences.payloadMode(app) == PayloadMode.Offline
                val profile = when {
                    offline -> cachedProfileFor(selectionId)
                    selectionId == null -> repository.resolveTarget(DeviceSnapshot.current())
                    else -> repository.resolveTarget(selectionId)
                }

                // The transport is chosen here rather than beside the Shizuku check, because the
                // profile is what decides whether a shell is required at all - and a target that
                // requires one can be carried by a pairing instead of by Shizuku. Frozen for the
                // whole run, so a mid-run preference change cannot mix transports between the exploit
                // and the KernelSU staging steps.
                val shizukuRequested = !forceStandalone && AppPreferences.shizukuMode(app)
                val localAdbPaired = AdbCredentialStore.hasStoredKey(app) && AppPreferences.adbPaired(app)
                val transport = chooseRunTransport(
                    shellRequired = profile.routePolicy.prefersShellTransport,
                    shizukuRequested = shizukuRequested,
                    shizukuUsable = ShizukuController.isRunning() && ShizukuController.isGranted(),
                    localAdbPaired = localAdbPaired,
                ) ?: error(app.getString(shellTransportRefusalStringId(shizukuRequested)))
                activeRunTransport = transport
                activeRunShizuku = transport == RunTransport.Shizuku

                if (transport == RunTransport.Shizuku) {
                    activeStage = RunStage.Transport
                    appendLog(app.getString(R.string.log_shizuku_prepare))
                    if (!ShizukuController.isRunning() && !ShizukuController.pingUntilRunning()) {
                        error(app.getString(R.string.error_shizuku_unavailable))
                    }
                    if (!ShizukuController.isGranted() && !ShizukuController.requestPermission()) {
                        error(app.getString(R.string.error_shizuku_permission))
                    }
                    appendLog(app.getString(R.string.log_shizuku_permission))
                }
                appendLog(
                    app.getString(
                        when (transport) {
                            RunTransport.Shizuku -> R.string.log_transport_shizuku
                            RunTransport.LocalAdb -> R.string.log_transport_local_adb
                            RunTransport.App -> R.string.log_transport_app
                        },
                    ),
                )
                appendLog(app.getString(R.string.log_profile, profile.profileId))
                if (profile.sourceLabel.isNotEmpty()) {
                    appendLog(app.getString(R.string.log_payload_source, profile.sourceLabel))
                }
                updateHistoryTarget(profile)

                // Before the download, so the wait is the first thing the screen reports rather than
                // something that appears after the payload is already staged.
                awaitBootSettle(AppPreferences.bootSettleSeconds(app))

                val payloads = if (offline) {
                    activeStage = RunStage.Download
                    setPhase(InstallPhase.Downloading, app.getString(R.string.status_loading_cached))
                    KnownGoodPayloadStore.load(app, profile.profileId).also { cached ->
                        appendLog(app.getString(R.string.log_payload_cached, cached.exploit.name))
                    }
                } else {
                    activeStage = RunStage.Download
                    setPhase(InstallPhase.Downloading, app.getString(R.string.status_downloading_payload))
                    repository.download(profile) { appendLog("[*] $it") }.also {
                        appendLog(app.getString(R.string.log_download_verified))
                    }
                }

                activeStage = RunStage.Exploit
                setPhase(InstallPhase.Exploiting, app.getString(R.string.status_exploit_running))
                // Stated before the payload runs, so a failed run says which policy produced it.
                appendLog(profile.routePolicy.describe())
                appendLog(app.getString(R.string.log_payload_origin, payloads.origin.name.lowercase()))
                executeExploit(
                    payloads.exploit,
                    profile.requiresFreshP0Session,
                    profile.routePolicy,
                )

                // Optional, and before the KernelSU load rather than after: what it protects against
                // is a write made while bootstrap root is the only root on the device.
                if (AppPreferences.partitionReadOnlyMode(app)) setPartitionBlocksToRo()

                val modulesSkipped = if (AppPreferences.disableKsuModules(app)) {
                    moveModulesAside()
                } else {
                    appendLog(app.getString(R.string.log_ksu_modules_disabled_off))
                    false
                }

                try {
                    activeStage = RunStage.KernelSu
                    setPhase(InstallPhase.LoadingKernelSu, app.getString(R.string.status_ksu_loading))
                    installKernelSu(payloads)
                } finally {
                    // Modules are only meant to sit out the load itself. Restoring here also
                    // covers a load that fails, which is where leaving them aside would strand
                    // them with nothing in the app to bring them back.
                    if (modulesSkipped) restoreModules()
                }

                // While the run still holds the root it just obtained: the permission below cannot be
                // given any other way on the device, and the Shizuku start is what makes the next run
                // possible without a cable. Neither can fail the install.
                runCatching { PostRootSetup.apply(app) }
                    .onSuccess { outcome -> appendLog(outcome.logLine(app)) }
                    .onFailure { error ->
                        appendLog(
                            app.getString(
                                R.string.log_postroot_permission_failed,
                                error.message ?: error.javaClass.simpleName,
                            ),
                        )
                    }
                if (AppPreferences.shizukuBootMode(app)) ShizukuBootService.start(app)

                setPhase(InstallPhase.Installed, app.getString(R.string.status_ksu_active))
                appendLog(app.getString(R.string.log_install_complete))
                // A payload becomes the offline fallback only here, once KernelSU is verified: that is
                // what makes "known good" mean something, and it is why nothing is written while the
                // exploit is running. Publishing is best-effort - a full disk must not turn a root
                // that worked into a failure - but it is said out loud when it does not happen.
                if (payloads.origin == PayloadOrigin.Downloaded) {
                    runCatching { KnownGoodPayloadStore.publish(app, payloads) }
                        .onSuccess { cached ->
                            appendLog(app.getString(R.string.log_payload_cached_now, cached.profileId))
                        }
                        .onFailure { error ->
                            appendLog(
                                app.getString(
                                    R.string.log_payload_cache_failed,
                                    error.message ?: error.javaClass.simpleName,
                                ),
                            )
                        }
                }
                finishHistory(InstallRunResult.Succeeded)
            } catch (error: Throwable) {
                // The stage and the last payload output travel with the failure: the message alone
                // is the same for a download that failed and an exploit that gave up, and only one
                // of those is worth retrying straight away.
                val stage = activeStage
                val reason = error.message ?: error.javaClass.simpleName
                val failure = RunFailure.of(stage, reason, failureEvidence(mutableState.value.log))
                appendLog("[-] $reason")
                setPhase(
                    InstallPhase.Failed,
                    app.getString(R.string.status_stage_failed, app.getString(stage.label)),
                )
                mutableState.value = mutableState.value.copy(failure = failure)
                updateHistory { entry ->
                    entry.copy(failureStage = stage, failureReason = reason)
                }
                finishHistory(InstallRunResult.Failed)
            } finally {
                activeRunShizuku = null
            activeRunTransport = null
            }
        }
    }

    /**
     * Moves the module directory aside so the late-load starts without any module, and reports
     * whether they are actually out of the way. It never fails the run: the helper holds no
     * raised privileges of its own, and a device where the move is refused is a device whose
     * modules were never in the load's way to begin with.
     *
     * The move script first puts back a directory left over from a run that was killed between
     * the two moves, so a stranding cannot outlive one interrupted run.
     */
    private suspend fun moveModulesAside(): Boolean {
        val result = runMaintenance(MODULES_ASIDE_SCRIPT)
        when {
            result.code == MODULES_BACKUP_EXISTS -> appendLog(
                app.getString(R.string.log_ksu_modules_backup_present, MODULES_BACKUP_DIRECTORY),
            )
            result.code != 0 -> appendLog(
                app.getString(
                    R.string.log_ksu_modules_move_failed,
                    result.output.ifBlank { "exit ${result.code}" },
                ),
            )
            result.output.contains(MODULES_MOVED_MARKER) -> {
                appendLog(app.getString(R.string.log_ksu_modules_moved, MODULES_BACKUP_DIRECTORY))
                return true
            }
            else -> appendLog(app.getString(R.string.log_ksu_modules_absent, MODULES_DIRECTORY))
        }
        return false
    }

    private suspend fun restoreModules() {
        val result = runMaintenance(MODULES_RESTORE_SCRIPT)
        when {
            result.code != 0 -> appendLog(
                app.getString(
                    R.string.log_ksu_modules_restore_failed,
                    MODULES_BACKUP_DIRECTORY,
                    MODULES_DIRECTORY,
                    result.output.ifBlank { "exit ${result.code}" },
                ),
            )
            result.output.contains(MODULES_RESTORED_MARKER) ->
                appendLog(app.getString(R.string.log_ksu_modules_restored))
            else -> appendLog(app.getString(R.string.log_ksu_modules_restore_skipped))
        }
    }

    private suspend fun executeExploit(
        payload: File,
        requiresFreshP0Session: Boolean,
        routePolicy: ExploitRoutePolicy,
    ) {
        if (activeRunTransport == RunTransport.LocalAdb) {
            executeExploitOverLocalAdb(payload, requiresFreshP0Session, routePolicy)
            return
        }
        val shizuku = shizukuEnabled()
        val logFile = if (shizuku) File(SHIZUKU_LOG_PATH) else File(app.filesDir, "exploit.log")
        if (shizuku) {
            ShizukuController.exec(arrayOf("rm", "-f", SHIZUKU_LOG_PATH)).waitFor()
        } else {
            logFile.delete()
        }
        val helper = helperFile()
        if (!shizuku) {
            require(helper.canExecute()) { app.getString(R.string.error_helper_unavailable) }
        }
        val logPrefix = mutableState.value.log
        val bootToken = currentBootToken()
        val cachedP0Offset = if (requiresFreshP0Session) null else cachedP0Offset(bootToken)
        val process = if (shizuku) {
            val stagedPayload = shizukuStage(payload, SHIZUKU_PAYLOAD_PATH, "755")
            ShizukuController.exec(
                arrayOf("/system/bin/sh", "-c", "true"),
                shizukuEnvironment(
                    stagedPayload.absolutePath,
                    helper.absolutePath,
                    requiresFreshP0Session,
                    cachedP0Offset,
                    routePolicy,
                ),
            )
        } else {
            val processBuilder = ProcessBuilder(
                helper.absolutePath,
                "--run-payload",
                payload.absolutePath,
                helper.absolutePath,
                logFile.absolutePath,
            ).redirectErrorStream(true)
            processBuilder.environment().putAll(
                exploitEnvironment(requiresFreshP0Session, cachedP0Offset, routePolicy),
            )
            processBuilder.start()
        }
        val captured = StringBuilder()
        val readLog: () -> String = if (shizuku) {
            { drainProcessOutput(process, captured) }
        } else {
            // Keep draining stdout while polling: if the helper fills the OS
            // pipe buffer it blocks on write and stops making log progress,
            // which would trip the stall detector spuriously.
            { drainProcessOutput(process, captured); logFile.readTextIfPresent() }
        }

        try {
            val startedAt = SystemClock.elapsedRealtime()
            var lastProgressAt = startedAt
            var lastRawLog = ""
            while (process.isAlive) {
                val rawLog = readLog()
                if (rawLog != lastRawLog) {
                    if (!requiresFreshP0Session) cacheP0Offset(bootToken, rawLog)
                    publishExploitLog(logPrefix, rawLog)
                    lastRawLog = rawLog
                    lastProgressAt = SystemClock.elapsedRealtime()
                }
                val now = SystemClock.elapsedRealtime()
                if (!requiresFreshP0Session) {
                    require(now - lastProgressAt < EXPLOIT_STALL_MILLIS) {
                        app.getString(R.string.error_exploit_stalled, (EXPLOIT_STALL_MILLIS / 1000L).toInt())
                    }
                }
                require(now - startedAt < exploitTotalMillis(requiresFreshP0Session)) {
                    // Reported in minutes of the ceiling that actually applies, which is an hour for
                    // a fresh session and fifteen minutes otherwise.
                    app.getString(
                        R.string.error_exploit_timeout,
                        (exploitTotalMillis(requiresFreshP0Session) / 60_000L).toInt(),
                    )
                }
                delay(if (shizuku) SHIZUKU_LOG_POLL_INTERVAL else LOG_POLL_INTERVAL)
            }

            val exitCode = process.waitFor()
            val rawLog = readLog()
            if (!requiresFreshP0Session) cacheP0Offset(bootToken, rawLog)
            publishExploitLog(logPrefix, rawLog)
            // Both transports drain into `captured` during the poll loop, so a child that still
            // holds the pipe open cannot block the loop; nothing here reads it, because what the
            // payload said belongs in the log rather than in a failure message.
            require(exitCode == 0) {
                // The payload's output is the log, so the message says what the status means instead
                // of repeating it: inlining the whole output here is what turned a failed run into a
                // page of unreadable text.
                app.getString(R.string.error_payload_exit, exitCode, payloadExitDetail(exitCode))
            }
            require(rawLog.contains("exploit completed") && rawLog.contains("done=1 root=1")) {
                app.getString(R.string.error_success_marker)
            }
        } finally {
            if (process.isAlive) {
                process.destroy()
                delay(500.milliseconds)
                if (process.isAlive) process.destroyForcibly()
            }
        }
        appendLog(app.getString(R.string.log_bootstrap_root))
    }

    /**
     * Runs the payload through the device's own adbd, over wireless debugging.
     *
     * This is the path for a target whose feed entry says it only works from a shell, on a device with
     * no usable Shizuku. The shell context is what such a payload needs, and the pairing is what
     * provides one without a cable.
     *
     * Two differences from the other transports are worth knowing. The helper and the payload are
     * *pushed* rather than staged through a binder, because there is no binder here. And the command
     * owns one open shell for its whole life, streamed: adbd kills a backgrounded process the moment
     * its shell closes, so a payload that ran detached would be killed at the start and the run would
     * wait out its whole ceiling for a process that no longer existed.
     *
     * Wireless debugging is turned on for this and off again afterwards by [TemporaryWirelessAdb], so
     * the window exists only while the payload does.
     */
    private suspend fun executeExploitOverLocalAdb(
        payload: File,
        requiresFreshP0Session: Boolean,
        routePolicy: ExploitRoutePolicy,
    ) {
        val bootToken = currentBootToken()
        val cachedP0Offset = if (requiresFreshP0Session) null else cachedP0Offset(bootToken)
        val logPrefix = mutableState.value.log
        val helper = nativeHelperFile()
        require(helper.isFile) { app.getString(R.string.error_helper_unavailable) }

        val totalMillis = exploitTotalMillis(requiresFreshP0Session)
        // A socket handshake and a pushed upload are blocking work, and the run itself is driven from
        // the main dispatcher, so the whole transport lives on the IO dispatcher.
        val output = withContext(Dispatchers.IO) {
            TemporaryWirelessAdb.use(app) {
            WirelessAdbSession.open(app).use { session ->
                session.push(helper, ADB_HELPER_PATH, executable = true)
                session.push(payload, ADB_PAYLOAD_PATH)
                session.runStreaming(
                    command = localAdbExploitCommand(
                        requiresFreshP0Session,
                        cachedP0Offset,
                        routePolicy,
                    ),
                    overallTimeoutMs = totalMillis,
                    // A fresh session is deliberately allowed to sit silent for as long as its ceiling:
                    // what it is doing is scanning, and a stall limit there would cut off the very run
                    // the ceiling was set for.
                    stallTimeoutMs = if (requiresFreshP0Session) totalMillis else EXPLOIT_STALL_MILLIS,
                    shouldStop = { !mutableState.value.busy },
                ) { raw ->
                    if (!requiresFreshP0Session) cacheP0Offset(bootToken, raw)
                    publishExploitLog(logPrefix, raw)
                }
            }
        }
        }

        val exitCode = localAdbExploitExitCode(output)
        require(exitCode == 0) {
            app.getString(R.string.error_payload_exit, exitCode, payloadExitDetail(exitCode))
        }
        require(output.contains("exploit completed") && output.contains("done=1 root=1")) {
            app.getString(R.string.error_success_marker)
        }
        appendLog(app.getString(R.string.log_bootstrap_root))
    }

    private fun drainProcessOutput(process: Process, buffer: StringBuilder): String {
        return try {
            drainStream(process.inputStream, buffer)
            drainStream(process.errorStream, buffer)
            buffer.toString()
        } catch (_: Throwable) {
            buffer.toString()
        }
    }

    private fun drainStream(stream: InputStream, buffer: StringBuilder) {
        val data = ByteArray(4096)
        while (stream.available() > 0) {
            val count = stream.read(data)
            if (count <= 0) break
            buffer.append(String(data, 0, count, Charsets.UTF_8))
        }
    }

    private fun publishExploitLog(prefix: String, rawLog: String) {
        mutableState.value = mutableState.value.copy(
            log = listOf(prefix, stripAnsi(rawLog))
                .filter(String::isNotBlank)
                .joinToString("\n"),
        )
        updateHistoryLog()
    }

    private suspend fun installKernelSu(payloads: VerifiedPayloads) {
        if (shizukuEnabled()) {
            shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_PATH, "755")
            shizukuStage(payloads.kernelSu, SHIZUKU_KSUD_STAGE_PATH, "755")
            appendLog(app.getString(R.string.log_ksu_staged))
        } else {
            val source = shellQuote(payloads.kernelSu.absolutePath)
            val stageCommand =
                "/system/bin/cp $source $SHIZUKU_KSUD_PATH && " +
                    "/system/bin/cp $source $SHIZUKU_KSUD_STAGE_PATH && " +
                    "/system/bin/chmod 755 $SHIZUKU_KSUD_PATH $SHIZUKU_KSUD_STAGE_PATH"
            val stage = runHelper("-c", stageCommand)
            require(stage.code == 0) { app.getString(R.string.error_ksu_stage, stage.output) }
            appendLog(app.getString(R.string.log_ksu_staged))
        }

        val lateLoad = runHelper("--late-load")
        require(lateLoad.code == 0) {
            app.getString(R.string.error_ksu_verify, lateLoad.code, lateLoad.output)
        }
        if (lateLoad.output.isNotBlank()) appendLog(lateLoad.output)
        activeStage = RunStage.Verify
        // An exit code says the late-load command finished, not that anything is reachable now, so
        // the run states which independent reading confirmed the control channel before it claims
        // success - and refuses to claim it when none of them did.
        val readings = KernelSuRuntime.readings(app, lateLoad.output)
        // Said every time, not only on a refusal: the readings are how the next person to read the
        // log tells an unusable load from a check that could not see a healthy one.
        appendLog(app.getString(R.string.log_ksu_control_readings, readings.summary()))
        require(readings.proofs.isNotEmpty()) { app.getString(R.string.error_ksu_not_ready) }
        appendLog(
            app.getString(
                R.string.log_ksu_control_verified,
                readings.proofs.joinToString { it.label },
            ),
        )
        storeInstallReceipt()
    }

    private fun detectInstalled(): Boolean {
        // Called from the run's own dispatcher, so the `su` fallback behind this is off the main
        // thread: the native paths alone under-report on this hardware once the system locks down.
        if (RootStatusProbe.isActive()) return true
        val bootToken = currentBootToken() ?: return false
        val receipt = app.getSharedPreferences(INSTALL_RECEIPT, Application.MODE_PRIVATE)
        return receipt.getString(RECEIPT_BOOT_TOKEN, null) == bootToken &&
            receipt.getBoolean(RECEIPT_VERIFIED, false)
    }

    private fun storeInstallReceipt() {
        val bootToken = currentBootToken() ?: error(app.getString(R.string.error_boot_id))
        val stored = app.getSharedPreferences(INSTALL_RECEIPT, Application.MODE_PRIVATE)
            .edit()
            .putString(RECEIPT_BOOT_TOKEN, bootToken)
            .putBoolean(RECEIPT_VERIFIED, true)
            .commit()
        require(stored) { app.getString(R.string.error_receipt) }
    }

    private fun currentBootToken(): String? = kernelBootToken()

    /** The slide offset cached for this boot, if an earlier run found one. */
    internal fun cachedOffsetForThisBoot(): String? = cachedP0Offset(currentBootToken())

    private fun cachedP0Offset(bootToken: String?): String? {
        if (bootToken == null) return null
        val stored = app.getSharedPreferences(P0_CACHE, Application.MODE_PRIVATE)
        if (stored.getString(P0_CACHE_BOOT_TOKEN, null) != bootToken) return null
        return stored.getString(P0_CACHE_OFFSET, null)
    }

    private fun cacheP0Offset(bootToken: String?, log: String) {
        if (bootToken == null) return
        val match = P0_OFFSET_PATTERN.findAll(log).lastOrNull() ?: return
        val offset = match.groupValues[1].toLongOrNull(16) ?: return
        if (offset !in 0..P0_OFFSET_MAX || offset and P0_OFFSET_MASK != 0L) return
        val value = "0x${offset.toString(16)}"
        val stored = app.getSharedPreferences(P0_CACHE, Application.MODE_PRIVATE)
        if (stored.getString(P0_CACHE_BOOT_TOKEN, null) == bootToken &&
            stored.getString(P0_CACHE_OFFSET, null) == value
        ) return
        stored.edit()
            .putString(P0_CACHE_BOOT_TOKEN, bootToken)
            .putString(P0_CACHE_OFFSET, value)
            .apply()
    }

    private fun localAdbExploitCommand(
        requiresFreshP0Session: Boolean,
        cachedP0Offset: String?,
        routePolicy: ExploitRoutePolicy,
    ): String = buildString {
        // The environment comes first, quoted as values, because this is a shell command rather than
        // a process spawn with an environment attached.
        exploitEnvironment(requiresFreshP0Session, cachedP0Offset, routePolicy).forEach { (name, value) ->
            append(name).append('=').append(shellQuote(value)).append(' ')
        }
        append(shellQuote(ADB_HELPER_PATH))
        append(" --run-payload")
        append(' ').append(shellQuote(ADB_PAYLOAD_PATH))
        append(' ').append(shellQuote(ADB_HELPER_PATH))
        append(' ').append(shellQuote(ADB_LOG_PATH))
        // The raw `shell:` service does not carry an exit code, so the command reports its own on the
        // end of the stream. Reading it back is what separates "the payload failed" from "the payload
        // finished and the run got nothing", which need different answers.
        append("; rc=").append('$').append('?').append("; printf '\\n")
        append(ADB_EXIT_MARKER).append("%s\\n' \"").append('$').append("rc\"")
    }

    private fun helperFile(): File =
        if (shizukuEnabled()) {
            shizukuStage(nativeHelperFile(), SHIZUKU_HELPER_PATH, "755")
        } else {
            nativeHelperFile()
        }

    private fun nativeHelperFile() = File(app.applicationInfo.nativeLibraryDir, "libcve43499root.so")

    private fun shizukuEnabled(): Boolean = activeRunShizuku ?: AppPreferences.shizukuMode(app)

    private fun shizukuStage(source: File, target: String, mode: String): File {
        val staged = File(target)
        if (stagedFileIsCurrent(staged, source)) return staged
        try {
            ShizukuController.writeFile(target, mode, source.inputStream())
        } catch (error: Throwable) {
            throw IllegalStateException(
                app.getString(R.string.error_shizuku_stage, target, error.message.orEmpty()),
                error,
            )
        }
        return staged
    }

    private fun shizukuEnvironment(
        payloadPath: String,
        helperPath: String,
        requiresFreshP0Session: Boolean,
        cachedP0Offset: String?,
        routePolicy: ExploitRoutePolicy,
    ): Array<String> = buildList {
        exploitEnvironment(requiresFreshP0Session, cachedP0Offset, routePolicy).forEach { (name, value) ->
            add("$name=$value")
        }
        add("CVE43499_ROOT_HELPER=$helperPath")
        add("LD_PRELOAD=$payloadPath")
    }.toTypedArray()

    /**
     * Runs a privileged maintenance script through the best transport available.
     *
     * The helper's handoff socket only exists to cross the pre-KernelSU boundary, and once
     * KernelSU has loaded a Samsung kernel may refuse new connects to it while KernelSU itself is
     * healthy - which is exactly when these module scripts run. KernelSU's own root shell is
     * therefore tried first and the helper stays as the fallback for a boot where the temporary
     * socket is still the only way in.
     */
    /**
     * Marks the image partitions read-only, using the bootstrap root the exploit just produced.
     *
     * It goes through the helper directly rather than through [runMaintenance], and deliberately: at
     * this point KernelSU has not been loaded, so the transport KernelSU's own shell would need does
     * not exist yet. The helper's temporary socket is the only root here, which is the same reason
     * this runs now - the window it closes is exactly the window it runs in.
     */
    private suspend fun setPartitionBlocksToRo() {
        val script = runCatching {
            app.assets.open(PartitionReadOnly.SCRIPT_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (script == null) {
            // A build without the asset is a packaging fault, not a device condition, and saying so
            // is more useful than reporting that the devices could not be set.
            appendLog(app.getString(R.string.log_ro_blocks_script_missing))
            return
        }
        val result = runHelper("-c", script)
        val count = PartitionReadOnly.countFrom(result.output)
        if (count >= 1) {
            appendLog(app.getString(R.string.log_ro_blocks_successful, count))
        } else {
            appendLog(app.getString(R.string.log_ro_blocks_failed))
        }
    }

    private suspend fun runMaintenance(command: String): CommandResult {
        val viaKernelSu = if (shizukuEnabled()) KernelSuRuntime.rootShell(command) else null
        return viaKernelSu
            ?.let { CommandResult(it.exitCode, it.output) }
            ?: runHelper("-c", command)
    }

    /**
     * Runs the bootstrap helper for a short management command. Unlike the
     * exploit run there is no log file to poll, so output is drained inline
     * and a hard deadline guards against a helper that never exits — without
     * this, a hung `--late-load` leaves the install stuck in LoadingKernelSu
     * indefinitely.
     */
    private suspend fun runHelper(vararg arguments: String): CommandResult {
        val helper = helperFile()
        val process = if (shizukuEnabled()) {
            ShizukuController.exec(arrayOf(helper.absolutePath) + arguments)
        } else {
            ProcessBuilder(listOf(helper.absolutePath) + arguments)
                .redirectErrorStream(true)
                .start()
        }
        val captured = StringBuilder()
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (process.isAlive) {
                drainProcessOutput(process, captured)
                require(SystemClock.elapsedRealtime() - startedAt < HELPER_TIMEOUT_MILLIS) {
                    app.getString(
                        R.string.error_helper_timeout,
                        captured.toString().trim().takeIf(String::isNotBlank)
                            ?.let { ": $it" } ?: "",
                    )
                }
                delay(HELPER_POLL_INTERVAL)
            }
            drainProcessOutput(process, captured)
            val exitCode = process.waitFor()
            return CommandResult(exitCode, stripAnsi(captured.toString().trim()))
        } finally {
            if (process.isAlive) {
                process.destroy()
                delay(500.milliseconds)
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\''")}'"

    /**
     * The target the cached payload names, refusing a selection that is not it.
     *
     * A run asked for offline cannot fall back to the network, so the selection has to agree with the
     * cache rather than be resolved against a catalog: there is no catalog to resolve it against.
     */
    private fun cachedProfileFor(selectionId: String?): TargetProfile = KnownGoodPayloadStore.profileFor(
        app,
        selectionId?.let { profileFromSelectionId(it) },
    )

    /**
     * Holds the run until the device has been up long enough, reporting the remaining time as it goes.
     *
     * The countdown is the phase message, so it is on the run screen's status card rather than only in
     * the log, and the wait ends early when the user says so. The elapsed clock is read every tick
     * rather than accumulated, so the app sleeping through part of the wait does not make the run
     * believe it waited longer than it did.
     */
    private suspend fun awaitBootSettle(requiredSeconds: Int) {
        val required = BootSettle.normalize(requiredSeconds)
        if (required <= 0) return
        val remaining = BootSettle.remainingMillis(required, BootSettle.elapsedMillis())
        if (remaining <= 0L) {
            appendLog(app.getString(R.string.log_boot_settled, BootSettle.label(required)))
            return
        }
        appendLog(app.getString(R.string.log_boot_settle, BootSettle.formatRemaining(remaining)))
        while (true) {
            if (bootSettleOverridden) {
                appendLog(app.getString(R.string.log_boot_settle_skipped))
                return
            }
            val left = BootSettle.remainingMillis(required, BootSettle.elapsedMillis())
            if (left <= 0L) {
                appendLog(app.getString(R.string.log_boot_settled, BootSettle.label(required)))
                return
            }
            setPhase(
                InstallPhase.Settling,
                app.getString(R.string.status_boot_settle, BootSettle.formatRemaining(left)),
            )
            delay(BOOT_SETTLE_TICK_MILLIS)
        }
    }

    private fun setPhase(phase: InstallPhase, message: String) {
        mutableState.value = mutableState.value.copy(phase = phase, message = message)
        appendLog("[*] $message")
    }

    private fun appendLog(line: String) {
        val cleanLine = stripAnsi(line).trim()
        if (cleanLine.isBlank()) return
        mutableState.value = mutableState.value.copy(
            log = (mutableState.value.log + "\n" + cleanLine).trim(),
        )
        updateHistoryLog()
    }

    private fun startHistory() {
        val entry = historyStore.create()
        activeHistoryEntry = entry
        publishHistory(entry)
    }

    private fun updateHistory(transform: (InstallHistoryEntry) -> InstallHistoryEntry) {
        val entry = activeHistoryEntry ?: return
        val updated = transform(entry)
        activeHistoryEntry = updated
        historyStore.save(updated)
        publishHistory(updated)
    }

    private fun updateHistoryLog() =
        updateHistory { it.copy(log = mutableState.value.log) }

    /**
     * Records which target ran and which catalog defined it, including the commit that catalog was
     * read at, so a finished run can be traced back to a revision rather than to a branch name.
     */
    private fun updateHistoryTarget(profile: TargetProfile) =
        updateHistory { entry ->
            entry.copy(
                profileId = profile.profileId,
                sourceId = profile.sourceId.takeIf(String::isNotBlank),
                sourceLabel = profile.sourceLabel.takeIf(String::isNotBlank),
                sourceCommit = profile.sourceCommit.takeIf(String::isNotBlank),
            )
        }

    private fun finishHistory(result: InstallRunResult) {
        updateHistory { entry ->
            entry.copy(
                completedAtMillis = System.currentTimeMillis(),
                result = result,
                log = mutableState.value.log,
            )
        }
        activeHistoryEntry = null
    }

    private fun publishHistory(entry: InstallHistoryEntry) {
        mutableHistory.value = (mutableHistory.value.filterNot { it.id == entry.id } + entry)
            .sortedByDescending(InstallHistoryEntry::startedAtMillis)
    }

    private fun File.readTextIfPresent(): String = if (exists()) readText() else ""

    companion object {
        /** How often the settle countdown is redrawn; a second would look like it stutters. */
        private const val BOOT_SETTLE_TICK_MILLIS = 500L

        private const val EXPLOIT_STALL_MILLIS = 90_000L
        private const val EXPLOIT_TOTAL_MILLIS = 900_000L
        // A profile that needs one fresh P0 session hands the pacing to the payload, and the
        // payloads that ask for it scan pages for far longer than the cached multi-attempt budget
        // ever needed: the fresh-session proposal allowed a single 840-second attempt, and the
        // controlled-page-scan one 1200 s of scan plus 2200 s of attempt. A 15-minute ceiling would
        // cut exactly those runs off, so for marked profiles the ceiling is the longest envelope
        // either of those needed. It is a limit, not a schedule - a run still ends when the payload
        // finishes.
        private const val EXPLOIT_TOTAL_MILLIS_FRESH = 3_600_000L
        private const val HELPER_TIMEOUT_MILLIS = 120_000L

        private const val MODULES_DIRECTORY = "/data/adb/modules"
        private const val MODULES_BACKUP_DIRECTORY = "/data/adb/modules_rmg_backup"
        private const val MODULES_MOVED_MARKER = "modules-moved"
        private const val MODULES_RESTORED_MARKER = "modules-restored"
        private const val MODULES_BACKUP_EXISTS = 5

        // `mv` into an existing directory nests the source inside it, so the two scripts below
        // check for the backup first and refuse rather than bury a module tree somewhere else.
        /** Where a wireless run stages its helper, payload and log, all under the shell's own directory. */
        private const val ADB_HELPER_PATH = "/data/local/tmp/rmg-ksud-helper"
        private const val ADB_PAYLOAD_PATH = "/data/local/tmp/rmg-payload"
        private const val ADB_LOG_PATH = "/data/local/tmp/rmg-exploit.log"

        private val MODULES_ASIDE_SCRIPT = """
            if [ -d $MODULES_BACKUP_DIRECTORY ] && [ ! -e $MODULES_DIRECTORY ]; then
                /system/bin/mv $MODULES_BACKUP_DIRECTORY $MODULES_DIRECTORY || exit 3
            fi
            if [ -d $MODULES_BACKUP_DIRECTORY ]; then
                exit $MODULES_BACKUP_EXISTS
            fi
            if [ -d $MODULES_DIRECTORY ]; then
                /system/bin/mv $MODULES_DIRECTORY $MODULES_BACKUP_DIRECTORY || exit 4
                echo $MODULES_MOVED_MARKER
            fi
        """.trimIndent()

        private val MODULES_RESTORE_SCRIPT = """
            if [ -d $MODULES_BACKUP_DIRECTORY ]; then
                if [ -e $MODULES_DIRECTORY ]; then
                    echo modules-already-present
                else
                    /system/bin/mv $MODULES_BACKUP_DIRECTORY $MODULES_DIRECTORY || exit 3
                    echo $MODULES_RESTORED_MARKER
                fi
            fi
        """.trimIndent()
        private const val INSTALL_RECEIPT = "install_receipt"
        private const val RECEIPT_BOOT_TOKEN = "kernel_boot_id"
        private const val RECEIPT_VERIFIED = "verified"
        private const val P0_CACHE = "p0_cache"
        private const val P0_CACHE_BOOT_TOKEN = "kernel_boot_id"
        private const val P0_CACHE_OFFSET = "offset"
        private const val P0_OFFSET_MAX = 0x1f0000L
        private const val P0_OFFSET_MASK = 0xffffL
        private const val SHIZUKU_LOG_PATH = "/data/local/tmp/ksu-exploit.log"
        private const val SHIZUKU_HELPER_PATH = "/data/local/tmp/ksu-helper"
        private const val SHIZUKU_PAYLOAD_PATH = "/data/local/tmp/ksu-payload"
        private const val SHIZUKU_KSUD_PATH = "/data/local/tmp/ksud-s25u-kdp"
        private const val SHIZUKU_KSUD_STAGE_PATH = "/data/local/tmp/.ksud-stage"
        private val LOG_POLL_INTERVAL = 250.milliseconds
        private val HELPER_POLL_INTERVAL = 250.milliseconds
        private val SHIZUKU_LOG_POLL_INTERVAL = 1.seconds
        private val ANSI_ESCAPE = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
        private val P0_OFFSET_PATTERN = Regex(
            "slide-kaslr-ok[^\\n]*slide=([0-9a-fA-F]{16})",
        )

        /** How long a run may take before the app gives up on it. */
        internal fun exploitTotalMillis(requiresFreshP0Session: Boolean): Long =
            if (requiresFreshP0Session) EXPLOIT_TOTAL_MILLIS_FRESH else EXPLOIT_TOTAL_MILLIS

        /**
         * The environment and cut-offs a run gets, assembled from the same constants the run uses
         * so the run-plan screen cannot drift from what actually happens. The direct transport
         * inherits the app's environment and only overrides these names; the Shizuku transport
         * passes its whole environment as `NAME=value` arguments, so the staged payload and helper
         * paths are part of it.
         */
        internal fun exploitPlan(
            requiresFreshP0Session: Boolean,
            cachedP0Offset: String?,
            shizuku: Boolean,
            routePolicy: ExploitRoutePolicy = ExploitRoutePolicy.LEGACY,
            bootSettleSeconds: Int = 0,
        ): ExploitPlan = ExploitPlan(
            bootSettleSeconds = BootSettle.normalize(bootSettleSeconds),
            environment = exploitEnvironment(requiresFreshP0Session, cachedP0Offset, routePolicy),
            shizukuArguments = if (shizuku) {
                mapOf(
                    "CVE43499_ROOT_HELPER" to SHIZUKU_HELPER_PATH,
                    "LD_PRELOAD" to SHIZUKU_PAYLOAD_PATH,
                )
            } else {
                emptyMap()
            },
            stallLimitMillis = if (requiresFreshP0Session) null else EXPLOIT_STALL_MILLIS,
            totalLimitMillis = exploitTotalMillis(requiresFreshP0Session),
            helperLimitMillis = HELPER_TIMEOUT_MILLIS,
        )

        internal fun exploitEnvironment(
            requiresFreshP0Session: Boolean,
            cachedP0Offset: String?,
            routePolicy: ExploitRoutePolicy = ExploitRoutePolicy.LEGACY,
        ): Map<String, String> = buildMap {
            // A fresh-session profile hands its pacing to the payload, so the policy's attempt and
            // timeout budget does not apply to it. The route still does: which way the payload finds
            // the slide is a different question from how many tries it gets.
            put(
                "EXPLOIT_ATTEMPTS",
                if (requiresFreshP0Session) "1" else routePolicy.attempts.toString(),
            )
            if (!requiresFreshP0Session) {
                put("P0_ATTEMPT_TIMEOUT_SEC", routePolicy.p0AttemptTimeoutSec.toString())
                put("EXPLOIT_ATTEMPT_TIMEOUT_SEC", routePolicy.attemptTimeoutSec.toString())
                if (routePolicy.p0OffsetCache) {
                    cachedP0Offset?.let { put(ExploitRoutePolicy.P0_OFFSET_ENV, it) }
                }
            }
            routePolicy.slideRoute.env?.let { put(ExploitRoutePolicy.SLIDE_SOURCE_ENV, it) }
        }

        private fun stripAnsi(value: String): String = ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
