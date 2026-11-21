package dev.wonderdesk.shared

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

/**
 * Real sunrise and sunset for where the phone is, so the animated wallpapers change colour when the sun really rises and sets.
 * Uses the last position Android already knows (coarse location, never tracked); without it, the time zone gives a rough position.
 */
object KitSun {
    private const val PREFS = "sun"
    @Volatile private var lat = Double.NaN
    @Volatile private var lon = Double.NaN
    @Volatile private var follow = true
    @Volatile private var loaded = false

    fun load(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        follow = p.getBoolean("follow", true)
        if (p.contains("lat")) { lat = p.getFloat("lat", 0f).toDouble(); lon = p.getFloat("lon", 0f).toDouble() }
        loaded = true
    }

    fun following(c: Context): Boolean { if (!loaded) load(c); return follow }
    fun setFollowing(c: Context, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("follow", on).apply(); follow = on; loaded = true }
    fun hasLocationPermission(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasPosition(c: Context): Boolean { if (!loaded) load(c); return !lat.isNaN() }

    /** Reads the last known position, if allowed, and keeps it. Cheap; call when the launcher comes to the front. */
    fun refresh(c: Context) {
        if (!loaded) load(c)
        if (!hasLocationPermission(c)) return
        runCatching {
            val lm = c.getSystemService(LocationManager::class.java)
            val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time } ?: return
            lat = best.latitude; lon = best.longitude
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()
        }
    }

    /** Sunrise and sunset as local hours (6.8 = 06:48) for a day, or null when the sun doesn't rise or set. */
    fun times(date: LocalDate = LocalDate.now()): Pair<Float, Float>? {
        val zone = ZoneId.systemDefault()
        val offsetH = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 3600.0
        val la = if (lat.isNaN()) 40.0 else lat
        val lo = if (lon.isNaN()) offsetH * 15.0 else lon
        val n = date.dayOfYear
        val b = 2 * PI * (n - 1) / 365.0
        val decl = 0.006918 - 0.399912 * cos(b) + 0.070257 * sin(b) - 0.006758 * cos(2 * b) + 0.000907 * sin(2 * b) - 0.002697 * cos(3 * b) + 0.00148 * sin(3 * b)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(b) - 0.032077 * sin(b) - 0.014615 * cos(2 * b) - 0.040849 * sin(2 * b))
        val phi = Math.toRadians(la)
        val cosH = (cos(Math.toRadians(90.833)) - sin(phi) * sin(decl)) / (cos(phi) * cos(decl))
        if (cosH < -1 || cosH > 1) return null
        val h = Math.toDegrees(acos(cosH))
        val noonMin = 720 - 4 * lo - eq + offsetH * 60
        return ((noonMin - 4 * h) / 60.0).toFloat().mod(24f) to ((noonMin + 4 * h) / 60.0).toFloat().mod(24f)
    }

    /**
     * The scenes are painted for a fixed day (sunrise around 6:30, sunset around 18:20). This stretches the real clock so the
     * scene's sunrise and sunset line up with the real ones.
     */
    fun sceneHour(real: Float): Float {
        if (!follow) return real
        val (sr, ss) = times() ?: return real
        if (ss <= sr) return real
        val srS = 6.5f; val ssS = 18.3f
        return if (real in sr..ss) srS + (real - sr) / (ss - sr) * (ssS - srS)
        else {
            val t = if (real > ss) real - ss else real + 24f - ss
            val len = 24f - (ss - sr)
            (ssS + t / len * (srS + 24f - ssS)) % 24f
        }
    }

    fun label(t: Float) = "%d:%02d".format(t.toInt(), ((t - t.toInt()) * 60).roundToInt().coerceAtMost(59))
}
