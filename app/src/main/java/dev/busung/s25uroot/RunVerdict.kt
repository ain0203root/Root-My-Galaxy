package dev.busung.s25uroot

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a run came to, wherever it is shown.
 *
 * One vocabulary for four surfaces that used to disagree: Home's status card, the run screen's card, the
 * rows in History, and the notification in the shade. They had four mappings of their own between a result
 * and a colour, and the disagreement was not cosmetic - a failed run drew Home's card in the accent colour,
 * so the one screen a phone opens on said "fine" about a run that had just failed.
 *
 * The states are the ones the app already has rather than the ones a colour scheme would suggest: [RootOnly]
 * is a success that did not load KernelSU and is neither of the other two, and [Stopped] is a decision rather
 * than an outcome, which is why it is neutral rather than red.
 */
internal enum class RunVerdict(@StringRes val label: Int) {
    /** Nothing has run: the screen is open and a start is what would change this. */
    Idle(R.string.status_not_installed),

    /** In flight, including the settle wait. */
    Running(R.string.history_running),

    /** Root is in and KernelSU is loaded. */
    Succeeded(R.string.history_succeeded),

    /** Root is in, and the load was switched off or not reached. */
    RootOnly(R.string.history_root_only),

    /** Something went wrong, and the run says which stage. */
    Failed(R.string.history_failed),

    /** The user ended it. */
    Stopped(R.string.history_stopped),
}

/** The verdict for a run in flight or just finished, from the phase the screen is holding. */
internal fun runVerdict(phase: InstallPhase, busy: Boolean): RunVerdict = when {
    busy -> RunVerdict.Running
    phase == InstallPhase.Installed -> RunVerdict.Succeeded
    phase == InstallPhase.RootOnly -> RunVerdict.RootOnly
    phase == InstallPhase.Failed -> RunVerdict.Failed
    phase == InstallPhase.Stopped -> RunVerdict.Stopped
    else -> RunVerdict.Idle
}

/** The verdict for a stored run, which is the same vocabulary one step later. */
internal fun runVerdict(result: InstallRunResult): RunVerdict = when (result) {
    InstallRunResult.Running -> RunVerdict.Running
    InstallRunResult.Succeeded -> RunVerdict.Succeeded
    InstallRunResult.RootOnly -> RunVerdict.RootOnly
    InstallRunResult.Failed -> RunVerdict.Failed
    InstallRunResult.Stopped -> RunVerdict.Stopped
}

/**
 * The three colours a surface needs for a verdict: what it sits on, what is written on that, and the accent
 * for anything drawn beside it.
 *
 * A pair rather than one colour because every surface here draws a container with text on it, and picking the
 * pair in four places is how one of them ends up with a combination that cannot be read. The accent is for
 * the glyphs and rules that are not on the container.
 *
 * Keyed to the theme's roles rather than to green, amber and red: this app's palette is generated from the
 * user's accent, so "the colour for success" is the accent and "the colour for a warning" is the tertiary role
 * - inventing literal hues here would be the fourth palette on a phone that has one.
 */
internal data class VerdictColors(val container: Color, val content: Color, val accent: Color)

@Composable
internal fun verdictColors(verdict: RunVerdict): VerdictColors {
    val scheme = MaterialTheme.colorScheme
    return when (verdict) {
        // Idle is the accent as well, because it is the same claim on every other screen: this is the thing
        // to press.
        RunVerdict.Idle -> VerdictColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        RunVerdict.Running ->
            VerdictColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
        RunVerdict.Succeeded ->
            VerdictColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        RunVerdict.RootOnly ->
            VerdictColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
        RunVerdict.Failed -> VerdictColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
        RunVerdict.Stopped ->
            VerdictColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, scheme.onSurfaceVariant)
    }
}

/** The glyph for a verdict, so the same outcome also looks the same. */
internal fun verdictIcon(verdict: RunVerdict): ImageVector = when (verdict) {
    RunVerdict.Idle -> Icons.Rounded.Warning
    RunVerdict.Running -> Icons.Rounded.Schedule
    RunVerdict.Succeeded -> Icons.Rounded.CheckCircle
    RunVerdict.RootOnly -> Icons.Rounded.LockOpen
    RunVerdict.Failed -> Icons.Rounded.Error
    RunVerdict.Stopped -> Icons.Rounded.Block
}
