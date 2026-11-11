package dev.wonderdesk.shared

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.File

/**
 * Crash catcher for every Wonderdesk app. Starts with the process (as a tiny content provider),
 * records uncaught exceptions, and on the next start also collects freezes (ANRs) and native crashes
 * Android saw. Reports go to Control Room, which keeps a list you can read and share.
 */
class WonderCrashInit : ContentProvider() {
    override fun onCreate(): Boolean {
        val c = context ?: return true
        WonderCrash.install(c.applicationContext)
        Thread { runCatching { WonderCrash.flush(c.applicationContext) } }.start()
        return true
    }
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

object WonderCrash {
    val REPORT_URI: Uri = Uri.parse("content://dev.wonderdesk.controlroom.crashes/report")

    private fun dir(c: Context) = File(c.filesDir, "crashes").apply { mkdirs() }

    fun install(c: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val r = record(c, "crash", e.javaClass.name + ": " + (e.message ?: ""), "Thread: ${t.name}\n" + e.stackTraceToString())
                File(dir(c), "${r.optLong("time")}.json").writeText(r.toString())
                c.getSharedPreferences("wondercrash", Context.MODE_PRIVATE).edit().putString("prompt", r.optString("summary")).commit()
                send(c, r)
            }
            previous?.uncaughtException(t, e)
        }
    }

    private fun record(c: Context, kind: String, summary: String, detail: String) = JSONObject()
        .put("pkg", c.packageName).put("kind", kind).put("time", System.currentTimeMillis()).put("summary", summary.take(400)).put("detail", detail.take(60_000))
        .put("version", runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrDefault("?"))
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

    /** True when Control Room sends reports by itself, false when it asks first, null when it isn't installed. */
    fun status(c: Context): Boolean? = call(c, "status", null)?.getBoolean("auto")
    fun call(c: Context, method: String, arg: String?): android.os.Bundle? = runCatching { c.contentResolver.call(REPORT_URI, method, arg, null) }.getOrNull()

    private fun send(c: Context, r: JSONObject): Boolean = runCatching {
        c.contentResolver.insert(REPORT_URI, ContentValues().apply { put("json", r.toString()) }) != null
    }.getOrDefault(false)

    /** Sends crashes saved earlier, plus freezes and native crashes Android recorded since last time. */
    fun flush(c: Context) {
        dir(c).listFiles()?.forEach { f -> runCatching { if (send(c, JSONObject(f.readText()))) f.delete() } }
        if (Build.VERSION.SDK_INT < 30) return
        val prefs = c.getSharedPreferences("wondercrash", Context.MODE_PRIVATE)
        val since = prefs.getLong("exitSeen", 0L)
        var newest = since
        val am = c.getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(null, 0, 10).filter { it.timestamp > since }.forEach { info ->
            newest = maxOf(newest, info.timestamp)
            val kind = when (info.reason) { ApplicationExitInfo.REASON_ANR -> "freeze"; ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"; else -> return@forEach }
            val trace = runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            val r = record(c, kind, info.description ?: kind, trace.take(60_000)).put("time", info.timestamp)
            if (!send(c, r)) File(dir(c), "${info.timestamp}.json").writeText(r.toString())
        }
        prefs.edit().putLong("exitSeen", newest).apply()
    }
}
