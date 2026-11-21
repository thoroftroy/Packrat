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

/** Small symbols that drift through an app's scene, so every app's scene is about its own subject (maths signs for Abacus, notes for Echo, arrows for Wayfinder). */
private class AppMotif(private val w: Int, private val h: Int, private val glyphs: List<String>) {
    private class G(var x: Float, var y: Float, val s: String, val size: Float, val speed: Float, val sway: Float, val phase: Float, val alpha: Int)
    private val rnd = java.util.Random(5)
    private val items = List(if (glyphs.isEmpty()) 0 else 16) { make(true) }
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); textAlign = android.graphics.Paint.Align.CENTER }
    private var t = 0f
    private fun make(anywhere: Boolean) = G(rnd.nextFloat() * w, if (anywhere) rnd.nextFloat() * h else h + 30f, glyphs[rnd.nextInt(glyphs.size)], h * (.05f + rnd.nextFloat() * .06f), h * (.02f + rnd.nextFloat() * .035f), w * (.01f + rnd.nextFloat() * .02f), rnd.nextFloat() * 6.28f, 90 + rnd.nextInt(90))
    fun step(dt: Float) { t += dt; for (g in items) { g.y -= g.speed * dt; if (g.y < -g.size) { val n = make(false); g.x = n.x; g.y = n.y } } }
    fun draw(c: android.graphics.Canvas, color: Int) {
        for (g in items) { paint.color = color; paint.alpha = g.alpha; paint.textSize = g.size; c.drawText(g.s, g.x + kotlin.math.sin(t * .5f + g.phase) * g.sway, g.y, paint) }
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
 * The live scene behind an app's window. Drawn off the main thread (so scrolling and taps never wait for it) into two bitmaps that swap,
 * at 24 frames a second and a good resolution, only while the app is on screen. A fine noise is laid over it so large soft gradients
 * don't show as lines. The wash on top keeps text and panels easy to read.
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
        for (y in 0 until n) for (x in 0 until n) { val v = r.nextInt(256); b.setPixel(x, y, android.graphics.Color.argb(26, v, v, v)) }
        b.asImageBitmap()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ratio = (constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)).coerceIn(.3f, 3f)
        val h = 520; val w = (h * ratio).toInt().coerceAtLeast(120)
        val bufs = remember(w) { arrayOf(android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888), android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)) }
        val canvases = remember(bufs) { arrayOf(android.graphics.Canvas(bufs[0]), android.graphics.Canvas(bufs[1])) }
        val runner = remember(bufs, cfg) { KitSceneRunner(w, h) }
        val motif = remember(bufs, appName) { AppMotif(w, h, motifFor(appName)) }
        val motifColor = when (look) { WonderLook.SUPER -> android.graphics.Color.rgb(255, 225, 255); WonderLook.BLISS -> android.graphics.Color.rgb(70, 78, 45); else -> android.graphics.Color.rgb(25, 80, 125) }
        val lock = remember(runner) { Any() }
        var front by remember(bufs) { mutableStateOf<ImageBitmap?>(null) }
        // One still frame always; moving only when motion is full and the app is in front.
        LaunchedEffect(runner) {
            withContext(Dispatchers.Default) { synchronized(lock) { runner.update(.2f, cfg, 100, false); runner.draw(canvases[0], cfg); motif.step(.1f); motif.draw(canvases[0], motifColor) } }
            front = bufs[0].asImageBitmap()
        }
        LaunchedEffect(runner, resumed, motion) {
            if (!resumed || motion != WonderMotion.FULL) return@LaunchedEffect
            var back = 1; var last = System.nanoTime()
            while (true) {
                val t0 = System.nanoTime(); val dt = ((t0 - last) / 1e9f).coerceIn(.01f, .2f); last = t0
                withContext(Dispatchers.Default) { synchronized(lock) { bufs[back].eraseColor(0); runner.update(dt, cfg, 100, false); runner.draw(canvases[back], cfg); motif.step(dt); motif.draw(canvases[back], motifColor) } }
                front = bufs[back].asImageBitmap(); back = 1 - back
                val spent = (System.nanoTime() - t0) / 1_000_000
                delay((42 - spent).coerceAtLeast(4))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            front?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.High) }
            // A soft wash over the scene so text and panels stay easy to read.
            drawRect(when (look) { WonderLook.SUPER -> Color(0x4D000000); WonderLook.BLISS -> Color(0xB0F4F1E4); else -> Color(0x85FFFFFF) })
            // Fine noise (dithering): breaks up the banding that shows as lines in big smooth gradients.
            drawRect(ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)))
        }
    }
}
