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
private class AppMotif(private val w: Int, private val h: Int, private var glyphs: List<String>) {
    private class G(var x: Float, var y: Float, var s: String, val size: Float, val speed: Float, val sway: Float, val phase: Float, val alpha: Int) { var old: String? = null }
    /** A symbol shot up through the scene by a celebration; it rises fast and fades out. */
    private class Spark(var x: Float, var y: Float, val s: String, val size: Float, val vy: Float, val vx: Float, var life: Float, val spin: Float)
    private val rnd = java.util.Random(5)
    private val items = List(14) { make(true) }
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
        repeat(22) {
            sparks += Spark(w * (.08f + rnd.nextFloat() * .84f), h + rnd.nextFloat() * h * .15f, with[rnd.nextInt(with.size)], h * (.03f + rnd.nextFloat() * .035f),
                h * (.45f + rnd.nextFloat() * .55f), w * (rnd.nextFloat() - .5f) * .25f, 1.6f + rnd.nextFloat() * 1.4f, (rnd.nextFloat() - .5f) * 90f)
        }
        if (sparks.size > 90) repeat(sparks.size - 90) { sparks.removeAt(0) }
    }
    fun busy() = sparks.isNotEmpty() || swap < 1f
    fun step(dt: Float) {
        t += dt
        if (swap < 1f) swap = (swap + dt / .9f).coerceAtMost(1f)
        for (g in items) { g.y -= g.speed * dt; if (g.y < -g.size) { val n = make(false); g.x = n.x; g.y = n.y; g.s = n.s; g.old = null } }
        val it = sparks.iterator()
        while (it.hasNext()) { val p = it.next(); p.y -= p.vy * dt; p.x += p.vx * dt; p.life -= dt; if (p.life <= 0f || p.y < -p.size) it.remove() }
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
            paint.textSize = p.size; paint.alpha = (200 * (p.life / 1.2f).coerceIn(0f, 1f)).toInt()
            c.save(); c.rotate(p.spin * (3f - p.life), p.x, p.y); c.drawText(p.s, p.x, p.y, paint); c.restore()
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
)

/** The scene's mood for the app in front. Apps set it from their own state; the kit's scene reads it. */
object WonderMood {
    internal val mood = mutableStateOf(SceneMood())
    internal val burst = mutableStateOf<Pair<Long, List<String>>?>(null)
    fun set(m: SceneMood) { mood.value = m }
    fun clear() { mood.value = SceneMood() }
    /** A shower of symbols shooting up through the scene: a task done, a sum answered, an update installed. */
    fun celebrate(glyphs: List<String> = listOf("✦", "★", "✓")) { burst.value = System.nanoTime() to glyphs }
}

/** Gives the scene this mood while the calling screen is showing, and puts the app's usual scene back when it leaves. */
@Composable
fun SceneMoodEffect(mood: SceneMood) {
    DisposableEffect(mood) {
        WonderMood.set(mood)
        onDispose { if (WonderMood.mood.value == mood) WonderMood.clear() }
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
        val motif = remember(w, h, appName) { AppMotif(w, h, motifFor(appName)) }
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
        val drawCfg = remember(cfg, shift.value.toInt()) { cfg.copy(hueShift = shift.value.toInt()) }
        val moodGlyphs = mood.glyphs
        LaunchedEffect(motif, moodGlyphs) { motif.setGlyphs(moodGlyphs ?: motifFor(appName)) }
        val burst = WonderMood.burst.value
        LaunchedEffect(motif, burst) { burst?.let { motif.burst(it.second) } }
        var breath by remember { mutableFloatStateOf(0f) }
        val motifColor = when (look) { WonderLook.SUPER -> android.graphics.Color.rgb(255, 225, 255); WonderLook.BLISS -> android.graphics.Color.rgb(70, 78, 45); else -> android.graphics.Color.rgb(25, 80, 125) }
        var tick by remember(runner) { mutableLongStateOf(0L) }
        var luma by remember(runner) { mutableFloatStateOf(.6f) }
        // One still frame always; moving only when motion is full and the app is in front.
        LaunchedEffect(runner) { runner.update(.2f, cfg, 100, false); motif.step(.1f); luma = daylight(runner.hour()); tick++ }
        // With motion reduced the scene is a still picture; it is redrawn when the mood changes so it still follows the app.
        LaunchedEffect(runner, drawCfg, tint, hush, moodGlyphs) { if (motion != WonderMotion.FULL || !resumed) { motif.step(1f); tick++ } }
        LaunchedEffect(runner, resumed, motion) {
            if (!resumed || motion != WonderMotion.FULL) return@LaunchedEffect
            var last = 0L; var n = 0
            while (true) {
                withFrameNanos { now ->
                    if (last == 0L) last = now
                    if (now - last >= 33_000_000L) {
                        val dt = ((now - last) / 1e9f).coerceIn(.01f, .2f); last = now
                        runner.update(dt * energy, cfg, 100, false); motif.step(dt * energy.coerceAtLeast(.25f))
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
                runner.draw(nc, drawCfg); motif.draw(nc, motifColor)
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
            // Fine noise (dithering): breaks up the banding that shows as lines in big smooth gradients.
            drawRect(ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)))
        }
    }
}
