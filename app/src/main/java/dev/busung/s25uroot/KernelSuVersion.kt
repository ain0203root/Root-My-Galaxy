package dev.busung.s25uroot

import android.content.Context

/**
 * The KernelSU this phone is actually running, as far as it can be read.
 *
 * [nothingRead] is a real answer and not an error: before the first successful run there is no
 * KernelSU on the phone to ask, and a manager that was installed by hand is the only thing there is.
 * The app says what it could read and claims nothing about what it could not, because a version pair
 * invented on a missing reading would be a warning about nothing.
 */
internal data class KernelSuVersionReading(
    /**
     * The userspace KernelSU - `ksud` - and the release it was built from, e.g. `3.3.0`.
     *
     * This is what a payload stages: the same build's kernel module is what a manager talks to, so
     * comparing a manager against this compares it against the KernelSU in this boot. The kernel-side
     * width of that pairing ([kernelCode]) is read in the same round trip and kept for the log, but it
     * is not what the comparison uses: it is a number with no name, and the manager's own version is a
     * name.
     */
    val daemon: String? = null,

    /** KernelSU's own `version` number for the loaded module, when the kernel answered for it. */
    val kernelCode: Int? = null,
) {
    /** Whether anything answered at all. */
    val isRead: Boolean get() = daemon != null || kernelCode != null
}

/** `ksud 3.3.0 (uapi: 3)`, and the same with the project's name or the `v` in front of it. */
private val DOTTED_VERSION = Regex("""(\d+\.\d+(?:\.\d+)*)""")

/** The line `ksud debug version` prints for the kernel-side module. */
private val KERNEL_VERSION_LINE = Regex("""Kernel\s+Version:\s*(\d+)""")

private const val DAEMON_MARKER = "RMG_DAEMON="
private const val KERNEL_MARKER = "RMG_KERNEL="

/**
 * The version in one line of `ksud --version` output, or null when there is not one.
 *
 * `ksud` is clap-based with `version = defs::FULL_VERSION`, which is `"{VERSION_NAME} (uapi: {n})"`
 * with the tag's leading `v` stripped - so the first dotted number is the release, and the `uapi`
 * beside it is a protocol number that must never be mistaken for one. That is why this looks for a
 * dotted number rather than for a number: `3` alone is not a version this app will compare against a
 * manager's.
 */
internal fun parseKsudVersion(output: String): String? =
    DOTTED_VERSION.find(output)?.groupValues?.get(1)

/**
 * The kernel-side version number `ksud debug version` reports, or null when it did not.
 *
 * Zero is not a version: the call reports zero when the module is not there to answer, which is the
 * difference between "no KernelSU" and "KernelSU 0".
 */
internal fun parseKernelVersionCode(output: String): Int? =
    KERNEL_VERSION_LINE.find(output)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

/** Both readings from one shell round trip's output, which is why they are marked apart. */
internal fun parseVersionReadings(output: String): KernelSuVersionReading {
    var daemon: String? = null
    var kernelCode: Int? = null
    output.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith(DAEMON_MARKER) ->
                daemon = parseKsudVersion(trimmed.removePrefix(DAEMON_MARKER))
            trimmed.startsWith(KERNEL_MARKER) ->
                kernelCode = parseKernelVersionCode(trimmed.removePrefix(KERNEL_MARKER))
        }
    }
    return KernelSuVersionReading(daemon = daemon, kernelCode = kernelCode)
}

/**
 * The release part of a version name, or null when the name has no release in it.
 *
 * A build can carry a suffix the release does not - `3.3.0-ksun` against `3.3.0` - and a tag can
 * carry a `v`, so the dotted number is taken out of the name rather than the name trimmed to match.
 * Anything with no dotted number in it is not a version this app will compare: a package manager
 * answer of `unknown` is a reading that failed, and comparing it as text is how an unreadable manager
 * becomes a warning about a mismatch nobody has.
 */
internal fun releaseOf(version: String?): String? =
    DOTTED_VERSION.find(version?.trim().orEmpty())?.groupValues?.get(1)

/** What the app can say about the manager on the phone against the KernelSU that is running. */
internal enum class ManagerVersionState {
    /** One of the two readings is missing, so there is nothing to compare and nothing to claim. */
    Unknown,

    /** The manager's version and the running KernelSU's are the same release. */
    Matching,

    /** They are not the same release, which is the case the app could not see until now. */
    Differing,
}

/**
 * Whether the installed manager matches the KernelSU this boot is running.
 *
 * What this is for: a manager is a plain app that talks to the loaded module over KernelSU's socket,
 * so any version can be installed at any time - and a manager from one line against a kernel from
 * another is the one pairing the app had no way to see, having only ever known the version it would
 * install itself. Nothing here refuses either of them; it is a reading, and the screen says it.
 */
internal fun managerVersionState(managerVersion: String?, kernelVersion: String?): ManagerVersionState {
    val manager = releaseOf(managerVersion) ?: return ManagerVersionState.Unknown
    val kernel = releaseOf(kernelVersion) ?: return ManagerVersionState.Unknown
    return if (manager == kernel) ManagerVersionState.Matching else ManagerVersionState.Differing
}

/**
 * The version the warning's own action should install, or null when there is nothing to act on.
 *
 * Only a mismatch, and only when the version to install is a version rather than an empty reading: an
 * "install" button on a row where nothing could be read would be a control that cannot work, which is
 * the same as no control. The version offered is the **running KernelSU's**, not the flavour's default:
 * the default is a decision this app made, and the kernel is the thing actually asking.
 */
internal fun managerMismatchTarget(
    state: ManagerVersionState,
    kernelVersion: String?,
): String? = kernelVersion?.takeIf { state == ManagerVersionState.Differing }

/**
 * The KernelSU version, read from the device.
 *
 * One root shell for both readings, because each shell is a process of its own and the two questions
 * are asked at the same moment: `ksud -V` for the userspace build, and `ksud debug version` for the
 * kernel-side module. `ksud` is asked where the payload leaves it first - `/data/adb/ksud` - and by
 * name second, so a device whose daemon is elsewhere is still readable.
 *
 * The read goes through [KernelSuRuntime.rootShell], which is KernelSU's own `su` or a Shizuku server
 * that already holds root. That makes the reading available exactly when it means something: with no
 * KernelSU loaded there is no daemon to ask about, and no shell to ask with.
 */
internal object KernelSuVersionProbe {

    /**
     * The command, in one `su -c` invocation.
     *
     * `2>/dev/null` inside rather than outside, so a `ksud` that is missing or refuses prints nothing
     * for its own line and leaves the other one intact. The markers are what keep the two answers
     * apart, since both are read as one stream of output.
     */
    private val COMMAND = listOf(
        "K=/data/adb/ksud",
        "[ -x \"${'$'}K\" ] || K=ksud",
        "echo \"$DAEMON_MARKER${'$'}(\"${'$'}K\" -V 2>/dev/null || \"${'$'}K\" --version 2>/dev/null)\"",
        "echo \"$KERNEL_MARKER${'$'}(\"${'$'}K\" debug version 2>/dev/null)\"",
    ).joinToString("; ")

    /** The reading already made in this boot, which does not change until the phone restarts. */
    @Volatile
    private var cachedBootToken: String? = null

    @Volatile
    private var cached: KernelSuVersionReading? = null

    /**
     * Reads the running KernelSU's version, or says that nothing answered.
     *
     * Cached per kernel boot, and only when something answered: the daemon cannot change without a
     * restart or a new run, and a reading of nothing is worth asking again - the usual reason for it
     * is a grant this app has not been given yet, which is exactly what changes between two visits to
     * this screen.
     */
    fun read(context: Context): KernelSuVersionReading {
        val bootToken = kernelBootToken()
        if (bootToken != null && bootToken == cachedBootToken) {
            cached?.let { return it }
        }
        val reading = runCatching { readNow() }.getOrNull() ?: KernelSuVersionReading()
        AppLog.debug(
            AppLogTags.KERNEL_SU,
            "Read the running KernelSU: " +
                "daemon=${reading.daemon ?: "no answer"}, kernel=${reading.kernelCode ?: "no answer"}",
        )
        if (bootToken != null && reading.isRead) {
            cachedBootToken = bootToken
            cached = reading
        }
        return reading
    }

    private fun readNow(): KernelSuVersionReading? {
        val result = KernelSuRuntime.rootShell(COMMAND) ?: return null
        return parseVersionReadings(result.output)
    }
}
