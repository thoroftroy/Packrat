package dev.wonderdesk.shared

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Ambient materials never intercept input and stop drawing frames when backgrounded. */
@Composable
fun WonderBackdrop(look: WonderLook, motion: WonderMotion, transparent: Boolean = false) {
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var systemMotion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var phase by remember { mutableLongStateOf(0L) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            systemMotion = ValueAnimator.areAnimatorsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, systemMotion, motion, look) {
        if (active && systemMotion && motion == WonderMotion.FULL && (look == WonderLook.AQUA || look == WonderLook.SUPER)) {
            val started = SystemClock.elapsedRealtime()
            while (true) {
                phase = SystemClock.elapsedRealtime() - started
                delay(100L)
            }
        }
    }
    val seconds = if (motion == WonderMotion.FULL && systemMotion) phase / 1000f else 0f
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (look) {
            WonderLook.AQUA -> {
                if (!transparent) drawRect(Brush.verticalGradient(listOf(Color(0xFFEAF5FA), Color(0xFFDCEAF0), Color(0xFFE8F3F4))))
                val radius = max(w, h) * .75f
                for (i in 0..2) {
                    val x = w * (.5f + .38f * sin(seconds * .12f + i * 2.1f))
                    val y = h * (.34f + .32f * cos(seconds * .1f + i * 2.3f))
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (transparent) .08f else .36f), Color(0xFF8FCFE3).copy(alpha = if (transparent) .03f else .10f), Color.Transparent), Offset(x, y), radius), radius, Offset(x, y))
                }
            }
            WonderLook.SUPER -> {
                if (!transparent) drawRect(Brush.verticalGradient(listOf(Color(0xFF0D0A1A), Color(0xFF1A1230), Color(0xFF102B3A))))
                val colors = listOf(Color(0xFFB01EFF), Color(0xFF00EAD7), Color(0xFFFF2E9A))
                val radius = max(w, h) * .7f
                colors.forEachIndexed { i, color ->
                    val x = w * (.5f + .38f * sin(seconds * .5f + i * 2.1f))
                    val y = h * (.5f + .38f * cos(seconds * .38f + i * 2.1f))
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (transparent) .12f else .28f), Color.Transparent), Offset(x, y), radius), radius, Offset(x, y))
                }
            }
            WonderLook.BLISS -> drawRect(Brush.verticalGradient(listOf(Color(0xFFF4F1E4), Color(0xFFECE9D8), Color(0xFFDAD6C4))))
            WonderLook.DARK -> drawRect(Brush.verticalGradient(listOf(Color(0xFF333943), Color(0xFF23262B), Color(0xFF191D24))))
        }
    }
}
