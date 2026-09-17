package dev.busung.s25uroot

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RecoveryTool {
    RestartZygote,
    SoftReboot,
    RebootAndUnroot,
}

private data class RecoveryMessage(
    val title: String,
    val detail: String,
    val failure: Boolean,
)

/**
 * The post-root repair actions, in Advanced mode.
 *
 * They are a settings group like every other one - the same rows, the same two-point gaps, the same
 * shared corners - because they are actions *about* the app's state, and a second visual language for
 * three rows only made them look like something else was going on. What separates them is that a tap
 * does not run one: it opens a dialog that says what the action costs, and the dialog's own button is
 * what starts it. Holding was the older confirmation and it was the wrong one, because a hold is
 * invisible until it succeeds, so nothing on the screen said these rows behaved differently from
 * every other row beside them.
 *
 * Root is not asked about up front: the action itself asks for a root shell and reports the refusal,
 * because the cheap in-process probe for KernelSU can answer no on a device where root is usable.
 */
@Composable
internal fun RootRecoverySection(
    onBootRootModeChanged: (Boolean) -> Unit,
    /** False when runs are told not to load KernelSU, which is what these actions consume. */
    kernelSuLoadingEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<RecoveryTool?>(null) }
    var confirming by remember { mutableStateOf<RecoveryTool?>(null) }
    var message by remember { mutableStateOf<RecoveryMessage?>(null) }

    fun report(tool: RecoveryTool, outcome: RecoveryOutcome) {
        message = RecoveryMessage(
            title = context.getString(tool.titleRes()),
            // What was accepted differs between the actions: two of them restart the Android runtime
            // and one restarts the phone, so the accepted message is the action's own.
            detail = if (outcome.accepted) context.getString(tool.acceptedRes()) else outcome.detail,
            failure = !outcome.accepted,
        )
    }

    fun run(tool: RecoveryTool) {
        if (running != null) return
        running = tool
        scope.launch {
            // Every shell here is a real process that is waited on, and two of the actions hold the
            // channel open until the child acknowledges them, so none of it may run on the UI thread.
            val outcome = withContext(Dispatchers.IO) {
                val bootToken = kernelBootToken()
                // Only worked out if something is refused, and worked out then from what the device
                // says about itself rather than from the refusal: "no root here" and "no root for
                // this app" are different problems with different fixes, and the readings that tell
                // them apart (the module list, the app's own `su`) cost more than the answer is worth
                // on a run that is going to work.
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
                                capabilities = RootRecovery.capabilities(rootShell)
                                    ?: KsudCapabilities(),
                            )
                            RecoveryTool.RebootAndUnroot -> {
                                // Cleared before the reboot is asked for, and put back if the request
                                // is refused: a reboot that happened first would come back rooted.
                                AppPreferences.setBootRootMode(context, false)
                                RootRecovery.rebootAndUnroot(rootShell, bootToken).also { result ->
                                    if (!result.accepted) AppPreferences.setBootRootMode(context, true)
                                }
                            }
                        }
                    }
                }
            }
            // The stored state is the one the screen follows, so a refusal puts root on boot back
            // on screen as well as on disk, and an accepted one leaves both off.
            if (tool == RecoveryTool.RebootAndUnroot) {
                onBootRootModeChanged(AppPreferences.bootRootMode(context))
            }
            report(tool, outcome)
            running = null
        }
    }

    confirming?.let { tool ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = { Icon(tool.icon(), contentDescription = null) },
            title = { Text(stringResource(tool.titleRes())) },
            text = { Text(stringResource(tool.confirmRes())) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    confirming = null
                    run(tool)
                }) {
                    Text(stringResource(tool.actionRes()))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    message?.let { shown ->
        AlertDialog(
            onDismissRequest = { message = null },
            icon = {
                Icon(
                    if (shown.failure) Icons.Rounded.Warning else Icons.Rounded.Shield,
                    contentDescription = null,
                )
            },
            title = { Text(shown.title) },
            text = { Text(shown.detail) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    message = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    // No explanatory paragraph of its own: the rows say what they do, and the dialog says what each
    // one costs. What is left is the list itself, in the same shape as the groups above it.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RecoveryTool.entries.forEachIndexed { index, tool ->
            SettingsCard(
                icon = tool.icon(),
                title = stringResource(tool.titleRes()),
                description = stringResource(tool.summaryRes()),
                position = when (index) {
                    0 -> SettingsCardPosition.Top
                    RecoveryTool.entries.lastIndex -> SettingsCardPosition.Bottom
                    else -> SettingsCardPosition.Middle
                },
                busy = running == tool,
                enabled = kernelSuLoadingEnabled,
                // The dependency is stated on the row rather than only in the refusal dialog: with
                // loading off these actions cannot ever run, and a card that looks live and then
                // refuses is the shape of bug this screen has already had once.
                value = if (kernelSuLoadingEnabled) {
                    ""
                } else {
                    stringResource(R.string.recovery_needs_kernel_su)
                },
                onClick = { confirming = tool },
            )
        }
    }
}

private fun RecoveryTool.icon(): ImageVector = when (this) {
    RecoveryTool.RestartZygote -> Icons.Rounded.RestartAlt
    RecoveryTool.SoftReboot -> Icons.Rounded.Memory
    RecoveryTool.RebootAndUnroot -> Icons.Rounded.Warning
}

private fun RecoveryTool.titleRes(): Int = when (this) {
    RecoveryTool.RestartZygote -> R.string.recovery_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_unroot
}

private fun RecoveryTool.summaryRes(): Int = when (this) {
    RecoveryTool.RestartZygote -> R.string.recovery_restart_zygote_summary
    RecoveryTool.SoftReboot -> R.string.recovery_soft_reboot_summary
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_unroot_summary
}

private fun RecoveryTool.confirmRes(): Int = when (this) {
    RecoveryTool.RestartZygote -> R.string.recovery_confirm_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_confirm_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_confirm_reboot_unroot
}

private fun RecoveryTool.actionRes(): Int = when (this) {
    RecoveryTool.RestartZygote -> R.string.recovery_action_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_action_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_action_reboot_unroot
}

private fun RecoveryTool.acceptedRes(): Int = when (this) {
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_scheduled
    else -> R.string.recovery_scheduled
}
