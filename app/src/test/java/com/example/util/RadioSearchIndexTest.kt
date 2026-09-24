package com.example.util

import com.example.data.model.RadioStation
import com.example.data.cache.SearchResultCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RadioSearchIndexTest {
    private val atual = RadioStation("atual", "Rádio Atual São Paulo 94,1 FM", "https://example.org/live", country = "Brasil", countryCode = "BR", state = "SP", city = "São Paulo", tags = "forro")
    private val other = atual.copy(id = "other", name = "Rádio Jazz", country = "Portugal", countryCode = "PT", state = "", city = "Lisboa", tags = "jazz")

    @Test fun accentsTokensAndFiltersMatchLegacyBehavior() = runBlocking {
        val stations = listOf(other, atual)
        val index = RadioSearchIndex(stations)
        for (q in listOf("atual sao", "RÁDIO", "sao\tpaulo", "missing", "")) {
            assertEquals(stations.filter { RadioSearchEngine.matchesMultiToken(it, q) }.map { it.id }.sorted(), index.search(q, null, null, null, null).map { it.id }.sorted())
        }
        assertEquals(listOf("atual"), index.search("atual", "BR", "forro", "SP", "Sao Paulo").map { it.id })
        assertTrue(index.search("atual", "PT", null, null, null).isEmpty())
        assertTrue(index.search("atual", "BR", "jazz", null, null).isEmpty())
    }

    @Test fun cacheRetainsEmptyResultsAndEvictsLeastRecentlyUsed() {
        val cache = SearchResultCache(maxCapacity = 2)
        cache.put("empty", emptyList())
        cache.put("one", listOf(atual))
        assertEquals(emptyList<RadioStation>(), cache.get("empty"))
        cache.put("two", listOf(other))
        assertNull(cache.get("one"))
        assertNotNull(cache.get("empty"))
    }

    @Test fun fullCatalogScaleSearch() = runBlocking {
        val stations = (0 until 45_000).map { atual.copy(id = "station_$it", name = "Emissora $it") } + atual
        val started = System.nanoTime()
        val index = RadioSearchIndex(stations)
        val buildMs = (System.nanoTime() - started) / 1_000_000
        val times = (0 until 20).map {
            val start = System.nanoTime()
            assertEquals(listOf("atual"), index.search("atual sao", "BR", null, null, null).map { it.id })
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        println("SEARCH_BENCHMARK stations=${stations.size} buildMs=$buildMs medianMs=${times[10]} p95Ms=${times[18]}")
    }
}
