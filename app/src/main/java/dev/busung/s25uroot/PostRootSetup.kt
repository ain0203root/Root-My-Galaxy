package dev.busung.s25uroot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What a run does once KernelSU is verified, while it still holds root.
 *
 * One thing, and it is the one permission the app cannot give itself any other way. Turning wireless
 * debugging on needs `WRITE_SECURE_SETTINGS`, which is development-flagged: the user grants it over a
 * cable with `adb install -g` or `pm grant`, and without it the wireless transport only works when
 * they have already switched wireless debugging on by hand. A rooted boot can close that gap without
 * a cable, so it does - once, at the moment root is known to be real.
 *
 * Nothing here fails a run. The root that was just obtained is the result; a permission that did not
 * get granted is a line in the log, because the fallback (`adb install -g`, or the user's own hands)
 * still exists and a client that just got root should not lose it over a bonus.
 */
internal object PostRootSetup {

    /**
     * The grant to run, or null when there is nothing to do.
     *
     * Pure, so the quoting can be tested: the package name is the app's own, but a command assembled
     * by string concatenation is exactly the place where a name with a quote in it turns into a
     * second command.
     */
    internal fun grantCommand(packageName: String, alreadyGranted: Boolean): String? =
        if (alreadyGranted) {
            null
        } else {
            "pm grant ${shellQuote(packageName)} android.permission.WRITE_SECURE_SETTINGS"
        }

    /**
     * Runs the grant over whatever root shell the device will give us.
     *
     * The transport is asked for rather than assumed: after the late-load, a Samsung kernel may refuse
     * new connections to the bootstrap helper while KernelSU itself is healthy, so the shell that the
     * rest of the app uses after root is the one tried here. No shell is not a failure either - it
     * means the grant is left to the next boot, which has one.
     */
    suspend fun apply(context: Context): PostRootOutcome = withContext(Dispatchers.IO) {
        val granted = runCatching { AdbPairing.hasWriteSecureSettings(context) }.getOrDefault(false)
        val command = grantCommand(context.packageName, granted)
            ?: return@withContext PostRootOutcome.AlreadyGranted

        val shell = runCatching { KernelSuRuntime.rootShell(command) }.getOrNull()
            ?: return@withContext PostRootOutcome.NoRootShell
        if (shell.exitCode == 0) {
            PostRootOutcome.Granted
        } else {
            PostRootOutcome.Failed(shell.output.trim().takeLast(160))
        }
    }
}

/** What the post-root setup managed, so the run can say it rather than guess. */
internal sealed interface PostRootOutcome {
    data object AlreadyGranted : PostRootOutcome
    data object Granted : PostRootOutcome
    data object NoRootShell : PostRootOutcome
    data class Failed(val detail: String) : PostRootOutcome

    fun logLine(context: Context): String = when (this) {
        AlreadyGranted -> context.getString(R.string.log_postroot_permission_present)
        Granted -> context.getString(R.string.log_postroot_permission_granted)
        NoRootShell -> context.getString(R.string.log_postroot_no_shell)
        is Failed -> context.getString(R.string.log_postroot_permission_failed, detail)
    }
}
