package dev.wonderdesk.shared

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

/**
 * Pure, deterministic tap/ambient particle model ported from the owner's
 * BlissPlayer motion system (same clock, sparks, trails and rings).
 * No Android dependencies; rendering lives in [WonderFxOverlay].
 */
class WonderMotionField(seed: Long = 0xB1155L) {
    data class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val radius: Float, var life: Float, val duration: Float, val color: Int,
        val ambient: Boolean, var previousX: Float = x, var previousY: Float = y
    )
    data class Ring(val x: Float, val y: Float, var age: Float = 0f, val color: Int)

    val sparks = mutableListOf<Spark>()
    val rings = mutableListOf<Ring>()
    var elapsed = 0f
        private set
    private var state = seed
    private var seeded = false
    private val neon = intArrayOf(
        0xFF6DFFE1.toInt(), 0xFFFF6AAE.toInt(), 0xFF6B9DFF.toInt(),
        0xFFFFCE68.toInt(), 0xFFD6A8FF.toInt(), 0xFFFFFFFF.toInt()
    )
    private val retroLight = intArrayOf(
        0xFF0058E6.toInt(), 0xFF3A93FF.toInt(), 0xFF9DBB61.toInt(),
        0xFFE8B33C.toInt(), 0xFFFFFFFF.toInt()
    )
    private val retroDark = intArrayOf(
        0xFF7FB2E5.toInt(), 0xFF5B7A99.toInt(), 0xFFFFB84D.toInt(),
        0xFFFF7A45.toInt(), 0xFFE8E8E8.toInt()
    )
    val aqua = intArrayOf(0xFFEEFEFF.toInt(), 0xFFBFE9FF.toInt(), 0xFFFFFFFF.toInt())

    private fun random(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L
        return ((state ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
    }

    fun burst(x: Float, y: Float, look: WonderLook, full: Boolean, density: Float = 2.5f) {
        when (look) {
            WonderLook.SUPER -> {
                repeat(if (full) 3 else 1) {
                    rings.add(Ring(x, y, color = neon[(random() * neon.size).toInt()]))
                }
                if (rings.size > 12) rings.removeAll(rings.take(rings.size - 12))
                val sc = if (full) 1.4f else 0.7f   // dp (Bliss Player uses density × 1.4 in pixels)
                repeat(if (full) 60 else 24) {
                    if (sparks.size >= 180) return@repeat
                    val angle = random() * 6.2831853f
                    val speed = (65 + random() * 195) * sc
                    val duration = 0.5f + random() * 0.8f
                    sparks.add(Spark(x, y, cos(angle) * speed, sin(angle) * speed,
                        (1.3f + random() * 2.8f) * sc, duration, duration,
                        neon[(random() * neon.size).toInt()], false))
                }
            }
            WonderLook.AQUA -> {
                // Water rings only, like the owner's Aqua renderer.
                repeat(if (full) 2 else 1) {
                    rings.add(Ring(x, y, color = aqua[(random() * aqua.size).toInt()]))
                }
                if (rings.size > 12) rings.removeAll(rings.take(rings.size - 12))
            }
            else -> {
                // Retro pixel pop: no rings, a few short sparks.
                repeat(8) {
                    if (sparks.size >= 180) return@repeat
                    val angle = random() * 6.2831853f
                    val speed = (65 + random() * 195) * 0.55f
                    val duration = (0.5f + random() * 0.8f) * 0.55f
                    val pal = if (look.dark) retroDark else retroLight
                    sparks.add(Spark(x, y, cos(angle) * speed, sin(angle) * speed,
                        (1.3f + random() * 2.8f) * 0.55f, duration, duration,
                        pal[(random() * pal.size).toInt()], false))
                }
            }
        }
    }

    fun step(seconds: Float, width: Float, height: Float, ambient: Boolean) {
        val dt = seconds.coerceIn(0f, 0.05f)
        elapsed += dt
        if (width <= 0 || height <= 0) return
        if (!seeded && ambient) {
            repeat(26) { addAmbient(width, height, true) }
            seeded = true
        }
        val iterator = sparks.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0) { iterator.remove(); continue }
            p.previousX = p.x
            p.previousY = p.y
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.ambient) p.x += sin(elapsed * 0.9f + p.radius) * dt * 8f
            else p.vy += 105f * dt
        }
        val ri = rings.iterator()
        while (ri.hasNext()) { if (ri.next().also { it.age += dt }.age > 1.1f) ri.remove() }
        if (ambient && sparks.count { it.ambient } < 26) addAmbient(width, height, false)
    }

    private fun addAmbient(w: Float, h: Float, anywhere: Boolean) {
        val duration = 6f + random() * 7f
        sparks.add(Spark(random() * w, if (anywhere) random() * h else h * (0.6f + random() * 0.4f),
            (random() - 0.5f) * 7f, -(9f + random() * 16f),
            0.7f + random() * 1.3f, duration, duration,
            neon[(random() * neon.size).toInt()], true))
    }

    fun clear() { sparks.clear(); rings.clear(); seeded = false }

    /** Called after a tap adds effects so the overlay wakes up. */
    var onBurst: (() -> Unit)? = null
}

/**
 * Tap ripples and sparks. Sleeps completely until something is tapped: frames run on vsync only
 * while an effect is alive (or the Super look's ambient sparks are on), and never while the app
 * is in the background. Never consumes input; honors the motion setting and Android's own.
 */
@Composable
fun WonderFxOverlay(field: WonderMotionField = remember { WonderMotionField() }) {
    val look = LocalLook.current
    val motion = LocalMotion.current
    val systemMotion = remember { ValueAnimator.areAnimatorsEnabled() }
    val active = motion != WonderMotion.OFF && systemMotion
    val density = LocalDensity.current.density
    val resumed = rememberIsResumed()
    val ambient = motion == WonderMotion.FULL && look == WonderLook.SUPER
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        var wake by remember { mutableIntStateOf(0) }
        var tick by remember { mutableLongStateOf(0L) }
        DisposableEffect(field) { field.onBurst = { wake++ }; onDispose { field.onBurst = null } }
        LaunchedEffect(look, motion, w, h) { field.clear() }
        LaunchedEffect(active, resumed, ambient, wake, w, h) {
            if (!active || !resumed) return@LaunchedEffect
            var last = 0L
            while (isActive && (ambient || field.sparks.isNotEmpty() || field.rings.isNotEmpty())) {
                // Ambient-only periods run at half rate to save power; taps run at full vsync.
                if (ambient && field.rings.isEmpty() && field.sparks.none { !it.ambient }) kotlinx.coroutines.delay(24L)
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                    last = now
                    field.step(dt, w / density, h / density, ambient)
                    tick = now
                }
            }
            tick = -1L
        }
        if (!active) return@BoxWithConstraints
        val retro = look != WonderLook.AQUA && look != WonderLook.SUPER
        val superRainbow = look == WonderLook.SUPER && motion == WonderMotion.FULL
        Canvas(Modifier.fillMaxSize()) {
            if (tick == 0L) return@Canvas
            if (ambient) {
                // Bliss Player's aurora: three faint rainbow glows drifting behind everything.
                val t = field.elapsed; val r = max(size.width, size.height) * .5f
                for (i in 0 until 3) {
                    val cx = size.width * (.5f + .44f * sin(t * 1.9f + i * 2.094f)); val cy = size.height * (.5f + .44f * cos(t * 1.4f + i * 1.7f + 1f))
                    val hue = ((t * 95f + i * 120f) % 360f + 360f) % 360f
                    drawCircle(Brush.radialGradient(listOf(Color.hsv(hue, .85f, 1f, 30f / 255f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
                }
            }
            for (ring in field.rings) {
                val fraction = (ring.age / 1.1f).coerceIn(0f, 1f)
                val spread = if (retro) 34f else if (motion != WonderMotion.FULL) 38f else 100f
                val scale = density
                val radius = (9f + fraction * spread) * scale
                val alpha = ((1f - fraction) * if (look == WonderLook.AQUA) 130 else 200).toInt()
                val rc = if (look == WonderLook.AQUA) Color(238, 254, 255, alpha) else rainbow(ring.color, alpha, ring.x, ring.y, field.elapsed, superRainbow)
                val at = Offset(ring.x * scale, ring.y * scale)
                drawCircle(color = rc, radius = radius, center = at, style = Stroke(width = (2.5f - fraction * 2f) * scale))
                drawCircle(color = rc.copy(alpha = rc.alpha / 2f), radius = radius * .76f, center = at, style = Stroke(width = .7f * scale))
            }
            for (p in field.sparks) {
                if (look == WonderLook.AQUA) continue
                val fraction = (p.life / p.duration).coerceIn(0f, 1f)
                if (retro) {
                    val alpha = (fraction * 255).toInt().coerceIn(0, 255)
                    val c = Color(p.color).copy(alpha = alpha / 255f)
                    val arm = max(2f * density, p.radius * 3.2f * fraction + 1f)
                    drawLine(c, Offset((p.x - arm) * density, p.y * density), Offset((p.x + arm) * density, p.y * density), strokeWidth = max(1f, density * 0.9f))
                    drawLine(c, Offset(p.x * density, (p.y - arm) * density), Offset(p.x * density, (p.y + arm) * density), strokeWidth = max(1f, density * 0.9f))
                    val sq = max(1.5f, density * 1.4f * fraction + 0.5f)
                    drawRect(c, topLeft = Offset(p.x * density - sq, p.y * density - sq), size = Size(sq * 2, sq * 2))
                    continue
                }
                // Super, drawn the way Bliss Player does it: a soft glow that fades to nothing, a streak along the
                // spark's path, a bright core, and colours that drift through the rainbow.
                val alpha = ((if (p.ambient) .38f + sin(fraction * 5f) * .15f else fraction) * 230).toInt().coerceIn(0, 255)
                val c = rainbow(p.color, alpha, p.x, p.y, field.elapsed, superRainbow)
                val centre = Offset(p.x * density, p.y * density)
                val glow = max(1f, p.radius * (if (p.ambient) 3f else 5f) * density)
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), centre, glow), glow, centre)
                if (!p.ambient) drawLine(c, Offset(p.previousX * density, p.previousY * density), centre, strokeWidth = max(.6f * density, p.radius * .45f * density))
                drawCircle(c, max(.6f * density, p.radius * fraction * density), centre)
            }
        }
    }
}

/** Super's drifting rainbow (Bliss Player's formula); other looks keep their palette colour. */
private fun rainbow(base: Int, alpha: Int, x: Float, y: Float, t: Float, on: Boolean): Color {
    if (!on) return Color(base).copy(alpha = alpha / 255f)
    val hue = ((t * 46f + x * 0.65f + y * 0.47f) % 360f + 360f) % 360f
    return Color.hsv(hue, .85f, 1f, alpha / 255f)
}

/** Records taps into the motion field without consuming gestures. */
fun Modifier.wonderFx(
    field: WonderMotionField,
    enabled: Boolean,
    look: WonderLook,
    full: Boolean,
    density: Float
): Modifier = pointerInput(enabled, field, look, full) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Main)
            if (enabled && event.type == PointerEventType.Press) {
                val position = event.changes.firstOrNull()?.position
                if (position != null) {
                    field.burst(position.x / density, position.y / density, look, full, density)
                    field.onBurst?.invoke()
                }
            }
        }
    }
}
