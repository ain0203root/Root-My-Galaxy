package dev.busung.s25uroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What a run would find, checked before it is started.
 *
 * A sheet rather than a screen, because it is a question asked about the thing the run button is about: this
 * is the one that answers "would this work", and the answer is worth having without losing the page the run
 * is started from.
 *
 * The check runs on opening and again on demand, and it is a visible piece of work rather than a silent one -
 * it downloads and hashes the payload, which on a slow connection is ten seconds of nothing. What it never does
 * is a *failing* check with no explanation: every item that could not be answered says so in its own words,
 * which is the difference between a check that reports and a check that decides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreflightSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<PreflightReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var copying by remember { mutableStateOf(false) }
    var copyError by remember { mutableStateOf<String?>(null) }
    // Bumped by "Check again". A counter rather than a boolean, so asking twice in a row is two runs rather
    // than one - the second answer is the one that reflects what changed on the phone.
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        checking = true
        error = null
        val result = runCatching { PreflightRunner.run(context) }
        report = result.getOrNull()
        error = result.exceptionOrNull()?.message
        checking = false
    }

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.preflight_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = report?.deviceLabel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The whole report with this check inside it, rather than the check alone: this sheet is the
                // last thing looked at before a run, which makes it the moment a report is actually wanted -
                // and a report is the phone's identity, the versions and the log tail as well as these lines.
                IconButton(
                    enabled = report != null && !copying,
                    onClick = {
                        clickHaptic(view)
                        val shown = report ?: return@IconButton
                        copying = true
                        scope.launch {
                            val gathered = runCatching {
                                collectDiagnosticReport(context, preflightText(context, shown))
                            }
                            copying = false
                            gathered.onSuccess { copyReportToClipboard(context, it) }
                                .onFailure { failure ->
                                    copyError = failure.message ?: failure.javaClass.simpleName
                                }
                        }
                    },
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.logs_copy),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Read once into a val: the state is a `var` behind `remember`, so it cannot be smart-cast - and
            // a check that half-renders from one snapshot and half from the next is how a verdict and its
            // lines come to be about two different runs.
            val shown = report
            when {
                checking && shown == null -> Row(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.preflight_checking))
                }
                error != null -> Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
                shown != null -> {
                    PreflightVerdictCard(shown.verdict)
                    Text(
                        text = stringResource(R.string.preflight_target) + ": " + shown.targetLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        shown.items.forEach { item -> PreflightItemRow(item) }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    enabled = !checking,
                    onClick = {
                        clickHaptic(view)
                        attempt++
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.preflight_run_again))
                }
                TextButton(onClick = {
                    clickHaptic(view)
                    onDismiss()
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_close))
                }
            }
            // The copy is the only thing here that reaches outside the app, so its failure is the only one
            // that would otherwise pass in silence.
            copyError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The answer, above the lines that produced it.
 *
 * Headline first because it is the whole point of opening the sheet, and the lines under it are the evidence
 * for it rather than a list to be read first.
 */
@Composable
private fun PreflightVerdictCard(verdict: PreflightVerdict) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(verdict.headline),
                style = MaterialTheme.typography.titleMedium,
                color = verdict.color(),
            )
            Text(
                text = stringResource(verdict.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One line of the check: what it looked at, what was found, and how it came out. */
@Composable
private fun PreflightItemRow(item: PreflightItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(item.id.label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(item.state.label),
            style = MaterialTheme.typography.labelSmall,
            color = item.state.color(),
        )
    }
}

@Composable
private fun PreflightState.color(): Color = when (this) {
    PreflightState.Ready -> MaterialTheme.colorScheme.primary
    PreflightState.Note -> MaterialTheme.colorScheme.onSurfaceVariant
    PreflightState.Warn -> MaterialTheme.colorScheme.tertiary
    PreflightState.Fail -> MaterialTheme.colorScheme.error
}

@Composable
private fun PreflightVerdict.color(): Color = when (this) {
    PreflightVerdict.Ready -> MaterialTheme.colorScheme.primary
    PreflightVerdict.MayBeRefused -> MaterialTheme.colorScheme.tertiary
    PreflightVerdict.Blocked -> MaterialTheme.colorScheme.error
}
