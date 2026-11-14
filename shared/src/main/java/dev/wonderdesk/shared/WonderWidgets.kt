package dev.wonderdesk.shared

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews

/**
 * The ids of an app's copy of the shared widget card layouts (widget_card.xml / widget_card_small.xml):
 * an optional picture, a small label, a title, a line of detail, an optional progress bar and one action button.
 */
class CardIds(val full: Int, val small: Int, val root: Int, val img: Int, val label: Int, val title: Int, val text: Int, val bar: Int, val btn: Int, val bgs: Triple<Int, Int, Int>)

/** What a card widget shows. [progress] 0..100 or null to hide; [button] null hides the action. */
data class Card(val label: String, val title: String, val text: String, val progress: Int? = null, val button: String? = null, val image: Bitmap? = null,
                val open: PendingIntent? = null, val action: PendingIntent? = null)

/** Builds a card widget that fits any size, in the widget's chosen [look]. */
fun cardWidget(c: Context, ids: CardIds, look: WidgetLook, card: Card): RemoteViews = responsiveWidget(ids.small, null, ids.full) { layout ->
    RemoteViews(c.packageName, layout).apply {
        applyLook(look, ids.root, ids.bgs, listOf(ids.title), listOf(ids.label, ids.text))
        setTextViewText(ids.label, card.label); setTextViewText(ids.title, card.title); setTextViewText(ids.text, card.text)
        setViewVisibility(ids.text, if (card.text.isBlank()) View.GONE else View.VISIBLE)
        if (card.progress != null) { setViewVisibility(ids.bar, View.VISIBLE); setProgressBar(ids.bar, 100, card.progress.coerceIn(0, 100), false) } else setViewVisibility(ids.bar, View.GONE)
        if (card.image != null) { setViewVisibility(ids.img, View.VISIBLE); setImageViewBitmap(ids.img, card.image) } else setViewVisibility(ids.img, View.GONE)
        if (layout == ids.full && card.button != null) { setViewVisibility(ids.btn, View.VISIBLE); setTextViewText(ids.btn, card.button); card.action?.let { setOnClickPendingIntent(ids.btn, it) } }
        else if (layout == ids.full) setViewVisibility(ids.btn, View.GONE)
        card.open?.let { setOnClickPendingIntent(ids.root, it) }
    }
}

/** A PendingIntent that opens [cls] (an activity) with [extras]; [code] keeps widgets' intents apart. */
fun widgetOpen(c: Context, cls: Class<*>, code: Int, build: (Intent) -> Unit = {}): PendingIntent =
    PendingIntent.getActivity(c, code, Intent(c, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).also(build), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** A PendingIntent that sends [action] to the widget provider [cls]. */
fun widgetBroadcast(c: Context, cls: Class<*>, code: Int, action: String, build: (Intent) -> Unit = {}): PendingIntent =
    PendingIntent.getBroadcast(c, code, Intent(c, cls).setAction(action).also(build), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Redraws every instance of a widget provider (call after the data behind it changes). */
fun refreshWidgets(c: Context, cls: Class<out android.appwidget.AppWidgetProvider>) {
    runCatching {
        val m = android.appwidget.AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(android.content.ComponentName(c, cls))
        if (ids.isNotEmpty()) c.sendBroadcast(Intent(c, cls).setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
    }
}

/** Sent by Control Room to every Wonderdesk app when the suite look changes. */
const val WONDER_APPEARANCE_CHANGED = "dev.wonderdesk.APPEARANCE_CHANGED"

/** Redraws every widget this app has on the home screen when the suite look changes (and after updates). */
class AppearanceReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        GlobalAppearance.cached = null
        val m = android.appwidget.AppWidgetManager.getInstance(c)
        runCatching {
            m.getInstalledProvidersForPackage(c.packageName, null).forEach { info ->
                val ids = m.getAppWidgetIds(info.provider)
                if (ids.isNotEmpty()) c.sendBroadcast(Intent().setComponent(info.provider).setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}
