package dev.busung.s25uroot

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** How a start attempt ended, and by which route. */
internal data class ShizukuStartOutcome(
    val started: Boolean,
    val method: String? = null,
    val detail: String = "",
)

/**
 * The command that starts a modern Shizuku build's server for this app.
 *
 * Shizuku ships its starter as a native library inside its own APK, and it needs the path of the
 * APK it belongs to, which is what the `--apk=` argument is for. Kept as a function of its inputs so
 * the quoting and the argument shape can be tested without a device.
 */
internal fun shizukuStarterCommand(starterPath: String, apkPath: String): String =
    "${shellQuote(starterPath)} --apk=${shellQuote(apkPath)}"

/**
 * Starts Shizuku's server without a computer, using the root this app already put on the device.
 *
 * Shizuku is what this app uses to run the payload as shell, and it is normally started by the user
 * over adb - so after a reboot the transport is simply gone until someone finds a cable. Once
 * KernelSU is loaded that is unnecessary: KernelSU's own root shell can run Shizuku's starter, which
 * is what this does. It is deliberately not a general "start Shizuku" button for a device with no
 * root: without root or adb the app has no way to start a privileged process, and pretending
 * otherwise would only produce a button that does nothing.
 *
 * Current Shizuku builds expose the native starter; older or manually installed builds may have
 * dropped a `start.sh` on shared storage. The native route is always preferred and a missing legacy
 * script is not an error, so a device that has neither is reported as such once rather than as two
 * failures.
 */
internal object ShizukuStarter {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val BINDER_RACE_PROBE_MILLIS = 500L

    /** A binder that appears just after a probe means another starter won the race, not a failure. */
    private val LEGACY_START_PATHS = listOf(
        "/storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
        "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
    )

    private val startLock = Mutex()

    /**
     * [shell] runs a command as root and is expected to be KernelSU's shell. The binder is re-probed
     * immediately before every launch because a start from this app racing another starter (the
     * Shizuku app itself, or a previous boot's attempt) is otherwise indistinguishable from one that
     * never took effect.
     */
    suspend fun start(
        context: Context,
        shell: (String) -> ShizukuController.ShellResult,
        binderTimeoutMillis: Long = DEFAULT_BINDER_TIMEOUT_MILLIS,
        onLog: (String) -> Unit = {},
    ): ShizukuStartOutcome = startLock.withLock {
        if (ShizukuController.pingUntilRunning(BINDER_RACE_PROBE_MILLIS)) {
            onLog("[+] Shizuku is already running; no starter needed")
            return@withLock ShizukuStartOutcome(started = true, method = METHOD_ALREADY_RUNNING)
        }

        val native = nativeStarter(context)
        if (native == null) {
            onLog("[*] The Shizuku app is not installed; only a legacy starter could be used")
        } else if (shell("test -f ${shellQuote(native.starterPath)}").exitCode != 0) {
            onLog("[*] This Shizuku build has no native starter; checking the legacy script")
        } else {
            if (ShizukuController.pingUntilRunning(BINDER_RACE_PROBE_MILLIS)) {
                return@withLock ShizukuStartOutcome(started = true, method = METHOD_ALREADY_RUNNING)
            }
            onLog("[*] Starting Shizuku with its own native starter")
            val result = shell(shizukuStarterCommand(native.starterPath, native.apkPath))
            if (result.exitCode == 0 && ShizukuController.pingUntilRunning(binderTimeoutMillis)) {
                onLog("[+] Shizuku started and its binder answered")
                return@withLock ShizukuStartOutcome(started = true, method = METHOD_NATIVE)
            }
            onLog(
                "[!] The native starter " +
                    if (result.exitCode != 0) {
                        "exited ${result.exitCode}${result.output.trim().takeLast(240).let {
                            if (it.isBlank()) "" else ": $it"
                        }}; checking the legacy script"
                    } else {
                        "finished but no binder followed; checking the legacy script"
                    },
            )
        }

        if (ShizukuController.pingUntilRunning(BINDER_RACE_PROBE_MILLIS)) {
            return@withLock ShizukuStartOutcome(started = true, method = METHOD_ALREADY_RUNNING)
        }

        val legacy = legacyStartScript(shell)
        if (legacy != null) {
            onLog("[*] Starting Shizuku with the legacy start.sh")
            val result = shell("sh ${shellQuote(legacy)} 2>&1")
            if (result.exitCode == 0 && ShizukuController.pingUntilRunning(binderTimeoutMillis)) {
                onLog("[+] Shizuku started and its binder answered")
                return@withLock ShizukuStartOutcome(started = true, method = METHOD_LEGACY)
            }
            val detail = result.output.trim().takeLast(240).ifBlank { "exit ${result.exitCode}" }
            val reason = "the legacy start.sh did not produce a binder: $detail"
            onLog("[!] $reason")
            return@withLock ShizukuStartOutcome(started = false, method = METHOD_LEGACY, detail = reason)
        }

        val detail = "no Shizuku starter on this device produced a binder"
        onLog("[!] $detail")
        return@withLock ShizukuStartOutcome(started = false, detail = detail)
    }

    /** Where Shizuku's own starter and its APK live, when Shizuku is installed. */
    private fun nativeStarter(context: Context): NativeStarter? {
        val info = runCatching {
            context.packageManager.getApplicationInfo(SHIZUKU_PACKAGE, 0)
        }.getOrNull() ?: return null
        val libraryDir = info.nativeLibraryDir?.takeIf(String::isNotBlank) ?: return null
        val apkPath = info.sourceDir?.takeIf(String::isNotBlank) ?: return null
        return NativeStarter(
            starterPath = File(libraryDir, "libshizuku.so").absolutePath,
            apkPath = apkPath,
        )
    }

    /**
     * The first legacy start script that exists, asked of the device rather than of the app, because
     * shared storage is a different filesystem from the one the app sees.
     */
    private fun legacyStartScript(
        shell: (String) -> ShizukuController.ShellResult,
    ): String? = firstPresent(LEGACY_START_PATHS) { path ->
        shell("test -f ${shellQuote(path)}").exitCode == 0
    }

    private data class NativeStarter(val starterPath: String, val apkPath: String)

    private const val METHOD_ALREADY_RUNNING = "already running"
    internal const val METHOD_NATIVE = "native starter"
    internal const val METHOD_LEGACY = "legacy start.sh"
    internal const val DEFAULT_BINDER_TIMEOUT_MILLIS = 20_000L
}

/**
 * The exit code a command reports when there was no root shell to run it in.
 *
 * It is deliberately outside the range a real command uses, so "never ran" cannot be read as
 * "ran and failed with something specific".
 */
internal const val NO_ROOT_SHELL_EXIT = 127

/** The first candidate that a probe accepts, in order; null when none do. */
internal fun firstPresent(candidates: List<String>, probe: (String) -> Boolean): String? =
    candidates.firstOrNull(probe)
