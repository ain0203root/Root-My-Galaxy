package dev.busung.s25uroot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.busung.s25uroot.ui.theme.RootMyGalaxyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InstallActivity : ComponentActivity() {
    private val installViewModel by viewModels<InstallViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val selectionId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val startInstall = savedInstanceState == null && AppPreferences.consumeInstallRequest(
            this,
            intent.getStringExtra(EXTRA_INSTALL_REQUEST_ID),
        )
        intent.removeExtra(EXTRA_INSTALL_REQUEST_ID)
        setContent {
            RootMyGalaxyTheme(
                accentColor = AppPreferences.accentColor(this),
                themeMode = AppPreferences.themeMode(this),
            ) {
                val installState by installViewModel.state.collectAsStateWithLifecycle()
                BackHandler(enabled = installState.busy) {}
                LaunchedEffect(startInstall, selectionId) {
                    if (startInstall) installViewModel.install(selectionId)
                }
                InstallScreen(
                    installState = installState,
                    onRetry = { installViewModel.install(selectionId) },
                    onSkipBootSettle = { installViewModel.skipBootSettle() },
                    onStop = { installViewModel.stopRun() },
                    onRebootAndRetry = { installViewModel.armRetryAfterReboot() },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_INSTALL_REQUEST_ID = "install_request_id"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}

internal data class InstallerStep(
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val icon: ImageVector,
)

internal val installerSteps = listOf(
    InstallerStep(R.string.step_support_title, R.string.step_support_detail, Icons.Rounded.Security),
    InstallerStep(R.string.step_download_title, R.string.step_download_detail, Icons.Rounded.CloudDownload),
    InstallerStep(R.string.step_exploit_title, R.string.step_exploit_detail, Icons.Rounded.Memory),
    InstallerStep(R.string.step_ksu_title, R.string.step_ksu_detail, Icons.Rounded.Check),
)

private fun clickHaptic(view: View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}

@Composable
private fun InstallScreen(
    installState: InstallUiState,
    onRetry: () -> Unit,
    onSkipBootSettle: () -> Unit,
    onStop: () -> Unit,
    /** Arms one retry for the next boot and reboots; reports whether the reboot was requested. */
    onRebootAndRetry: suspend () -> Boolean,
    onClose: () -> Unit,
) {
    val logScrollState = rememberScrollState()
    val view = LocalView.current
    var showRetryChoice by remember { mutableStateOf(false) }
    // Whether a reboot was *asked for*, which is all the app can know: null while nothing has been
    // asked, false when the phone would not take the request.
    var retryNotice by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(installState.log) {
        delay(40)
        logScrollState.scrollTo(logScrollState.maxValue)
    }

    // The page scrolls, and the log panel has a height of its own rather than a share of what is left.
    // It used to be the weighted remainder, which is fine until the failure card grows: a run that died
    // in the exploit left the log a title, a copy button and no text at all, because the card above had
    // already taken the height the log was supposed to live in. A log that can vanish is worse than a
    // page that has to be scrolled - and the log is the only continuous account of a run.
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.install_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = if (installState.busy) {
                        stringResource(R.string.install_keep_open)
                    } else {
                        installState.message
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InstallerStatusCard(installState)
            InstallerSteps(
                phase = installState.phase,
                failure = installState.failure,
                stoppedAt = installState.stoppedAt,
            )
            InstallerLog(
                output = installState.log,
                // A height of its own, because this panel is the only continuous account of a run: as a
                // weighted remainder it collapsed to nothing the moment a failure card had something to
                // say. The status card is still capped, so the two together cannot push the buttons out
                // of reach; the page scroll handles the rest.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LOG_PANEL_HEIGHT),
                scrollState = logScrollState,
            )

            // Offered only while the app is holding the run: the wait is a floor, not a rule, and the
            // user is the one who knows whether this boot has already settled.
            if (installState.phase == InstallPhase.Settling) {
                FilledTonalButton(
                    onClick = {
                        clickHaptic(view)
                        onSkipBootSettle()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                ) {
                    Text(stringResource(R.string.action_run_now))
                }
            }

            // Offered while the run is in flight, which is the only way out of a run that has stopped
            // making progress: back is disabled for the length of a run, and a payload that is hung has
            // nothing else that could be pressed. It is a tonal button below the log rather than beside
            // the run's own controls, because stopping is not part of what the run is doing.
            if (installState.busy) {
                FilledTonalButton(
                    onClick = {
                        clickHaptic(view)
                        onStop()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                ) {
                    Text(stringResource(R.string.action_stop_run))
                }
            }

            if (!installState.busy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The step after a successful load, and the reason it is here rather than only in
                    // Settings: KernelSU has just been loaded into the running kernel, and the modules
                    // that go with it are mounted but not yet in a Zygote. A userspace restart is what
                    // puts them there, and asking for it from the screen that just finished is the
                    // moment the user is thinking about it.
                    if (installState.phase == InstallPhase.Installed) {
                        RecoveryActionButton(
                            tool = RecoveryTool.SoftReboot,
                            label = stringResource(R.string.install_load_modules),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (installState.phase) {
                            InstallPhase.Failed, InstallPhase.Stopped -> {
                                FilledTonalButton(
                                    onClick = {
                                        clickHaptic(view)
                                        onClose()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.action_close))
                                }
                                Button(
                                    onClick = {
                                        clickHaptic(view)
                                        showRetryChoice = true
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                            InstallPhase.Installed, InstallPhase.RootOnly -> Button(
                                onClick = {
                                    clickHaptic(view)
                                    onClose()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_done))
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // Retrying straight away is offered second and named for what it costs: the exploit is a race
    // against a boot that is already busy, and the same boot has already had one attempt go through
    // it. The reboot is first because it is the better odds, and because it is the option that keeps
    // everything the device has done this boot out of the way of the next attempt.
    if (showRetryChoice) {
        val scope = rememberCoroutineScope()
        var arming by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!arming) showRetryChoice = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.retry_choice_title)) },
            text = { Text(stringResource(R.string.retry_choice_body)) },
            confirmButton = {
                TextButton(
                    enabled = !arming,
                    onClick = {
                        clickHaptic(view)
                        arming = true
                        scope.launch {
                            val rebooted = onRebootAndRetry()
                            arming = false
                            showRetryChoice = false
                            retryNotice = rebooted
                        }
                    },
                ) {
                    Text(stringResource(R.string.retry_after_reboot))
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !arming,
                        onClick = {
                            clickHaptic(view)
                            showRetryChoice = false
                        },
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        enabled = !arming,
                        onClick = {
                            clickHaptic(view)
                            showRetryChoice = false
                            onRetry()
                        },
                    ) {
                        Text(stringResource(R.string.retry_now))
                    }
                }
            },
        )
    }

    // What a reboot that could not be asked for means: the retry is armed either way, so the only
    // thing the user has to be told is the part the app could not do. A reboot that *was* requested
    // needs no dialog - the screen is about to go away with the phone.
    retryNotice?.let { rebooted ->
        if (!rebooted) {
            AlertDialog(
                onDismissRequest = { retryNotice = null },
                icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                title = { Text(stringResource(R.string.retry_armed_title)) },
                text = { Text(stringResource(R.string.retry_armed_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        clickHaptic(view)
                        retryNotice = null
                    }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }
    }
}

@Composable
private fun InstallerStatusCard(installState: InstallUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = STATUS_CARD_MAX_HEIGHT)
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when (installState.phase) {
                InstallPhase.Failed -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (installState.phase == InstallPhase.Failed) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
    ) {
        Column(
            // Scrollable, because the cap above must clip something: the payload's own lines are the
            // part that can be any length, and losing the stage or the progress bar off the top of a
            // failure card would be worse than scrolling to reach the last of them.
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedContent(targetState = installState.phase, label = "install-status-icon") { phase ->
                    when {
                        installState.busy -> LoadingIndicator(
                            modifier = Modifier.size(44.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        phase == InstallPhase.Installed -> Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                        // Root was obtained, so this is not the failure icon: it is the honest
                        // "root only" reading of a run whose load was switched off.
                        phase == InstallPhase.RootOnly -> Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                        else -> Icon(
                            Icons.Rounded.Error,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = installState.message,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = installPhaseDetail(installState),
                        color = LocalContentColor.current.copy(alpha = 0.78f),
                        // A cause, not a log: the card must not grow into one no matter what a
                        // payload or a message turns out to contain.
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            installState.failure?.let { failure -> FailureReport(failure) }
            LinearProgressIndicator(
                progress = {
                    installProgress(
                        phase = installState.phase,
                        failureStage = installState.failure?.stage ?: installState.stoppedAt,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun InstallerSteps(
    phase: InstallPhase,
    failure: RunFailure?,
    stoppedAt: RunStage? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            installerSteps.forEachIndexed { index, step ->
                val stepState = installerStepState(phase, index, failure?.stage ?: stoppedAt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = when (stepState) {
                            InstallerStepState.Done, InstallerStepState.Active ->
                                MaterialTheme.colorScheme.primary
                            // Where the run stopped, in the same colour the failure card uses: the two
                            // are the same fact, and a step marker in the ordinary accent colour would
                            // read as one that is still working.
                            InstallerStepState.Failed -> MaterialTheme.colorScheme.error
                            InstallerStepState.Pending -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = when (stepState) {
                            InstallerStepState.Done, InstallerStepState.Active ->
                                MaterialTheme.colorScheme.onPrimary
                            InstallerStepState.Failed -> MaterialTheme.colorScheme.onError
                            InstallerStepState.Pending -> MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (stepState) {
                                    InstallerStepState.Done -> Icons.Rounded.Check
                                    InstallerStepState.Failed -> Icons.Rounded.Close
                                    else -> step.icon
                                },
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(step.title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(step.detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        )
                    }
                    if (stepState == InstallerStepState.Active &&
                        phase !in setOf(InstallPhase.Failed, InstallPhase.Ready)
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

internal fun copyLogToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("RootMyGalaxyLog", text))
    Toast.makeText(context, context.getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
}

@Composable
private fun InstallerLog(
    output: String,
    modifier: Modifier,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val context = LocalContext.current
    val view = LocalView.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.install_live_progress),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clickHaptic(view)
                    copyLogToClipboard(context, output)
                }) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy_log),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = output.ifBlank { stringResource(R.string.install_preparing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun installPhaseDetail(installState: InstallUiState): String =
    if (installState.phase == InstallPhase.Failed && installState.failure != null) {
        // The stage is the headline, so the detail line carries the cause instead of repeating what
        // the log is for.
        installState.failure.reason
    } else {
        stringResource(
            when (installState.phase) {
                InstallPhase.Checking -> R.string.phase_checking
                InstallPhase.Ready -> R.string.phase_ready
                InstallPhase.Settling -> R.string.phase_settling
                InstallPhase.Downloading -> R.string.phase_downloading
                InstallPhase.Exploiting -> R.string.phase_exploiting
                InstallPhase.LoadingKernelSu -> R.string.phase_loading_ksu
                InstallPhase.Installed -> R.string.phase_installed
                InstallPhase.RootOnly -> R.string.phase_root_only
                InstallPhase.Failed -> R.string.phase_failed
                // The status card's own message is "Stopped by you", so the detail line says what that
                // means for the device rather than repeating it.
                InstallPhase.Stopped -> R.string.phase_stopped
            },
        )
    }

/**
 * The stage, the reason, and the last thing the payload said. Payload output is the only account
 * of the kernel race, so it is shown where the failure is reported rather than only in the log.
 */
@Composable
private fun FailureReport(failure: RunFailure) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.failure_stage),
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )
        Text(stringResource(failure.stage.label), style = MaterialTheme.typography.bodyMedium)
        if (failure.evidence.isNotEmpty()) {
            Text(
                stringResource(R.string.failure_evidence),
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
            failure.evidence.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * How tall the status card may grow.
 *
 * A failure card carries the stage, the cause, the payload's last lines and a progress bar, and it
 * used to take as much room as its evidence needed - which on a short window left the run log with
 * nothing and the panel collapsed to an empty strip. It scrolls past this instead.
 */
private val STATUS_CARD_MAX_HEIGHT = 216.dp

/**
 * How tall the run log is, whatever else is on the screen.
 *
 * Fixed rather than a share of what is left, because what is left is exactly what a long failure report
 * takes: the panel was measured at zero text height on a run that died in the exploit, showing its title
 * and its copy button over empty space while the run's actual output sat unread in the state. Roughly a
 * screen third, which is enough for the tail of a payload's output - the part that says why it stopped.
 */
private val LOG_PANEL_HEIGHT = 280.dp

/**
 * How far the bar has come.
 *
 * A failure stops at the step it died in rather than dropping back to nothing: an empty bar on a run
 * that reached the kernel exploit threw away the one thing the card could still say about it.
 */
internal fun installProgress(phase: InstallPhase, failureStage: RunStage?): Float = when (phase) {
    InstallPhase.Checking -> 0.1f
    InstallPhase.Ready -> 0f
    InstallPhase.Settling -> 0.15f
    InstallPhase.Downloading -> 0.3f
    InstallPhase.Exploiting -> 0.6f
    InstallPhase.LoadingKernelSu -> 0.85f
    InstallPhase.Installed -> 1f
    // Everything that was going to happen happened, and the load was not part of it, so the bar
    // stops short of claiming a step the run was told to skip.
    InstallPhase.RootOnly -> 0.9f
    InstallPhase.Failed -> failureStage
        ?.let { reached -> (installerStepForStage(reached) + 1) / installerSteps.size.toFloat() }
        ?: 0f
    // Stopped where it was stopped, for the same reason a failure is: the bar's job is to say how far
    // the run got, and how far it got is the part with consequences.
    InstallPhase.Stopped -> installerStepForStage(failureStage ?: RunStage.Target)
        .let { reached -> (reached + 1) / installerSteps.size.toFloat() }
}

/** What a step in the install card is doing, or what it turned out to be. */
internal enum class InstallerStepState {
    /** Not reached. */
    Pending,

    /** Running now, or where a run that was stopped in its tracks was working. */
    Active,

    /** Finished. */
    Done,

    /** This is the step the run stopped in. */
    Failed,
}

/**
 * Which step a run that stopped had reached.
 *
 * Several stages share a step - the target lookup and the transport are both part of the support check,
 * and verifying the control channel is part of loading KernelSU - so the mapping is by step and not by
 * stage.
 */
internal fun installerStepForStage(stage: RunStage): Int = when (stage) {
    RunStage.Transport, RunStage.Target -> 0
    RunStage.Download -> 1
    RunStage.Exploit -> 2
    RunStage.KernelSu, RunStage.Verify -> 3
}

/**
 * The state of one step.
 *
 * The failure is placed at the step it happened in. The card used to put the first step in progress for
 * every failure, so a run that died in the kernel exploit showed "Support check" as the step in flight
 * and no mark at all on the step that failed - both things the card exists to answer, both wrong.
 */
internal fun installerStepState(
    phase: InstallPhase,
    stepIndex: Int,
    failureStage: RunStage? = null,
): InstallerStepState = when (phase) {
    InstallPhase.Installed -> InstallerStepState.Done

    // A run that was told not to load KernelSU ended at the exploit, so the step it never reached stays
    // empty: the screen should not tick a load that was deliberately not asked for.
    InstallPhase.RootOnly ->
        if (stepIndex <= 2) InstallerStepState.Done else InstallerStepState.Pending

    InstallPhase.Failed -> {
        val failedAt = failureStage?.let(::installerStepForStage)
        when {
            // Nothing is claimed when the stage is not known: a card with no marks is better than one
            // pointing at a step that may not be the one that stopped.
            failedAt == null -> InstallerStepState.Pending
            stepIndex < failedAt -> InstallerStepState.Done
            stepIndex == failedAt -> InstallerStepState.Failed
            else -> InstallerStepState.Pending
        }
    }

    // Stopped, not failed: the steps behind it were done, and the one it was stopped in is where work
    // was happening rather than a step that did something wrong - which is why it gets the running mark
    // and not the error one.
    InstallPhase.Stopped -> {
        val stoppedAt = failureStage?.let(::installerStepForStage)
        when {
            stoppedAt == null -> InstallerStepState.Pending
            stepIndex < stoppedAt -> InstallerStepState.Done
            stepIndex == stoppedAt -> InstallerStepState.Active
            else -> InstallerStepState.Pending
        }
    }

    else -> {
        val activeIndex = when (phase) {
            InstallPhase.Checking, InstallPhase.Ready, InstallPhase.Settling -> 0
            InstallPhase.Downloading -> 1
            InstallPhase.Exploiting -> 2
            InstallPhase.LoadingKernelSu -> 3
            // Unreachable: the branches above take these phases. Listed so a new phase cannot fall
            // through to the first step and look like a support check in progress.
            InstallPhase.Installed,
            InstallPhase.RootOnly,
            InstallPhase.Failed,
            InstallPhase.Stopped,
            -> 0
        }
        when {
            stepIndex < activeIndex -> InstallerStepState.Done
            stepIndex == activeIndex -> InstallerStepState.Active
            else -> InstallerStepState.Pending
        }
    }
}
