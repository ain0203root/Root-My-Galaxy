package dev.busung.s25uroot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
        // Taken off the intent for the same reason the install request is: an answer that stayed there
        // would run the attempt it named again on every rotation, and a run is not something to repeat
        // because the screen was turned.
        val answer = if (savedInstanceState == null) {
            RunAnswer.fromExtra(intent.getStringExtra(EXTRA_RUN_ANSWER)).also {
                intent.removeExtra(EXTRA_RUN_ANSWER)
            }
        } else {
            null
        }
        setContent {
            RootMyGalaxyTheme(
                accentColor = AppPreferences.accentColor(this),
                themeMode = AppPreferences.themeMode(this),
            ) {
                val installState by installViewModel.state.collectAsStateWithLifecycle()
                BackHandler(enabled = installState.busy) {}
                LaunchedEffect(startInstall, selectionId, answer) {
                    when {
                        answer != null -> startAnsweredRun(answer, selectionId)
                        startInstall -> installViewModel.install(selectionId)
                    }
                }
                // Asked over the run screen, because the run is what the answer is about: starting
                // Shizuku runs the installation the screen was opened for, and running without it runs
                // the same one the other way.
                ShizukuHoldDialog(
                    prompt = installState.transportPrompt,
                    onStartShizuku = { installViewModel.startShizukuForHeldRun(selectionId) },
                    onRunWithoutShizuku = { installViewModel.runHeldRunWithoutShizuku(selectionId) },
                    onDismiss = installViewModel::dismissTransportPrompt,
                )
                InstallScreen(
                    installState = installState,
                    onRetry = { installViewModel.install(selectionId) },
                    onSkipBootSettle = { installViewModel.skipBootSettle() },
                    onStop = { installViewModel.stopRun() },
                    onRebootAndRetry = { installViewModel.armRetryAfterReboot() },
                    onClose = ::finish,
                    onOpenSetting = ::openSettingsCard,
                )
            }
        }
    }

    /**
     * Runs the attempt one of the boot notification's answers asked for.
     *
     * The notification is where a boot install that ran out of Shizuku ends up, because a boot has
     * nobody to ask and this screen is where the question can be answered. Neither answer is taken on
     * trust here: [RunAnswer.StandardMethod] runs with Shizuku skipped, which is the same code path as
     * the dialog's own second button, and [RunAnswer.RetryShizuku] starts the ordinary run - which holds
     * and shows that same dialog when Shizuku really is not there - and then makes the start attempt, so
     * a Shizuku that is already up simply continues.
     *
     * A start that fails leaves the dialog standing with its reason and with the other answer still
     * offered, which is the whole point of routing this through the screen rather than running it blind.
     */
    private fun startAnsweredRun(answer: RunAnswer, selectionId: String?) {
        installViewModel.install(selectionId, withoutShizuku = answer.withoutShizuku)
        if (answer.startsShizukuFirst) installViewModel.startShizukuForHeldRun(selectionId)
    }

    /**
     * Opens one of the app's settings cards, in the window that holds the list.
     *
     * Asked of the existing window rather than a new one, because this screen was started *from* that
     * window: without the flags a jump would stack a second MainActivity on top of the first, and the
     * back button would walk through two copies of the app before reaching the run.
     *
     * This screen is deliberately left where it is. It is showing a failed run, which is the thing the
     * person may want back: closing it here would take the log with it, and the log is the only account
     * of what the payload did.
     */
    private fun openSettingsCard(target: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(SettingsTarget.EXTRA, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    companion object {
        const val EXTRA_INSTALL_REQUEST_ID = "install_request_id"
        const val EXTRA_PROFILE_ID = "profile_id"

        /** One of [RunAnswer]'s extras: what a boot notification's answer asked for. */
        const val EXTRA_RUN_ANSWER = "run_answer"
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

@Composable
private fun InstallScreen(
    installState: InstallUiState,
    onRetry: () -> Unit,
    onSkipBootSettle: () -> Unit,
    onStop: () -> Unit,
    /** Arms one retry for the next boot and reboots; reports whether the reboot was requested. */
    onRebootAndRetry: suspend () -> Boolean,
    onClose: () -> Unit,
    /** Opens a settings card by its target, for the one failure whose fix is a switch in this app. */
    onOpenSetting: (String) -> Unit,
) {
    val logScrollState = rememberScrollState()
    // The page's own scroll, held out here so the button over it can drive the same state.
    val pageScrollState = rememberScrollState()
    // Where this screen's buttons start, in window coordinates. The back-to-top button floats over the page,
    // and the page ends in the controls that stop and close a run - so it stands down while any of them is
    // on screen rather than sitting over the right end of the one control a hung run depends on.
    var controlsTop by remember { mutableStateOf(Float.POSITIVE_INFINITY) }
    val view = LocalView.current
    var showRetryChoice by remember { mutableStateOf(false) }
    // Whether a reboot was *asked for*, which is all the app can know: null while nothing has been
    // asked, false when the phone would not take the request.
    var retryNotice by remember { mutableStateOf<Boolean?>(null) }
    // Seconds left before a retry in this boot, or null when none is waiting. Held here rather than in
    // the view model because it is a countdown on a screen, not a fact about the run: nothing on the
    // device changes while it runs, and leaving the screen cancels it with nothing left to clean up.
    var waitRemaining by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(waitRemaining != null) {
        if (waitRemaining == null) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val left = InBootRetry.remainingSeconds(SystemClock.elapsedRealtime() - startedAt)
            waitRemaining = left
            if (left <= 0) break
            delay(1_000)
        }
        waitRemaining = null
        onRetry()
    }
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
        PageColumn(
            padding = padding,
            scrollState = pageScrollState,
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            controlsTop = controlsTop,
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

            InstallerStatusCard(installState, onOpenReadOnlySetting = { onOpenSetting(SettingsTarget.PartitionReadOnly) })
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

            // The run's own controls, as one group rather than two separately padded buttons: a
            // 20dp trailer on each of them plus the page's own 16dp between items put 36dp between two
            // buttons and 16dp everywhere else, which reads as a missing panel rather than as spacing.
            //
            // Skipping the wait is offered only while the app is holding the run, because the wait is a
            // floor and not a rule and the user is the one who knows whether this boot has settled.
            // Stopping is offered for the whole run, since it is the only way out of one that has hung:
            // back is disabled for the length of a run and nothing else can be pressed.
            // The two groups below are one block rather than two items, so that where they begin can be
            // measured in one place - and because they are one thing: what can be done about the run as it
            // stands. The wrapping column's spacing is the page's own, so the layout is what it was.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates -> controlsTop = coordinates.positionInWindow().y },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (installState.phase == InstallPhase.Settling || installState.busy) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (installState.phase == InstallPhase.Settling) {
                            FilledTonalButton(
                                onClick = {
                                    clickHaptic(view)
                                    onSkipBootSettle()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_run_now))
                            }
                        }
                        if (installState.busy) {
                            FilledTonalButton(
                                onClick = {
                                    clickHaptic(view)
                                    onStop()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_stop_run))
                            }
                        }
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
                                onOpenSetting = onOpenSetting,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (installState.phase) {
                                InstallPhase.Failed, InstallPhase.Stopped -> {
                                    val waiting = waitRemaining
                                    if (waiting != null) {
                                        WaitingRetryCard(
                                            secondsLeft = waiting,
                                            modifier = Modifier.weight(1f),
                                            onRetryNow = {
                                                clickHaptic(view)
                                                waitRemaining = null
                                                onRetry()
                                            },
                                            onStopWaiting = {
                                                clickHaptic(view)
                                                waitRemaining = null
                                            },
                                        )
                                    } else {
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
    }

    // All three answers are named where they are offered, in the order they are offered, because what
    // separates them is what each one buys rather than how it feels: the restart puts everything this
    // boot has done out of the way of the next attempt, the wait lets the state a failed attempt leaves
    // behind clear itself without one, and trying again at once buys only speed. The restart is first
    // because it is the best odds.
    //
    // Stacked - one full-width answer per row, each carrying the line that says what it buys - rather
    // than an AlertDialog, whose actions all live in a single run at the end of the message. Four
    // answers with labels this long do not fit in that run on a phone: it wraps, which puts the primary
    // answer on a line of its own and crowds the other three together underneath it, and that crowding
    // is what this dialog looked like. A list survives any label length and any screen width, and it
    // reads in the order the answers are worth taking.
    if (showRetryChoice) {
        val scope = rememberCoroutineScope()
        var arming by remember { mutableStateOf(false) }
        // A retry in this boot is not offered when this boot cannot take another attempt. The restart
        // is the answer that clears it, so the dialog keeps that one and says why the other two are
        // missing rather than leaving their absence to be noticed.
        val blocked = installState.failure?.inBootRetryBlocked
        Dialog(onDismissRequest = { if (!arming) showRetryChoice = false }) {
            Surface(
                // A share of the width rather than a fixed size, so it is as wide as the alerts this app
                // already shows and still fits the narrowest screen.
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.retry_choice_title),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    RetryOption(
                        label = stringResource(R.string.retry_after_reboot),
                        detail = stringResource(R.string.retry_option_reboot_detail),
                        enabled = !arming,
                        emphasis = RetryOptionEmphasis.Primary,
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
                    )
                    if (blocked == null) {
                        RetryOption(
                            label = stringResource(R.string.retry_wait),
                            detail = stringResource(R.string.retry_option_wait_detail),
                            enabled = !arming,
                            onClick = {
                                clickHaptic(view)
                                showRetryChoice = false
                                waitRemaining = InBootRetry.remainingSeconds(0)
                            },
                        )
                        RetryOption(
                            label = stringResource(R.string.retry_now),
                            detail = stringResource(R.string.retry_option_now_detail),
                            enabled = !arming,
                            emphasis = RetryOptionEmphasis.Quiet,
                            onClick = {
                                clickHaptic(view)
                                showRetryChoice = false
                                onRetry()
                            },
                        )
                    } else {
                        // Why the answers that are not here are not here - which is the whole reason the
                        // failure carried the block this far.
                        Text(
                            text = stringResource(
                                when (blocked) {
                                    InBootRetryBlock.PayloadMayStillRun -> R.string.retry_single_payload_hint
                                    InBootRetryBlock.PipeBudgetSpent -> R.string.retry_pipe_budget_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        enabled = !arming,
                        onClick = {
                            clickHaptic(view)
                            showRetryChoice = false
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
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

/**
 * The notice for a failure whose retry cannot happen in this boot.
 *
 * Not a warning for its own sake: everything the screen offers after a failure assumes this boot can
 * be run in again, and the reason it cannot is carried on the failure so the notice can name it
 * rather than leaving the missing retry unexplained.
 */
@Composable
private fun InBootRetryNotice(block: InBootRetryBlock) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(block.notice),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * How much weight an answer carries, which is the only thing that separates the three of them.
 */
private enum class RetryOptionEmphasis { Primary, Secondary, Quiet }

/**
 * One answer on the retry dialog: what it is called, and the line that says what it buys.
 *
 * The second line sits inside the button rather than beside it because the pairing is the point: an
 * answer read without its trade-off is picked on its name, and "Retry now" is the name that says least
 * about what it costs. Faded rather than recoloured, so the same line is legible on all three of the
 * containers an answer can be drawn in.
 */
@Composable
private fun RetryOption(
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: RetryOptionEmphasis = RetryOptionEmphasis.Secondary,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Start,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                textAlign = TextAlign.Start,
            )
        }
    }
    val padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    when (emphasis) {
        RetryOptionEmphasis.Primary -> Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            contentPadding = padding,
        ) {
            body()
        }
        RetryOptionEmphasis.Secondary -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            contentPadding = padding,
        ) {
            body()
        }
        RetryOptionEmphasis.Quiet -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            contentPadding = padding,
        ) {
            body()
        }
    }
}

/**
 * The wait between a failed run and a retry in the same boot.
 *
 * Shown instead of the retry button rather than beside it, because both would start the same run and
 * two ways to start one thing on one screen is how it gets started twice. The wait can be cut short,
 * so someone who knows this boot is settled is not held by a rule they disagree with.
 */
@Composable
private fun WaitingRetryCard(
    secondsLeft: Int,
    onRetryNow: () -> Unit,
    onStopWaiting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.retry_waiting_body, secondsLeft),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onStopWaiting) {
                    Text(stringResource(R.string.retry_waiting_cancel))
                }
                Button(onClick = onRetryNow) {
                    Text(stringResource(R.string.retry_waiting_start))
                }
            }
        }
    }
}

@Composable
private fun InstallerStatusCard(
    installState: InstallUiState,
    /** Where the notice's own button goes: to the switch that refused the write. */
    onOpenReadOnlySetting: () -> Unit,
) {
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
            // The one failure whose fix is a switch in this app: it is named here, beside the failure,
            // with the way to it - and only when it is this run's own protection that refused the write.
            // The other fact that changes what comes next: something may still be running. Beside the
            // failure rather than only in the log, because it is the reason the retry question below
            // keeps one answer instead of three.
            installState.failure?.inBootRetryBlocked?.let { block -> InBootRetryNotice(block) }
            if (installState.failure?.readOnlyWall == true) {
                ReadOnlyWallNotice(onOpenSetting = onOpenReadOnlySetting)
            }
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
 * The question a run asks when Use Shizuku is on and Shizuku is not running.
 *
 * Two answers, and both are starts: one starts Shizuku and runs through it, the other starts the run
 * without it. What it replaces is one answer and no choice - the run failed, telling the person to go
 * and start Shizuku somewhere else and come back, which is a round trip this screen can make itself.
 *
 * A start that failed stays here with its reason rather than closing: the other answer is still open,
 * and a second ask is exactly what a route that was not up yet needs.
 */
@Composable
private fun ShizukuHoldDialog(
    prompt: TransportPrompt?,
    onStartShizuku: () -> Unit,
    onRunWithoutShizuku: () -> Unit,
    onDismiss: () -> Unit,
) {
    prompt ?: return
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
        title = { Text(stringResource(R.string.status_shizuku_hold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.shizuku_hold_body))
                prompt.startDetail?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !prompt.starting,
                onClick = {
                    clickHaptic(view)
                    onStartShizuku()
                },
            ) {
                if (prompt.starting) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (prompt.starting) R.string.status_shizuku_starting else R.string.settings_shizuku_start,
                    ),
                )
            }
        },
        dismissButton = {
            // Deliberately live while a start is in flight. An attempt can take a minute on a device
            // where it has several routes to try, and disabling the other answer for that minute is how
            // this question turns into a screen with nothing to press - which is what it looked like
            // when the start was the only thing on offer. The view model drops a start that lands after
            // this answer was taken, so changing your mind mid-attempt cannot start two runs.
            TextButton(
                enabled = true,
                onClick = {
                    clickHaptic(view)
                    onRunWithoutShizuku()
                },
            ) {
                Text(stringResource(R.string.action_run_without_shizuku))
            }
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
