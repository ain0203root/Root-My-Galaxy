package dev.busung.s25uroot

import android.content.Context

/**
 * Proof that the KernelSU control channel is live for this boot.
 *
 * The app used to take the helper's exit code for that, which says the late-load command finished
 * rather than that anything is reachable afterwards: a loaded-but-unreachable channel and a healthy
 * one were the same result. Each proof below is independent and one is enough, because a single
 * reliable check would have to be one the app cannot always make - the boot-time late-load runs as
 * shell, and a Samsung kernel may refuse new connects to the handoff socket even though KernelSU
 * itself is healthy.
 */
internal enum class ControlProof(val label: String) {
    /** The in-process native probe sees KernelSU from the app's own process. */
    NativeProbe("native probe"),

    /** KernelSU's own `su` answered through Shizuku, which also proves a usable root shell. */
    ShizukuElevation("KernelSU su through Shizuku"),

    /** The helper printed a live control report, which is a reading rather than an exit status. */
    HelperReport("helper control report"),
}

/** KernelSU's own reading of the control channel, as the helper prints it. */
internal data class KernelSuControl(
    val version: Int,
    val flags: Int,
    val uapi: Int,
    val features: Int,
)

private val CONTROL_REPORT = Regex(
    "version=(\\d+)\\s+flags=(\\S+)\\s+uapi=(\\d+)\\s+features=(\\S+)",
)

private fun String.hexOrNull(): Int? =
    removePrefix("0x").removePrefix("0X").toIntOrNull(16)

/**
 * Reads the helper's control line, e.g.
 * `KernelSU control verified version=33214 flags=0x7 uapi=4 features=0x3`.
 *
 * A version of zero is not a live channel, so it does not count as a report.
 */
internal fun parseControlReport(output: String): KernelSuControl? {
    val match = CONTROL_REPORT.find(output) ?: return null
    val (version, flags, uapi, features) = match.destructured
    val control = KernelSuControl(
        version = version.toIntOrNull() ?: return null,
        flags = flags.hexOrNull() ?: return null,
        uapi = uapi.toIntOrNull() ?: return null,
        features = features.hexOrNull() ?: return null,
    )
    return control.takeIf { it.version > 0 }
}

/**
 * The proof set from three independent readings.
 *
 * Pure, so which proof sets count as ready can be tested without a rooted device.
 */
internal fun controlProofs(
    nativeProbe: Boolean,
    shizukuElevated: Boolean,
    helperOutput: String,
): Set<ControlProof> = buildSet {
    if (nativeProbe) add(ControlProof.NativeProbe)
    if (shizukuElevated) add(ControlProof.ShizukuElevation)
    if (parseControlReport(helperOutput) != null) add(ControlProof.HelperReport)
}

/**
 * The live probes behind [controlProofs], for the app to call on the device.
 *
 * Everything here is best-effort and reports absence rather than throwing: a probe failing is a
 * reason not to claim control, not a reason for a run to die with a stack trace.
 */
internal object KernelSuRuntime {
    fun proofs(context: Context, helperOutput: String): Set<ControlProof> = controlProofs(
        nativeProbe = runCatching { NativeProbe.isKernelSuActive() }.getOrDefault(false),
        shizukuElevated = shizukuElevation(),
        helperOutput = helperOutput,
    )

    /**
     * Runs [command] as root, using KernelSU itself rather than the bootstrap handoff.
     *
     * The helper's handoff socket exists only to cross the pre-KernelSU boundary; once KernelSU
     * has loaded, a Samsung kernel may refuse new connects to it while KernelSU is perfectly
     * healthy. A command that has to keep working after root therefore asks KernelSU directly.
     * Returns null when no root shell can be had, so the caller can fall back instead of failing.
     */
    fun rootShell(command: String): ShizukuController.ShellResult? {
        if (!ShizukuController.isRunning() || !ShizukuController.isGranted()) return null
        // Shizuku's own process is already the shell uid, so a device that granted the late-load's
        // shell allowance answers `id` as root without a second escalation hop.
        val direct = runCatching { ShizukuController.shell("id") }.getOrNull()
        if (direct != null && direct.isRoot()) {
            return runCatching { ShizukuController.shell(command) }.getOrNull()
        }
        if (!(runCatching { ShizukuController.shell("su -c id") }.getOrNull()?.isRoot() == true)) {
            return null
        }
        return runCatching {
            ShizukuController.shell("su -c ${shellQuote(command)}")
        }.getOrNull()
    }

    private fun ShizukuController.ShellResult.isRoot(): Boolean =
        exitCode == 0 && output.contains("uid=0")

    /** Asks KernelSU itself for a root shell through the Shizuku server that is already running. */
    private fun shizukuElevation(): Boolean =
        runCatching { rootShell("id") }.getOrNull()?.isRoot() == true
}
