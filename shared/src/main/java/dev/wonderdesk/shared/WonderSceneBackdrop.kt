package dev.wonderdesk.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * Each Wonderdesk app has its own colour for the shared animated scene: Aqua water and bubbles, the Bliss hills, the Super nebula.
 * The number is how far the scene's colours are turned around the colour wheel (Aqua's blue is about 200 degrees), so Abacus has orange water
 * and Safekeeper a lighter, softer blue. The scenes follow the real sunrise and sunset for where you are, like the home screen wallpaper.
 */
object WonderScenery {
    private val HUES = mapOf(
        "Abacus" to -165, "Safekeeper" to -14, "Pulse" to -72, "Chronicle" to -196, "Timepiece" to -148, "Scribble" to 82, "Vault" to 40, "Cinema" to 118,
        "Memories" to -32, "Packrat" to -118, "Pages" to -138, "Echo" to 140, "Wayfinder" to -52, "Beacon" to -98, "Control Room" to 14, "Wonderdesk Apphub" to 22,
        "Ripple" to 0, "Polaris" to 0, "Launcher" to 0,
    )
    fun hue(appName: String): Int = HUES.entries.firstOrNull { appName.contains(it.key, ignoreCase = true) }?.value ?: 0
}

/** Average brightness (0 dark to 1 bright) of a scene frame, from a coarse grid of pixels. */
private fun sceneLuma(b: android.graphics.Bitmap): Float {
    var sum = 0f; var n = 0
    for (iy in 1..6) for (ix in 1..6) { val p = b.getPixel(b.width * ix / 7, b.height * iy / 7); sum += (.299f * android.graphics.Color.red(p) + .587f * android.graphics.Color.green(p) + .114f * android.graphics.Color.blue(p)) / 255f; n++ }
    return sum / n
}

/** Small symbols that drift through an app's scene, so every app's scene is about its own subject (maths signs for Abacus, notes for Echo, arrows for Wayfinder). */
private class AppMotif(private val w: Int, private val h: Int, private var glyphs: List<String>, count: Int = 14) {
    private class G(var x: Float, var y: Float, var s: String, val size: Float, val speed: Float, val sway: Float, val phase: Float, val alpha: Int) { var old: String? = null; var vx = 0f; var vy = 0f }
    /** A symbol shot up through the scene by a celebration; it rises fast and fades out. */
    private class Spark(var x: Float, var y: Float, val s: String, val size: Float, val vy: Float, val vx: Float, var life: Float, val spin: Float) { var age = 0f }
    private val rnd = java.util.Random(5)
    private val items = List(count) { make(true) }
    private val sparks = ArrayList<Spark>()
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); textAlign = android.graphics.Paint.Align.CENTER }
    private var t = 0f
    /** 1 when the newest symbols have fully faded in after a change. */
    private var swap = 1f
    private fun pick() = if (glyphs.isEmpty()) "" else glyphs[rnd.nextInt(glyphs.size)]
    private fun make(anywhere: Boolean) = G(rnd.nextFloat() * w, if (anywhere) rnd.nextFloat() * h else h + 30f, pick(), h * (.022f + rnd.nextFloat() * .026f), h * (.02f + rnd.nextFloat() * .035f), w * (.01f + rnd.nextFloat() * .02f), rnd.nextFloat() * 6.28f, 38 + rnd.nextInt(50))
    /** New symbols cross-fade in where the old ones are, so the scene changes subject without anything jumping. */
    fun setGlyphs(next: List<String>) {
        if (next == glyphs) return
        glyphs = next
        for (g in items) { g.old = g.s; g.s = pick() }
        swap = 0f
    }
    fun burst(with: List<String>) {
        if (with.isEmpty()) return
        // A gentle lift of a dozen symbols that fade in, rise and fade away: a celebration, not a spray.
        repeat(12) {
            sparks += Spark(w * (.12f + rnd.nextFloat() * .76f), h * (.55f + rnd.nextFloat() * .4f), with[rnd.nextInt(with.size)], h * (.028f + rnd.nextFloat() * .026f),
                h * (.16f + rnd.nextFloat() * .2f), w * (rnd.nextFloat() - .5f) * .06f, 2.4f + rnd.nextFloat() * 1.2f, (rnd.nextFloat() - .5f) * 30f)
        }
        if (sparks.size > 36) repeat(sparks.size - 36) { sparks.removeAt(0) }
    }
    /** A finger went by: symbols near it are pushed away (and along, when it is dragging), then drift back to their slow rise. */
    fun push(x: Float, y: Float, dx: Float, dy: Float, strength: Float) {
        val r = minOf(w, h) * .28f
        for (g in items) {
            val ex = g.x - x; val ey = g.y - y; val d = kotlin.math.hypot(ex, ey)
            if (d > r || d < 1f) continue
            val f = (1f - d / r) * strength
            g.vx += ex / d * r * 2.2f * f + dx * 6f * f; g.vy += ey / d * r * 2.2f * f + dy * 6f * f
        }
    }
    fun busy() = sparks.isNotEmpty() || swap < 1f
    fun step(dt: Float) {
        t += dt
        if (swap < 1f) swap = (swap + dt / .9f).coerceAtMost(1f)
        val damp = kotlin.math.exp(-dt * 2.4f)
        for (g in items) {
            g.x += g.vx * dt; g.y += g.vy * dt; g.vx *= damp; g.vy *= damp
            if (g.x < -g.size) g.x += w + 2 * g.size else if (g.x > w + g.size) g.x -= w + 2 * g.size
            g.y -= g.speed * dt; if (g.y < -g.size) { val n = make(false); g.x = n.x; g.y = n.y; g.s = n.s; g.old = null; g.vx = 0f; g.vy = 0f }
            if (g.y > h + g.size * 2) g.y = h + g.size
        }
        val it = sparks.iterator()
        while (it.hasNext()) { val p = it.next(); p.y -= p.vy * dt; p.x += p.vx * dt; p.life -= dt; p.age += dt; if (p.life <= 0f || p.y < -p.size) it.remove() }
    }
    fun draw(c: android.graphics.Canvas, color: Int) {
        paint.color = color
        for (g in items) {
            val x = g.x + kotlin.math.sin(t * .5f + g.phase) * g.sway
            paint.textSize = g.size
            val o = g.old
            if (o != null && swap < 1f) { paint.alpha = (g.alpha * (1f - swap)).toInt(); c.drawText(o, x, g.y, paint) }
            if (g.s.isNotEmpty()) { paint.alpha = (g.alpha * (if (o != null) swap else 1f)).toInt(); c.drawText(g.s, x, g.y, paint) }
        }
        for (p in sparks) {
            paint.textSize = p.size; paint.alpha = (170 * (p.age / .5f).coerceIn(0f, 1f) * (p.life / 1.2f).coerceIn(0f, 1f)).toInt()
            c.save(); c.rotate(p.spin * p.age, p.x, p.y); c.drawText(p.s, p.x, p.y, paint); c.restore()
        }
    }
}

private fun motifFor(appName: String): List<String> = when {
    appName.contains("Abacus", true) -> listOf("+", "−", "×", "÷", "=", "π", "√", "7", "3")
    appName.contains("Pulse", true) -> listOf("%", "∿", "↑", "↓", "•")
    appName.contains("Chronicle", true) -> listOf("1", "5", "12", "20", "31", "•")
    appName.contains("Timepiece", true) -> listOf("12", "3", "6", "9", ":", "•")
    appName.contains("Scribble", true) -> listOf("#", "*", "_", "[ ]", "~", "¶")
    appName.contains("Vault", true) -> listOf("•", "•", "•", "*", "#")
    appName.contains("Cinema", true) -> listOf("▶", "■", "●", "▮")
    appName.contains("Memories", true) -> listOf("★", "✦", "•", "♥")
    appName.contains("Packrat", true) -> listOf("▤", "▥", "▦", "+")
    appName.contains("Pages", true) -> listOf("¶", "§", "A", "•")
    appName.contains("Echo", true) -> listOf("♪", "♫", "~", "•")
    appName.contains("Wayfinder", true) -> listOf("N", "E", "S", "W", "➤", "•")
    appName.contains("Beacon", true) -> listOf("◉", "•", "○")
    appName.contains("Control Room", true) -> listOf("☰", "●", "○", "▬")
    appName.contains("Apphub", true) || appName.contains("App Hub", true) -> listOf("↓", "↑", "✓", "•")
    appName.contains("Ripple", true) -> listOf(">", "_", "$", "~", "•")
    appName.contains("Safekeeper", true) -> listOf("✓", "•", "○", "◌")
    else -> emptyList()
}

/**
 * What an app wants its scene to feel like right now, taken from what is on its screen: the month Chronicle is showing, how close a
 * Timepiece timer is to the end, how hard the phone is working in Pulse. The scene eases into every change over a second or so.
 */
data class SceneMood(
    /** The colour the scene leans to, as a place on the colour wheel (0 red, 30 orange, 55 yellow, 120 green, 200 blue, 280 purple, 330 pink). Null keeps the app's own colour. */
    val hue: Float? = null,
    /** A colour laid over the scene; its alpha is how strongly it colours everything (a red that creeps in as a timer runs out). */
    val tint: Int = 0,
    /** How fast everything moves: 0 still, 1 normal, 3 frantic. */
    val energy: Float = 1f,
    /** 0 to 1: the tint breathes in and out, faster as the number rises (an alarm ringing, the last seconds of a timer). */
    val pulse: Float = 0f,
    /** Symbols that drift through the scene instead of the app's usual ones. */
    val glyphs: List<String>? = null,
    /** 0 to 1: greyer and quieter (a locked vault, a computer that is offline). */
    val hush: Float = 0f,
    /**
     * 0 to 1: how full something is, shown as a glowing tide that rises and falls in the scene (a timer running down, a backup or download
     * filling up, how far through a book, the battery, today's tasks done, the sound level while recording). Null shows none.
     */
    val level: Float? = null,
    /** The tide's colour on the colour wheel; null uses the scene's colour. */
    val levelHue: Float? = null,
)

/** Something that just happened in an app, shown as a wave through its scene. */
enum class ScenePing(internal val rgb: Int) {
    /** Finished or done (a task ticked, a backup complete): a green wave rises. */
    DONE(0xFF2EB85C.toInt()),
    /** Something new (a note, an alarm, a login): a blue wave rises. */
    ADDED(0xFF2F8BE8.toInt()),
    /** Saved: a soft blue wave. */
    SAVED(0xFF4FA3E8.toInt()),
    /** Deleted or moved away: a grey wave sinks. */
    REMOVED(0xFF8A8F98.toInt()),
    /** Something went wrong: a red flash and a shudder. */
    ERROR(0xFFE5483C.toInt()),
}

/** Touches on an app, passed on to its scene so it reacts where your finger is. In screen pixels; read by the scene each frame. */
object SceneTouch {
    internal val events = java.util.concurrent.ConcurrentLinkedQueue<FloatArray>()
    /** kind 0 press, 1 move (with dx, dy). */
    internal fun add(kind: Int, x: Float, y: Float, dx: Float = 0f, dy: Float = 0f) { events.add(floatArrayOf(kind.toFloat(), x, y, dx, dy)); while (events.size > 24) events.poll() }
}

/** Lets the scene feel taps, drags and scrolls on the screen above it, without taking them from the app. */
fun Modifier.sceneTouch(enabled: Boolean): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
            val ch = e.changes.firstOrNull() ?: continue
            when (e.type) {
                androidx.compose.ui.input.pointer.PointerEventType.Press -> SceneTouch.add(0, ch.position.x, ch.position.y)
                androidx.compose.ui.input.pointer.PointerEventType.Move -> { val d = ch.position - ch.previousPosition; if (d.x != 0f || d.y != 0f) SceneTouch.add(1, ch.position.x, ch.position.y, d.x, d.y) }
                else -> {}
            }
        }
    }
}

/**
 * What the scene does in answer to you and to the app: rings spreading where you tap, a wake behind a dragging finger, waves of colour
 * when something is done, added, deleted or goes wrong, and the tide that shows a level. Drawn over the wash so it is clearly seen.
 */
private class SceneReactions(private val w: Int, private val h: Int) {
    private class Rip(val x: Float, val y: Float, var age: Float, val life: Float, val str: Float)
    private class Wave(val kind: ScenePing, var age: Float)
    private val rips = ArrayList<Rip>(); private val waves = ArrayList<Wave>()
    private val s = minOf(w, h).toFloat()
    private var travel = 0f
    private var t = 0f
    /** Stirring from drags and scrolls: speeds the scene up for a moment. */
    var stir = 0f; private set
    private var shake = 0f
    private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()
    private fun a(c: Int, alpha: Float) = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    fun press(x: Float, y: Float) { rips += Rip(x, y, 0f, 1.2f, 1f); if (rips.size > 10) rips.removeAt(0) }
    fun move(x: Float, y: Float, d: Float) {
        travel += d; stir = minOf(2.2f, stir + d / s * 1.6f)
        if (travel > s * .08f) { travel = 0f; rips += Rip(x, y, 0f, .8f, .45f); if (rips.size > 10) rips.removeAt(0) }
    }
    fun ping(k: ScenePing) { waves += Wave(k, 0f); if (k == ScenePing.ERROR) shake = 1f; if (waves.size > 3) waves.removeAt(0) }
    fun step(dt: Float) {
        t += dt
        rips.forEach { it.age += dt }; rips.removeAll { it.age >= it.life }
        waves.forEach { it.age += dt }; waves.removeAll { it.age >= 1.9f }
        stir *= kotlin.math.exp(-dt * 1.5f); if (stir < .005f) stir = 0f
        shake = (shake - dt * 2f).coerceAtLeast(0f)
    }
    /** Sideways shudder after an error, in scene pixels. */
    fun shakeX() = if (shake > 0f) kotlin.math.sin(t * 60f) * s * .02f * shake else 0f

    /** The tide: a soft band of colour from the bottom up to [level], with a gently moving surface. */
    fun drawLevel(c: android.graphics.Canvas, level: Float, color: Int, energy: Float) {
        val top = h * (1f - level.coerceIn(0f, 1f))
        val amp = s * (.01f + .008f * energy.coerceAtMost(3f))
        path.reset(); path.moveTo(0f, h.toFloat())
        var x = 0f
        while (x <= w) { path.lineTo(x, top + kotlin.math.sin(x / w * 9.4f + t * 1.7f) * amp + kotlin.math.sin(x / w * 3.1f - t * 1.1f) * amp * .6f); x += w / 48f }
        path.lineTo(w.toFloat(), h.toFloat()); path.close()
        fill.shader = android.graphics.LinearGradient(0f, top, 0f, h.toFloat(), a(color, .34f), a(color, .14f), android.graphics.Shader.TileMode.CLAMP)
        c.drawPath(path, fill); fill.shader = null
        // The bright line along its surface.
        path.reset(); x = 0f
        while (x <= w) { val y = top + kotlin.math.sin(x / w * 9.4f + t * 1.7f) * amp + kotlin.math.sin(x / w * 3.1f - t * 1.1f) * amp * .6f; if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y); x += w / 48f }
        line.strokeWidth = s * .007f; line.color = a(color, .7f); c.drawPath(path, line)
    }

    fun draw(c: android.graphics.Canvas, color: Int) {
        for (r in rips) {
            val f = r.age / r.life
            for (k in 0..2) {
                val ff = f - k * .16f; if (ff <= 0f) continue
                line.strokeWidth = s * (.01f * (1f - ff) + .003f) * (.6f + .4f * r.str)
                line.color = a(color, .75f * r.str * (1f - ff)); c.drawCircle(r.x, r.y, s * .26f * ff * (.55f + .45f * r.str), line)
            }
            if (f < .45f) { val rr = s * .1f * (.5f + .5f * r.str)
                fill.shader = android.graphics.RadialGradient(r.x, r.y, rr, a(color, .4f * r.str * (1f - f / .45f)), 0, android.graphics.Shader.TileMode.CLAMP); c.drawCircle(r.x, r.y, rr, fill); fill.shader = null }
        }
        for (wv in waves) {
            val f = wv.age / 1.9f
            if (wv.kind == ScenePing.ERROR) { fill.color = a(wv.kind.rgb, .28f * (1f - f) * (1f - f)); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill); continue }
            // A band of light that sweeps up the screen (down for something removed), brightest in the middle.
            val ease = 1f - (1f - f) * (1f - f)
            val y = if (wv.kind == ScenePing.REMOVED) -s * .3f + (h + s * .6f) * ease else h + s * .3f - (h + s * .6f) * ease
            val bh = s * .42f; val al = .42f * (1f - f * .7f)
            fill.shader = android.graphics.LinearGradient(0f, y - bh, 0f, y + bh, intArrayOf(0, a(wv.kind.rgb, al), 0), floatArrayOf(0f, .5f, 1f), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, y - bh, w.toFloat(), y + bh, fill); fill.shader = null
        }
    }
}

/** The scene's mood for the app in front. Apps set it from their own state; the kit's scene reads it. */
object WonderMood {
    internal val mood = mutableStateOf(SceneMood())
    internal val burst = mutableStateOf<Pair<Long, List<String>>?>(null)
    internal val ping = mutableStateOf<Pair<Long, ScenePing>?>(null)
    /** The moods of the screens showing now, innermost last. */
    internal val stack = ArrayList<Pair<Any, SceneMood>>()
    fun set(m: SceneMood) { mood.value = m }
    fun clear() { mood.value = SceneMood() }
    /** A task done, a sum answered, an update installed: a green wave rises through the scene and a few symbols lift with it. */
    fun celebrate(glyphs: List<String> = listOf("✦", "★", "✓")) { val now = System.nanoTime(); burst.value = now to glyphs; ping.value = now to ScenePing.DONE }
    /** Something just happened: added, saved, deleted, or an error. The scene answers with a wave (or a red shudder). */
    fun ping(kind: ScenePing) { ping.value = System.nanoTime() to kind }
}

/** Gives the scene this mood while the calling screen is showing, and puts the app's usual scene back when it leaves. */
@Composable
fun SceneMoodEffect(mood: SceneMood) {
    // Moods stack: a screen inside another (a tab, a dialog) shows its own, and when it goes the one underneath comes back.
    val slot = remember { Any() }
    DisposableEffect(slot) { onDispose { WonderMood.stack.removeAll { it.first === slot }; WonderMood.mood.value = WonderMood.stack.lastOrNull()?.second ?: SceneMood() } }
    SideEffect {
        val i = WonderMood.stack.indexOfFirst { it.first === slot }
        if (i >= 0) WonderMood.stack[i] = slot to mood else WonderMood.stack.add(slot to mood)
        val top = WonderMood.stack.last().second
        if (WonderMood.mood.value != top) WonderMood.mood.value = top
    }
}

/** The main colour of each scene before any shift (Aqua's water is blue, the Bliss hills green, the Super nebula purple). */
private fun naturalHue(base: String) = when (base) { "bliss" -> 105f; "super" -> 285f; else -> 200f }

/** The shortest way round the colour wheel from [from] to [to], so blue to red never goes the long way through green. */
private fun nearestAngle(from: Float, to: Float): Float { val d = ((to - from) % 360f + 540f) % 360f - 180f; return from + d }

/** 0 (night) to 1 (full day), from the scene's hour, so the wash over the scene can be lighter by day and stronger at night. */
private fun daylight(hour: Float): Float {
    fun ramp(x: Float, a: Float, b: Float) = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return ramp(hour, 5.5f, 8f) * (1f - ramp(hour, 18f, 20.5f))
}

/**
 * The live scene behind an app's window. It is drawn straight onto the screen by the graphics chip at the screen's own sharpness (no small
 * picture stretched up, which is what made it look pixelated), about 30 times a second and only while the app is in front. Scene art that is
 * pre-drawn is built at half the screen's size and scaled up smoothly. A fine noise is laid over it so big soft gradients don't show as lines.
 * The wash on top keeps text and panels easy to read.
 */
@Composable
fun WonderSceneBackdrop(look: WonderLook, motion: WonderMotion, appName: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(Unit) { KitSun.refresh(context) }
    val cfg = remember(look, appName) { KitSceneConfig(base = KitSceneConfig.forLook(look), hueShift = WonderScenery.hue(appName), events = false) }
    val noise = remember {
        // A small tile of random light and dark specks, repeated over the scene.
        val n = 96; val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888); val r = java.util.Random(9)
        for (y in 0 until n) for (x in 0 until n) { val v = r.nextInt(256); b.setPixel(x, y, android.graphics.Color.argb(9, v, v, v)) }
        b.asImageBitmap()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pw = constraints.maxWidth.coerceAtLeast(2); val ph = constraints.maxHeight.coerceAtLeast(2)
        // The scene's own coordinate space: half the screen, never more than 1280 tall, so its pre-drawn art stays light on memory.
        val k = (minOf(1f, 1280f / ph) * .5f).coerceAtLeast(.2f)
        val w = (pw * k).toInt().coerceAtLeast(120); val h = (ph * k).toInt().coerceAtLeast(120)
        val runner = remember(w, h, cfg) { KitSceneRunner(w, h) }
        val themeName = remember(appName) { AppThemeLayer.themeFor(appName) }
        val theme = remember(w, h, themeName) { themeName?.let { AppThemeLayer(w, h, it) } }
        // Fewer drifting symbols when the app has its own picture, so the two don't crowd each other.
        val motif = remember(w, h, appName) { AppMotif(w, h, motifFor(appName), if (themeName != null) 7 else 14) }
        val react = remember(w, h) { SceneReactions(w, h) }
        theme?.level = WonderMood.mood.value.level ?: -1f
        val toScene = w.toFloat() / pw
        // The app's mood, eased in: colour turns the short way round, tint, speed, breathing and greyness glide to their new values.
        val mood = WonderMood.mood.value
        val appShift = WonderScenery.hue(appName).toFloat()
        val wantShift = mood.hue?.let { it - naturalHue(cfg.base) } ?: appShift
        val shift = remember(cfg) { androidx.compose.animation.core.Animatable(appShift) }
        LaunchedEffect(wantShift) { shift.animateTo(nearestAngle(shift.value, wantShift), androidx.compose.animation.core.tween(1400)) }
        val tint by androidx.compose.animation.animateColorAsState(Color(mood.tint), androidx.compose.animation.core.tween(1200), label = "moodTint")
        val energy by androidx.compose.animation.core.animateFloatAsState(mood.energy.coerceIn(0f, 4f), androidx.compose.animation.core.tween(1200), label = "moodEnergy")
        val pulse by androidx.compose.animation.core.animateFloatAsState(mood.pulse.coerceIn(0f, 1f), androidx.compose.animation.core.tween(900), label = "moodPulse")
        val hush by androidx.compose.animation.core.animateFloatAsState(mood.hush.coerceIn(0f, 1f), androidx.compose.animation.core.tween(1200), label = "moodHush")
        val level by androidx.compose.animation.core.animateFloatAsState(mood.level?.coerceIn(0f, 1f) ?: -.05f, androidx.compose.animation.core.tween(700), label = "moodLevel")
        val ping = WonderMood.ping.value
        val seenPing = remember(react) { WonderMood.ping.value?.first }
        LaunchedEffect(react, ping) { ping?.takeIf { it.first != seenPing }?.let { react.ping(it.second) } }
        val drawCfg = remember(cfg, shift.value.toInt()) { cfg.copy(hueShift = shift.value.toInt()) }
        val moodGlyphs = mood.glyphs
        LaunchedEffect(motif, moodGlyphs) { motif.setGlyphs(moodGlyphs ?: motifFor(appName)) }
        val burst = WonderMood.burst.value
        // Only a celebration that happens while this scene is up: one left over from earlier must not go off again when the app is opened,
        // turned or come back to (that was the burst of symbols on opening an app).
        val seenBurst = remember(motif) { WonderMood.burst.value?.first }
        // Opening an app is always calm: things that happen as it opens (connecting, unlocking, loading) don't set off a celebration.
        // The quiet spell starts again each time the app comes back to the front.
        var calmUntil by remember { mutableLongStateOf(System.nanoTime() + 4_000_000_000L) }
        LaunchedEffect(resumed) { if (resumed) calmUntil = maxOf(calmUntil, System.nanoTime() + 4_000_000_000L) }
        LaunchedEffect(motif, burst) { burst?.takeIf { it.first != seenBurst && it.first > calmUntil }?.let { motif.burst(it.second) } }
        var breath by remember { mutableFloatStateOf(0f) }
        // The app's picture takes the app's colour (or the mood's), lighter in Super and earthier in Bliss.
        val themeHue = naturalHue(cfg.base) + shift.value
        val themeColor = when (look) {
            WonderLook.SUPER -> android.graphics.Color.HSVToColor(floatArrayOf(((themeHue % 360f) + 360f) % 360f, .3f, 1f))
            WonderLook.BLISS -> android.graphics.Color.HSVToColor(floatArrayOf(((themeHue % 360f) + 360f) % 360f, .45f, .55f))
            else -> android.graphics.Color.HSVToColor(floatArrayOf(((themeHue % 360f) + 360f) % 360f, .6f, .78f))
        }
        val themeAlpha = when (look) { WonderLook.SUPER -> .42f; WonderLook.BLISS -> .4f; else -> .42f }
        val motifColor = when (look) { WonderLook.SUPER -> android.graphics.Color.rgb(255, 225, 255); WonderLook.BLISS -> android.graphics.Color.rgb(70, 78, 45); else -> android.graphics.Color.rgb(25, 80, 125) }
        var tick by remember(runner) { mutableLongStateOf(0L) }
        var luma by remember(runner) { mutableFloatStateOf(.6f) }
        // One still frame always; moving only when motion is full and the app is in front.
        LaunchedEffect(runner) { runner.update(.2f, cfg, 100, false); motif.step(.1f); theme?.step(.1f); luma = daylight(runner.hour()); tick++ }
        // With motion reduced the scene is a still picture; it is redrawn when the mood changes so it still follows the app.
        // Battery Saver holds the scene still too, like reduced motion.
        val saver = rememberBatterySaver()
        val moving = motion == WonderMotion.FULL && !saver
        LaunchedEffect(runner, drawCfg, tint, hush, moodGlyphs, moving) { if (!moving || !resumed) { motif.step(1f); theme?.step(1f); tick++ } }
        LaunchedEffect(runner, resumed, moving) {
            if (!resumed || !moving) return@LaunchedEffect
            var last = 0L; var n = 0
            while (true) {
                withFrameNanos { now ->
                    if (last == 0L) last = now
                    if (now - last >= 33_000_000L) {
                        val dt = ((now - last) / 1e9f).coerceIn(.01f, .2f); last = now
                        // Your touches: a tap rings out and pops what it lands on, a drag leaves a wake and pushes things aside, and both stir the scene.
                        while (true) {
                            val ev = SceneTouch.events.poll() ?: break
                            val x = ev[1] * toScene; val y = ev[2] * toScene
                            if (ev[0] == 0f) { react.press(x, y); runner.tap(x, y, cfg); motif.push(x, y, 0f, 0f, 1f); theme?.touch(x, y, 0f, 0f) }
                            else { val dx = ev[3] * toScene; val dy = ev[4] * toScene; react.move(x, y, kotlin.math.hypot(dx, dy)); motif.push(x, y, dx, dy, .35f); theme?.touch(x, y, dx, dy) }
                        }
                        react.step(dt)
                        val e = energy + react.stir
                        runner.update(dt * e, cfg, 100, false); motif.step(dt * e.coerceAtLeast(.25f)); theme?.step(dt * e.coerceAtLeast(.25f))
                        breath += dt * (1.1f + 4.5f * pulse)
                        if (n++ % 60 == 0) luma = daylight(runner.hour())
                        tick = now
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val frame = tick   // reading it here makes the scene redraw each time it changes
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save(); nc.scale(size.width / w, size.height / h)
                runner.draw(nc, drawCfg); nc.save(); nc.translate(react.shakeX(), 0f); motif.draw(nc, motifColor); nc.restore()
                nc.restore()
            }
            // A soft wash over the scene so text and panels stay easy to read; stronger at night.
            val a = (.46f + .38f * (1f - luma)).coerceIn(.46f, .86f)
            drawRect(when (look) { WonderLook.SUPER -> Color(0x4D000000); WonderLook.BLISS -> Color(.957f, .945f, .894f, a); else -> Color(1f, 1f, 1f, a) })
            // The mood's colour, over the wash: it recolours the scene while keeping its light and shade (white stays white, so text stays easy
            // to read), and breathes when the app is urgent.
            if (tint.alpha > .01f) {
                val b = if (pulse > .01f) .5f + .5f * kotlin.math.sin(breath) else 1f
                drawRect(tint.copy(alpha = (tint.alpha * (1f - pulse * .45f * (1f - b))).coerceIn(0f, 1f)), blendMode = androidx.compose.ui.graphics.BlendMode.Color)
                if (pulse > .01f) drawRect(tint.copy(alpha = .22f * pulse * b))
            }
            if (hush > .01f) {
                drawRect(Color.Gray.copy(alpha = hush), blendMode = androidx.compose.ui.graphics.BlendMode.Saturation)
                drawRect(Color.Black.copy(alpha = .12f * hush))
            }
            // The app's own picture goes over the wash, like a watermark: clear enough to see at any hour, too faint to get in the way of text.
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save(); nc.scale(size.width / w, size.height / h)
                if (level >= 0f && theme?.showsLevel != true) react.drawLevel(nc, level, mood.levelHue?.let { android.graphics.Color.HSVToColor(floatArrayOf(((it % 360f) + 360f) % 360f, .62f, .85f)) } ?: themeColor, energy)
                nc.translate(react.shakeX(), 0f)
                theme?.draw(nc, themeColor, themeAlpha)
                react.draw(nc, if (look == WonderLook.SUPER) android.graphics.Color.WHITE else themeColor)
                nc.restore()
            }
            // Fine noise (dithering): breaks up the banding that shows as lines in big smooth gradients.
            drawRect(ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)))
        }
    }
}
