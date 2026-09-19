package com.example.data.repository

import android.content.Context
import com.example.data.model.PodcastShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositório dedicado a rankings oficiais de Podcasts (Top Brasil e Top Mundial),
 * ordenando programas mais ouvidos com fallback robusto no catálogo local curado.
 */
class PodcastRankingRepository private constructor(
    private val context: Context,
    private val podcastRepository: PodcastRepository = PodcastRepository.getInstance(context)
) {
    suspend fun getTopPodcastsBrazil(limit: Int = 20): List<PodcastShow> = withContext(Dispatchers.IO) {
        val curated = podcastRepository.getCuratedShows()
        val brazilShows = curated.filter { it.country.equals("BR", ignoreCase = true) }
        if (brazilShows.isNotEmpty()) {
            brazilShows.take(limit)
        } else {
            curated.take(limit)
        }
    }

    suspend fun getTopPodcastsWorld(limit: Int = 20): List<PodcastShow> = withContext(Dispatchers.IO) {
        val curated = podcastRepository.getCuratedShows()
        curated.take(limit)
    }

    companion object {
        @Volatile
        private var INSTANCE: PodcastRankingRepository? = null

        fun getInstance(context: Context): PodcastRankingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PodcastRankingRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
