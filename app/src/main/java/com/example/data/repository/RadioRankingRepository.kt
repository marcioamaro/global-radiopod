package com.example.data.repository

import com.example.data.api.RadioApiClient
import com.example.data.model.RadioStation
import com.example.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Repositório dedicado a rankings oficiais de emissoras de rádio
 * (Top Brasil e Top Mundial), integrando dados em tempo real da
 * Radio Browser API (ordenados por votes e clickcount) com fallback
 * offline instantâneo no catálogo local de 7.548 estações.
 */
class RadioRankingRepository(
    private val apiClient: RadioApiClient = RadioApiClient
) {
    suspend fun getTopWorld(limit: Int = 100): List<RadioStation> = withContext(Dispatchers.IO) {
        val onlineList = try {
            withTimeoutOrNull(6000L) {
                val api = apiClient.getService()
                val dtos = api.getTopVotedStations(limit = limit, hideBroken = true)
                dtos.mapNotNull { dto ->
                    val url = dto.urlResolved?.ifBlank { null } ?: dto.url
                    if (!url.isNullOrBlank() && !dto.name.isNullOrBlank()) {
                        dto.toDomain()
                    } else null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Falha ao obter Top Mundial online: ${e.message}")
            null
        }

        if (!onlineList.isNullOrEmpty()) {
            return@withContext onlineList.take(limit)
        }

        // Fallback local curado ordenado por votos e popularidade
        CuratedData.CURATED_GLOBAL_STATIONS
            .sortedByDescending { it.votes }
            .take(limit)
    }

    suspend fun getTopBrazil(limit: Int = 100): List<RadioStation> = withContext(Dispatchers.IO) {
        val onlineList = try {
            withTimeoutOrNull(6000L) {
                val api = apiClient.getService()
                val dtos = api.getStationsByCountryCode(countryCode = "BR", limit = limit, hideBroken = true)
                dtos.mapNotNull { dto ->
                    val url = dto.urlResolved?.ifBlank { null } ?: dto.url
                    if (!url.isNullOrBlank() && !dto.name.isNullOrBlank()) {
                        dto.toDomain()
                    } else null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Falha ao obter Top Brasil online: ${e.message}")
            null
        }

        if (!onlineList.isNullOrEmpty()) {
            return@withContext onlineList.take(limit)
        }

        // Fallback local de 2.763 emissoras brasileiras ordenadas por votos
        CuratedData.CURATED_GLOBAL_STATIONS
            .filter { it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true) }
            .sortedByDescending { it.votes }
            .take(limit)
    }

    companion object {
        private const val TAG = "RadioRankingRepo"

        @Volatile
        private var INSTANCE: RadioRankingRepository? = null

        fun getInstance(): RadioRankingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RadioRankingRepository().also { INSTANCE = it }
            }
        }
    }
}
