package dev.wonderdesk.shared

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live readings an app hands to its own picture: the sound level in Echo, the answer in Abacus, which way the phone tilts, a key typed in
 * Scribble, a lap in Timepiece. Apps write it as things happen (many times a second is fine); the picture reads it every frame. Each picture
 * uses only what makes sense for it. Not Compose state, so writing it costs nothing.
 */
object SceneFeed {
    /** Loudness right now, 0 to 1. */
    @Volatile var amp = 0f
    /** Recent loudness (or any trace, like CPU use), newest last. */
    internal val wave = FloatArray(160)
    @Volatile internal var waveAt = 0
    /** True while [wave] is live (recording, playing, a graph on screen). */
    @Volatile var waveOn = false
    /** Adds the newest point of the trace (0 to 1) and makes it the loudness. */
    fun push(v: Float) { val x = v.coerceIn(0f, 1f); wave[waveAt % wave.size] = x; waveAt++; amp = x }
    /** How many things to show (bubbles for an answer, boxes, search results). Below 0: the picture's own amount. */
    @Volatile var count = -1
    /** Below 0, what is counted sinks instead of rising (a negative answer). */
    @Volatile var sign = 1
    /** A direction in degrees, or NaN (where the compass points). */
    @Volatile var angle = Float.NaN
    /** Which way the phone leans, -1 to 1 each way, while [tilting]. */
    @Volatile var tiltX = 0f
    @Volatile var tiltY = 0f
    @Volatile var tilting = false
    /** Counts things that just happened: a key typed, a lap, data arriving, a page turned, a task done. */
    @Volatile var beats = 0
    /** The character just typed, for pictures that show it. */
    @Volatile var typed: Char = ' '
    fun beat() { beats++ }
    fun typed(ch: Char) { typed = ch; beats++ }
    /** Something is running: a stopwatch, a recording, a copy, a backup. */
    @Volatile var active = false
    /** A word for the picture: "locked", "open", "error", "recording", "playing", "offline". */
    @Volatile var state = ""
    /** A few live settings, 0 to 1 each, for pictures that show them (Control Room: media, ring, notifications and alarm volume, brightness). */
    val values = FloatArray(5) { -1f }
    /** Small pictures to show (recent photos in Memories). */
    @Volatile var pictures: List<Bitmap> = emptyList()
    fun clear() { amp = 0f; waveOn = false; count = -1; sign = 1; angle = Float.NaN; tilting = false; active = false; state = "" }
}

/** Clears what this screen told its picture when the screen goes away. */
@Composable
fun SceneFeedScope() { DisposableEffect(Unit) { onDispose { SceneFeed.clear() } } }

/**
 * Each app's own picture in its animated scene, drawn over the water, hills or nebula, and each one answers to what its app is doing:
 *  - Abacus: bubbles for the answer (more for bigger numbers, sinking for negative ones, popping on an error), beads that count as you type
 *    and roll downhill when the phone tilts.
 *  - Echo: the live waveform of what you record or play, flowing across the screen.
 *  - Timepiece: gears that race while the stopwatch runs, a ring for the time left on a timer.
 *  - Pulse and Control Room: the real CPU trace and a heartbeat that quickens with the load.
 *  - Scribble: the letters you type float up; Pages: a page turns with yours; Chronicle: a page for each task left, torn off when done.
 *  - Ripple: rings for data arriving; Beacon: a blip on the radar for each result; Wayfinder: a compass that swings for each folder.
 *  - Polaris: a constellation that joins up as a page loads; Memories: your own recent photos in the frames.
 *  - Vault: a safe dial that clicks round as you type the password and swings open; Safekeeper: a shield that fills as a backup runs;
 *    Packrat and App Hub: boxes that stack up as work gets done.
 * Every picture also answers to your finger. Soft and see-through, so it never fights with what is on screen.
 */
internal class AppThemeLayer(private val w: Int, private val h: Int, private val theme: String) {
    private val rnd = java.util.Random(theme.hashCode().toLong())
    private val s = min(w, h).toFloat()
    private var t = 0f
    /** A level the app's mood gives (0 to 1), or below 0 for none. */
    var level = -1f
    /** Pictures that show the level themselves (a timer ring, a filling shield, a stack of boxes, a constellation joining up). */
    val showsLevel get() = theme in setOf("gears", "shield", "boxes", "stars")
    private var shownLevel = -1f
    private var lastBeats = SceneFeed.beats
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD) }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private fun ease(cur: Float, to: Float, dt: Float, rate: Float) = cur + (to - cur) * min(1f, dt * rate)

    // ---- Abacus: beads on rods, and bubbles for the answer.
    private class Bead(var x: Float, var target: Float, val rod: Int)
    private val rods = listOf(.2f, .38f, .56f, .74f)
    private val beads = ArrayList<Bead>().apply { rods.indices.forEach { r -> repeat(7) { i -> val x = .08f + i * .055f; add(Bead(x, x, r)) } } }
    private var nextCount = 2f
    private class Bub(var x: Float, var y: Float, val r: Float, val speed: Float, val ph: Float) { var vx = 0f; var pop = -1f }
    private val bubs = ArrayList<Bub>()
    private var spawnAcc = 0f

    // ---- Things that drift: pages, ink sheets, photo frames, boxes.
    private class Drift(var x: Float, var y: Float, val size: Float, val speed: Float, var rot: Float, val spin: Float, val sway: Float, val ph: Float, val n: Int) {
        var vx = 0f; var vy = 0f; var torn = -1f
    }
    private val drifts = ArrayList<Drift>().apply { repeat(when (theme) { "pages" -> 7; "book", "ink" -> 6; "frames" -> 7; "boxes" -> 7; else -> 0 }) { add(newDrift(true)) } }
    private fun newDrift(anywhere: Boolean) = Drift(rnd.nextFloat() * w, if (anywhere) rnd.nextFloat() * h else if (theme == "pages" || theme == "book") -s * .2f else h + s * .2f,
        s * (.09f + rnd.nextFloat() * .07f), h * (.018f + rnd.nextFloat() * .02f), rnd.nextFloat() * 360f, (rnd.nextFloat() - .5f) * 14f, w * (.02f + rnd.nextFloat() * .03f), rnd.nextFloat() * 6.28f, 1 + rnd.nextInt(31))

    // ---- Letters that float up (Scribble), a page turning (Pages).
    private class Letter(val x: Float, var y: Float, val ch: String, val size: Float, var age: Float, val rot: Float)
    private val letters = ArrayList<Letter>()
    private var typing = 0f
    private var turn = -1f

    // ---- Rings (Ripple, Echo peaks, Beacon pings), and their timing.
    private class Ring(val x: Float, val y: Float, var age: Float, val life: Float, val big: Float = 1f)
    private val rings = ArrayList<Ring>()
    private var nextRing = .3f
    private var ringGap = 0f

    // ---- Stars (Polaris), radar blips (Beacon).
    private val stars = List(18) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h * .85f, rnd.nextFloat() * 6.28f) }
    private val lit = FloatArray(18)
    private class Shoot(var x: Float, var y: Float, var life: Float)
    private val shoots = ArrayList<Shoot>()

    // ---- Turning things: gears, compass, dial.
    private var spin = 0f
    private var spinV = 0f
    private var recordSpeed = 6f
    private var arm = 0f
    private var heading = 0f
    private var dial = 0f
    private var dialTo = 0f
    private var open = 0f
    private var flash = 0f
    private var spike = 0f
    private var beatPhase = 0f
    private val faders = FloatArray(5) { .5f }
    private val faderV = FloatArray(5)

    fun step(dt: Float) {
        t += dt
        shownLevel = if (level < 0f) -1f else if (shownLevel < 0f) level else ease(shownLevel, level, dt, 6f)
        val nb = SceneFeed.beats - lastBeats; lastBeats = SceneFeed.beats
        if (nb > 0) onBeat(nb.coerceAtMost(6))
        flash = (flash - dt * 1.6f).coerceAtLeast(0f)
        spike = (spike - dt * 2.5f).coerceAtLeast(0f)
        spinV *= exp(-dt * 1.4f)
        when (theme) {
            "beads" -> stepAbacus(dt)
            "pages", "book", "ink", "frames", "boxes" -> stepDrifts(dt)
            "rings" -> {
                // Now and then a ring by itself; more when data is arriving (beats), none while offline.
                if (SceneFeed.state != "offline") { nextRing -= dt; if (nextRing <= 0f) { nextRing = 2.2f + rnd.nextFloat() * 2.4f; rings += Ring(rnd.nextFloat() * w, h * (.15f + rnd.nextFloat() * .7f), 0f, 5.5f + rnd.nextFloat() * 2f) } }
            }
            "sound" -> {
                // A loud moment sends a ring out from the middle of the waveform.
                ringGap -= dt
                if (SceneFeed.waveOn && SceneFeed.amp > .55f && ringGap <= 0f) { ringGap = .35f; rings += Ring(w * (.25f + rnd.nextFloat() * .5f), h * .5f, 0f, 2.2f, .5f + SceneFeed.amp) }
            }
            "gears" -> spin += dt * (8f + (if (SceneFeed.active) 70f else 0f)) + spinV * dt
            // A record turns at 33 rpm while music plays (200 degrees a second), barely drifts when paused, and spins with a flick.
            "record" -> { recordSpeed = ease(recordSpeed, if (SceneFeed.active) 200f else 6f, dt, 1.6f); spin += dt * recordSpeed + spinV * dt; arm = ease(arm, if (SceneFeed.active) 1f else 0f, dt, 2f) }
            "compass" -> {
                val want = SceneFeed.angle.takeIf { !it.isNaN() } ?: (sin(t * .08f) * 25f)
                heading += ((want - heading + 540f) % 360f - 180f) * min(1f, dt * 2.2f) + spinV * dt
            }
            "dial" -> {
                val st = SceneFeed.state
                open = ease(open, if (st == "open") 1f else 0f, dt, 2.2f)
                if (st == "error") dialTo = 0f
                dial = ease(dial, dialTo, dt, 7f) + spinV * dt; dialTo += spinV * dt
                if (st != "locked" && st != "error" && st != "open") { dial += dt * 2f; dialTo = dial }
            }
            "pulse" -> beatPhase += dt * (1.1f + SceneFeed.amp * 2.6f)
            "mixer" -> for (k in faders.indices) {
                // Springy faders: they slide to the phone's real level and settle with a little bounce.
                val want = SceneFeed.values[k].takeIf { it >= 0f } ?: (.5f + .3f * sin(t * .3f + k))
                faderV[k] += (want - faders[k]) * 60f * dt; faderV[k] *= exp(-dt * 9f); faders[k] += faderV[k] * dt
            }
            "stars" -> for (k in lit.indices) lit[k] = (lit[k] - dt * .6f).coerceAtLeast(0f)
        }
        rings.forEach { it.age += dt }; rings.removeAll { it.age > it.life }
        shoots.forEach { it.x += s * 1.4f * dt; it.y += s * .5f * dt; it.life -= dt * 1.2f }; shoots.removeAll { it.life <= 0f }
        letters.forEach { it.age += dt; it.y -= s * .09f * dt }; letters.removeAll { it.age > 3.2f }
        typing *= exp(-dt * .9f)
        if (turn >= 0f) { turn += dt / .9f; if (turn >= 1f) turn = -1f }
    }

    /** Something happened in the app. */
    private fun onBeat(n: Int) {
        when (theme) {
            "beads" -> nextCount = 0f
            "ink" -> repeat(n) {
                val ch = SceneFeed.typed.takeIf { !it.isWhitespace() }?.toString() ?: return@repeat
                // Start in the middle of the screen: the bottom is behind the keyboard while you type.
                letters += Letter(w * (.1f + rnd.nextFloat() * .8f), h * (.38f + rnd.nextFloat() * .2f), ch, s * (.08f + rnd.nextFloat() * .06f), 0f, (rnd.nextFloat() - .5f) * 40f)
                if (letters.size > 40) letters.removeAt(0); typing = min(4f, typing + .25f)
            }
            "book" -> turn = 0f
            "pages" -> drifts.filter { it.torn < 0f }.minByOrNull { abs(it.y - h * .4f) }?.let { it.torn = 0f }
            "rings" -> if (ringGap <= 0f || rings.size < 4) { rings += Ring(rnd.nextFloat() * w, h * (.15f + rnd.nextFloat() * .7f), 0f, 3.5f, .7f); ringGap = .12f }
            "radar" -> { flash = 1f; rings += Ring(w * .5f, h * .55f, 0f, 1.6f, 1f) }
            "gears" -> flash = 1f
            "dial" -> { dialTo += 36f + rnd.nextFloat() * 30f; flash = .6f }
            "pulse" -> spike = 1f
            "stars" -> shoots += Shoot(rnd.nextFloat() * w * .5f, rnd.nextFloat() * h * .4f, 1f)
            "boxes" -> flash = 1f
            "frames" -> flash = 1f
        }
    }

    /** Your finger, in scene pixels: a press (dx = dy = 0) or a drag. */
    fun touch(x: Float, y: Float, dx: Float, dy: Float) {
        val press = dx == 0f && dy == 0f
        when (theme) {
            "beads" -> {
                // Pop the bubbles you touch; tap near a rod to push its beads along.
                bubs.filter { it.pop < 0f && hypot(it.x - x, it.y - y) < it.r + s * .05f }.forEach { it.pop = 0f }
                if (press) rods.indexOfFirst { abs(h * it - y) < s * .06f }.takeIf { it >= 0 }?.let { slideRod(it, x / w) }
            }
            "gears", "compass", "dial", "record" -> {
                // Turn it with your finger: the drag around the middle spins it.
                val cx = w * .5f; val cy = h * .48f
                if (!press) { val cross = ((x - cx) * dy - (y - cy) * dx) / max(1f, hypot(x - cx, y - cy)); spinV += cross * 2.2f }
                else spinV += 40f
            }
            "pages", "book", "ink", "frames", "boxes" -> for (d in drifts) {
                val ex = d.x - x; val ey = d.y - y; val dd = hypot(ex, ey); val r = s * .3f
                if (dd < r && dd > 1f) { val f = 1f - dd / r; d.vx += ex / dd * s * 1.6f * f + dx * 4f * f; d.vy += ey / dd * s * 1.6f * f + dy * 4f * f }
            }
            "rings", "sound", "radar" -> if (press) rings += Ring(x, y, 0f, 2.6f, .8f)
            "stars" -> stars.indices.minByOrNull { hypot(stars[it][0] - x, stars[it][1] - y) }?.let { lit[it] = 1f; if (press) shoots += Shoot(x, y, .8f) }
            "pulse" -> if (press) spike = 1f
            "mixer" -> { val k = ((x / w) * faders.size).toInt().coerceIn(0, faders.size - 1); faderV[k] += if (press) 2.5f else -dy / h * 40f }
            "shield" -> if (press) flash = 1f
        }
    }

    private fun slideRod(rod: Int, side: Float) {
        val onRod = beads.filter { it.rod == rod }.sortedBy { it.target }
        val toRight = side < .5f
        onRod.forEachIndexed { i, b -> b.target = if (toRight) .92f - (onRod.size - 1 - i) * .055f else .08f + i * .055f }
    }

    private fun stepAbacus(dt: Float) {
        // Beads: tilt the phone and they all roll to the low end; otherwise one slides now and then, and with each key you press.
        if (SceneFeed.tilting && abs(SceneFeed.tiltX) > .04f) {
            for (r in rods.indices) { val onRod = beads.filter { it.rod == r }.sortedBy { it.target }
                onRod.forEachIndexed { i, b -> b.target = if (SceneFeed.tiltX > 0f) .92f - (onRod.size - 1 - i) * .055f else .08f + i * .055f } }
        } else {
            nextCount -= dt
            if (nextCount <= 0f) {
                nextCount = 1.4f + rnd.nextFloat() * 2.6f
                val rod = rnd.nextInt(rods.size); val onRod = beads.filter { it.rod == rod }.sortedBy { it.target }
                val k = rnd.nextInt(onRod.size); val toRight = onRod[k].target < .5f
                val moving = if (toRight) onRod.drop(k) else onRod.take(k + 1)
                moving.forEachIndexed { i, b -> b.target = if (toRight) .92f - (moving.size - 1 - i) * .055f else .08f + i * .055f }
            }
        }
        val roll = if (SceneFeed.tilting) 1.5f + abs(SceneFeed.tiltX) * 6f else 3.2f
        for (b in beads) b.x += (b.target - b.x) * min(1f, dt * roll)
        // Bubbles: as many as the answer calls for. They pop when there is an error, follow the bubble of the spirit level, and fizz with sound.
        val error = SceneFeed.state == "error"
        val want = when { error -> 0; SceneFeed.count >= 0 -> min(SceneFeed.count, 80); SceneFeed.waveOn -> (SceneFeed.amp * 70f).toInt(); else -> 5 }
        val live = bubs.count { it.pop < 0f }
        if (live < want) {
            spawnAcc += dt * (12f + want * 1.5f)
            var n = live
            while (spawnAcc >= 1f && n < want) { spawnAcc -= 1f; n++
                // Few bubbles for a small answer are big; a big answer is a fizz of small ones.
                val r = s * (.24f / sqrt(max(3f, want.toFloat()))).coerceIn(.025f, .13f) * (.7f + rnd.nextFloat() * .6f)
                val sinking = SceneFeed.sign < 0
                bubs += Bub(rnd.nextFloat() * w, if (sinking) -r * 2 else h + r * 2, r, s * (.12f + rnd.nextFloat() * .18f) * (if (sinking) .6f else 1f), rnd.nextFloat() * 6.28f)
            }
        } else spawnAcc = 0f
        if (live > want) bubs.filter { it.pop < 0f }.take(if (error) live else min(live - want, 3)).forEach { it.pop = 0f }
        val sinking = SceneFeed.sign < 0
        val it = bubs.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (b.pop >= 0f) { b.pop += dt * 3.5f; if (b.pop >= 1f) it.remove(); continue }
            // In the spirit level the bubbles run to the high side, like the real bubble does.
            if (SceneFeed.tilting) b.vx += -SceneFeed.tiltX * s * 2.5f * dt
            b.vx *= exp(-dt * 1.2f)
            b.x += b.vx * dt + sin(t * 1.3f + b.ph) * s * .02f * dt
            b.y += (if (sinking) 1f else -1f) * b.speed * dt * (1f + SceneFeed.amp * 2f)
            if (b.x < b.r) { b.x = b.r; b.vx = 0f } else if (b.x > w - b.r) { b.x = w - b.r; b.vx = 0f }
            if (!sinking && b.y < -b.r * 2) b.y = h + b.r * 2 else if (sinking && b.y > h + b.r * 2) b.y = -b.r * 2
        }
    }

    private fun stepDrifts(dt: Float) {
        // How many: Chronicle shows a page for each task still to do today, Packrat and App Hub a box for each thing in hand.
        val want = when (theme) { "pages", "boxes" -> SceneFeed.count.takeIf { it >= 0 }?.coerceIn(0, 14) ?: 7; else -> drifts.size }
        if (theme == "pages" || theme == "boxes") { while (drifts.count { it.torn < 0f } < want) drifts += newDrift(false); val extra = drifts.count { it.torn < 0f } - want
            if (extra > 0) drifts.filter { it.torn < 0f }.take(extra).forEach { it.torn = 0f } }
        val speedUp = when { theme == "ink" -> 1f + typing * 1.5f; theme == "boxes" && SceneFeed.active -> 4f; else -> 1f }
        val damp = exp(-dt * 2f)
        val it = drifts.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.x += d.vx * dt; d.y += d.vy * dt; d.vx *= damp; d.vy *= damp
            val down = theme == "pages" || theme == "book"
            if (d.torn >= 0f) { d.torn += dt; d.y += s * 1.1f * d.torn * dt * 3f; d.rot += 160f * dt; if (d.y > h + d.size * 2 || d.torn > 2.5f) it.remove(); continue }
            d.y += (if (down) 1f else -1f) * d.speed * dt * speedUp
            d.rot += d.spin * dt
            if (d.x < -d.size) d.x = w + d.size else if (d.x > w + d.size) d.x = -d.size
            val gone = if (down) d.y > h + d.size * 1.5f else d.y < -d.size * 1.5f
            if (gone) { val n = newDrift(false); d.x = n.x; d.y = n.y; d.vx = 0f; d.vy = 0f }
        }
    }

    /** [color] is the app's own colour; [alpha] how strongly to draw (lower at night and in the darker looks). */
    fun draw(c: Canvas, color: Int, alpha: Float) {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        if (a < 4) return
        fun col(x: Float) = Color.argb((a * x).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
        val white = { x: Float -> Color.argb((a * x).toInt().coerceIn(0, 255), 255, 255, 255) }
        when (theme) {
            "beads" -> {
                line.strokeWidth = s * .006f
                for ((i, ry) in rods.withIndex()) {
                    val y = h * ry
                    line.color = col(.55f); c.drawLine(w * .02f, y, w * .98f, y, line)
                    for (b in beads.filter { it.rod == i }) {
                        val x = w * b.x; val r = s * .032f
                        rect.set(x - r * 1.25f, y - r, x + r * 1.25f, y + r)
                        fill.shader = RadialGradient(x - r * .4f, y - r * .5f, r * 1.6f, intArrayOf(white(.95f), col(.9f), col(.55f)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
                        c.drawOval(rect, fill); fill.shader = null
                    }
                }
                val sinking = SceneFeed.sign < 0
                for (b in bubs) {
                    if (b.pop >= 0f) { line.strokeWidth = s * .004f; line.color = col(.8f * (1f - b.pop)); c.drawCircle(b.x, b.y, b.r * (1f + b.pop * 1.6f), line); continue }
                    fill.shader = RadialGradient(b.x - b.r * .35f, b.y - b.r * .4f, b.r * 1.3f, intArrayOf(white(1.9f), if (sinking) col(1.6f) else white(.6f), col(if (sinking) 1.9f else 1.4f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                    c.drawCircle(b.x, b.y, b.r, fill); fill.shader = null
                    line.strokeWidth = s * .006f; line.color = col(1.9f); c.drawCircle(b.x, b.y, b.r, line)
                }
            }
            "record" -> {
                // A vinyl record: grooves catching the light, a coloured label, and a tone arm that lowers while music plays.
                val cx = w * .5f; val cy = h * .5f; val r = s * .42f
                fill.color = col(.28f); c.drawCircle(cx, cy, r, fill)
                line.strokeWidth = s * .002f
                for (k in 0 until 18) { line.color = white(.10f + .08f * ((k % 3) / 2f)); c.drawCircle(cx, cy, r * (.42f + .55f * k / 18f), line) }
                // The shine stays still while the record turns under it, which is what makes it look like it spins.
                fill.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, white(.22f), Color.TRANSPARENT, Color.TRANSPARENT, white(.18f), Color.TRANSPARENT), floatArrayOf(0f, .1f, .2f, .5f, .6f, .7f))
                c.drawCircle(cx, cy, r, fill); fill.shader = null
                c.save(); c.rotate(spin, cx, cy)
                fill.color = col(.75f); c.drawCircle(cx, cy, r * .3f, fill)
                fill.color = white(.55f); c.drawCircle(cx + r * .17f, cy, r * .035f, fill)
                text.color = white(.75f); text.textSize = s * .035f; c.drawText("BLISS", cx, cy - r * .1f, text)
                c.restore()
                fill.color = white(.7f); c.drawCircle(cx, cy, r * .03f, fill)
                // Tone arm: pivots from the top right corner and swings onto the record while playing.
                val px = cx + r * 1.05f; val py = cy - r * 1.0f
                val ang = (-28f + 22f * arm) * PI.toFloat() / 180f
                val len = r * 1.25f
                val ex = px - sin(ang) * len * .35f - cos(ang) * len * .0f; val ey = py + cos(ang) * len
                line.color = white(.6f); line.strokeWidth = s * .012f; c.drawLine(px, py, ex - len * .25f * arm, ey, line)
                fill.color = white(.65f); c.drawCircle(px, py, s * .03f, fill)
                rect.set(ex - len * .25f * arm - s * .03f, ey - s * .015f, ex - len * .25f * arm + s * .03f, ey + s * .035f); c.drawRect(rect, fill)
            }
            "gears" -> {
                drawGear(c, w * .78f, h * .2f, s * .26f, 14, spin, col(.55f))
                drawGear(c, w * .78f - s * .43f, h * .2f + s * .2f, s * .17f, 9, -spin * 14f / 9f + 12f, col(.45f))
                drawGear(c, w * .2f, h * .82f, s * .3f, 16, spin * 5f / 8f, col(.4f))
                val cx = w * .5f; val cy = h * .5f; val r = s * .36f
                line.color = col(.35f); line.strokeWidth = s * .006f; c.drawCircle(cx, cy, r, line)
                for (k in 0 until 12) { val an = k * 30f * PI.toFloat() / 180f; val r1 = r * (if (k % 3 == 0) .84f else .9f); c.drawLine(cx + sin(an) * r1, cy - cos(an) * r1, cx + sin(an) * r * .97f, cy - cos(an) * r * .97f, line) }
                val now = java.time.LocalTime.now(); val secs = now.second + now.nano / 1e9f; val mins = now.minute + secs / 60f; val hrs = (now.hour % 12) + mins / 60f
                line.strokeWidth = s * .014f; line.color = col(.5f)
                val ha = hrs * 30f * PI.toFloat() / 180f; c.drawLine(cx, cy, cx + sin(ha) * r * .5f, cy - cos(ha) * r * .5f, line)
                line.strokeWidth = s * .009f
                val ma = mins * 6f * PI.toFloat() / 180f; c.drawLine(cx, cy, cx + sin(ma) * r * .78f, cy - cos(ma) * r * .78f, line)
                // While the stopwatch runs, a fast hand sweeps the face.
                if (SceneFeed.active) { line.strokeWidth = s * .006f; line.color = col(.8f); val sa = (t * 360f) * PI.toFloat() / 180f; c.drawLine(cx, cy, cx + sin(sa) * r * .92f, cy - cos(sa) * r * .92f, line) }
                if (flash > 0f) { line.strokeWidth = s * .01f; line.color = col(.8f * flash); c.drawCircle(cx, cy, r * (1.05f + (1f - flash) * .5f), line) }
                // A timer: a thick ring round the face for the time left, shrinking as it runs down.
                if (shownLevel >= 0f) {
                    rect.set(cx - r * 1.08f, cy - r * 1.08f, cx + r * 1.08f, cy + r * 1.08f)
                    line.strokeWidth = s * .035f; line.color = col(.18f); c.drawArc(rect, 0f, 360f, false, line)
                    line.color = col(.8f); c.drawArc(rect, -90f, 360f * shownLevel, false, line)
                }
            }
            "pages", "book", "ink" -> {
                for (d in drifts) {
                    val x = d.x + sin(t * .5f + d.ph) * d.sway
                    c.save(); c.translate(x, d.y); c.rotate(d.rot + sin(t * .7f + d.ph) * 14f)
                    val pw = d.size; val ph = d.size * 1.3f
                    rect.set(-pw / 2, -ph / 2, pw / 2, ph / 2)
                    fill.color = white(.55f); c.drawRoundRect(rect, pw * .08f, pw * .08f, fill)
                    line.color = col(.6f); line.strokeWidth = s * .004f; c.drawRoundRect(rect, pw * .08f, pw * .08f, line)
                    line.strokeWidth = s * .003f
                    for (k in 1..4) { val y = -ph / 2 + ph * (.25f + k * .14f); c.drawLine(-pw * .32f, y, pw * (if (k == 4) .05f else .32f), y, line) }
                    if (theme == "pages") {
                        fill.color = col(.7f); c.drawRect(-pw / 2, -ph / 2, pw / 2, -ph / 2 + ph * .18f, fill)
                        text.color = col(.75f); text.textSize = ph * .2f; c.drawText(d.n.toString(), 0f, -ph / 2 + ph * .4f, text)
                        // Done: a big tick, then it falls away.
                        if (d.torn >= 0f) { line.color = Color.argb((255 * min(1f, d.torn * 4f)).toInt().coerceAtMost(a), 46, 184, 92); line.strokeWidth = s * .014f
                            path.reset(); path.moveTo(-pw * .25f, ph * .05f); path.lineTo(-pw * .05f, ph * .25f); path.lineTo(pw * .3f, -ph * .15f); c.drawPath(path, line) }
                    }
                    c.restore()
                }
                if (theme == "ink") for (l in letters) {
                    val f = l.age / 3.2f
                    text.color = col(.9f * (1f - f) * min(1f, l.age * 5f)); text.textSize = l.size * (1f + f * .4f)
                    c.save(); c.rotate(l.rot * f, l.x, l.y); c.drawText(l.ch, l.x + sin(l.age * 2f + l.x) * s * .03f, l.y, text); c.restore()
                }
                if (theme == "book" && turn >= 0f) {
                    // A big page turning over, from the right edge across to the left.
                    val pw = w * .42f; val ph = h * .5f; val cx = w * .5f; val top = h * .22f
                    val k = cos(turn * PI.toFloat())
                    rect.set(cx, top, cx + pw * k, top + ph); if (k < 0f) rect.sort()
                    fill.color = white(.75f * (1f - turn * .6f)); c.drawRect(rect, fill)
                    line.color = col(.7f * (1f - turn)); line.strokeWidth = s * .005f; c.drawRect(rect, line)
                    line.strokeWidth = s * .003f
                    for (j in 1..7) { val y = top + ph * j / 9f; c.drawLine(rect.left + rect.width() * .12f, y, rect.right - rect.width() * .12f, y, line) }
                }
            }
            "frames" -> {
                val pics = SceneFeed.pictures
                drifts.forEachIndexed { i, d ->
                    val x = d.x + sin(t * .4f + d.ph) * d.sway
                    c.save(); c.translate(x, d.y); c.rotate(d.rot * .3f + sin(t * .5f + d.ph) * 8f)
                    val fw = d.size * 1.1f; val fh = d.size * 1.3f
                    rect.set(-fw / 2, -fh / 2, fw / 2, fh / 2); fill.color = white(.75f); c.drawRect(rect, fill)
                    rect.set(-fw * .42f, -fh * .42f, fw * .42f, fh * .22f)
                    val pic = pics.getOrNull(i % max(1, pics.size))?.takeIf { pics.isNotEmpty() && !it.isRecycled }
                    if (pic != null) {
                        // One of your own recent photos, cropped to fill the frame.
                        val sc = max(rect.width() / pic.width, rect.height() / pic.height); val sw = rect.width() / sc; val sh = rect.height() / sc
                        val src = android.graphics.Rect(((pic.width - sw) / 2).toInt(), ((pic.height - sh) / 2).toInt(), ((pic.width + sw) / 2).toInt(), ((pic.height + sh) / 2).toInt())
                        bmp.alpha = (a * .85f).toInt(); c.drawBitmap(pic, src, rect, bmp)
                    } else {
                        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, col(.35f), col(.75f), Shader.TileMode.CLAMP); c.drawRect(rect, fill); fill.shader = null
                        fill.color = white(.6f); c.drawCircle(fw * .2f, -fh * .25f, fw * .07f, fill)
                    }
                    if (flash > 0f) { fill.color = white(.8f * flash); c.drawRect(-fw / 2, -fh / 2, fw / 2, fh / 2, fill) }
                    c.restore()
                }
                // Recording the screen: a red light blinks in the corner of every frame's world.
                if (SceneFeed.state == "recording") { fill.color = Color.argb((a * (.5f + .5f * sin(t * 5f))).toInt(), 230, 40, 40); c.drawCircle(w * .9f, h * .08f, s * .03f, fill) }
            }
            "boxes" -> {
                for (d in drifts) {
                    val x = d.x + sin(t * .45f + d.ph) * d.sway
                    c.save(); c.translate(x, d.y); c.rotate(sin(t * .3f + d.ph) * 10f + (if (d.torn >= 0f) d.rot else 0f))
                    drawBox(c, d.size * .8f, col(.6f), col(.7f), white(.65f))
                    c.restore()
                }
                // Work in hand stacks up boxes at the bottom as it gets done.
                if (shownLevel >= 0f) {
                    val n = (shownLevel * 15f).toInt(); val b = s * .085f
                    var k = 0; var row = 0
                    while (k < n) { val inRow = 5 - row; for (j in 0 until inRow) { if (k >= n) break
                        c.save(); c.translate(w * .5f + (j - (inRow - 1) / 2f) * b * 1.05f, h - b * .6f - row * b * 1.02f); drawBox(c, b, col(.75f), col(.85f), white(.7f)); c.restore(); k++ }
                        row++ }
                }
            }
            "rings" -> for (r in rings) {
                val f = r.age / r.life
                line.strokeWidth = s * .005f * r.big
                for (k in 0..2) { val ff = f - k * .12f; if (ff <= 0f) continue; line.color = col(.75f * (1f - ff).coerceIn(0f, 1f)); c.drawCircle(r.x, r.y, s * .5f * ff * r.big, line) }
            }
            "radar" -> {
                val cx = w * .5f; val cy = h * .55f; val r = s * .48f
                line.strokeWidth = s * .004f; line.color = col(.4f)
                for (k in 1..3) c.drawCircle(cx, cy, r * k / 3f, line)
                c.drawLine(cx - r, cy, cx + r, cy, line); c.drawLine(cx, cy - r, cx, cy + r, line)
                val ang = (t * (40f + flash * 200f)) % 360f
                fill.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, col(.55f)), floatArrayOf(0f, .8f, 1f))
                c.save(); c.rotate(ang - 90f, cx, cy); c.drawCircle(cx, cy, r, fill); c.restore(); fill.shader = null
                // A blip for each search result (or a few strays when not searching).
                val n = SceneFeed.count.takeIf { it >= 0 }?.coerceAtMost(40) ?: 6
                for (k in 0 until n) {
                    val bd = r * (.18f + ((k * 0.618f) % 1f) * .78f); val ba = (k * 137.5f) % 360f
                    val bx = cx + cos(ba * PI.toFloat() / 180f) * bd; val by = cy + sin(ba * PI.toFloat() / 180f) * bd
                    val d = ((ba + 90f + 360f) % 360f); val since = ((ang - d + 720f) % 360f) / 360f
                    fill.color = col(.25f + .7f * (1f - since)); c.drawCircle(bx, by, s * (.01f + .008f * (1f - since)), fill)
                }
                for (rg in rings) { val f = rg.age / rg.life; line.strokeWidth = s * .006f; line.color = col(.7f * (1f - f)); c.drawCircle(rg.x, rg.y, r * f * rg.big, line) }
            }
            "sound" -> {
                val live = SceneFeed.waveOn
                if (live) {
                    // The live waveform: what you are recording or playing, flowing from right to left across the middle.
                    val n = SceneFeed.wave.size; val at = SceneFeed.waveAt; val cy = h * .5f; val amp = h * .22f
                    path.reset()
                    for (i in 0 until n) { val v = SceneFeed.wave[((at - n + i) % n + n) % n]; val x = w * i / (n - 1f); val y = cy - v * amp * (.4f + .6f * abs(sin(i * .9f + t * 6f))); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                    for (i in n - 1 downTo 0) { val v = SceneFeed.wave[((at - n + i) % n + n) % n]; val x = w * i / (n - 1f); path.lineTo(x, cy + v * amp * (.4f + .6f * abs(cos(i * .7f + t * 5f)))) }
                    path.close()
                    fill.shader = LinearGradient(0f, cy - amp, 0f, cy + amp, intArrayOf(col(.15f), col(.6f), col(.15f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP); c.drawPath(path, fill); fill.shader = null
                    line.strokeWidth = s * .004f; line.color = col(.85f); c.drawPath(path, line)
                    fill.color = white(.5f + .4f * SceneFeed.amp); c.drawCircle(w.toFloat() - s * .02f, cy, s * (.01f + .03f * SceneFeed.amp), fill)
                }
                // Bars along the bottom: a level meter that follows the sound, idling when there is none.
                val n = 22; val bw = w / (n * 1.5f)
                for (k in 0 until n) {
                    val wob = (.35f + .3f * sin(t * 2.1f + k * .7f) + .25f * sin(t * 3.7f + k * 1.9f))
                    val v = (if (live) SceneFeed.amp * (.5f + .7f * wob) else wob * .6f).coerceIn(.04f, 1f)
                    val bh = h * (if (live) .3f else .14f) * v; val x = w * (k + .5f) / n
                    rect.set(x - bw / 2, h - bh, x + bw / 2, h.toFloat())
                    fill.shader = LinearGradient(0f, h - bh, 0f, h.toFloat(), white(.6f), col(.65f), Shader.TileMode.CLAMP); c.drawRoundRect(rect, bw * .4f, bw * .4f, fill); fill.shader = null
                }
                for (r in rings) { val f = r.age / r.life; line.strokeWidth = s * .006f; line.color = col(.75f * (1f - f)); c.drawCircle(r.x, r.y, s * .45f * f * r.big, line) }
            }
            "compass" -> {
                val cx = w * .5f; val cy = h * .48f; val r = s * .42f
                c.save(); c.rotate(heading, cx, cy)
                line.color = col(.42f); line.strokeWidth = s * .005f; c.drawCircle(cx, cy, r, line); c.drawCircle(cx, cy, r * .86f, line)
                for (k in 0 until 32) { val an = k * 11.25f * PI.toFloat() / 180f; val r1 = r * (if (k % 4 == 0) .78f else .82f); c.drawLine(cx + sin(an) * r1, cy - cos(an) * r1, cx + sin(an) * r * .86f, cy - cos(an) * r * .86f, line) }
                for (k in 0 until 4) {
                    path.reset(); val an = k * 90f * PI.toFloat() / 180f; val an2 = an + PI.toFloat() / 4
                    path.moveTo(cx + sin(an) * r * .74f, cy - cos(an) * r * .74f); path.lineTo(cx + sin(an2) * r * .14f, cy - cos(an2) * r * .14f); path.lineTo(cx, cy)
                    path.lineTo(cx + sin(an - PI.toFloat() / 4) * r * .14f, cy - cos(an - PI.toFloat() / 4) * r * .14f); path.close()
                    fill.color = if (k == 0) col(.7f) else col(.32f); c.drawPath(path, fill)
                }
                text.color = col(.6f); text.textSize = s * .06f; c.drawText("N", cx, cy - r * 1.04f, text)
                c.restore()
                // The dotted route; it races along while files are being copied or moved.
                val pace = if (SceneFeed.active) 10f else 1f
                line.strokeWidth = s * .006f; line.color = col(.5f); line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(s * .02f, s * .02f), -t * s * .04f * pace)
                path.reset(); path.moveTo(-10f, h * .86f); path.cubicTo(w * .3f, h * .7f, w * .2f, h * .95f, w * .6f, h * .82f); path.cubicTo(w * .85f, h * .74f, w * .9f, h * .9f, w + 10f, h * .78f)
                c.drawPath(path, line); line.pathEffect = null
            }
            "stars" -> {
                // The constellation joins up as the page loads, and is whole when it has.
                val joined = if (shownLevel >= 0f) (shownLevel * (stars.size - 1)).toInt() else stars.size - 1
                line.strokeWidth = s * .003f
                for (k in 0 until stars.size - 1) if (k % 3 != 2) { val p = stars[k]; val q = stars[k + 1]; line.color = col(if (k < joined) .55f else .12f); c.drawLine(p[0], p[1], q[0], q[1], line) }
                for ((k, st) in stars.withIndex()) { val tw = .55f + .45f * sin(t * 1.4f + st[2]); val l = lit[k]
                    fill.color = white(min(1f, .85f * tw + l)); c.drawCircle(st[0], st[1], s * (.006f + .006f * tw + .02f * l), fill) }
                for (sh in shoots) { line.strokeWidth = s * .005f; line.color = white(.9f * sh.life); c.drawLine(sh.x, sh.y, sh.x - s * .18f, sh.y - s * .065f, line) }
            }
            "dial" -> {
                val cx = w * .5f; val cy = h * .46f; val r = s * .4f
                // Unlocked, the door swings aside; locked, the dial clicks round with each character of the password.
                c.save(); c.translate(-w * .55f * open, 0f); c.rotate(dial, cx, cy)
                line.color = col(.5f * (1f - open * .5f)); line.strokeWidth = s * .012f; c.drawCircle(cx, cy, r, line)
                line.strokeWidth = s * .004f
                for (k in 0 until 60) { val an = k * 6f * PI.toFloat() / 180f; val r1 = r * (if (k % 5 == 0) .8f else .87f); c.drawLine(cx + sin(an) * r1, cy - cos(an) * r1, cx + sin(an) * r * .93f, cy - cos(an) * r * .93f, line) }
                text.color = col(.55f); text.textSize = s * .045f
                for (k in 0 until 12) { val an = k * 30f * PI.toFloat() / 180f; c.drawText((k * 5).toString(), cx + sin(an) * r * .68f, cy - cos(an) * r * .68f + text.textSize * .35f, text) }
                c.restore()
                line.color = col(.4f); line.strokeWidth = s * .008f; c.drawCircle(cx - w * .55f * open, cy, r * .22f, line)
                // The marker at the top lights up with each click.
                path.reset(); path.moveTo(cx, cy - r * 1.04f); path.lineTo(cx - s * .025f, cy - r * 1.12f); path.lineTo(cx + s * .025f, cy - r * 1.12f); path.close()
                fill.color = col(.5f + .5f * flash); c.drawPath(path, fill)
                if (open > .02f) { fill.color = col(.18f * open); c.drawCircle(cx, cy, r * .9f, fill); line.color = col(.5f * open); line.strokeWidth = s * .01f; c.drawCircle(cx, cy, r * .9f, line) }
            }
            "shield" -> {
                val cx = w * .5f; val cy = h * .46f; val sc = s * .42f * (1f + .015f * sin(t * 1.1f) + .04f * flash + (if (SceneFeed.active) .02f * sin(t * 4f) else 0f))
                path.reset(); path.moveTo(cx, cy - sc); path.cubicTo(cx + sc * .55f, cy - sc * .8f, cx + sc * .8f, cy - sc * .85f, cx + sc * .8f, cy - sc * .75f)
                path.cubicTo(cx + sc * .8f, cy + sc * .2f, cx + sc * .4f, cy + sc * .7f, cx, cy + sc); path.cubicTo(cx - sc * .4f, cy + sc * .7f, cx - sc * .8f, cy + sc * .2f, cx - sc * .8f, cy - sc * .75f)
                path.cubicTo(cx - sc * .8f, cy - sc * .85f, cx - sc * .55f, cy - sc * .8f, cx, cy - sc); path.close()
                fill.shader = LinearGradient(0f, cy - sc, 0f, cy + sc, white(.35f), col(.35f), Shader.TileMode.CLAMP); c.drawPath(path, fill); fill.shader = null
                // A backup running: the shield fills from the bottom as it goes.
                if (shownLevel >= 0f) { c.save(); c.clipPath(path); val top = cy + sc - 2f * sc * shownLevel
                    fill.color = col(.6f); c.drawRect(cx - sc, top + s * .012f * sin(t * 3f), cx + sc, cy + sc, fill); c.restore() }
                line.color = col(.55f + .4f * flash); line.strokeWidth = s * .008f; c.drawPath(path, line)
                line.strokeWidth = s * .02f; val tick = Path(); tick.moveTo(cx - sc * .3f, cy); tick.lineTo(cx - sc * .05f, cy + sc * .25f); tick.lineTo(cx + sc * .35f, cy - sc * .25f); c.drawPath(tick, line)
            }
            "mixer" -> {
                // A mixing desk: one fader for each volume and the screen brightness, sitting where the phone really is.
                val names = listOf("MEDIA", "RING", "NOTIFY", "ALARM", "LIGHT")
                val top = h * .2f; val bottom = h * .78f
                for (k in faders.indices) {
                    val x = w * (k + .5f) / faders.size; val v = faders[k].coerceIn(-.05f, 1.05f); val y = bottom - (bottom - top) * v
                    line.strokeWidth = s * .018f; line.color = col(.22f); c.drawLine(x, top, x, bottom, line)
                    line.color = col(.7f); c.drawLine(x, bottom, x, y, line)
                    line.strokeWidth = s * .003f; line.color = col(.35f)
                    for (j in 0..10) { val ty = top + (bottom - top) * j / 10f; c.drawLine(x + s * .03f, ty, x + s * (if (j % 5 == 0) .055f else .042f), ty, line) }
                    rect.set(x - s * .05f, y - s * .022f, x + s * .05f, y + s * .022f)
                    fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, white(1f), col(.9f), Shader.TileMode.CLAMP); c.drawRoundRect(rect, s * .012f, s * .012f, fill); fill.shader = null
                    line.color = col(.9f); line.strokeWidth = s * .004f; c.drawLine(rect.left + s * .012f, y, rect.right - s * .012f, y, line)
                    text.color = col(.6f); text.textSize = s * .03f; c.drawText(names[k], x, bottom + s * .07f, text)
                }
                // The flashlight on: a warm beam floods down from the top.
                if (SceneFeed.state == "torch") { fill.shader = RadialGradient(w * .5f, -h * .05f, h * .8f, Color.argb((a * 1.4f).toInt().coerceAtMost(255), 255, 230, 140), 0, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill); fill.shader = null }
            }
            "pulse" -> {
                line.strokeWidth = s * .002f; line.color = col(.22f)
                val g = s * .1f; var gx = (t * s * .05f) % g - g; while (gx < w) { c.drawLine(gx, 0f, gx, h.toFloat(), line); gx += g }
                var gy = 0f; while (gy < h) { c.drawLine(0f, gy, w.toFloat(), gy, line); gy += g }
                // The heartbeat: quicker and taller the harder the phone works (a tap makes it jump).
                val load = SceneFeed.amp
                path.reset(); val base = h * .42f; val period = w * (.5f - .25f * load); val off = (beatPhase * w * .22f) % period
                val up = 1f + load * 1.2f + spike
                var x = -period + off; path.moveTo(-10f, base)
                while (x < w + period) {
                    path.lineTo(x, base); path.lineTo(x + period * .06f, base - h * .02f * up); path.lineTo(x + period * .1f, base)
                    path.lineTo(x + period * .14f, base + h * .03f * up); path.lineTo(x + period * .18f, base - h * .16f * up); path.lineTo(x + period * .22f, base + h * .06f * up)
                    path.lineTo(x + period * .27f, base); path.lineTo(x + period * .36f, base - h * .03f * up); path.lineTo(x + period * .44f, base); x += period
                }
                line.strokeWidth = s * .009f; line.color = col(.6f + .3f * spike); c.drawPath(path, line)
                // Below it, the real trace (CPU use over the last minutes), filled like a graph.
                if (SceneFeed.waveOn) {
                    val n = SceneFeed.wave.size; val at = SceneFeed.waveAt; val bottom = h * .92f; val top = h * .62f
                    path.reset(); path.moveTo(0f, bottom)
                    for (i in 0 until n) { val v = SceneFeed.wave[((at - n + i) % n + n) % n]; path.lineTo(w * i / (n - 1f), bottom - (bottom - top) * v) }
                    path.lineTo(w.toFloat(), bottom); path.close()
                    fill.shader = LinearGradient(0f, top, 0f, bottom, col(.45f), col(.08f), Shader.TileMode.CLAMP); c.drawPath(path, fill); fill.shader = null
                }
            }
        }
    }

    private fun drawBox(c: Canvas, b: Float, edge: Int, tape: Int, light: Int) {
        rect.set(-b / 2, -b / 2, b / 2, b / 2)
        fill.shader = LinearGradient(0f, -b / 2, 0f, b / 2, light, edge, Shader.TileMode.CLAMP); c.drawRoundRect(rect, b * .14f, b * .14f, fill); fill.shader = null
        line.color = tape; line.strokeWidth = s * .004f; c.drawRoundRect(rect, b * .14f, b * .14f, line)
        c.drawLine(0f, -b / 2, 0f, b / 2, line); c.drawLine(-b / 2, -b * .12f, b / 2, -b * .12f, line)
    }

    private fun drawGear(c: Canvas, cx: Float, cy: Float, r: Float, teeth: Int, deg: Float, color: Int) {
        path.reset()
        val inner = r * .82f; val step = 360f / teeth
        for (k in 0 until teeth) {
            val a0 = (deg + k * step) * PI.toFloat() / 180f; val a1 = a0 + step * .25f * PI.toFloat() / 180f
            val a2 = a0 + step * .5f * PI.toFloat() / 180f; val a3 = a0 + step * .75f * PI.toFloat() / 180f
            if (k == 0) path.moveTo(cx + cos(a0) * inner, cy + sin(a0) * inner) else path.lineTo(cx + cos(a0) * inner, cy + sin(a0) * inner)
            path.lineTo(cx + cos(a1) * r, cy + sin(a1) * r); path.lineTo(cx + cos(a2) * r, cy + sin(a2) * r); path.lineTo(cx + cos(a3) * inner, cy + sin(a3) * inner)
        }
        path.close()
        line.color = color; line.strokeWidth = r * .05f; c.drawPath(path, line)
        c.drawCircle(cx, cy, r * .42f, line); c.drawCircle(cx, cy, r * .12f, line)
        for (k in 0 until 5) { val a = (deg + k * 72f) * PI.toFloat() / 180f; c.drawLine(cx + cos(a) * r * .12f, cy + sin(a) * r * .12f, cx + cos(a) * r * .42f, cy + sin(a) * r * .42f, line) }
    }

    companion object {
        /** Which picture each app gets. */
        fun themeFor(appName: String): String? = when {
            appName.contains("Abacus", true) -> "beads"
            appName.contains("Bliss", true) -> "record"
            appName.contains("Timepiece", true) -> "gears"
            appName.contains("Chronicle", true) -> "pages"
            appName.contains("Pages", true) -> "book"
            appName.contains("Scribble", true) -> "ink"
            appName.contains("Ripple", true) -> "rings"
            appName.contains("Echo", true) -> "sound"
            appName.contains("Beacon", true) -> "radar"
            appName.contains("Wayfinder", true) -> "compass"
            appName.contains("Polaris", true) -> "stars"
            appName.contains("Memories", true) -> "frames"
            appName.contains("Vault", true) -> "dial"
            appName.contains("Safekeeper", true) -> "shield"
            appName.contains("Pulse", true) -> "pulse"
            appName.contains("Control Room", true) -> "mixer"
            appName.contains("Packrat", true) || appName.contains("Apphub", true) || appName.contains("App Hub", true) -> "boxes"
            else -> null
        }
    }
}
