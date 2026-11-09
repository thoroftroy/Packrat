package dev.wonderdesk.shared

import android.content.Context
import org.json.JSONObject

/** Generated assets are copied directly from the repository's one central catalog. */
object SharedCatalog {
    fun aliases(context: Context): Map<String, List<String>> {
        val source = context.assets.open("wonderdesk/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val apps = source.getJSONArray("apps")
        return (0 until apps.length()).associate { index ->
            val app = apps.getJSONObject(index)
            val terms = mutableListOf(app.getString("name"), app.optString("generic"))
            for (key in listOf("aliases", "keywords")) {
                val array = app.optJSONArray(key)
                if (array != null) for (i in 0 until array.length()) terms += array.getString(i)
            }
            app.getString("id").replace("-", "") to terms.filter { it.isNotBlank() }.distinct()
        }
    }

    fun ecosystemVersion(context: Context): String = runCatching {
        context.assets.open("wonderdesk/versions.json").bufferedReader().use { JSONObject(it.readText()).getString("ecosystem") }
    }.getOrDefault("unavailable")
}
