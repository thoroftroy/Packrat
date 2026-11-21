package dev.wonderdesk.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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

/** The live scene behind an app's window. 12 frames a second at a small size and only while the app is on screen, so it costs little battery. */
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ratio = (constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)).coerceIn(.3f, 3f)
        val h = 300; val w = (h * ratio).toInt().coerceAtLeast(120)
        val bmp = remember(w) { android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888) }
        val canvas = remember(bmp) { android.graphics.Canvas(bmp) }
        val runner = remember(bmp, cfg) { KitSceneRunner(w, h) }
        var frame by remember { mutableIntStateOf(0) }
        // One still frame always; moving only when motion is full and the app is in front.
        LaunchedEffect(runner) { runner.update(.2f, cfg, 100, false); runner.draw(canvas, cfg); frame++ }
        LaunchedEffect(runner, resumed, motion) {
            if (!resumed || motion != WonderMotion.FULL) return@LaunchedEffect
            var last = 0L
            while (true) androidx.compose.runtime.withFrameNanos { t ->
                if (last == 0L) last = t
                val dt = ((t - last) / 1e9f).coerceAtMost(.2f)
                if (dt >= 1f / 12f) { runner.update(dt, cfg, 100, false); runner.draw(canvas, cfg); last = t; frame++ }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            frame
            drawImage(bmp.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
            // A soft wash over the scene so text and panels stay easy to read.
            drawRect(when (look) { WonderLook.SUPER -> Color(0x4D000000); WonderLook.BLISS -> Color(0xB8F4F1E4); else -> Color(0x99FFFFFF) })
        }
    }
}
