package com.example.data.repository

import com.example.data.api.RadioApiClient
import com.example.data.db.FavoriteStationDao
import com.example.data.db.toDomain
import com.example.data.db.toEntity
import com.example.data.model.RadioStation
import com.example.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RadioRepository(
    private val favoriteDao: FavoriteStationDao
) {
    val favoritesFlow: Flow<List<RadioStation>> = favoriteDao.getAllFavorites()
        .map { list -> list.map { it.toDomain() }.sortedBy { it.name.trim().lowercase() } }

    suspend fun getFavoritesDirect(): List<RadioStation> = withContext(Dispatchers.IO) {
        favoriteDao.getAllFavoritesDirect().map { it.toDomain() }.sortedBy { it.name.trim().lowercase() }
    }

    fun isFavoriteFlow(stationId: String): Flow<Boolean> = favoriteDao.isFavorite(stationId)

    suspend fun toggleFavorite(station: RadioStation): Boolean = withContext(Dispatchers.IO) {
        val isFav = favoriteDao.isFavoriteDirect(station.id)
        if (isFav) {
            favoriteDao.deleteFavoriteById(station.id)
            false
        } else {
            favoriteDao.insertFavorite(station.toEntity())
            true
        }
    }

    suspend fun addFavorite(station: RadioStation) = withContext(Dispatchers.IO) {
        favoriteDao.insertFavorite(station.toEntity())
    }

    suspend fun removeFavorite(stationId: String) = withContext(Dispatchers.IO) {
        favoriteDao.deleteFavoriteById(stationId)
    }

    suspend fun getTopStations(limit: Int = 60): List<RadioStation> = withContext(Dispatchers.IO) {
        try {
            val api = RadioApiClient.getService()
            val list = api.getTopVotedStations(limit)
            if (list.isNotEmpty()) {
                val favIds = getFavoriteIdsSet()
                return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                    .sortedBy { it.name.trim().lowercase() }
            }
        } catch (e: Exception) {
            try {
                val backupApi = RadioApiClient.rotateServer()
                val list = backupApi.getTopClickedStations(limit)
                if (list.isNotEmpty()) {
                    val favIds = getFavoriteIdsSet()
                    return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                        .sortedBy { it.name.trim().lowercase() }
                }
            } catch (_: Exception) {
                // Fallback to curated
            }
        }
        val favIds = getFavoriteIdsSet()
        CuratedData.CURATED_GLOBAL_STATIONS
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun getStationsByGenre(tag: String, limit: Int = 60): List<RadioStation> = withContext(Dispatchers.IO) {
        try {
            val api = RadioApiClient.getService()
            val list = api.getStationsByTag(tag = tag, limit = limit)
            if (list.isNotEmpty()) {
                val favIds = getFavoriteIdsSet()
                return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                    .sortedBy { it.name.trim().lowercase() }
            }
        } catch (e: Exception) {
            try {
                val backupApi = RadioApiClient.rotateServer()
                val list = backupApi.searchStations(tag = tag, limit = limit)
                if (list.isNotEmpty()) {
                    val favIds = getFavoriteIdsSet()
                    return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                        .sortedBy { it.name.trim().lowercase() }
                }
            } catch (_: Exception) {
                // Fallback
            }
        }
        val favIds = getFavoriteIdsSet()
        CuratedData.CURATED_GLOBAL_STATIONS
            .filter { it.tags.contains(tag, ignoreCase = true) }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun getStationsByCountry(countryCode: String, limit: Int = 60): List<RadioStation> = withContext(Dispatchers.IO) {
        try {
            val api = RadioApiClient.getService()
            val list = api.getStationsByCountryCode(countryCode = countryCode.lowercase(), limit = limit)
            if (list.isNotEmpty()) {
                val favIds = getFavoriteIdsSet()
                return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                    .sortedBy { it.name.trim().lowercase() }
            }
        } catch (e: Exception) {
            try {
                val backupApi = RadioApiClient.rotateServer()
                val list = backupApi.searchStations(countryCode = countryCode.uppercase(), limit = limit)
                if (list.isNotEmpty()) {
                    val favIds = getFavoriteIdsSet()
                    return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                        .sortedBy { it.name.trim().lowercase() }
                }
            } catch (_: Exception) {
                // Fallback
            }
        }
        val favIds = getFavoriteIdsSet()
        CuratedData.CURATED_GLOBAL_STATIONS
            .filter { it.countryCode.equals(countryCode, ignoreCase = true) }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun searchStations(
        query: String,
        countryCode: String? = null,
        genreTag: String? = null,
        stateCode: String? = null,
        city: String? = null,
        limit: Int = 80
    ): List<RadioStation> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifBlank { null }
        val cleanCountry = if (countryCode.isNullOrBlank() || countryCode.equals("ALL", ignoreCase = true)) null else countryCode
        val cleanTag = if (genreTag.isNullOrBlank() || genreTag.equals("ALL", ignoreCase = true)) null else genreTag
        val cleanState = if (stateCode.isNullOrBlank() || stateCode.equals("ALL", ignoreCase = true)) null else stateCode.trim()
        val cleanCity = if (city.isNullOrBlank() || city.equals("ALL", ignoreCase = true)) null else city.trim()

        // If all parameters are null/blank, return top curated
        if (cleanQuery == null && cleanCountry == null && cleanTag == null && cleanState == null && cleanCity == null) {
            val favIds = getFavoriteIdsSet()
            return@withContext CuratedData.CURATED_GLOBAL_STATIONS
                .map { it.copy(isFavorite = favIds.contains(it.id)) }
                .sortedBy { it.name.trim().lowercase() }
        }

        val apiStateParam = cleanState ?: cleanCity

        try {
            val api = RadioApiClient.getService()
            val list = api.searchStations(
                name = cleanQuery ?: cleanCity,
                tag = cleanTag,
                countryCode = cleanCountry,
                state = apiStateParam,
                limit = limit
            )
            if (list.isNotEmpty()) {
                val favIds = getFavoriteIdsSet()
                return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                    .sortedBy { it.name.trim().lowercase() }
            }
        } catch (e: Exception) {
            try {
                val backupApi = RadioApiClient.rotateServer()
                val list = backupApi.searchStations(
                    name = cleanQuery ?: cleanCity,
                    tag = cleanTag,
                    countryCode = cleanCountry,
                    state = apiStateParam,
                    limit = limit
                )
                if (list.isNotEmpty()) {
                    val favIds = getFavoriteIdsSet()
                    return@withContext list.map { it.toDomain(favIds.contains(it.stationUuid)) }
                        .sortedBy { it.name.trim().lowercase() }
                }
            } catch (_: Exception) {
                // Fallback
            }
        }

        val favIds = getFavoriteIdsSet()
        CuratedData.CURATED_GLOBAL_STATIONS
            .filter { station ->
                val matchQuery = cleanQuery == null ||
                        station.name.contains(cleanQuery, ignoreCase = true) ||
                        station.country.contains(cleanQuery, ignoreCase = true) ||
                        station.city.contains(cleanQuery, ignoreCase = true) ||
                        station.state.contains(cleanQuery, ignoreCase = true) ||
                        station.tags.contains(cleanQuery, ignoreCase = true)

                val matchCountry = cleanCountry == null ||
                        station.countryCode.equals(cleanCountry, ignoreCase = true)

                val matchTag = cleanTag == null ||
                        station.tags.contains(cleanTag, ignoreCase = true) ||
                        station.primaryGenre.contains(cleanTag, ignoreCase = true)

                val matchState = cleanState == null ||
                        station.state.equals(cleanState, ignoreCase = true) ||
                        station.tags.contains(cleanState, ignoreCase = true)

                val matchCity = cleanCity == null ||
                        station.city.contains(cleanCity, ignoreCase = true) ||
                        station.name.contains(cleanCity, ignoreCase = true) ||
                        station.tags.contains(cleanCity, ignoreCase = true)

                matchQuery && matchCountry && matchTag && matchState && matchCity
            }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }
    }

    private suspend fun getFavoriteIdsSet(): Set<String> {
        return try {
            favoriteDao.getAllFavorites().firstOrNull()?.map { it.id }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
