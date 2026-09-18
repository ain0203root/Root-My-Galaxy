package dev.busung.s25uroot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The ways out of the running Android, with the ones this device cannot do left visible and greyed.
 *
 * Visible rather than hidden, because the answer to "why is Reboot to EDL not here" is not "it is not
 * supported" - it is that this phone has no root and no Shizuku shell at the moment, which is a state the
 * user can change. Each greyed row says which of the two is missing, since a grant in the KernelSU manager
 * and starting Shizuku are different fixes.
 *
 * What can be done is probed when the sheet opens and not before: both tiers are real commands, and asking
 * a device for a shell on every recomposition of a page would be paying for an answer nothing is reading.
 */
@Composable
internal fun RebootSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Null while the probe is out, which is not the same as ShellTier.None: nothing is known yet, and the
    // rows say so rather than claiming the phone cannot do something.
    var tier by remember { mutableStateOf<ShellTier?>(null) }
    var confirming by remember { mutableStateOf<RebootTarget?>(null) }
    var refusal by remember { mutableStateOf<RecoveryOutcome?>(null) }
    LaunchedEffect(Unit) {
        tier = currentShellTier()
    }

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.reboot_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    when (tier) {
                        null -> R.string.reboot_status_checking
                        ShellTier.Root -> R.string.reboot_status_root
                        ShellTier.Unprivileged -> R.string.reboot_status_shizuku
                        ShellTier.None -> R.string.reboot_status_none
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (tier == ShellTier.None) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.size(4.dp))
            RebootTarget.entries.forEach { target ->
                val refusalForRow = tier?.let { known -> rebootRefusalFor(known, target) }
                RebootTargetRow(
                    target = target,
                    // Offered only once the probe has answered and said yes: a row that turns out to be
                    // refused is worse than one that was never offered.
                    enabled = tier != null && refusalForRow == null,
                    reason = refusalForRow,
                    onSelect = {
                        refusal = null
                        if (target.leavesAndroid) confirming = target else scope.launch {
                            refusal = runRebootTarget(context, target)
                        }
                    },
                )
            }
            refusal?.let { outcome ->
                if (!outcome.accepted) {
                    Text(
                        text = outcome.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    confirming?.let { target ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.reboot_confirm_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.reboot_confirm_body,
                        stringResource(target.label),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirming = null
                    scope.launch { refusal = runRebootTarget(context, target) }
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirming = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * One target, as a row of the sheet.
 *
 * The icon is the same for all six on purpose: they are one kind of action, and six different glyphs would
 * suggest six different kinds. What separates them is written next to them.
 */
@Composable
private fun RebootTargetRow(
    target: RebootTarget,
    enabled: Boolean,
    reason: RebootRefusal?,
    onSelect: () -> Unit,
) {
    val view = LocalView.current
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (enabled) {
                    Modifier.clickable {
                        clickHaptic(view)
                        onSelect()
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(target.label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                // A reason replaces what the target does rather than joining it: the only question a row
                // that cannot be pressed raises is what is missing.
                val line = when {
                    reason != null -> stringResource(reason.lineRes())
                    else -> target.detail?.let { stringResource(it) }
                }
                if (line != null) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reason != null) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
