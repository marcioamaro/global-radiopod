package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object PodcastLibraryBackup {
    private val arrays = listOf("episode_bookmarks", "podcast_subscriptions_json", "podcast_recents_json")
    fun export(context: Context): JSONObject {
        val prefs = context.getSharedPreferences("podcast_preferences", Context.MODE_PRIVATE)
        return JSONObject().apply {
            arrays.forEach { put(it, JSONArray(prefs.getString(it, "[]"))) }
            put("positions", JSONObject().apply { prefs.all.forEach { (key, value) ->
                if (key.startsWith("pos_") && value is Long) put(key, value)
            } })
        }
    }
    fun validate(data: JSONObject) {
        arrays.filter(data::has).forEach { key ->
            val list = data.getJSONArray(key)
            require(list.length() <= 2000)
            for (i in 0 until list.length()) {
                val entry = list.getJSONObject(i)
                require(entry.getString("id").isNotBlank())
                if (key == "episode_bookmarks") {
                    require(entry.getLong("positionMs") >= 0 && entry.getString("note").length <= 2000)
                    val episode = entry.getJSONObject("episode")
                    require(episode.getString("id").isNotBlank())
                    requireNotNull(com.squareup.moshi.Moshi.Builder().build().adapter(com.example.data.model.PodcastEpisode::class.java).fromJson(episode.toString()))
                } else {
                    require(entry.getString("title").isNotBlank())
                    val uri = java.net.URI(entry.getString(if (key == "podcast_subscriptions_json") "feedUrl" else "audioUrl"))
                    require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
                }
            }
        }
        data.optJSONObject("positions")?.let { positions -> positions.keys().forEach {
            require(it.startsWith("pos_") && positions.getLong(it) >= 0)
        } }
    }
    fun restore(context: Context, data: JSONObject) {
        validate(data)
        val prefs = context.getSharedPreferences("podcast_preferences", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        arrays.filter(data::has).forEach { key ->
            val imported = data.getJSONArray(key)
            val existing = JSONArray(prefs.getString(key, "[]"))
            val merged = linkedMapOf<String, JSONObject>()
            for (i in 0 until imported.length()) imported.getJSONObject(i).let { merged[it.getString("id")] = it }
            for (i in 0 until existing.length()) existing.getJSONObject(i).let { merged.putIfAbsent(it.getString("id"), it) }
            edit.putString(key, JSONArray(merged.values.toList().take(if (key == "podcast_recents_json") 40 else 2000)).toString())
        }
        data.optJSONObject("positions")?.let { positions -> positions.keys().forEach { edit.putLong(it, positions.getLong(it)) } }
        check(edit.commit())
        com.example.data.repository.PodcastRepository.getInstance(context).reloadFromStorage()
    }
}
