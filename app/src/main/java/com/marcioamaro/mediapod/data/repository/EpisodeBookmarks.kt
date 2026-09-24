package com.marcioamaro.mediapod.data.repository

import android.content.Context
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class EpisodeBookmark(val id: String, val episode: PodcastEpisode, val positionMs: Long, val note: String)

class EpisodeBookmarks(context: Context) {
    private val prefs = context.getSharedPreferences("podcast_preferences", Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder().build().adapter(PodcastEpisode::class.java)
    private val _items = MutableStateFlow(read())
    val items = _items.asStateFlow()

    private fun read(): List<EpisodeBookmark> = runCatching {
        val array = JSONArray(prefs.getString("episode_bookmarks", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            EpisodeBookmark(item.getString("id"), requireNotNull(adapter.fromJson(item.getJSONObject("episode").toString())),
                item.getLong("positionMs"), item.getString("note"))
        }
    }.getOrDefault(emptyList())

    fun reload() { _items.value = read() }
    fun save(episode: PodcastEpisode, position: Long, note: String, id: String = UUID.randomUUID().toString()) {
        require(position >= 0 && note.length <= 2000)
        val next = listOf(EpisodeBookmark(id, episode, position, note.trim())) + _items.value.filterNot { it.id == id }
        require(next.size <= 2000)
        persist(next)
    }
    fun delete(id: String) = persist(_items.value.filterNot { it.id == id })
    private fun persist(items: List<EpisodeBookmark>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("episode", JSONObject(adapter.toJson(it.episode)))
            .put("positionMs", it.positionMs).put("note", it.note)) }
        check(prefs.edit().putString("episode_bookmarks", array.toString()).commit())
        _items.value = items
    }
}
