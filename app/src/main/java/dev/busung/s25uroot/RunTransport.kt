package dev.busung.s25uroot

/** Where a run's payload is executed. */
internal enum class RunTransport {
    /** The app's own process, running the helper directly. The default for a normal payload. */
    App,

    /** Shizuku's shell, and the helper staged where that shell can reach it. */
    Shizuku,

    /** The device's own adbd over wireless debugging, paired by this app. */
    LocalAdb,
}

/** What a run has available, from the feed's policy and from the device. */
internal data class TransportAvailability(
    val shellRequired: Boolean,
    val shizukuRequested: Boolean,
    val shizukuUsable: Boolean,
    val localAdbPaired: Boolean,
)

/**
 * Which transport a run gets, or null when nothing can carry it.
 *
 * Two rules, and both matter more than the ordering of the rest:
 *
 * - **A payload that needs a shell never falls back to the app's own domain.** The feed says a target
 *   only works from a shell context, which is a statement about what the payload can do there; running
 *   it as the app anyway would produce a failure that looks like the payload's fault. So the answer is
 *   null, and the run says which of the two transports it needed and what is wrong with each.
 * - **A usable Shizuku wins over a paired local ADB**, because a Shizuku session is already
 *   authenticated and needs no window in which wireless debugging is on - the local ADB path turns a
 *   device setting on and off around itself, which is worth avoiding when it does not have to happen.
 *   A *requested but unusable* Shizuku does not win: the point of asking is to get a shell, and a
 *   pairing that is actually there beats a preference that is not.
 *
 * Pure, so every combination can be checked without a device - which matters because the ones that go
 * wrong are the ones where two things are half-true at once.
 */
internal fun chooseRunTransport(
    shellRequired: Boolean,
    shizukuRequested: Boolean,
    shizukuUsable: Boolean,
    localAdbPaired: Boolean,
): RunTransport? = when {
    shellRequired -> when {
        shizukuRequested && shizukuUsable -> RunTransport.Shizuku
        localAdbPaired -> RunTransport.LocalAdb
        else -> null
    }
    shizukuRequested && shizukuUsable -> RunTransport.Shizuku
    else -> RunTransport.App
}

/**
 * What the local-ADB command prints when it is done, since the ADB shell carries no exit code.
 *
 * It is deliberately not ADB's own `__ADB_EXIT__=` marker: this one is part of the *payload* command's
 * output, and the two must stay distinguishable when the transport reports a failure of its own.
 */
internal const val ADB_EXIT_MARKER = "RMG_PAYLOAD_EXIT="

/**
 * The exit code a local-ADB payload run reported, or a failure code when it reported none.
 *
 * The last marker is used, because the payload's own output is streamed ahead of it and could contain
 * the marker's text; a run that could not report a code is [LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE],
 * so "no answer" is never read as success.
 */
internal fun localAdbExploitExitCode(output: String): Int {
    val markerIndex = output.lastIndexOf(ADB_EXIT_MARKER)
    if (markerIndex < 0) return LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE
    return output.substring(markerIndex + ADB_EXIT_MARKER.length)
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.toIntOrNull()
        ?: LocalAdbClient.UNKNOWN_SHELL_EXIT_CODE
}

/**
 * Why a run could not start, as the string that names both transports it could have used.
 *
 * Naming both is the point: "no shell transport" leaves the user guessing whether a setting is off,
 * whether a pairing is missing, or whether Shizuku is installed but not running, and each of those has
 * a different answer. Two cases rather than one because "Shizuku is on and silent" and "Shizuku is off"
 * send the user to different places - one to a button that starts it, the other to a setting.
 */
internal fun shellTransportRefusalStringId(shizukuRequested: Boolean): Int =
    if (shizukuRequested) {
        R.string.error_shell_transport_shizuku_silent
    } else {
        R.string.error_shell_transport_shizuku_off
    }
