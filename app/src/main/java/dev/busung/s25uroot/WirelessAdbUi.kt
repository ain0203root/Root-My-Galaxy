package dev.busung.s25uroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * What the wireless transport is, what it is doing, and the four things that can be done to it.
 *
 * The distinction the whole screen is built around: a stored pairing is a record, not a state. So the
 * card shows *unverified* until a test has connected, and the test is offered right next to the
 * pairing rather than hidden - "it says paired but nothing works" is the failure this screen exists to
 * make visible before a run fails on it.
 */
@Composable
internal fun WirelessAdbDialog(
    snapshot: WirelessAdbSnapshot?,
    busy: Boolean,
    onPair: (forceRepair: Boolean) -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onTest: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wireless_adb_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.wireless_adb_pair_help),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (snapshot != null) {
                    Text(
                        text = wirelessAdbStateLabel(snapshot.authState),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = snapshot.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    snapshot.fingerprint?.let { fingerprint ->
                        Text(
                            text = stringResource(R.string.wireless_adb_fingerprint) + ": " + fingerprint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (busy) {
                    Text(
                        text = stringResource(R.string.adb_pair_working),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    enabled = !busy,
                    onClick = { onPair(snapshot?.keyPresent == true) },
                ) {
                    Text(
                        stringResource(
                            // With a key already stored, pairing again has to clear it first, which is
                            // the only way out of a pairing the device has forgotten.
                            if (snapshot?.keyPresent == true) R.string.wireless_adb_pair_again
                            else R.string.wireless_adb_pair,
                        ),
                    )
                }
                // The screen the code is generated in, reachable without leaving with an instruction
                // to find it: "open Developer options" as a sentence is what this button replaces.
                TextButton(enabled = !busy, onClick = onOpenDeveloperOptions) {
                    Text(stringResource(R.string.adb_pair_open_developer_options))
                }
                TextButton(enabled = !busy, onClick = onTest) {
                    Text(stringResource(R.string.wireless_adb_test))
                }
                if (snapshot?.keyPresent == true) {
                    TextButton(enabled = !busy, onClick = onForget) {
                        Text(stringResource(R.string.wireless_adb_forget))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The one-line summary of the authorization state, for the card and the dialog. */
@Composable
internal fun wirelessAdbStateLabel(state: WirelessAdbAuthState): String = stringResource(
    when (state) {
        WirelessAdbAuthState.NoCredential -> R.string.settings_wireless_adb_none
        WirelessAdbAuthState.SavedUnverified -> R.string.settings_wireless_adb_unverified
        WirelessAdbAuthState.Valid -> R.string.settings_wireless_adb_valid
        WirelessAdbAuthState.PairingRejected -> R.string.settings_wireless_adb_rejected
        WirelessAdbAuthState.PortUnavailable -> R.string.settings_wireless_adb_port_missing
        WirelessAdbAuthState.PermissionRequired -> R.string.settings_wireless_adb_no_permission
        WirelessAdbAuthState.ConnectionFailed -> R.string.settings_wireless_adb_failed
    },
)
