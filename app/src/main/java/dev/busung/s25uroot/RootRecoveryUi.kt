package dev.busung.s25uroot

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
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

/** How long a recovery card has to be held before it acts. */
private const val HOLD_TO_CONFIRM_MILLIS = 1_400L

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
 * Every one of them is a hold rather than a tap, and each states what it will cost before it is held:
 * two of them restart the Android user interface, which closes whatever is open, and one of them
 * reboots the phone without root. Root is not asked about up front - the card that is held asks for a
 * root shell and reports the refusal - because the cheap in-process probe for KernelSU can answer no
 * on a device where root is perfectly usable, and a card that refused on that answer would be wrong.
 */
@Composable
internal fun RootRecoverySection(
    onBootRootModeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<RecoveryTool?>(null) }
    var message by remember { mutableStateOf<RecoveryMessage?>(null) }
    fun report(tool: RecoveryTool, outcome: RecoveryOutcome) {
        message = RecoveryMessage(
            title = context.getString(tool.titleRes()),
            // What was accepted differs between the actions: two of them restart the Android runtime
            // and one restarts the phone, so the accepted message is the action's own.
            detail = if (outcome.accepted) context.getString(tool.acceptedRes())
            else outcome.detail,
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
        Text(
            stringResource(R.string.settings_recovery),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            stringResource(R.string.settings_recovery_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
        RecoveryHoldCard(
            icon = Icons.Rounded.RestartAlt,
            title = stringResource(R.string.recovery_restart_zygote),
            description = stringResource(R.string.recovery_restart_zygote_summary),
            enabled = running == null,
            busy = running == RecoveryTool.RestartZygote,
            onConfirmed = { run(RecoveryTool.RestartZygote) },
        )
        RecoveryHoldCard(
            icon = Icons.Rounded.Memory,
            title = stringResource(R.string.recovery_soft_reboot),
            description = stringResource(R.string.recovery_soft_reboot_summary),
            enabled = running == null,
            busy = running == RecoveryTool.SoftReboot,
            onConfirmed = { run(RecoveryTool.SoftReboot) },
        )
        RecoveryHoldCard(
            icon = Icons.Rounded.Warning,
            title = stringResource(R.string.recovery_reboot_unroot),
            description = stringResource(R.string.recovery_reboot_unroot_summary),
            enabled = running == null,
            busy = running == RecoveryTool.RebootAndUnroot,
            onConfirmed = { run(RecoveryTool.RebootAndUnroot) },
        )
    }
}

private fun RecoveryTool.titleRes(): Int = when (this) {
    RecoveryTool.RestartZygote -> R.string.recovery_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_unroot
}

private fun RecoveryTool.acceptedRes(): Int = when (this) {
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_scheduled
    else -> R.string.recovery_scheduled
}

/**
 * A card that acts only after being held, with a bar that fills over the hold.
 *
 * Holding is the confirmation here rather than a dialog: these are destructive, they are the only way
 * to repair a rooted boot, and a tap that opened a dialog is a tap away from a restart of the Android
 * runtime. Releasing early cancels, and nothing is scheduled unless the hold ran its course.
 */
@Composable
private fun RecoveryHoldCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    busy: Boolean,
    onConfirmed: () -> Unit,
) {
    val view = LocalView.current
    var holding by remember { mutableStateOf(false) }
    // Fills over the same span the hold has to survive, so the bar is the hold rather than a spinner
    // next to it, and it drains back when the finger is lifted.
    val holdProgress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(durationMillis = HOLD_TO_CONFIRM_MILLIS.toInt(), easing = LinearEasing),
        label = "recovery-hold",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .pointerInput(enabled, onConfirmed) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    holding = true
                    // A null result means the timeout fired, which is the hold completing; a released
                    // pointer returns the up event and cancels.
                    // The gesture scope's own timeout, not the coroutine one: inside a pointer
                    // gesture only the event scope's restricted suspending functions may be called,
                    // and this one is a member of the scope itself.
                    val released = withTimeoutOrNull(HOLD_TO_CONFIRM_MILLIS) {
                        waitForUpOrCancellation()
                    }
                    holding = false
                    if (released == null) {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onConfirmed()
                    }
                }
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
            } else {
                LinearProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
