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
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class InstallPhase {
    Checking,
    Ready,
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
     * takes — the boot service, which cannot wait on the UI state itself.
     */
    suspend fun runToCompletion(selectionId: String? = null, forceStandalone: Boolean = false) {
        install(selectionId, forceStandalone)
        installJob?.join()
    }

    fun install(selectionId: String? = null, forceStandalone: Boolean = false) {
        if (installJob?.isActive == true || mutableState.value.phase == InstallPhase.Installed) return
        discoveryJob?.cancel()
        installJob = viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = InstallUiState(
                phase = InstallPhase.Checking,
                probeOutput = mutableState.value.probeOutput,
            )
            startHistory()
            // Freeze the transport for the whole run so a mid-run preference
            // change cannot mix Shizuku and standalone execution between the
            // exploit and the KernelSU staging steps. The boot service asks for
            // standalone explicitly rather than by toggling the stored
            // preference, which would leave it wrong if the run never finished.
            activeRunShizuku = !forceStandalone && AppPreferences.shizukuMode(app)
            try {
                if (shizukuEnabled()) {
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
                activeStage = RunStage.Target
                setPhase(InstallPhase.Checking, app.getString(R.string.status_checking_github))
                val profile = if (selectionId == null) {
                    repository.resolveTarget(DeviceSnapshot.current())
                } else {
                    repository.resolveTarget(selectionId)
                }
                appendLog(app.getString(R.string.log_profile, profile.profileId))
                if (profile.sourceLabel.isNotEmpty()) {
                    appendLog(app.getString(R.string.log_payload_source, profile.sourceLabel))
                }
                updateHistoryProfile(profile.profileId)

                activeStage = RunStage.Download
                setPhase(InstallPhase.Downloading, app.getString(R.string.status_downloading_payload))
                val payloads = repository.download(profile) { appendLog("[*] $it") }
                appendLog(app.getString(R.string.log_download_verified))

                activeStage = RunStage.Exploit
                setPhase(InstallPhase.Exploiting, app.getString(R.string.status_exploit_running))
                executeExploit(payloads.exploit, profile.requiresFreshP0Session)

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

                setPhase(InstallPhase.Installed, app.getString(R.string.status_ksu_active))
                appendLog(app.getString(R.string.log_install_complete))
                finishHistory(InstallRunResult.Succeeded)
            } catch (error: Throwable) {
                // The stage and the last payload output travel with the failure: the message alone
                // is the same for a download that failed and an exploit that gave up, and only one
                // of those is worth retrying straight away.
                val stage = activeStage
                val reason = error.message ?: error.javaClass.simpleName
                val failure = RunFailure(stage, reason, failureEvidence(mutableState.value.log))
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
        val result = runHelper("-c", MODULES_ASIDE_SCRIPT)
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
        val result = runHelper("-c", MODULES_RESTORE_SCRIPT)
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

    private suspend fun executeExploit(payload: File, requiresFreshP0Session: Boolean) {
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
                exploitEnvironment(requiresFreshP0Session, cachedP0Offset),
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
            // Both transports drain into `captured` during the poll loop, so
            // this never blocks on a child still holding the pipe open.
            val earlyOutput = captured.toString().trim()
            require(exitCode == 0) {
                app.getString(
                    R.string.error_payload_exit,
                    exitCode,
                    earlyOutput.takeIf(String::isNotBlank)?.let { " ($it)" } ?: "",
                )
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
        storeInstallReceipt()
        appendLog(app.getString(R.string.log_ksu_control_verified))
    }

    private fun detectInstalled(): Boolean {
        if (NativeProbe.isKernelSuActive()) return true
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

    private fun currentBootToken(): String? = runCatching {
        File("/proc/sys/kernel/random/boot_id")
            .readText(Charsets.US_ASCII)
            .trim()
            .takeIf(String::isNotBlank)
    }.getOrNull()

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
    ): Array<String> = buildList {
        exploitEnvironment(requiresFreshP0Session, cachedP0Offset).forEach { (name, value) ->
            add("$name=$value")
        }
        add("CVE43499_ROOT_HELPER=$helperPath")
        add("LD_PRELOAD=$payloadPath")
    }.toTypedArray()

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

    private fun updateHistoryProfile(profileId: String) =
        updateHistory { it.copy(profileId = profileId) }

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
        private const val EXPLOIT_ATTEMPTS = "24"
        private const val P0_ATTEMPT_TIMEOUT_SEC = "45"
        private const val EXPLOIT_ATTEMPT_TIMEOUT_SEC = "120"
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
        private const val P0_OFFSET_ENV = "SLIDE_P0_OFFSET"
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
        ): ExploitPlan = ExploitPlan(
            environment = exploitEnvironment(requiresFreshP0Session, cachedP0Offset),
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
        ): Map<String, String> = buildMap {
            put("EXPLOIT_ATTEMPTS", if (requiresFreshP0Session) "1" else EXPLOIT_ATTEMPTS)
            if (!requiresFreshP0Session) {
                put("P0_ATTEMPT_TIMEOUT_SEC", P0_ATTEMPT_TIMEOUT_SEC)
                put("EXPLOIT_ATTEMPT_TIMEOUT_SEC", EXPLOIT_ATTEMPT_TIMEOUT_SEC)
                cachedP0Offset?.let { put(P0_OFFSET_ENV, it) }
            }
        }

        private fun stripAnsi(value: String): String = ANSI_ESCAPE.replace(value, "").replace("\r", "")
    }
}
