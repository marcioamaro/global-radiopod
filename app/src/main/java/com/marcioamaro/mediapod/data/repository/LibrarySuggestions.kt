package com.marcioamaro.mediapod.data.repository

import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.model.PodcastShow

object LibrarySuggestions {
    enum class Mode { UNPLAYED, DOWNLOADED, RECENT_FAVORITES, SHORT }
    fun playlist(episodes: List<PodcastEpisode>, mode: Mode, played: Set<String>, downloaded: Set<String>,
                 favoriteShows: Set<String>, maxMinutes: Int, limit: Int): List<PodcastEpisode> =
        episodes.distinctBy { it.id }.filter {
            when (mode) {
                Mode.UNPLAYED -> it.id !in played
                Mode.DOWNLOADED -> it.id in downloaded
                Mode.RECENT_FAVORITES -> it.showId in favoriteShows
                Mode.SHORT -> it.durationMs in 1..(maxMinutes.coerceIn(5, 120) * 60000L)
            }
        }.take(limit.coerceIn(1, 100))

    fun discover(catalog: List<PodcastShow>, enabled: Boolean, interests: String,
                 history: List<PodcastShow>, useHistory: Boolean, excluded: Set<String>): List<PodcastShow> {
        if (!enabled) return emptyList()
        val words = interests.lowercase().split(',', ';').map { it.trim() }.filter { it.length >= 2 }.take(20)
        val categories = if (useHistory) history.map { it.category.lowercase() }.toSet() else emptySet()
        return catalog.filter { it.id !in excluded }.map { show ->
            val text = "${show.title} ${show.category} ${show.description}".lowercase()
            show to (words.count { it in text } * 2 + if (show.category.lowercase() in categories) 1 else 0)
        }.filter { it.second > 0 }.sortedWith(compareByDescending<Pair<PodcastShow, Int>> { it.second }.thenBy { it.first.title })
            .take(20).map { it.first }
    }
}
