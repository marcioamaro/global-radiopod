package com.example.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadioRankingRepositoryTest {

    private lateinit var rankingRepository: RadioRankingRepository

    @Before
    fun setup() {
        rankingRepository = RadioRankingRepository.getInstance()
    }

    @Test
    fun testGetTopBrazilReturnsStations() = runBlocking {
        val list = rankingRepository.getTopBrazil(limit = 20)
        assertNotNull(list)
        assertFalse(list.isEmpty())
        assertTrue(list.size <= 20)
        // Todas as estações do Top Brasil devem pertencer ao Brasil
        assertTrue(list.all { it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true) })
    }

    @Test
    fun testGetTopWorldReturnsStations() = runBlocking {
        val list = rankingRepository.getTopWorld(limit = 20)
        assertNotNull(list)
        assertFalse(list.isEmpty())
        assertTrue(list.size <= 20)
    }

    @Test
    fun testTopBrazilSortedByVotes() = runBlocking {
        val list = rankingRepository.getTopBrazil(limit = 10)
        if (list.size >= 2) {
            for (i in 0 until list.size - 1) {
                assertTrue(
                    "Top Brasil deve estar ordenado descendentemente por votos",
                    list[i].votes >= list[i + 1].votes
                )
            }
        }
    }
}
