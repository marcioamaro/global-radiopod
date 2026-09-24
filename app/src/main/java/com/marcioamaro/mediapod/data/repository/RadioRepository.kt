package com.marcioamaro.mediapod.data.repository

import com.marcioamaro.mediapod.data.db.FavoriteStationDao
import com.marcioamaro.mediapod.data.db.RadioStationDao
import com.marcioamaro.mediapod.data.db.toCatalogEntity
import com.marcioamaro.mediapod.data.db.toDomain
import com.marcioamaro.mediapod.data.db.toEntity
import com.marcioamaro.mediapod.data.cache.SearchResultCache
import com.marcioamaro.mediapod.data.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RadioRepository(
    private val favoriteDao: FavoriteStationDao,
    private val stationDao: RadioStationDao? = null,
    private val searchCache: SearchResultCache = SearchResultCache.getInstance(),
    private val catalogPreferences: android.content.SharedPreferences? = null
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

    private val localSearchIndex by lazy {
        com.marcioamaro.mediapod.util.RadioSearchIndex(CuratedData.CURATED_GLOBAL_STATIONS)
    }
    private val catalogMutex = Mutex()
    @Volatile private var catalogSeeded = false

    private suspend fun ensureDatabaseSeeded(): List<RadioStation> {
        if (stationDao == null) return CuratedData.CURATED_GLOBAL_STATIONS
        return try {
            catalogMutex.withLock {
                if (!catalogSeeded) {
                    // Synchronize approved additions, corrections and removals atomically.
                    // Favorites live in a separate table and remain available.
                    val stations = CuratedData.CURATED_GLOBAL_STATIONS
                    if (catalogPreferences?.getString("version", null) != RadioCatalog.version ||
                        stationDao.getStationCount() != stations.size) {
                        stationDao.replaceCatalog(stations.map { it.toCatalogEntity() })
                        // Write only after the database transaction succeeds. An interruption retries safely.
                        catalogPreferences?.edit()?.putString("version", RadioCatalog.version)?.commit()
                    }
                    catalogSeeded = true
                }
            }
            CuratedData.CURATED_GLOBAL_STATIONS
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            CuratedData.CURATED_GLOBAL_STATIONS
        }
    }

    suspend fun getTopStations(): List<RadioStation> = withContext(Dispatchers.IO) {
        val favIds = getFavoriteIdsSet()
        val stations = ensureDatabaseSeeded()
        stations
            .map { if (it.id in favIds) it.copy(isFavorite = true) else it }
            .sortedByDescending { it.votes }
    }

    suspend fun getStationsByGenre(tag: String): List<RadioStation> = withContext(Dispatchers.IO) {
        ensureDatabaseSeeded()
        searchStations(query = "", genreTag = tag)
    }

    suspend fun getStationsByCountry(countryCode: String): List<RadioStation> = withContext(Dispatchers.IO) {
        ensureDatabaseSeeded()
        searchStations(query = "", countryCode = countryCode)
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
            return@withContext cached.map { if (it.id in favIds) it.copy(isFavorite = true) else it }
        }

        val favIds = getFavoriteIdsSet()
        val localMatches = withContext(Dispatchers.Default) {
            localSearchIndex.search(cleanQuery.orEmpty(), cleanCountry, cleanTag, cleanState, cleanCity)
        }
        // Cache catalog references, not copies carrying potentially stale favorite flags.
        searchCache.put(cacheKey, localMatches)
        localMatches.map { if (it.id in favIds) it.copy(isFavorite = true) else it }
    }

    suspend fun getFavoriteIdsSet(): Set<String> {
        return try {
            favoriteDao.getAllFavorites().firstOrNull()?.map { it.id }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
