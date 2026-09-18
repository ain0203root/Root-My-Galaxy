package dev.busung.s25uroot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The run's own controls, in a bar that floats over the page rather than ending it.
 *
 * The same treatment the tabs' pages give their navigation bar, for the same reason: a control that lives at
 * the end of a scrolling page can only be reached by scrolling to it, and on this screen the thing being read
 * is the log - which took the worst of it, ending where the buttons began. As a sibling of the page it costs
 * the page no layout, the log keeps the whole screen, and the controls are where a thumb already is.
 *
 * Which of them exist is not this file's business: it draws a row and the screen fills it. What is here is the
 * *shape* - the pill, the fade behind it and the height it takes - because all three are the same on both
 * screens that float a bar, and the tab bar's version of them already lives with the bar in `MainActivity`.
 *
 * The fade is drawn on the bar's own band rather than by the page, which is what makes it leave with the bar
 * and what stops a long page from arriving at the pill at full contrast. A gradient and not a blur: a blur of
 * a scrolling log means rendering it into an offscreen layer and re-blurring it every frame.
 *
 * [progress] is the run's own fraction along its stages, drawn as a hairline across the pill's top edge. It is
 * here rather than only on the status card because the card can be scrolled away - the bar cannot, which is
 * the whole reason it floats - and the question "how far has it got" is one the log answers only by being
 * read. The caller supplies the fraction rather than this file deriving one, so the strip and the card's bar
 * cannot disagree about where the run is.
 */
@Composable
internal fun RunActionBar(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Before the insets, so the fade reaches the bottom of the screen rather than stopping at the
            // top of the gesture area - the page under the bar is page the bar is floating over too.
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    // 12dp rather than the tab bar's 8dp: these are filled buttons with corners of their own,
                    // and the bar's own curve would otherwise cut into the outer two.
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
                // Over the pill's own top padding rather than above the buttons, so the strip costs the bar no
                // height: this control row is already the tallest thing on the screen it floats over.
                RunProgressHairline(progress)
            }
        }
    }
}

/** How thick the run's progress hairline is - a rule, not a bar. */
private val PROGRESS_HAIRLINE_HEIGHT = 3.dp

/**
 * The run's fraction, as a hairline across the top of the pill.
 *
 * Animated to the new value rather than snapped to it: a stage boundary in this app is a coroutine arriving
 * somewhere, and a line that jumps says a stage was entered rather than that the run moved - which, over a
 * twelve-minute exploit, is the difference between a progress line and an event counter.
 */
@Composable
private fun BoxScope.RunProgressHairline(progress: Float) {
    val fraction by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "run-progress",
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(PROGRESS_HAIRLINE_HEIGHT)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Whether the run screen has anything to offer for this state, which is whether its bar exists at all.
 *
 * An empty pill over a page is worse than no bar, and this is the one place the question is answered - the
 * screen builds its row from the same phases, so the two cannot disagree about whether there is one.
 *
 * Busy covers every phase the app is working through, including the settle wait; the four at the end are the
 * states a run can finish in, and each has something to press: a retry after a failure or a stop, and a way
 * out after a success. [InstallPhase.Ready] is the one phase that is neither - the screen is open and the run
 * has not started - and that is a page with nothing to press rather than a bar with nothing in it.
 */
internal fun runControlsOffered(phase: InstallPhase, busy: Boolean): Boolean = when {
    busy -> true
    phase == InstallPhase.Failed -> true
    phase == InstallPhase.Stopped -> true
    phase == InstallPhase.Installed -> true
    phase == InstallPhase.RootOnly -> true
    else -> false
}
