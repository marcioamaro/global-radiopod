package com.example.data.repository

import com.example.data.api.DialTunerApiClient
import com.example.data.api.RadioApiClient
import com.example.data.db.FavoriteStationDao
import com.example.data.db.RadioStationDao
import com.example.data.db.toCatalogEntity
import com.example.data.db.toDomain
import com.example.data.db.toEntity
import com.example.data.cache.SearchResultCache
import com.example.data.model.RadioStation
import com.example.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RadioRepository(
    private val favoriteDao: FavoriteStationDao,
    private val stationDao: RadioStationDao? = null,
    private val searchCache: SearchResultCache = SearchResultCache.getInstance()
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

    private suspend fun ensureDatabaseSeeded(): List<RadioStation> {
        if (stationDao == null) return CuratedData.CURATED_GLOBAL_STATIONS
        val count = try { stationDao.getStationCount() } catch (_: Exception) { 0 }
        return if (count < CuratedData.CURATED_GLOBAL_STATIONS.size) {
            try {
                stationDao.insertStations(CuratedData.CURATED_GLOBAL_STATIONS.map { it.toCatalogEntity() })
                stationDao.getAllStationsDirect().map { it.toDomain() }
            } catch (_: Exception) {
                CuratedData.CURATED_GLOBAL_STATIONS
            }
        } else {
            try {
                stationDao.getAllStationsDirect().map { it.toDomain() }
            } catch (_: Exception) {
                CuratedData.CURATED_GLOBAL_STATIONS
            }
        }
    }

    suspend fun getTopStations(): List<RadioStation> = withContext(Dispatchers.IO) {
        val favIds = getFavoriteIdsSet()
        val stations = ensureDatabaseSeeded()
        stations
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedByDescending { it.votes }
    }

    suspend fun getStationsByGenre(tag: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val favIds = getFavoriteIdsSet()
        val stations = ensureDatabaseSeeded()
        stations
            .filter { com.example.util.RadioSearchEngine.matchesGenre(it, tag) }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun getStationsByCountry(countryCode: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val favIds = getFavoriteIdsSet()
        val isAll = countryCode.isBlank() || countryCode.equals("ALL", ignoreCase = true)
        val isBrazil = countryCode.equals("BR", ignoreCase = true)

        val stations = ensureDatabaseSeeded()
        stations
            .filter { station ->
                when {
                    isAll -> true
                    isBrazil -> station.countryCode.equals("BR", ignoreCase = true) || station.country.contains("Brasil", ignoreCase = true)
                    else -> station.countryCode.equals(countryCode, ignoreCase = true)
                }
            }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }
    }

    suspend fun searchStations(
        query: String,
        countryCode: String? = null,
        genreTag: String? = null,
        stateCode: String? = null,
        city: String? = null
    ): List<RadioStation> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifBlank { null }
        val cleanCountry = if (countryCode.isNullOrBlank() || countryCode.equals("ALL", ignoreCase = true)) null else countryCode
        val cleanTag = if (genreTag.isNullOrBlank() || genreTag.equals("ALL", ignoreCase = true)) null else genreTag
        val cleanState = if (stateCode.isNullOrBlank() || stateCode.equals("ALL", ignoreCase = true)) null else stateCode.trim()
        val cleanCity = if (city.isNullOrBlank() || city.equals("ALL", ignoreCase = true)) null else city.trim()

        val isCountryBrazil = cleanCountry?.equals("BR", ignoreCase = true) == true

        val cacheKey = SearchResultCache.buildKey(
            query = cleanQuery ?: "",
            countryCode = cleanCountry,
            stateCode = cleanState,
            cityName = cleanCity,
            genreTag = cleanTag
        )

        val cached = searchCache.get(cacheKey)
        if (cached != null) {
            val favIds = getFavoriteIdsSet()
            return@withContext cached.map { it.copy(isFavorite = favIds.contains(it.id)) }
        }

        val favIds = getFavoriteIdsSet()
        val stations = ensureDatabaseSeeded()
        val localMatches = stations
            .filter { station ->
                // 1. Busca textual por múltiplos tokens (AND)
                val matchQuery = cleanQuery == null || com.example.util.RadioSearchEngine.matchesMultiToken(station, cleanQuery)

                // 2. Filtro de País
                val matchCountry = when {
                    cleanCountry == null -> true
                    isCountryBrazil -> station.countryCode.equals("BR", ignoreCase = true) || station.country.contains("Brasil", ignoreCase = true)
                    else -> station.countryCode.equals(cleanCountry, ignoreCase = true)
                }

                // 3. Filtro de Gênero
                val matchTag = cleanTag == null || com.example.util.RadioSearchEngine.matchesGenre(station, cleanTag)

                // 4. Filtro de Estado / UF (quando Brasil)
                val matchState = when {
                    !isCountryBrazil -> true
                    cleanState == null -> true
                    else -> com.example.util.RadioSearchEngine.matchesUf(station, cleanState)
                }

                // 5. Filtro de Cidade (quando Brasil)
                val matchCity = when {
                    !isCountryBrazil || cleanCity == null -> true
                    else -> {
                        val normCity = com.example.util.RadioSearchEngine.normalize(cleanCity)
                        com.example.util.RadioSearchEngine.normalize(station.city).contains(normCity) ||
                        com.example.util.RadioSearchEngine.normalize(station.name).contains(normCity)
                    }
                }

                matchQuery && matchCountry && matchTag && matchState && matchCity
            }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
            .sortedBy { it.name.trim().lowercase() }

        // Guarda em cache de resultados para navegação offline/imediata
        if (localMatches.isNotEmpty()) {
            searchCache.put(cacheKey, localMatches)
        }

        // Enriquecimento sob demanda online quando houver query e poucos resultados locais
        if (cleanQuery != null && cleanQuery.length >= 3 && localMatches.size < 5) {
            try {
                enrichStationsFromOnlineApis(cleanQuery)
            } catch (e: Exception) {
                android.util.Log.d("RadioRepository", "Enriquecimento online falhou para $cleanQuery: ${e.message}")
            }
        }

        localMatches
    }

    private suspend fun enrichStationsFromOnlineApis(query: String) {
        if (stationDao == null) return
        try {
            val newEntities = mutableListOf<com.example.data.db.RadioStationEntity>()

            // 1. Consulta Radio Browser API (base colaborativa aberta e confiável)
            try {
                val radioBrowserResults = RadioApiClient.getService().searchStations(name = query, limit = 100)
                for (item in radioBrowserResults) {
                    val streamUrl = item.urlResolved?.ifBlank { null } ?: item.url
                    if (!streamUrl.isNullOrBlank() && !item.name.isNullOrBlank()) {
                        val id = item.stationUuid ?: "rb_${java.util.UUID.randomUUID().toString().take(8)}"
                        newEntities.add(
                            com.example.data.db.RadioStationEntity(
                                id = id,
                                name = item.name.trim(),
                                streamUrl = streamUrl,
                                favicon = item.favicon ?: "",
                                homepage = item.homepage ?: "",
                                tags = item.tags ?: "",
                                country = item.country ?: "Brasil",
                                countryCode = item.countryCode ?: "BR",
                                state = item.state ?: "",
                                city = "",
                                codec = item.codec ?: "MP3",
                                bitrate = item.bitrate ?: 128,
                                votes = item.votes ?: 100
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. Consulta DialTuner API para rádios brasileiras
            try {
                val dialResults = DialTunerApiClient.api.search(query)
                for (item in dialResults) {
                    val streamUrl = item.stream ?: item.backup
                    if (!streamUrl.isNullOrBlank() && item.name.isNotBlank()) {
                        val id = "dialtuner_${item.id ?: java.util.UUID.randomUUID().toString().take(8)}"
                        newEntities.add(
                            com.example.data.db.RadioStationEntity(
                                id = id,
                                name = item.name.trim(),
                                streamUrl = streamUrl,
                                favicon = item.cover ?: "",
                                homepage = item.url ?: "",
                                tags = (item.genres ?: listOf(item.genre ?: "")).filterNotNull().joinToString(", "),
                                country = item.country ?: "Brasil",
                                countryCode = item.cc ?: "BR",
                                state = item.region ?: "",
                                city = item.city ?: "",
                                codec = item.format ?: "AAC",
                                bitrate = item.bitrate ?: 128,
                                votes = item.id?.toInt() ?: 1000
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            if (newEntities.isNotEmpty()) {
                stationDao.insertStations(newEntities)
            }
        } catch (_: Exception) {}
    }

    suspend fun getFavoriteIdsSet(): Set<String> {
        return try {
            favoriteDao.getAllFavorites().firstOrNull()?.map { it.id }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
