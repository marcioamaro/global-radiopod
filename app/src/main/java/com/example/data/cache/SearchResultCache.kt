package com.example.data.cache

import com.example.data.model.RadioStation

/**
 * Cache thread-safe em memória para resultados de pesquisas e filtros taxonômicos
 * (Gênero, País, Estado, Cidade, Termo de Busca), permitindo navegação offline
 * e instantânea dentro de subconjuntos de estações já carregados.
 */
class SearchResultCache(
    private val maxCapacity: Int = 20,
    private val timeToLiveMs: Long = 15 * 60 * 1000L // 15 minutos
) {
    private data class CacheEntry(
        val stations: List<RadioStation>,
        val timestamp: Long
    )

    private val cache = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)
    private val maxStationReferences = 100_000

    @Synchronized fun put(key: String, stations: List<RadioStation>) {
        if (key.isBlank() || stations.size > maxStationReferences) return
        cache.remove(key)
        cache[key] = CacheEntry(stations, System.currentTimeMillis())
        while (cache.size > maxCapacity || cache.values.sumOf { it.stations.size } > maxStationReferences) {
            cache.remove(cache.keys.first())
        }
    }

    @Synchronized fun get(key: String): List<RadioStation>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > timeToLiveMs) {
            cache.remove(key)
            return null
        }
        return entry.stations
    }

    fun contains(key: String): Boolean = get(key) != null

    @Synchronized fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized fun clear() {
        cache.clear()
    }

    val size: Int get() = synchronized(this) { cache.size }

    companion object {
        @Volatile
        private var INSTANCE: SearchResultCache? = null

        fun getInstance(): SearchResultCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchResultCache().also { INSTANCE = it }
            }
        }

        fun buildKey(
            query: String = "",
            countryCode: String? = null,
            stateCode: String? = null,
            cityName: String? = null,
            genreTag: String? = null
        ): String {
            return listOf(
                "q=${query.trim().lowercase()}",
                "cc=${countryCode?.trim()?.uppercase().orEmpty()}",
                "st=${stateCode?.trim()?.uppercase().orEmpty()}",
                "city=${cityName?.trim()?.lowercase().orEmpty()}",
                "tag=${genreTag?.trim()?.lowercase().orEmpty()}"
            ).joinToString(separator = "|")
        }
    }
}
