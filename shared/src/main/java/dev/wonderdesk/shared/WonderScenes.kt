package dev.wonderdesk.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* ======================================================================================================
 * Dynamic scenes: Aqua (water and bubbles), Bliss (a grass field under a moving sky) and Super (space,
 * rainbows and glowing rings). Colours move smoothly with the time of day, things happen at random, and
 * a tap pops a bubble or ripples the grass. A custom scene is one of these with your own rules.
 * ==================================================================================================== */

/** Conditions in a custom scene's rules. */
data class KitRule(val whenKind: String, val a: Int, val b: Int, val base: String, val hue: Int) {
    fun toJson() = JSONObject().put("when", whenKind).put("a", a).put("b", b).put("base", base).put("hue", hue)
    fun label(): String = when (whenKind) {
        "hours" -> "Between $a:00 and $b:00"
        "charging" -> "While charging"
        "battery" -> "Battery below $a%"
        else -> whenKind
    } + " → " + (if (base.isNotEmpty()) KitSceneConfig.name(base) else "same scene") + (if (hue != 0) ", colours shifted $hue°" else "")
    companion object { fun from(o: JSONObject) = KitRule(o.optString("when"), o.optInt("a"), o.optInt("b"), o.optString("base"), o.optInt("hue")) }
}

/** Everything that can be changed about a scene. */
data class KitSceneConfig(
    /** aqua, bliss, super */
    val base: String = "aqua",
    /** -1 follows the clock; otherwise a fixed hour of the day. */
    val fixedHour: Float = -1f,
    /** How fast the day goes: 1 is real time, 60 is a day per 24 minutes. */
    val daySpeed: Int = 1,
    val hueShift: Int = 0,
    val events: Boolean = true,
    /** 1 rare, 2 now and then, 3 often. */
    val eventRate: Int = 2,
    val batteryTint: Boolean = false,
    val chargingGlow: Boolean = false,
    val rules: List<KitRule> = emptyList(),
) {
    fun toJson() = JSONObject().put("base", base).put("fixedHour", fixedHour.toDouble()).put("speed", daySpeed).put("hue", hueShift).put("events", events).put("rate", eventRate)
        .put("batteryTint", batteryTint).put("chargingGlow", chargingGlow).put("rules", JSONArray(rules.map { it.toJson() }))

    companion object {
        fun name(base: String) = when (base) { "aqua" -> "Aqua"; "bliss" -> "Bliss"; "super" -> "Super"; else -> base }
        fun from(o: JSONObject) = KitSceneConfig(o.optString("base", "aqua"), o.optDouble("fixedHour", -1.0).toFloat(), o.optInt("speed", 1), o.optInt("hue"), o.optBoolean("events", true), o.optInt("rate", 2),
            o.optBoolean("batteryTint"), o.optBoolean("chargingGlow"), o.optJSONArray("rules")?.let { a -> (0 until a.length()).map { KitRule.from(a.getJSONObject(it)) } } ?: emptyList())

        /** The scene that matches a Wonderdesk look. */
        fun forLook(look: WonderLook?) = when (look) {
            WonderLook.BLISS, WonderLook.DARK -> "bliss"
            WonderLook.SUPER -> "super"
            else -> "aqua"
        }

    }
}

/** What is going on around the scene right now. */
class KitEnv(var hour: Float, var battery: Int, var charging: Boolean)

private fun blend(a: Int, b: Int, f: Float): Int {
    val t = f.coerceIn(0f, 1f)
    return Color.argb((Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(), (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
}

/** Colour stops that blend smoothly around the clock: [at] hours with [colors] each. */
private class Keys(val at: FloatArray, val colors: Array<IntArray>) {
    fun at(hour: Float, i: Int): Int {
        val h = ((hour % 24) + 24) % 24
        var k = at.size - 1
        for (j in 0 until at.size - 1) if (h >= at[j] && h < at[j + 1]) { k = j; break }
        val next = (k + 1) % at.size
        val span = if (next == 0) 24 - at[k] + at[0] else at[next] - at[k]
        val into = if (h >= at[k]) h - at[k] else h + 24 - at[k]
        val f = into / span
        val s = f * f * (3 - 2 * f)
        return blend(colors[k][i], colors[next][i], s)
    }
}

private val SKY = Keys(floatArrayOf(0f, 5f, 6.5f, 8.5f, 12f, 16f, 18.3f, 19.6f, 21.5f),
    arrayOf(intArrayOf(0xFF050B24.toInt(), 0xFF0B1A44.toInt()), intArrayOf(0xFF1B2A5E.toInt(), 0xFF5B4B8A.toInt()), intArrayOf(0xFF4A6FB5.toInt(), 0xFFFFA56B.toInt()),
        intArrayOf(0xFF3A8EE0.toInt(), 0xFF9AD4FF.toInt()), intArrayOf(0xFF1E7FE8.toInt(), 0xFF8FD0FF.toInt()), intArrayOf(0xFF2F86E0.toInt(), 0xFFA8DAFF.toInt()),
        intArrayOf(0xFF4A5FA8.toInt(), 0xFFFF8A5B.toInt()), intArrayOf(0xFF2B2A6B.toInt(), 0xFFB5527A.toInt()), intArrayOf(0xFF0A1236.toInt(), 0xFF1E2A5E.toInt())))

/** Water seen from below: surface, middle and deep colours. */
private val WATER = Keys(floatArrayOf(0f, 5f, 6.5f, 8.5f, 12f, 16f, 18.3f, 19.6f, 21.5f),
    arrayOf(intArrayOf(0xFF0E4A94.toInt(), 0xFF082F6B.toInt(), 0xFF010A22.toInt()), intArrayOf(0xFF2A4E96.toInt(), 0xFF1A2E6E.toInt(), 0xFF050C2A.toInt()), intArrayOf(0xFFFFB27A.toInt(), 0xFF3F7FC4.toInt(), 0xFF0B2A66.toInt()),
        intArrayOf(0xFF7FDCFF.toInt(), 0xFF22A8EE.toInt(), 0xFF0A4FA8.toInt()), intArrayOf(0xFF8FE4FF.toInt(), 0xFF22A8EE.toInt(), 0xFF063B8E.toInt()), intArrayOf(0xFF7FDCFF.toInt(), 0xFF1E9CE6.toInt(), 0xFF083E92.toInt()),
        intArrayOf(0xFFFFA26B.toInt(), 0xFF8A5BB0.toInt(), 0xFF140F4E.toInt()), intArrayOf(0xFF8A4A9E.toInt(), 0xFF2B2A6B.toInt(), 0xFF0A0A30.toInt()), intArrayOf(0xFF123A82.toInt(), 0xFF0A2458.toInt(), 0xFF020A26.toInt())))

private val GRASS = Keys(floatArrayOf(0f, 5f, 6.5f, 8.5f, 12f, 16f, 18.3f, 19.6f, 21.5f),
    arrayOf(intArrayOf(0xFF0E2A24.toInt(), 0xFF153D2E.toInt()), intArrayOf(0xFF1D3C34.toInt(), 0xFF2A5A3E.toInt()), intArrayOf(0xFF6E8F4A.toInt(), 0xFF8FB04A.toInt()), intArrayOf(0xFF5DB04A.toInt(), 0xFF7FD05A.toInt()),
        intArrayOf(0xFF4FA83E.toInt(), 0xFF76D14B.toInt()), intArrayOf(0xFF5AAE40.toInt(), 0xFF7FD052.toInt()), intArrayOf(0xFF9A8A3E.toInt(), 0xFFC8A04A.toInt()), intArrayOf(0xFF3E4A3A.toInt(), 0xFF5A5A44.toInt()), intArrayOf(0xFF15302A.toInt(), 0xFF1E4234.toInt())))

/** 0 in full day, 1 in the dead of night, smooth through twilight. */
private fun nightness(h: Float): Float {
    val x = ((h % 24) + 24) % 24
    return when { x < 5f -> 1f; x < 7f -> 1f - (x - 5f) / 2f; x < 18f -> 0f; x < 20.5f -> (x - 18f) / 2.5f; else -> 1f }.coerceIn(0f, 1f)
}

abstract class KitScene(val w: Int, val h: Int, seed: Long) {
    protected val rnd = Random(seed)
    protected val m = max(w, h).toFloat()
    var time = 0f
    abstract fun draw(c: Canvas, env: KitEnv, cfg: KitSceneConfig)
    open fun step(dt: Float, env: KitEnv, cfg: KitSceneConfig) { time += dt }
    open fun tap(x: Float, y: Float) {}

    /** A shooting star every so often, when the sky is dark enough. */
    protected class Streak(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float)
    protected val streaks = ArrayList<Streak>()
    private var nextEvent = 6f
    protected fun scheduleEvents(dt: Float, cfg: KitSceneConfig, canStreak: Boolean, other: () -> Unit = {}) {
        if (!cfg.events) return
        nextEvent -= dt
        if (nextEvent <= 0f) {
            nextEvent = (when (cfg.eventRate) { 1 -> 90f; 3 -> 12f; else -> 35f }) * (.6f + rnd.nextFloat() * .8f)
            if (canStreak && rnd.nextBoolean()) streaks.add(Streak(rnd.nextFloat() * w, rnd.nextFloat() * h * .3f, m * (.5f + rnd.nextFloat() * .4f), m * (.2f + rnd.nextFloat() * .2f), 1f)) else other()
        }
        val it = streaks.iterator()
        while (it.hasNext()) { val s = it.next(); s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt * 1.1f; if (s.life <= 0f) it.remove() }
    }
    protected fun drawStreaks(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = m * .003f }
        for (s in streaks) {
            val len = m * .09f; val d = kotlin.math.sqrt(s.vx * s.vx + s.vy * s.vy)
            p.shader = LinearGradient(s.x, s.y, s.x - s.vx / d * len, s.y - s.vy / d * len, Color.argb((255 * s.life.coerceIn(0f, 1f)).toInt(), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawLine(s.x, s.y, s.x - s.vx / d * len, s.y - s.vy / d * len, p)
        }
    }
}

/** A soft round glow, painted once and then scaled, tinted and faded for free every frame. */
private fun glowSprite(n: Int, vararg stops: Int): Bitmap {
    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(n / 2f, n / 2f, n / 2f, stops, null, Shader.TileMode.CLAMP) }
    Canvas(b).drawCircle(n / 2f, n / 2f, n / 2f, p)
    return b
}

/** A soft ring, painted once. */
private fun ringSprite(n: Int): Bitmap {
    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = n * .05f; color = Color.WHITE; maskFilter = BlurMaskFilter(n * .035f, BlurMaskFilter.Blur.NORMAL) }
    Canvas(b).drawCircle(n / 2f, n / 2f, n * .4f, p)
    return b
}

private val ADD_MODE = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
private fun hsv(a: Int, hue: Float, s: Float, v: Float) = Color.HSVToColor(a, floatArrayOf(((hue % 360f) + 360f) % 360f, s, v))

/**
 * Aqua: looking up through water at the sky. Every part of the day looks like itself: a rose and gold dawn with long low rays from the left,
 * a fresh turquoise morning, a deep azure noon with short bright rays straight down, a warm golden afternoon with the rays leaning the other way,
 * an orange and violet sunset, an indigo dusk, and a real night: almost black water, no sun rays, the moon shimmering through the surface with one
 * pale moonbeam, and plankton that glows teal. Now and then a cloud drifts over and the light dims for a few seconds.
 * Bubbles you can pop, drifting specks and the odd fish, as before.
 */
class KitAquaScene(w: Int, h: Int) : KitScene(w, h, 3) {
    private val field = KitBubbleField(w, h, count = 30, seed = 11, popping = true)
    private class Fish(var x: Float, val y: Float, val size: Float, val dir: Int)
    private val fish = ArrayList<Fish>()
    private var bg: Bitmap? = null
    private var bgKey = ""

    private class Beam(val x: Float, val wd: Float, val sp: Float, val ph: Float, val str: Float)
    private val beams = List(9) { Beam(.02f + it * .115f + rnd.nextFloat() * .05f, .10f + rnd.nextFloat() * .16f, .6f + rnd.nextFloat() * .9f, rnd.nextFloat() * 6.28f, .5f + rnd.nextFloat() * .5f) }
    private val beamSprite: Bitmap = Bitmap.createBitmap(192, 512, Bitmap.Config.ARGB_8888).also { b ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL); shader = LinearGradient(0f, 0f, 0f, 512f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, .5f, .95f), Shader.TileMode.CLAMP) }
        Canvas(b).drawPath(Path().apply { moveTo(80f, 0f); lineTo(112f, 0f); lineTo(170f, 512f); lineTo(22f, 512f); close() }, p)
    }
    private val causticTile = makeCaustic(256)
    private val causticA = android.graphics.BitmapShader(causticTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val causticB = android.graphics.BitmapShader(causticTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val mat = android.graphics.Matrix()
    private val rayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = ADD_MODE }
    private val motePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = ADD_MODE }
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = ADD_MODE }
    private val causticPaint = Paint().apply { xfermode = ADD_MODE }
    private val motes = List(64) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, m * (.003f + rnd.nextFloat() * .006f), rnd.nextFloat() * 6.28f, m * (.004f + rnd.nextFloat() * .01f)) }
    private val moteSprite = glowSprite(48, 0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF)
    private val sunSprite = glowSprite(128, 0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x22FFFFFF, 0x00FFFFFF)
    private val src = android.graphics.Rect(); private val dst = RectF()
    private val fishDir = HashMap<Fish, Int>()
    private val fishPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    // A passing cloud: [cloudAt] runs from 0 to 1 while it crosses, dimming the rays and ripples in the middle.
    private var nextCloud = 25f + rnd.nextFloat() * 30f
    private var cloudAt = -1f
    private var cloudLen = 12f
    private var lastHour = 12f
    private var filterKey = 0
    private var moteKey = 0

    private fun makeCaustic(n: Int): Bitmap {
        val px = IntArray(n * n); val tw = (2 * PI / n).toFloat()
        for (y in 0 until n) for (x in 0 until n) {
            val f = (sin(tw * (3 * x + 2 * y)) + sin(tw * (-2 * x + 4 * y) + 1.7f) + sin(tw * (5 * x - 3 * y) + 3.1f) + sin(tw * (-4 * x - 5 * y) + .6f)) / 4f
            var v = 1f - abs(f); v *= v; v *= v; v *= v
            px[y * n + x] = (((v * 255f * 1.7f).toInt().coerceIn(0, 255)) shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    override fun step(dt: Float, env: KitEnv, cfg: KitSceneConfig) {
        super.step(dt, env, cfg)
        lastHour = env.hour
        field.step(dt)
        scheduleEvents(dt, cfg, false) { val left = rnd.nextBoolean(); val f = Fish(if (left) -m * .1f else w + m * .1f, h * (.3f + rnd.nextFloat() * .4f), m * (.012f + rnd.nextFloat() * .01f), if (left) 1 else -1); fish.add(f); fishDir[f] = f.dir }
        val it = fish.iterator()
        while (it.hasNext()) { val f = it.next(); f.x += (fishDir[f] ?: 1) * m * .09f * dt; if (f.x < -m * .2f || f.x > w + m * .2f) { it.remove(); fishDir.remove(f) } }
        val night = nightness(env.hour)
        // Plankton drifts more lazily at night.
        for (s in motes) { s[1] -= s[4] * dt * (1f - night * .5f); s[0] += sin(time * .4f + s[3]) * m * .004f * dt * 8; if (s[1] < -10f) { s[1] = h + 10f; s[0] = rnd.nextFloat() * w } }
        if (cloudAt >= 0f) { cloudAt += dt / cloudLen; if (cloudAt >= 1f) cloudAt = -1f }
        else { nextCloud -= dt; if (nextCloud <= 0f) { cloudAt = 0f; cloudLen = 9f + rnd.nextFloat() * 10f; nextCloud = 40f + rnd.nextFloat() * 70f } }
    }

    override fun tap(x: Float, y: Float) { field.popAt(x, y) }

    private fun water(hour: Float): Bitmap {
        val key = "%.1f".format(hour)
        if (bg == null || key != bgKey) {
            val b = bg ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
            val night = nightness(hour)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w * .12f, h.toFloat(), intArrayOf(AQUA_WATER.at(hour, 0), AQUA_WATER.at(hour, 1), AQUA_WATER.at(hour, 2)), floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
            // The deep gets properly dark at night, a little dark by day.
            p.shader = LinearGradient(0f, h * .45f, 0f, h.toFloat(), Color.TRANSPARENT, blend(0x66000A1C, 0xD0000208.toInt(), night), Shader.TileMode.CLAMP); c.drawRect(0f, h * .45f, w.toFloat(), h.toFloat(), p)
            bg = b; bgKey = key
        }
        return bg!!
    }

    /** Where the sun is: -1 low on the left (morning) through 0 overhead to 1 low on the right (evening), and how high (0 to 1). */
    private fun sunSide(hour: Float) = (((hour - 6.5f) / (18.3f - 6.5f)) * 2f - 1f).coerceIn(-1.15f, 1.15f)
    private fun sunHeight(hour: Float) = sin(PI.toFloat() * ((hour - 6.5f) / (18.3f - 6.5f)).coerceIn(0f, 1f))
    /** The moon crosses the night the same way, left to right. */
    private fun moonSide(hour: Float): Float { val t = (((hour - 19.5f) + 24f) % 24f) / 11f; return (t * 2f - 1f).coerceIn(-1f, 1f) }

    private fun tintFilter(color: Int) = android.graphics.LightingColorFilter(color and 0xFFFFFF, 0)

    override fun draw(c: Canvas, env: KitEnv, cfg: KitSceneConfig) {
        val hour = env.hour
        val night = nightness(hour); val day = 1f - night
        val height = sunHeight(hour); val side = sunSide(hour)
        // A cloud dims the light smoothly in and out as it passes.
        val cloud = if (cloudAt >= 0f) 1f - .62f * sin(PI.toFloat() * cloudAt).let { it * it } * day else 1f
        val rayColor = AQUA_RAYS.at(hour, 0)
        c.drawBitmap(water(hour), 0f, 0f, null)

        // The sun's (or by night the moon's) glow seen through the surface.
        src.set(0, 0, sunSprite.width, sunSprite.height)
        if (day > .02f) {
            val sx = w * (.5f + .44f * side); val r = m * (.38f + .42f * height)
            glowPaint.colorFilter = tintFilter(rayColor); glowPaint.alpha = (day * cloud * (105f + 35f * height)).toInt().coerceIn(0, 255)
            dst.set(sx - r, -r * .9f, sx + r, r * .55f); c.drawBitmap(sunSprite, src, dst, glowPaint)
        }
        if (night > .02f) {
            val mx = w * (.5f + .38f * moonSide(hour)); val r = m * .07f
            // The moon wobbles with the surface ripples, a bright smudge rather than a crisp disc.
            val wob = sin(time * 1.3f) * r * .18f
            glowPaint.colorFilter = tintFilter(0xFFDDE8FF.toInt()); glowPaint.alpha = (night * 235f).toInt()
            dst.set(mx - r * 1.3f + wob, m * .012f, mx + r * 1.3f - wob, m * .012f + r * 1.1f); c.drawBitmap(sunSprite, src, dst, glowPaint)
            glowPaint.alpha = (night * 70f).toInt(); dst.set(mx - r * 5f, -r * 3f, mx + r * 5f, r * 4f); c.drawBitmap(sunSprite, src, dst, glowPaint)
        }

        // Ripples of light: strong at noon, softer at the ends of the day, a faint silver shimmer under the moon.
        val causticStrength = (day * (.45f + .55f * height) * cloud + night * .16f).coerceIn(0f, 1f)
        val cKey = if (night > .5f) 1 else 0
        if (cKey != filterKey) { filterKey = cKey; causticPaint.colorFilter = if (cKey == 1) tintFilter(0xFFB8CCFF.toInt()) else null }
        val reach = h * (.62f + .18f * height)
        for (layer in 0..1) {
            val sc = m * (if (layer == 0) .0016f else .0023f)
            mat.reset(); mat.setScale(sc, sc); mat.postTranslate(time * m * (if (layer == 0) .012f else -.016f), time * m * (if (layer == 0) .008f else .01f))
            val sh = if (layer == 0) causticA else causticB
            sh.setLocalMatrix(mat); causticPaint.shader = sh
            val bands = 16
            for (i in 0 until bands) {
                val f = i / bands.toFloat(); causticPaint.alpha = (74f * causticStrength * (1f - f) * (1f - f) * (if (layer == 0) 1f else .75f)).toInt().coerceIn(0, 255)
                c.drawRect(0f, reach * f, w.toFloat(), reach * (i + 1) / bands + 1f, causticPaint)
            }
        }
        causticPaint.shader = null

        // Light shafts. They lean away from the sun (long and slanted when it is low, short and straight at noon), take its colour,
        // and are gone at night except for one pale moonbeam.
        src.set(0, 0, beamSprite.width, beamSprite.height)
        val lean = side * 30f
        val rays = (day * (.38f + .62f * kotlin.math.sqrt(height)) * cloud).coerceIn(0f, 1f)
        rayPaint.colorFilter = tintFilter(rayColor)
        if (rays > .01f) for (bm in beams) {
            val x = w * bm.x + sin(time * .07f * bm.sp + bm.ph) * w * .06f - side * w * .08f
            val a = (.4f + .6f * (sin(time * .22f * bm.sp + bm.ph) * .5f + .5f)) * rays * bm.str * (.3f + .08f * height)
            val bw = w * bm.wd * (1.7f - .5f * height)
            val len = h * (1.25f - .3f * height)
            c.save(); c.translate(x, -m * .03f); c.rotate(lean + sin(time * .1f * bm.sp + bm.ph * 1.7f) * 5f)
            dst.set(-bw / 2f, 0f, bw / 2f, len); rayPaint.alpha = (a * 255).toInt().coerceIn(0, 255)
            c.drawBitmap(beamSprite, src, dst, rayPaint); c.restore()
        }
        if (night > .5f) {
            val mx = w * (.5f + .38f * moonSide(hour))
            rayPaint.colorFilter = tintFilter(0xFFC8D8FF.toInt())
            val bw = w * .22f
            c.save(); c.translate(mx, 0f); c.rotate(moonSide(hour) * -18f + sin(time * .12f) * 3f)
            dst.set(-bw / 2f, 0f, bw / 2f, h * .9f); rayPaint.alpha = ((night - .5f) * 2f * 70f * (.75f + .25f * sin(time * .3f))).toInt().coerceIn(0, 255)
            c.drawBitmap(beamSprite, src, dst, rayPaint); c.restore()
        }

        // Drifting specks by day; at night the plankton glows teal and pulses.
        val mKey = if (night > .45f) 1 else 0
        if (mKey != moteKey) { moteKey = mKey; motePaint.colorFilter = if (mKey == 1) tintFilter(0xFF4DFFD6.toInt()) else tintFilter(0xFFE6F8FF.toInt()) }
        val sp = android.graphics.Rect(0, 0, moteSprite.width, moteSprite.height)
        for (s in motes) {
            val glow = sin(time * (1.3f - night * .7f) + s[3]) * .5f + .5f
            val r = s[2] * (3f + night * 2.2f * glow)
            motePaint.alpha = (((.3f + .7f * glow) * 140f * (day * .9f + .2f)) + night * 120f * glow * glow).toInt().coerceIn(0, 255)
            dst.set(s[0] - r, s[1] - r, s[0] + r, s[1] + r); c.drawBitmap(moteSprite, sp, dst, motePaint)
        }
        fishPaint.color = Color.argb((60 * (1f - night * .5f)).toInt(), 0, 20, 40)
        for (f in fish) {
            val d = fishDir[f] ?: 1
            c.save(); c.translate(f.x, f.y + sin(time * 2 + f.x * .01f) * f.size * .3f); c.scale(d.toFloat(), 1f)
            c.drawPath(Path().apply { moveTo(-f.size * 2, 0f); quadTo(0f, -f.size * 1.1f, f.size * 2f, 0f); quadTo(0f, f.size * 1.1f, -f.size * 2, 0f); close() }, fishPaint)
            c.drawPath(Path().apply { moveTo(-f.size * 1.8f, 0f); lineTo(-f.size * 3.1f, -f.size); lineTo(-f.size * 3.1f, f.size); close() }, fishPaint); c.restore()
        }
        field.draw(c)
        // A passing cloud also leaves a soft shadow on the water.
        if (cloud < .99f) { shade.color = Color.argb(((1f - cloud) * 70f).toInt(), 0, 10, 30); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade) }
    }
}

/** Water seen from below at each part of the day: surface, middle and deep colours. Every part of the day has its own look. */
private val AQUA_WATER = Keys(floatArrayOf(0f, 4.6f, 5.8f, 6.9f, 9f, 12.4f, 15.3f, 17.3f, 18.3f, 19.3f, 20.6f),
    arrayOf(
        intArrayOf(0xFF0B2552.toInt(), 0xFF06163A.toInt(), 0xFF01040F.toInt()),   // night: ink blue
        intArrayOf(0xFF1E2C66.toInt(), 0xFF111C4A.toInt(), 0xFF02061A.toInt()),   // before dawn
        intArrayOf(0xFFC97A9E.toInt(), 0xFF5A58A6.toInt(), 0xFF111F58.toInt()),   // dawn: rose
        intArrayOf(0xFFE0A66A.toInt(), 0xFF3F8ECB.toInt(), 0xFF0A3272.toInt()),   // sunrise: gold
        intArrayOf(0xFF5FD2D2.toInt(), 0xFF139FC4.toInt(), 0xFF06528E.toInt()),   // morning: turquoise
        intArrayOf(0xFF3FB0EE.toInt(), 0xFF0A78D6.toInt(), 0xFF032E88.toInt()),   // noon: azure
        intArrayOf(0xFF8ED3AE.toInt(), 0xFF2192A4.toInt(), 0xFF0B4076.toInt()),   // afternoon: warm, greener
        intArrayOf(0xFFE6B062.toInt(), 0xFF4A8AA6.toInt(), 0xFF133F72.toInt()),   // golden hour
        intArrayOf(0xFFFF8B5C.toInt(), 0xFFB2507E.toInt(), 0xFF24165C.toInt()),   // sunset: orange and magenta
        intArrayOf(0xFF7C4AAA.toInt(), 0xFF2F2C7A.toInt(), 0xFF0A0A36.toInt()),   // dusk: violet
        intArrayOf(0xFF27367C.toInt(), 0xFF121E52.toInt(), 0xFF03071E.toInt()),   // late dusk: indigo
    ))

/** The colour of the light shafts through the day. */
private val AQUA_RAYS = Keys(floatArrayOf(0f, 5.6f, 6.6f, 8.5f, 12.4f, 15.5f, 17.4f, 18.4f, 19.6f),
    arrayOf(intArrayOf(0xFFA8BCFF.toInt()), intArrayOf(0xFFFF9EB6.toInt()), intArrayOf(0xFFFFCB80.toInt()), intArrayOf(0xFFF0FFFA.toInt()), intArrayOf(0xFFFFFFFF.toInt()),
        intArrayOf(0xFFFFF0C2.toInt()), intArrayOf(0xFFFFB45E.toInt()), intArrayOf(0xFFFF7A52.toInt()), intArrayOf(0xFF9C80FF.toInt())))

/** A green hill under a sky that goes through sunrise, noon, sunset and a night of stars. Soft light and shadow drift across it. */
class KitBlissScene(w: Int, h: Int) : KitScene(w, h, 7) {
    private class Cloud(var x: Float, val y: Float, val size: Float, val speed: Float)
    private val clouds = List(6) { Cloud(rnd.nextFloat() * w, h * (.06f + rnd.nextFloat() * .3f), m * (.011f + rnd.nextFloat() * .016f), m * (.004f + rnd.nextFloat() * .009f)) }
    private val stars = List(150) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h * .55f, .5f + rnd.nextFloat() * 1.3f, rnd.nextFloat() * 6.28f) }
    private val cloudBmp: Bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888).also { b ->
        val cv = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r0 = Random(3)
        val puffs = List(16) { i -> val px = .16f + .68f * (i / 15f); val arch = sin(PI.toFloat() * (px - .16f) / .68f); Triple(px + (r0.nextFloat() - .5f) * .06f, .66f - arch * (.12f + r0.nextFloat() * .16f), 256f * (.1f + .07f * arch + r0.nextFloat() * .04f)) }
        for ((px, py, r) in puffs) { p.shader = RadialGradient(512 * px, 256 * (py + .06f), r * 1.1f, intArrayOf(0x88AEBFDC.toInt(), 0x00AEBFDC), null, Shader.TileMode.CLAMP); cv.drawCircle(512 * px, 256 * (py + .06f), r * 1.1f, p) }
        for ((px, py, r) in puffs) { p.shader = RadialGradient(512 * px, 256 * py, r, intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xE8FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, .55f, .8f, 1f), Shader.TileMode.CLAMP); cv.drawCircle(512 * px, 256 * py, r, p) }
    }
    private val cloudPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gust = glowSprite(256, 0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF)
    private val seedSprite = glowSprite(32, 0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF)
    private val ring = ringSprite(256)
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = ADD_MODE }
    private val horizon = h * .56f
    private var ground: Bitmap? = null
    private var groundKey = ""
    private val hillClip = Path().apply { moveTo(0f, h * .6f); cubicTo(w * .3f, h * .52f, w * .65f, h * .58f, w.toFloat(), h * .55f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
    private class Wave(val x: Float, val y: Float, var age: Float)
    private val waves = ArrayList<Wave>()
    private val seeds = ArrayList<FloatArray>()
    private var rainbow = 0f
    private val src = android.graphics.Rect(); private val dst = RectF()

    override fun step(dt: Float, env: KitEnv, cfg: KitSceneConfig) {
        super.step(dt, env, cfg)
        for (c in clouds) { c.x += c.speed * dt; if (c.x - c.size * 3.5f > w) c.x = -c.size * 3.5f }
        scheduleEvents(dt, cfg, nightness(env.hour) > .6f) { if (nightness(env.hour) < .4f) rainbow = 1f }
        if (rainbow > 0f) rainbow = (rainbow - dt / 40f).coerceAtLeast(0f)
        val wi = waves.iterator(); while (wi.hasNext()) { val x = wi.next(); x.age += dt; if (x.age > 2.4f) wi.remove() }
        val it = seeds.iterator(); while (it.hasNext()) { val s = it.next(); s[0] += s[2] * dt; s[1] += s[3] * dt; s[4] -= dt / 5f; if (s[4] <= 0f) it.remove() }
    }

    override fun tap(x: Float, y: Float) {
        waves.add(Wave(x, y, 0f))
        repeat(9) { seeds.add(floatArrayOf(x, y, m * (.01f + rnd.nextFloat() * .06f), -m * (.02f + rnd.nextFloat() * .07f), 1f)) }
    }

    private fun ground(hr: Float): Bitmap {
        val key = "%.1f".format(hr)
        if (ground == null || key != groundKey) {
            val b = ground ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(Color.TRANSPARENT)
            val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val far = GRASS.at(hr, 0); val near = GRASS.at(hr, 1); val sky = SKY.at(hr, 1)
            fun hill(y0: Float, c1: Float, c2: Float, y1: Float, top: Int, bottom: Int) {
                val path = Path().apply { moveTo(0f, y0); cubicTo(w * .3f, c1, w * .65f, c2, w.toFloat(), y1); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
                p.shader = LinearGradient(0f, minOf(y0, y1, c1, c2), 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP); c.drawPath(path, p)
            }
            hill(h * .60f, h * .52f, h * .58f, h * .55f, blend(far, sky, .45f), blend(far, sky, .25f))
            hill(h * .66f, h * .57f, h * .64f, h * .61f, blend(far, sky, .12f), near)
            hill(h * .75f, h * .62f, h * .66f, h * .70f, blend(far, 0xFFFFFFFF.toInt(), .08f), blend(near, 0xFF000000.toInt(), .25f))
            // a soft glow where the sun hits the hills, and a darker edge at the bottom
            val warm = (1f - abs(hr - 6.8f).coerceAtMost(1.5f) / 1.5f).coerceAtLeast(1f - abs(hr - 18.5f).coerceAtMost(1.7f) / 1.7f)
            p.shader = LinearGradient(0f, horizon, 0f, h.toFloat(), Color.argb((warm * 80).toInt(), 255, 170, 90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = LinearGradient(0f, h * .8f, 0f, h.toFloat(), Color.TRANSPARENT, 0x55000000, Shader.TileMode.CLAMP); c.drawRect(0f, h * .8f, w.toFloat(), h.toFloat(), p)
            // fine grain so the grass reads as grass, not paint
            p.shader = null; p.strokeWidth = 1.2f
            val r = Random(5)
            for (i in 0 until 2600) {
                val x = r.nextFloat() * w; val y = h * .56f + r.nextFloat().let { it * it } * (h * .44f)
                p.color = if (r.nextBoolean()) Color.argb(26, 255, 255, 220) else Color.argb(30, 0, 40, 0)
                c.drawLine(x, y, x + (r.nextFloat() - .5f) * 3f, y - 2f - r.nextFloat() * 7f * (y / h), p)
            }
            p.xfermode = null
            ground = b; groundKey = key
        }
        return ground!!
    }

    override fun draw(c: Canvas, env: KitEnv, cfg: KitSceneConfig) {
        val hr = env.hour; val night = nightness(hr)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h * .62f, SKY.at(hr, 0), SKY.at(hr, 1), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w.toFloat(), h * .63f, p); p.shader = null
        if (night > .15f) for (s in stars) { p.color = Color.argb((255 * night * (.5f + .5f * sin(time * 1.4f + s[3]))).toInt().coerceIn(0, 255), 255, 255, 255); c.drawCircle(s[0], s[1], s[2], p) }
        drawStreaks(c)
        fun arc(f: Float): Pair<Float, Float> = (w * (.08f + .84f * f)) to (horizon - sin(PI.toFloat() * f) * h * .46f + h * .02f)
        val sunF = (hr - 6f) / 12.7f
        if (sunF in -.05f..1.05f) {
            val (sx, sy) = arc(sunF)
            val low = 1f - sin(PI.toFloat() * sunF.coerceIn(0f, 1f))
            val gr = m * (.25f + .2f * low)
            dst.set(sx - gr, sy - gr, sx + gr, sy + gr); src.set(0, 0, gust.width, gust.height)
            addPaint.alpha = 190; addPaint.colorFilter = android.graphics.PorterDuffColorFilter(blend(0xFFFFF2B0.toInt(), 0xFFFF8A4B.toInt(), low), android.graphics.PorterDuff.Mode.MULTIPLY)
            c.drawBitmap(gust, src, dst, addPaint); addPaint.colorFilter = null
            p.color = blend(0xFFFFF8D8.toInt(), 0xFFFFC27A.toInt(), low); c.drawCircle(sx, sy, m * .032f, p)
        }
        val moonF = (((hr + 24f - 18f) % 24f)) / 12f
        if (moonF in 0f..1f) {
            val (mx, my) = arc(moonF)
            dst.set(mx - m * .16f, my - m * .16f, mx + m * .16f, my + m * .16f); addPaint.alpha = 110; c.drawBitmap(gust, src, dst, addPaint)
            p.color = 0xFFF2F5FF.toInt(); c.drawCircle(mx, my, m * .03f, p)
            p.color = blend(SKY.at(hr, 0), SKY.at(hr, 1), .3f); c.drawCircle(mx + m * .012f, my - m * .006f, m * .026f, p)
        }
        if (rainbow > 0f) {
            val a = (min(1f, rainbow * 3f) * min(1f, (1f - rainbow) * 6f + .3f) * 100).toInt()
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = m * .012f }
            for (i in 0 until 7) { rp.color = Color.HSVToColor(a, floatArrayOf(i * 42f, .75f, 1f)); c.drawArc(RectF(w * .05f + i * m * .011f, horizon - w * .7f + i * m * .011f, w * .95f - i * m * .011f, horizon + w * .6f - i * m * .011f), 180f, 180f, false, rp) }
        }
        val cloudCol = blend(blend(0xFFFFFFFF.toInt(), 0xFFFFC9A8.toInt(), (1f - abs(hr - 18.3f).coerceAtMost(1.6f) / 1.6f) * .8f), 0xFF3A4670.toInt(), night)
        cloudPaint.colorFilter = android.graphics.PorterDuffColorFilter(cloudCol, android.graphics.PorterDuff.Mode.MULTIPLY); cloudPaint.alpha = if (night > .5f) 130 else 245
        src.set(0, 0, cloudBmp.width, cloudBmp.height)
        for (cl in clouds) { dst.set(cl.x - cl.size * 5f, cl.y - cl.size * 2.5f, cl.x + cl.size * 5f, cl.y + cl.size * 2.5f); c.drawBitmap(cloudBmp, src, dst, cloudPaint) }
        c.drawBitmap(ground(hr), 0f, 0f, null)
        // light and shade rolling over the hills, like wind through grass
        c.save(); c.clipPath(hillClip)
        src.set(0, 0, gust.width, gust.height)
        val warmth = blend(0xFFFFFFFF.toInt(), 0xFFFFC890.toInt(), (1f - abs(hr - 18.5f).coerceAtMost(1.7f) / 1.7f).coerceAtLeast(0f))
        addPaint.colorFilter = android.graphics.PorterDuffColorFilter(warmth, android.graphics.PorterDuff.Mode.MULTIPLY)
        for (i in 0 until 4) {
            val f = ((time * (.018f + i * .006f) + i * .27f) % 1f)
            val x = -w * .4f + f * w * 1.8f; val y = h * (.64f + i * .08f) + sin(time * .3f + i) * h * .012f
            dst.set(x - w * .55f, y - h * .05f, x + w * .55f, y + h * .05f)
            addPaint.alpha = ((1f - night * .7f) * 64f * sin(PI.toFloat() * f)).toInt().coerceIn(0, 255); c.drawBitmap(gust, src, dst, addPaint)
        }
        addPaint.colorFilter = null
        for (wv in waves) {
            val f = wv.age / 2.4f; val r = f * m * .45f
            dst.set(wv.x - r, wv.y - r * .45f, wv.x + r, wv.y + r * .45f); src.set(0, 0, ring.width, ring.height)
            addPaint.alpha = (150 * (1f - f)).toInt().coerceIn(0, 255); c.drawBitmap(ring, src, dst, addPaint)
        }
        c.restore()
        src.set(0, 0, seedSprite.width, seedSprite.height)
        for (s in seeds) { val r = m * .01f; addPaint.alpha = (s[4] * 230).toInt().coerceIn(0, 255); dst.set(s[0] - r, s[1] - r, s[0] + r, s[1] + r); c.drawBitmap(seedSprite, src, dst, addPaint) }
    }
}

/** Space with slow glowing nebulae, a rainbow ribbon, twinkling stars and rings of light that keep spreading out. */
/** Super's "day": the space behind everything changes with the hour, so night, dawn, noon and sunset each look like themselves. */
private val SUPER_SPACE = Keys(floatArrayOf(0f, 4.8f, 6.2f, 8.8f, 12.4f, 15.6f, 17.9f, 19.2f, 20.8f),
    arrayOf(
        intArrayOf(0xFF010208.toInt(), 0xFF050820.toInt(), 0xFF0C0828.toInt()),   // night: almost black, a hint of indigo
        intArrayOf(0xFF06061C.toInt(), 0xFF140A34.toInt(), 0xFF2A0C3C.toInt()),   // before dawn
        intArrayOf(0xFF1E0A30.toInt(), 0xFF5A1848.toInt(), 0xFF8C3640.toInt()),   // dawn: plum to rose
        intArrayOf(0xFF04163A.toInt(), 0xFF0A3A60.toInt(), 0xFF16606C.toInt()),   // morning: teal
        intArrayOf(0xFF0A1658.toInt(), 0xFF22288A.toInt(), 0xFF3C2C9E.toInt()),   // noon: bright cobalt
        intArrayOf(0xFF120838.toInt(), 0xFF2E0E58.toInt(), 0xFF4E1268.toInt()),   // afternoon: violet
        intArrayOf(0xFF280820.toInt(), 0xFF6A142E.toInt(), 0xFFA4422A.toInt()),   // sunset: crimson to amber
        intArrayOf(0xFF0E0830.toInt(), 0xFF26104C.toInt(), 0xFF40104E.toInt()),   // dusk: purple
        intArrayOf(0xFF040418.toInt(), 0xFF0A0A2E.toInt(), 0xFF180A36.toInt()),   // late evening
    ))

/** The three colours of the gas clouds through the day. */
private val SUPER_GAS = Keys(floatArrayOf(0f, 5.4f, 6.6f, 9f, 12.4f, 15.6f, 18f, 19.4f),
    arrayOf(
        intArrayOf(0xFF5A6CFF.toInt(), 0xFF3A9CFF.toInt(), 0xFF8A5CFF.toInt()),   // night: cold blues
        intArrayOf(0xFFFF6AB4.toInt(), 0xFF9A6CFF.toInt(), 0xFFFF9A7A.toInt()),   // dawn: pink, lilac, peach
        intArrayOf(0xFFFFB46A.toInt(), 0xFFFF6A9A.toInt(), 0xFFFFE08A.toInt()),   // sunrise: gold and coral
        intArrayOf(0xFF4AF0D8.toInt(), 0xFF4AB4FF.toInt(), 0xFF9AFFB0.toInt()),   // morning: aqua and mint
        intArrayOf(0xFF6AA0FF.toInt(), 0xFFB46AFF.toInt(), 0xFF5AF0FF.toInt()),   // noon: electric blue and violet
        intArrayOf(0xFFC85AFF.toInt(), 0xFFFF5AC8.toInt(), 0xFF6A7AFF.toInt()),   // afternoon: magenta and violet
        intArrayOf(0xFFFF5A3A.toInt(), 0xFFFFA83A.toInt(), 0xFFFF3A8A.toInt()),   // sunset: red, amber, rose
        intArrayOf(0xFF9A4AFF.toInt(), 0xFFFF4A9A.toInt(), 0xFF4A5AFF.toInt()),   // dusk: purple and pink
    ))

class KitSuperScene(w: Int, h: Int) : KitScene(w, h, 21) {
    private class Star(val x: Float, val y: Float, val r: Float, val ph: Float, val depth: Float)
    private val stars = List(200) { Star(rnd.nextFloat() * w, rnd.nextFloat() * h, .6f + rnd.nextFloat() * 1.6f, rnd.nextFloat() * 6.28f, .3f + rnd.nextFloat() * .7f) }
    private class Blob(val x: Float, val y: Float, val r: Float, val hue: Float, val ph: Float, val sp: Float)
    private val blobs = List(7) { Blob(rnd.nextFloat() * w, rnd.nextFloat() * h, m * (.3f + rnd.nextFloat() * .3f), rnd.nextFloat() * 360f, rnd.nextFloat() * 6.28f, .6f + rnd.nextFloat() * .8f) }
    private val nebula = glowSprite(256, 0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF)
    private val star = glowSprite(48, 0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF)
    private val ring = ringSprite(512)
    private val ribbon: Bitmap = Bitmap.createBitmap(1024, 600, Bitmap.Config.ARGB_8888).also { b ->
        val cv = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 17f; maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL) }
        for (i in 0 until 7) { p.color = Color.HSVToColor(255, floatArrayOf(i * 51f, .8f, 1f)); cv.drawArc(RectF(60f + i * 15f, 60f + i * 15f, 964f - i * 15f, 1000f - i * 15f), 180f, 180f, false, p) }
    }
    private var base: Bitmap? = null
    private var baseKey = -1
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = ADD_MODE }
    private val plain = Paint(Paint.FILTER_BITMAP_FLAG)
    private class Ring(val x: Float, val y: Float, var age: Float, val hue: Float)
    private val rings = ArrayList<Ring>()
    private var flash = 0f
    private val src = android.graphics.Rect(); private val dst = RectF()

    override fun step(dt: Float, env: KitEnv, cfg: KitSceneConfig) {
        super.step(dt, env, cfg)
        scheduleEvents(dt, cfg, true) { rings.add(Ring(rnd.nextFloat() * w, rnd.nextFloat() * h, 0f, rnd.nextFloat() * 360f)); flash = .45f }
        val it = rings.iterator(); while (it.hasNext()) { val r = it.next(); r.age += dt; if (r.age > 5f) it.remove() }
        if (flash > 0) flash -= dt
    }

    override fun tap(x: Float, y: Float) { rings.add(Ring(x, y, 0f, (time * 40f) % 360f)) }

    private fun backdrop(hour: Float): Bitmap {
        // Redrawn every few minutes of scene time as the colours move on, not every frame.
        val key = (hour * 12f).toInt()
        if (base == null || key != baseKey) {
            val b = base ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b); val p = Paint()
            p.shader = LinearGradient(0f, 0f, w * .5f, h.toFloat(), intArrayOf(SUPER_SPACE.at(hour, 0), SUPER_SPACE.at(hour, 1), SUPER_SPACE.at(hour, 2)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            base = b; baseKey = key
        }
        return base!!
    }

    override fun draw(c: Canvas, env: KitEnv, cfg: KitSceneConfig) {
        val hueBase = (time * 5f + env.hour * 12f) % 360f
        val night = nightness(env.hour); val day = 1f - night
        val gasK = .5f + .55f * day; val starK = .6f + .4f * night
        c.drawBitmap(backdrop(env.hour), 0f, 0f, null)
        src.set(0, 0, nebula.width, nebula.height)
        for ((bi, b) in blobs.withIndex()) {
            val x = b.x + sin(time * .09f * b.sp + b.ph) * m * .1f; val y = b.y + cos(time * .07f * b.sp + b.ph) * m * .1f
            val r = b.r * (1f + .08f * sin(time * .2f + b.ph))
            // Each cloud takes one of the hour's three colours.
            addPaint.colorFilter = android.graphics.PorterDuffColorFilter(SUPER_GAS.at(env.hour, bi % 3), android.graphics.PorterDuff.Mode.MULTIPLY)
            addPaint.alpha = ((95 + 35 * sin(time * .3f + b.ph)) * gasK).toInt().coerceIn(0, 255); dst.set(x - r, y - r, x + r, y + r); c.drawBitmap(nebula, src, dst, addPaint)
        }
        addPaint.colorFilter = null
        // twinkling stars drifting slowly downward, nearer ones faster
        src.set(0, 0, star.width, star.height)
        for (s in stars) {
            val y = (s.y + time * m * .004f * s.depth) % h
            val tw = .4f + .6f * (sin(time * 1.8f * s.depth + s.ph) * .5f + .5f)
            val r = s.r * 2.6f; addPaint.alpha = (255 * tw * s.depth * starK).toInt().coerceIn(0, 255); dst.set(s.x - r, y - r, s.x + r, y + r); c.drawBitmap(star, src, dst, addPaint)
        }
        // rainbow ribbons that breathe
        src.set(0, 0, ribbon.width, ribbon.height)
        for (k in 0 until 2) {
            val cx = w * (if (k == 0) .1f else .9f); val cy = h * (if (k == 0) .74f else .26f); val sc = m * (.9f + .05f * sin(time * .4f + k)) / 1024f
            c.save(); c.translate(cx, cy); c.rotate((time * 2.5f + k * 180f) % 360f); c.scale(sc, sc); dst.set(-512f, -530f, 512f, 70f)
            addPaint.alpha = (70 + 30 * sin(time * .6f + k)).toInt(); c.drawBitmap(ribbon, src, dst, addPaint); c.restore()
        }
        // rings that spread out from the middle, and from taps
        src.set(0, 0, ring.width, ring.height)
        for (i in 0 until 6) {
            val f = ((time * .1f + i / 6f) % 1f); val r = f * m * .65f
            addPaint.colorFilter = android.graphics.PorterDuffColorFilter(hsv(255, hueBase + i * 50f, .75f, 1f), android.graphics.PorterDuff.Mode.MULTIPLY)
            addPaint.alpha = (170 * (1f - f) * min(1f, f * 6f)).toInt().coerceIn(0, 255); dst.set(w / 2f - r, h * .5f - r, w / 2f + r, h * .5f + r); c.drawBitmap(ring, src, dst, addPaint)
        }
        for (rg in rings) {
            val f = rg.age / 5f; val r = f * m * .5f
            addPaint.colorFilter = android.graphics.PorterDuffColorFilter(hsv(255, rg.hue + rg.age * 60f, .6f, 1f), android.graphics.PorterDuff.Mode.MULTIPLY)
            addPaint.alpha = (200 * (1f - f)).toInt().coerceIn(0, 255); dst.set(rg.x - r, rg.y - r, rg.x + r, rg.y + r); c.drawBitmap(ring, src, dst, addPaint)
        }
        addPaint.colorFilter = null
        drawStreaks(c)
        if (flash > 0f) { plain.color = Color.argb((flash * 80).toInt(), 255, 255, 255); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), plain) }
    }
}

/** Runs a scene for a configuration: follows the clock, applies the rules and the colour shift. */
class KitSceneRunner(private val w: Int, private val h: Int) {
    private val scenes = HashMap<String, KitScene>()
    private val env = KitEnv(12f, 100, false)
    private var clock = 0f
    private val hue = Paint()

    private fun scene(id: String) = scenes.getOrPut(id) { when (id) { "bliss" -> KitBlissScene(w, h); "super" -> KitSuperScene(w, h); else -> KitAquaScene(w, h) } }

    /** The configuration after the rules and the battery have had their say. */
    fun effective(cfg: KitSceneConfig): Pair<String, Int> {
        var base = cfg.base; var shift = cfg.hueShift
        for (r in cfg.rules) {
            val on = when (r.whenKind) {
                "hours" -> env.hour.toInt().let { hh -> if (r.a <= r.b) hh in r.a until r.b else hh >= r.a || hh < r.b }
                "charging" -> env.charging
                "battery" -> env.battery < r.a && !env.charging
                else -> false
            }
            if (on) { if (r.base.isNotEmpty()) base = r.base; shift += r.hue }
        }
        if (cfg.batteryTint && env.battery < 20 && !env.charging) shift += 150
        return base to shift
    }

    fun update(dt: Float, cfg: KitSceneConfig, battery: Int, charging: Boolean) {
        clock += dt
        env.battery = battery; env.charging = charging
        val now = java.time.LocalTime.now()
        val real = now.hour + now.minute / 60f + now.second / 3600f
        env.hour = if (cfg.fixedHour >= 0) cfg.fixedHour else if (cfg.daySpeed == 1) KitSun.sceneHour(real) else (real + clock * cfg.daySpeed / 3600f) % 24f
        scene(effective(cfg).first).step(dt, env, cfg)
    }

    fun draw(c: Canvas, cfg: KitSceneConfig) {
        val (base, shift) = effective(cfg)
        val s = scene(base)
        if (shift % 360 != 0) {
            val cm = ColorMatrix(); val a = Math.toRadians(shift.toDouble()); val cs = cos(a).toFloat(); val sn = sin(a).toFloat()
            cm.set(floatArrayOf(.213f + cs * .787f - sn * .213f, .715f - cs * .715f - sn * .715f, .072f - cs * .072f + sn * .928f, 0f, 0f,
                .213f - cs * .213f + sn * .143f, .715f + cs * .285f + sn * .140f, .072f - cs * .072f - sn * .283f, 0f, 0f,
                .213f - cs * .213f - sn * .787f, .715f - cs * .715f + sn * .715f, .072f + cs * .928f + sn * .072f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            hue.colorFilter = ColorMatrixColorFilter(cm)
            c.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), hue); s.draw(c, env, cfg); c.restore()
        } else s.draw(c, env, cfg)
        if (cfg.chargingGlow && env.charging) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, h.toFloat(), 0f, h * .8f, Color.argb(90 + (40 * sin(clock * 2f)).toInt(), 80, 255, 160), Color.TRANSPARENT, Shader.TileMode.CLAMP); c.drawRect(0f, h * .8f, w.toFloat(), h.toFloat(), p)
        }
    }

    fun tap(x: Float, y: Float, cfg: KitSceneConfig) { scene(effective(cfg).first).tap(x, y) }
    fun hour() = env.hour

    companion object {
        /** A still picture of a scene at some hour, for previews and when the live wallpaper is off. */
        fun still(cfg: KitSceneConfig, w: Int, h: Int, hour: Float? = null): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val r = KitSceneRunner(w, h)
            val c = cfg.copy(fixedHour = hour ?: if (cfg.fixedHour >= 0) cfg.fixedHour else java.time.LocalTime.now().let { it.hour + it.minute / 60f })
            repeat(6) { r.update(.5f, c, 100, false) }
            r.draw(Canvas(b), c)
            return b
        }
    }
}

/** Soft bubbles drifting up through the water. Sprites are drawn once and scaled, so a frame costs very little. */
class KitBubbleField(private val w: Int, private val h: Int, count: Int = 26, seed: Long = 11, private val popping: Boolean = false) {
    private class B(var x: Float, var y: Float, val r: Float, val speed: Float, val sway: Float, val phase: Float, val alpha: Int)
    private val rnd = Random(seed)
    private val m = max(w, h).toFloat()
    private val bubbles = List(count) { spawn(true) }
    private val sprite: Bitmap = makeSprite(192)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(0, 0, sprite.width, sprite.height)
    private val dst = RectF()
    private var t = 0f
    private class Pop(val x: Float, val y: Float, val r: Float) { var age = 0f; val drops = List(7) { floatArrayOf((it * 51.4f + 20f) * 0.01745f, .6f + (it % 3) * .25f) } }
    private val pops = ArrayList<Pop>()

    /** A tap: if it lands on a bubble, the bubble bursts into a ring and little drops, and a new one rises later. */
    fun popAt(px: Float, py: Float): Boolean {
        if (!popping) return false
        var best: B? = null; var bd = Float.MAX_VALUE
        for (b in bubbles) { val x = b.x + sin(t * .6f + b.phase) * b.sway; val d = (x - px) * (x - px) + (b.y - py) * (b.y - py); val reach = b.r + m0 * .03f; if (d < reach * reach && d < bd) { bd = d; best = b } }
        val b = best ?: return false
        pops.add(Pop(b.x + sin(t * .6f + b.phase) * b.sway, b.y, b.r))
        val n = spawn(false); b.x = n.x; b.y = n.y + h * .3f
        return true
    }
    private val m0 get() = max(w, h).toFloat()

    private fun spawn(anywhere: Boolean): B {
        val r = m * (.006f + rnd.nextFloat() * rnd.nextFloat() * .03f)
        return B(rnd.nextFloat() * w, if (anywhere) rnd.nextFloat() * h else h + r * 2 + rnd.nextFloat() * h * .2f, r,
            m * (.025f + rnd.nextFloat() * .05f) * (.6f + r / (m * .02f) * .5f), w * (.006f + rnd.nextFloat() * .018f), rnd.nextFloat() * 6.28f, 90 + rnd.nextInt(150))
    }

    private fun makeSprite(n: Int): Bitmap {
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG); val r = n / 2f
        // body: nearly clear in the middle, brighter toward the rim, like a real bubble
        p.shader = RadialGradient(r, r, r, intArrayOf(0x08FFFFFF, 0x14BFE9FF, 0x55E3F6FF, 0xCCFFFFFF.toInt()), floatArrayOf(0f, .6f, .88f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(r, r, r - 1f, p)
        // highlight crescent top left and a small reflection bottom right
        p.shader = null; p.color = 0xCCFFFFFF.toInt()
        c.drawArc(RectF(n * .16f, n * .16f, n * .62f, n * .62f), 190f, 70f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = n * .045f; color = 0xDDFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND })
        p.color = 0x66FFFFFF; c.drawCircle(n * .72f, n * .72f, n * .035f, p)
        return b
    }

    fun step(dt: Float) {
        t += dt
        val pi = pops.iterator(); while (pi.hasNext()) { val q = pi.next(); q.age += dt; if (q.age > .6f) pi.remove() }
        for (b in bubbles) {
            b.y -= b.speed * dt
            if (b.y < -b.r * 3) { val n = spawn(false); b.x = n.x; b.y = n.y }
        }
    }

    fun draw(c: Canvas) {
        for (b in bubbles) {
            val x = b.x + sin(t * .6f + b.phase) * b.sway
            dst.set(x - b.r, b.y - b.r, x + b.r, b.y + b.r)
            paint.alpha = b.alpha
            c.drawBitmap(sprite, src, dst, paint)
        }
        if (pops.isNotEmpty()) {
            val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            for (q in pops) {
                val f = q.age / .6f; val a = ((1f - f) * 230).toInt()
                pp.strokeWidth = max(2f, q.r * .12f * (1f - f)); pp.color = Color.argb(a, 235, 248, 255)
                c.drawCircle(q.x, q.y, q.r * (1f + f * 1.1f), pp)
                pp.style = Paint.Style.FILL
                for (d in q.drops) { val dist = q.r * (1f + f * 2.4f * d[1]); pp.color = Color.argb(a, 235, 248, 255); c.drawCircle(q.x + cos(d[0]) * dist, q.y + sin(d[0]) * dist + f * f * q.r, max(1.5f, q.r * .14f * (1f - f)), pp) }
                pp.style = Paint.Style.STROKE
            }
        }
    }

    /** One frame with the bubbles spread out, for previews and the still wallpaper. */
    fun drawStill(c: Canvas) { step(.001f); draw(c) }
}
