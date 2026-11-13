package dev.wonderdesk.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/* ------------------------------------ tap the tab again: back to top ------------------------------------ */

/** Scrolls [state] back to the top when the user taps the tab that's already open. */
@Composable
fun ScrollToTopOnReselect(state: LazyListState) {
    val r = LocalTabReselect.current
    val start = remember { r.value }
    LaunchedEffect(r.value) { if (r.value != start) { if (state.firstVisibleItemIndex > 30) state.scrollToItem(12); state.animateScrollToItem(0) } }
}

@Composable
fun ScrollToTopOnReselect(state: LazyGridState) {
    val r = LocalTabReselect.current
    val start = remember { r.value }
    LaunchedEffect(r.value) { if (r.value != start) { if (state.firstVisibleItemIndex > 60) state.scrollToItem(24); state.animateScrollToItem(0) } }
}

@Composable
fun ScrollToTopOnReselect(state: ScrollState) {
    val r = LocalTabReselect.current
    val start = remember { r.value }
    LaunchedEffect(r.value) { if (r.value != start) state.animateScrollTo(0) }
}

/** A list state that jumps back to the top when its tab is tapped again. */
@Composable
fun rememberTabTopList(): LazyListState { val s = androidx.compose.foundation.lazy.rememberLazyListState(); ScrollToTopOnReselect(s); return s }

/** A grid state that jumps back to the top when its tab is tapped again. */
@Composable
fun rememberTabTopGrid(): LazyGridState { val s = androidx.compose.foundation.lazy.grid.rememberLazyGridState(); ScrollToTopOnReselect(s); return s }

/* -------------------------------------------- pull to refresh -------------------------------------------- */

/**
 * Pull down past the top of [content] (any scrolling list inside) to refresh. A glossy spinner follows the
 * finger, gives a tap when it's far enough, and spins while [onRefresh] runs.
 */
@Composable
fun PullToRefresh(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val p = wonderPalette()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val trigger = with(density) { 72.dp.toPx() }
    val rest = with(density) { 56.dp.toPx() }
    val pull = remember { Animatable(0f) }
    var refreshing by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    fun release() {
        if (pull.value >= trigger && !refreshing) {
            refreshing = true
            scope.launch {
                pull.animateTo(rest, tween(160))
                runCatching { onRefresh() }
                pull.animateTo(0f, tween(220)); refreshing = false
            }
        } else if (!refreshing) scope.launch { pull.animateTo(0f, spring(stiffness = 500f)) }
        armed = false
    }
    val connection = remember(enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || refreshing || source != NestedScrollSource.Drag || available.y >= 0 || pull.value <= 0f) return Offset.Zero
                val used = maxOf(available.y, -pull.value)
                scope.launch { pull.snapTo(pull.value + used) }
                return Offset(0f, used)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || refreshing || source != NestedScrollSource.Drag || available.y <= 0) return Offset.Zero
                val next = (pull.value + available.y * .5f).coerceAtMost(trigger * 1.6f)
                scope.launch { pull.snapTo(next) }
                if (next >= trigger && !armed) { armed = true; view.tick() } else if (next < trigger) armed = false
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value > 0f) { release(); return available }
                return Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = pull.value * .35f }) { content() }
        if (pull.value > 1f || refreshing) {
            val progress = (pull.value / trigger).coerceIn(0f, 1f)
            val spin = rememberInfiniteTransition(label = "refresh")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin")
            Box(Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = pull.value - 44.dp.toPx(); alpha = progress.coerceAtLeast(.25f); scaleX = .6f + .4f * progress; scaleY = scaleX }
                .size(40.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Brush.verticalGradient(listOf(p.panel, p.window))).border(1.dp, p.panelBorder, CircleShape)
                .drawBehind {
                    val sw = 3.dp.toPx(); val inset = 10.dp.toPx()
                    val sweep = if (refreshing) 270f else 300f * progress
                    val start = if (refreshing) angle else -90f + 200f * progress
                    drawArc(p.accent, start, sweep, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(sw, cap = StrokeCap.Round))
                    drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) .10f else .55f), Color.Transparent)), Offset(size.width * .18f, size.height * .06f), Size(size.width * .64f, size.height * .4f))
                })
        }
    }
}

/* ---------------------------------------------- swipe actions ---------------------------------------------- */

/** One thing a list row does when swiped: [destructive] lets the row slide away instead of springing back. */
data class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val destructive: Boolean = false, val onSwipe: () -> Unit)

/**
 * Swipe a row right for [start] or left for [end]. The coloured action shows under the row with its icon and
 * name, and there's a tap when it's far enough to trigger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(start: SwipeAction? = null, end: SwipeAction? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (start == null && end == null) { Box(modifier) { content() }; return }
    val p = wonderPalette()
    val view = LocalView.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        val a = when (v) { SwipeToDismissBoxValue.StartToEnd -> start; SwipeToDismissBoxValue.EndToStart -> end; else -> null }
        if (a != null) { view.tick(); a.onSwipe() }
        a?.destructive == true
    }, positionalThreshold = { it * .32f })
    SwipeToDismissBox(state, modifier = modifier, enableDismissFromStartToEnd = start != null, enableDismissFromEndToStart = end != null, backgroundContent = {
        val dir = state.dismissDirection
        val a = if (dir == SwipeToDismissBoxValue.StartToEnd) start else if (dir == SwipeToDismissBoxValue.EndToStart) end else null
        val reached = state.targetValue != SwipeToDismissBoxValue.Settled
        Row(Modifier.fillMaxSize().background(if (a == null) Color.Transparent else if (reached) a.color else a.color.copy(alpha = .55f)).padding(horizontal = 22.dp),
            horizontalArrangement = if (dir == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            if (a != null) {
                Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(22.dp).graphicsLayer { val s = if (reached) 1.15f else 1f; scaleX = s; scaleY = s })
                Spacer(Modifier.width(8.dp))
                Text(a.label, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }) { Box(Modifier.background(p.panel)) { content() } }
}
