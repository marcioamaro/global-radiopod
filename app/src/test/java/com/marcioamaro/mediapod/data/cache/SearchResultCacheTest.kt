package com.marcioamaro.mediapod.data.cache

import com.marcioamaro.mediapod.data.model.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchResultCacheTest {

    private lateinit var cache: SearchResultCache

    @Before
    fun setup() {
        cache = SearchResultCache(maxCapacity = 5, timeToLiveMs = 1000L)
    }

    private fun dummyStation(id: String): RadioStation {
        return RadioStation(
            id = id,
            name = "Estação $id",
            streamUrl = "http://stream.test/$id"
        )
    }

    @Test
    fun testPutAndGet() {
        val key = SearchResultCache.buildKey(query = "rock", countryCode = "BR")
        val stations = listOf(dummyStation("1"), dummyStation("2"))

        cache.put(key, stations)
        assertTrue(cache.contains(key))

        val retrieved = cache.get(key)
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.size)
        assertEquals("1", retrieved?.get(0)?.id)
    }

    @Test
    fun testEvictionOnMaxCapacity() {
        for (i in 1..6) {
            val key = "key_$i"
            cache.put(key, listOf(dummyStation("$i")))
        }
        // Capacidade máxima é 5, portanto não deve exceder 5 itens
        assertTrue(cache.size <= 5)
    }

    @Test
    fun testClear() {
        cache.put("k1", listOf(dummyStation("1")))
        cache.put("k2", listOf(dummyStation("2")))
        assertEquals(2, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
        assertFalse(cache.contains("k1"))
    }

    @Test
    fun testBuildKeyConsistency() {
        val k1 = SearchResultCache.buildKey(query = " Antena 1 ", countryCode = "br")
        val k2 = SearchResultCache.buildKey(query = "antena 1", countryCode = "BR")
        assertEquals(k1, k2)
    }
}
