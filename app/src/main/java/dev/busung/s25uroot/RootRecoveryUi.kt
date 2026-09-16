package dev.busung.s25uroot

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 * A tap opens a dialog that says what the action will cost, and the dialog's own button is what runs
 * it. Holding was the older confirmation and it was the wrong one for a card that reads like a button:
 * a hold is invisible until it succeeds, so nothing on the screen tells you it needs one, and the
 * three cards here were the only ones in the app that behaved differently from every other row. A
 * dialog names the consequence in words - everything open will close, or root will be gone - which is
 * more than a filling bar can say.
 *
 * Root is not asked about up front: the action itself asks for a root shell and reports the refusal,
 * because the cheap in-process probe for KernelSU can answer no on a device where root is perfectly
 * usable.
 */
@Composable
internal fun RootRecoverySection(
    onBootRootModeChanged: (Boolean) -> Unit,
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
                when {
                    KernelSuRuntime.rootShell("id") == null -> RecoveryOutcome(
                        accepted = false,
                        detail = context.getString(R.string.recovery_root_required),
                    )
                    bootToken == null -> RecoveryOutcome(
                        accepted = false,
                        detail = context.getString(R.string.error_boot_id),
                    )
                    else -> {
                        val rootShell: (String) -> ShizukuController.ShellResult = { command ->
                            KernelSuRuntime.rootShell(command) ?: ShizukuController.ShellResult(
                                NO_ROOT_SHELL_EXIT,
                                context.getString(R.string.recovery_root_required),
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // No heading of its own: this block is a settings section and the section label above it says
        // what it is. What is left is the one line that has to be read before any card here is used.
        Text(
            stringResource(R.string.settings_recovery_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
        RecoveryCard(
            icon = Icons.Rounded.RestartAlt,
            title = stringResource(R.string.recovery_restart_zygote),
            description = stringResource(R.string.recovery_restart_zygote_summary),
            enabled = running == null,
            busy = running == RecoveryTool.RestartZygote,
            onClick = { confirming = RecoveryTool.RestartZygote },
        )
        RecoveryCard(
            icon = Icons.Rounded.Memory,
            title = stringResource(R.string.recovery_soft_reboot),
            description = stringResource(R.string.recovery_soft_reboot_summary),
            enabled = running == null,
            busy = running == RecoveryTool.SoftReboot,
            onClick = { confirming = RecoveryTool.SoftReboot },
        )
        RecoveryCard(
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.recovery_reboot_unroot),
            description = stringResource(R.string.recovery_reboot_unroot_summary),
            enabled = running == null,
            busy = running == RecoveryTool.RebootAndUnroot,
            onClick = { confirming = RecoveryTool.RebootAndUnroot },
        )
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

/**
 * One repair action: a card that behaves like every other row in Settings, and asks before it acts.
 *
 * The progress bar only appears while the action is running. Nothing else is on it, because the
 * confirmation is a dialog now and a second, quieter confirmation drawn onto the card would be a way
 * to start something without reading it.
 */
@Composable
private fun RecoveryCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                }
            }
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
