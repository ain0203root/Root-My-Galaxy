package dev.busung.s25uroot

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which post-root repair action is being run. */
internal enum class RecoveryTool {
    RestartZygote,
    SoftReboot,
    RebootAndUnroot,
}

/**
 * The one place a repair action is actually run.
 *
 * It began as the settings section's own code, and it was pulled out when the run screen wanted the
 * same thing: *Restart userspace* after an install that just loaded KernelSU is the same action as the
 * one in Settings, and two implementations of "ask for a root shell, check the boot, hand it to the
 * child" would be two places for the same three rules to drift apart - which is exactly how the restart
 * ended up with a window shorter than the wait behind it.
 *
 * The root shell is asked for here rather than checked for beforehand: the in-process reading of
 * KernelSU can say no on a device where root is usable, so the refusal has to come from the thing that
 * actually needs it. Which refusal it was is then read from the device rather than from the attempt, so
 * "no root here" and "no root for this app" keep their different fixes.
 *
 * The boot token is part of every action because all three change something the boot owns, and a child
 * that finds the boot has changed under it refuses in its own words.
 */
internal suspend fun runRecoveryAction(context: Context, tool: RecoveryTool): RecoveryOutcome =
    // Every shell here is a real process that is waited on, and two of the actions hold the channel
    // open until the child acknowledges them, so none of it may run on the UI thread.
    withContext(Dispatchers.IO) {
        val bootToken = kernelBootToken()
        val refusalDetail by lazy {
            context.getString(
                when (recoveryRefusal(KernelSuRuntime.loadedInThisBoot())) {
                    RecoveryRefusal.RootMissing -> R.string.recovery_root_required
                    RecoveryRefusal.ShellMissing -> R.string.recovery_shell_unavailable
                },
            )
        }
        when {
            KernelSuRuntime.rootShell("id") == null -> RecoveryOutcome(
                accepted = false,
                detail = refusalDetail,
            )
            bootToken == null -> RecoveryOutcome(
                accepted = false,
                detail = context.getString(R.string.error_boot_id),
            )
            else -> {
                val rootShell: (String) -> ShizukuController.ShellResult = { command ->
                    KernelSuRuntime.rootShell(command) ?: ShizukuController.ShellResult(
                        NO_ROOT_SHELL_EXIT,
                        refusalDetail,
                    )
                }
                when (tool) {
                    RecoveryTool.RestartZygote ->
                        RootRecovery.restartZygote(rootShell, bootToken)
                    RecoveryTool.SoftReboot -> RootRecovery.softReboot(
                        shell = rootShell,
                        bootToken = bootToken,
                        capabilities = RootRecovery.capabilities(rootShell) ?: KsudCapabilities(),
                    )
                    RecoveryTool.RebootAndUnroot -> {
                        // Cleared before the reboot is asked for, and put back if the request is
                        // refused: a reboot that happened first would come back rooted.
                        AppPreferences.setBootRootMode(context, false)
                        RootRecovery.rebootAndUnroot(rootShell, bootToken).also { result ->
                            if (!result.accepted) AppPreferences.setBootRootMode(context, true)
                        }
                    }
                }
            }
        }
    }

/**
 * Asks the phone to reboot through whatever shell this device will give us, and says whether the
 * request was made.
 *
 * A failed run usually has no root to reboot with - that is often the whole reason it failed - and
 * there is no route here that pretends otherwise: [KernelSuRuntime.rootShell] covers the two ways root
 * is reachable (a Shizuku session that is already root, and KernelSU's own `su`), and a device that
 * gives neither returns false. False is not a failure of the caller: the retry the caller armed is
 * stored on disk and does not depend on this, and the screen that offers it says what to do by hand.
 * What must not happen is arming a retry and then reporting a reboot that never happened.
 */
internal suspend fun requestReboot(): Boolean = withContext(Dispatchers.IO) {
    // `svc power reboot` after `reboot`: some builds ship one and not the other, and both mean the
    // same thing to the user. A non-zero exit is not retried beyond that - a reboot that has already
    // been asked for does not need asking twice, and the caller reports what happened either way.
    val commands = listOf("/system/bin/reboot", "/system/bin/svc power reboot")
    commands.any { command ->
        runCatching { KernelSuRuntime.rootShell(command)?.exitCode == 0 }.getOrDefault(false)
    }
}
