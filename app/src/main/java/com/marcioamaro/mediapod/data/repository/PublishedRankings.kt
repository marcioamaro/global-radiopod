package com.marcioamaro.mediapod.data.repository

import android.content.Context
import com.marcioamaro.mediapod.data.model.PodcastShow
import com.marcioamaro.mediapod.data.model.RadioStation
import org.json.JSONObject

data class RankingInfo(val source: String, val sourceUrl: String, val period: String, val note: String, val available: Boolean)

/** Bundled rankings retain published popularity order and work offline. */
object PublishedRankings {
    private var snapshots = JSONObject()

    @Synchronized fun initialize(context: Context) {
        snapshots = try {
            JSONObject(CatalogUpdates.open(context, "media_rankings.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) { JSONObject() }
    }

    fun info(key: String): RankingInfo {
        val data = snapshots.optJSONObject(key) ?: JSONObject()
        return RankingInfo(data.optString("source"), data.optString("sourceUrl"),
            data.optString("period", "Popularidade"), data.optString("note"),
            (data.optJSONArray("entries")?.length() ?: 0) > 0)
    }

    fun podcasts(key: String, limit: Int): List<PodcastShow> {
        val entries = snapshots.optJSONObject(key)?.optJSONArray("entries") ?: return emptyList()
        return (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            val show = entry.optJSONObject("show") ?: JSONObject()
            PodcastShow(
                id = show.optString("id", "ranked_podcast_${entry.getInt("rank")}"),
                title = entry.getString("title"), author = show.optString("author"),
                description = show.optString("description"), feedUrl = show.optString("feedUrl"),
                artworkUrl = show.optString("artworkUrl"), country = show.optString("country", "GLOBAL"),
                category = show.optString("category", "Geral"), episodeCount = show.optInt("episodeCount"),
                rankPosition = entry.getInt("rank")
            )
        }.sortedBy { it.rankPosition }.take(limit.coerceIn(0, 20))
    }

    fun radios(key: String, limit: Int): List<RadioStation> {
        val entries = snapshots.optJSONObject(key)?.optJSONArray("entries") ?: return emptyList()
        return (0 until entries.length()).map { entries.getJSONObject(it) }
            .sortedBy { it.getInt("rank") }.take(limit.coerceIn(0, 20)).map { entry ->
                val station = entry.getJSONObject("station")
                val alternatives = station.optJSONArray("alternative_stream_urls")
                val tagsRaw = station.opt("tags")
                val parsedTags = when (tagsRaw) {
                    is org.json.JSONArray -> (0 until tagsRaw.length())
                        .map { tagsRaw.optString(it).replace("\"", "").replace("[", "").replace("]", "").trim() }
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    is String -> tagsRaw.replace("\"", "").replace("[", "").replace("]", "").trim()
                    else -> ""
                }
                RadioStation(
                    id = station.getString("id"), name = entry.getString("title"),
                    streamUrl = station.getString("primary_stream_url"),
                    alternativeStreamUrls = if (alternatives == null) emptyList() else
                        (0 until alternatives.length()).map { alternatives.getString(it) },
                    favicon = station.optString("favicon_url", station.optString("favicon")),
                    homepage = station.optString("homepage"), tags = parsedTags,
                    country = station.optString("country"), countryCode = station.optString("country_code"),
                    state = station.optString("state"), city = station.optString("city"),
                    codec = station.optString("codec"), bitrate = station.optInt("bitrate_kbps"),
                    rankPosition = entry.getInt("rank")
                )
            }
    }
}
