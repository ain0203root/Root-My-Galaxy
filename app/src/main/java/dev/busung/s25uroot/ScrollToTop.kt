package dev.busung.s25uroot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A screen's list, with the button that returns it to the top.
 *
 * Every scrolling page goes through this instead of placing the button itself, because the button has to be
 * a sibling of the list rather than a thing the list draws: it floats over the content and must not scroll
 * with it. The state is the caller's, since a page that jumps to a card needs to drive the same state this
 * draws over.
 *
 * The list's own bottom padding is extended by the room the button and the floating navigation bar need, so
 * the last card cannot end up sitting under either. Pages keep their own padding and do not have to know
 * what is drawn over them.
 */
@Composable
internal fun PageList(
    padding: PaddingValues,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val scrolled by rememberScrolledState(listState)
    Box(modifier = modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + pageBottomClearance(),
            ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        // Under the bar rather than over it: the bar is drawn by the app shell, which this page knows
        // nothing about, so the page's job is only to stop the row it is reading from arriving at the pill
        // at full contrast.
        BottomScrim(
            visible = scrolled,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        BackToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd).pageBottomInset(),
        )
    }
}

/**
 * The fade that keeps the pill legible where a page runs under it.
 *
 * Without it the bar's own row is the only thing between a scrolling row and the pill, and the two are 10dp
 * apart: a card sliding under it arrives at its full colour and stops against a hard edge. This is the page
 * fading into its own background over the bar's height, so what goes under the pill is already nearly the
 * colour of what surrounds it.
 *
 * Drawn only while the page has actually moved, which is the same rule the button above it uses. A page at
 * its top has its first row sitting well clear of the bar by its own bottom padding, so a permanent scrim
 * would be shading the end of a list that never reaches it - a strip of tint over nothing.
 *
 * A gradient rather than a blur. A blur of a scrolling list means rendering it into an offscreen layer and
 * re-blurring it every frame, for a fade the eye reads the same way here - the content under the bar is dark
 * on dark, and what a blur would preserve is detail that this app's last rows do not carry.
 *
 * Public to the module because the history list draws its own box rather than going through [PageList], and a
 * page that runs under the pill has to look the same whether or not it shares the wrapper.
 */
@Composable
internal fun BottomScrim(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_BAR_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
        )
    }
}

/**
 * Whether a list has moved, which is the one rule the scrim and the button both key off.
 *
 * Held here rather than written twice: the two have to agree, and the failure of a mismatch is a scrim over
 * a list at its top or a button that is not there when it is needed.
 */
@Composable
internal fun rememberScrolledState(listState: LazyListState): State<Boolean> = remember(listState) {
    derivedStateOf {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    }
}

/** A list state for a page that draws its own list rather than going through [PageList]. */
@Composable
internal fun rememberPageListState(): LazyListState = rememberLazyListState()

/**
 * A screen that scrolls as a column, with the button that returns it to the top.
 *
 * The same job [PageList] does for a list, for a screen whose content is not one - and the reason the button
 * is drawn here rather than at the call site: it has to be a sibling of the scrolling column, not a child of
 * it, so that it stays put while the column moves under it.
 */
@Composable
internal fun PageColumn(
    padding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    /**
     * Where anything on this screen the button must never sit over starts, in window coordinates - or the
     * default when the screen has nothing like that.
     *
     * Only a screen whose end is its own controls needs this. From this point down is something the user may
     * be about to press, and the run screen's Stop button is the one that cannot afford to be covered, so
     * while any of it is visible the button is not drawn at all. The comparison is against the bottom of the
     * scrolling area rather than the window, because that is the edge the button is measured from.
     */
    controlsTop: Float = Float.POSITIVE_INFINITY,
    content: @Composable ColumnScope.() -> Unit,
) {
    var viewportBottom by remember { mutableStateOf(0f) }
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportBottom = coordinates.positionInWindow().y + coordinates.size.height
                }
                .verticalScroll(scrollState),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        BackToTopFab(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            standDown = controlsTop < viewportBottom,
        )
    }
}

/**
 * The button that takes a long list back to its start.
 *
 * It draws itself only once the list has actually moved. One that is always there covers the row it sits
 * over for no reason, and it is at the top already - which is where it would take you.
 *
 * Animated rather than a jump: the list is what is being read, and losing it in one frame makes it hard to
 * tell where the top of it was.
 */
@Composable
internal fun BackToTopFab(listState: LazyListState, modifier: Modifier = Modifier) {
    val scrolled by rememberScrolledState(listState)
    BackToTopButton(visible = scrolled, modifier = modifier) {
        listState.animateScrollToItem(0)
    }
}

/**
 * The same button for a screen that scrolls a column rather than a list.
 *
 * [standDown] is the screen's own answer about whether the button would be in the way; [PageColumn] is where
 * that comes from.
 */
@Composable
internal fun BackToTopFab(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    standDown: Boolean = false,
) {
    val scrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    BackToTopButton(visible = scrolled && !standDown, modifier = modifier) {
        scrollState.animateScrollTo(0)
    }
}

@Composable
private fun BackToTopButton(
    visible: Boolean,
    modifier: Modifier,
    scrollToTop: suspend () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        SmallFloatingActionButton(
            onClick = {
                clickHaptic(view)
                scope.launch { scrollToTop() }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.action_back_to_top),
            )
        }
    }
}

/**
 * What the button takes of a list's own bottom padding.
 *
 * A small button is 40dp and sits 20dp off the edge, so this leaves the last row a little air above it.
 */
private val BACK_TO_TOP_CLEARANCE = 72.dp

/**
 * The room a page's last row keeps under it: the button's clearance, plus the bar the button now sits above.
 *
 * Both are needed rather than the larger of the two - the button stands on top of the bar, so the row it must
 * not sit under is the higher of the two, and that is the sum.
 */
private fun pageBottomClearance(): Dp = BACK_TO_TOP_CLEARANCE + NAV_BAR_HEIGHT

/**
 * Where the button sits in a page's bottom corner: its own air, then the bar's height, because the bar is
 * drawn over the page rather than beside it and the button would otherwise be behind the pill.
 */
private fun Modifier.pageBottomInset(): Modifier =
    padding(20.dp).padding(bottom = NAV_BAR_HEIGHT)
