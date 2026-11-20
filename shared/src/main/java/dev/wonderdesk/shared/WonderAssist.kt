package dev.wonderdesk.shared

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Cards for the Wonderdesk Launcher's floating button. Where an app would show a notification that is really a control (an alarm
 * ringing, a download running, a backup finishing, an update waiting), it can also post a card: a title, a line of text, an optional
 * progress bar and a few buttons. The floating button shows the cards at the top of its menu, so the same controls are one tap away
 * without pulling down the shade.
 *
 * What an app shows is the person's choice, made in Control Room (Wonderdesk tab): as a notification, as a card, or both. Apps ask with
 * [wantsNotification] and [wantsButton], or just use [notify] and [post], which ask for them. Things that must always be seen, like a
 * ringing alarm, keep their notification whatever the setting says.
 */
object WonderAssist {
    const val LAUNCHER = "dev.wonderdesk.launcher"
    const val CARD = "dev.wonderdesk.assist.CARD"
    const val CLEAR = "dev.wonderdesk.assist.CLEAR"
    private val MODES = Uri.parse("content://dev.wonderdesk.controlroom.assist/modes")

    @Volatile private var cache: Map<String, Pair<Boolean, Boolean>> = emptyMap()
    @Volatile private var cacheAt = 0L

    /** (shows as notification, shows as card) for an app key such as "timepiece"; both on unless Control Room says otherwise. */
    fun modes(c: Context, key: String): Pair<Boolean, Boolean> {
        if (System.currentTimeMillis() - cacheAt > 15_000) {
            cacheAt = System.currentTimeMillis()
            cache = runCatching {
                buildMap { c.contentResolver.query(MODES, null, null, null, null)?.use { cur -> while (cur.moveToNext()) put(cur.getString(0), (cur.getInt(1) == 1) to (cur.getInt(2) == 1)) } }
            }.getOrDefault(cache)
        }
        return cache[key] ?: (true to true)
    }
    fun wantsNotification(c: Context, key: String) = modes(c, key).first
    fun wantsButton(c: Context, key: String) = modes(c, key).second

    /** Posts [n] unless this app has been set to show cards only. When it has, any copy already showing is taken away. */
    fun notify(c: Context, key: String, id: Int, n: Notification) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (wantsNotification(c, key)) nm.notify(id, n) else nm.cancel(id)
    }

    /** Shows or updates a card. [id] is yours: posting the same id again replaces the card. A negative [progress] means no bar. */
    fun post(c: Context, key: String, id: String, title: String, text: String? = null, actions: List<Pair<String, PendingIntent>> = emptyList(),
             open: PendingIntent? = null, ongoing: Boolean = false, progress: Int = -1, expireMs: Long = 0L) {
        if (!wantsButton(c, key)) { clear(c, key, id); return }
        runCatching {
            c.sendBroadcast(Intent(CARD).setPackage(LAUNCHER).putExtra("pkg", c.packageName).putExtra("key", key).putExtra("id", id).putExtra("title", title).putExtra("text", text)
                .putExtra("progress", progress).putExtra("ongoing", ongoing).putExtra("expire", expireMs).putExtra("open", open)
                .putExtra("labels", actions.map { it.first }.toTypedArray()).putParcelableArrayListExtra("actions", ArrayList(actions.map { it.second })))
        }
    }

    fun clear(c: Context, key: String, id: String) {
        runCatching { c.sendBroadcast(Intent(CLEAR).setPackage(LAUNCHER).putExtra("pkg", c.packageName).putExtra("key", key).putExtra("id", id)) }
    }
}
