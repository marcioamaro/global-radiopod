package com.example.util

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Only fixed event codes are accepted. URLs, titles, paths and exception text never enter this report. */
object Diagnostics {
    enum class Event { PLAYBACK_FAILED, DOWNLOAD_FAILED, BACKUP_REJECTED, CATALOG_REJECTED }
    @Synchronized fun record(context: Context, event: Event) {
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        for (i in (current.length() - 49).coerceAtLeast(0) until current.length()) next.put(current.get(i))
        next.put(JSONObject().put("event", event.name).put("time", System.currentTimeMillis()))
        prefs.edit().putString("events", next.toString()).apply()
    }

    fun report(context: Context): String {
        val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        val events = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrDefault(JSONArray())
        val safe = JSONArray()
        for (i in 0 until events.length()) {
            val item = events.optJSONObject(i) ?: continue
            val event = runCatching { Event.valueOf(item.getString("event")) }.getOrNull() ?: continue
            safe.put(JSONObject().put("event", event.name).put("time", item.optLong("time")))
        }
        return JSONObject().put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
            .put("androidApi", Build.VERSION.SDK_INT).put("events", safe).toString(2)
    }

    fun clear(context: Context) { context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit().clear().apply() }
}
