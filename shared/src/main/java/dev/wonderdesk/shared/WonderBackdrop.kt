package dev.wonderdesk.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The window material behind every app: a soft Aqua sky, Luna sand, Dark slate or Super neon glow.
 * Drawn once and cached; it only redraws when the window size or look changes, so it costs no
 * battery while the app sits on screen.
 */
@Composable
fun WonderBackdrop(look: WonderLook, @Suppress("UNUSED_PARAMETER") motion: WonderMotion, transparent: Boolean = false) {
    Box(Modifier.fillMaxSize().drawWithCache {
        val w = size.width
        val h = size.height
        val base: Brush? = if (transparent) null else when (look) {
            WonderLook.AQUA -> Brush.verticalGradient(listOf(Color(0xFFEAF5FA), Color(0xFFDCEAF0), Color(0xFFE8F3F4)))
            WonderLook.SUPER -> Brush.verticalGradient(listOf(Color(0xFF0D0A1A), Color(0xFF1A1230), Color(0xFF102B3A)))
            WonderLook.BLISS -> Brush.verticalGradient(listOf(Color(0xFFF4F1E4), Color(0xFFECE9D8), Color(0xFFDAD6C4)))
            WonderLook.DARK -> Brush.verticalGradient(listOf(Color(0xFF333943), Color(0xFF23262B), Color(0xFF191D24)))
        }
        // Fixed glow positions (what the old drifting blobs looked like at rest).
        val glows: List<Triple<Offset, Float, Brush>> = when (look) {
            WonderLook.AQUA -> (0..2).map { i ->
                val c = Offset(w * (.5f + .38f * sin(1.3f + i * 2.1f)), h * (.34f + .32f * cos(1.1f + i * 2.3f)))
                val r = max(w, h) * .75f
                Triple(c, r, Brush.radialGradient(listOf(Color.White.copy(alpha = if (transparent) .08f else .36f), Color(0xFF8FCFE3).copy(alpha = if (transparent) .03f else .10f), Color.Transparent), c, r))
            }
            WonderLook.SUPER -> listOf(Color(0xFFB01EFF), Color(0xFF00EAD7), Color(0xFFFF2E9A)).mapIndexed { i, color ->
                val c = Offset(w * (.5f + .38f * sin(.8f + i * 2.1f)), h * (.5f + .38f * cos(.6f + i * 2.1f)))
                val r = max(w, h) * .7f
                Triple(c, r, Brush.radialGradient(listOf(color.copy(alpha = if (transparent) .12f else .28f), Color.Transparent), c, r))
            }
            else -> emptyList()
        }
        onDrawBehind {
            if (base != null) drawRect(base)
            glows.forEach { (c, r, b) -> drawCircle(b, r, c) }
        }
    })
}
