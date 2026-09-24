package com.example.data.repository

import android.content.Context
import com.example.data.model.PodcastShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PodcastRankingRepository private constructor(context: Context) {
    private val podcasts = PodcastRepository.getInstance(context)
    private suspend fun get(key: String, limit: Int): List<PodcastShow> = withContext(Dispatchers.Default) {
        val favorites = podcasts.favoritesFlow.value.map { it.id }.toSet()
        PublishedRankings.podcasts(key, limit).map { it.copy(isFavorite = it.id in favorites) }
    }
    suspend fun getTopPodcastsBrazil(limit: Int = 20) = get("podcast_brazil", limit)
    suspend fun getTopPodcastsWorld(limit: Int = 20) = get("podcast_world", limit)
    companion object {
        @Volatile private var instance: PodcastRankingRepository? = null
        fun getInstance(context: Context): PodcastRankingRepository = instance ?: synchronized(this) {
            instance ?: PodcastRankingRepository(context.applicationContext).also { instance = it }
        }
    }
}
