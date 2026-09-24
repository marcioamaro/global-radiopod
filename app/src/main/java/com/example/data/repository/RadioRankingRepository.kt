package com.example.data.repository

import com.example.data.model.RadioStation

/** Popularity snapshots, independent of arbitrary catalog vote values. */
class RadioRankingRepository {
    suspend fun getTopWorld(limit: Int = 20): List<RadioStation> = PublishedRankings.radios("radio_world", limit)
    suspend fun getTopBrazil(limit: Int = 20): List<RadioStation> = PublishedRankings.radios("radio_brazil", limit)
    companion object {
        private val instance = RadioRankingRepository()
        fun getInstance() = instance
    }
}
