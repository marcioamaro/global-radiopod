package com.example.util

import com.example.data.repository.CuratedData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AtualRadioCatalogTest {
    @Test fun saoPaulo941IsPackagedSeparatelyAndSearchable() = runBlocking {
        val stations = CuratedData.CURATED_GLOBAL_STATIONS
        val atual = stations.single { it.id == "br_sp_radio_atual_941_fm" }
        assertEquals("https://ice.fabricahost.com.br/radioatual941", atual.streamUrl)
        assertEquals("SP", atual.state)
        assertFalse(atual.tags.contains("gospel"))
        assertTrue(stations.any { it.id != atual.id && it.name.contains("Atual") && it.tags.contains("gospel") })
        val index = RadioSearchIndex(stations)
        for (query in listOf("Atual", "Atual 94.1", "Atual 94,1", "Radio Atual Sao Paulo")) {
            assertTrue(query, index.search(query, "BR", null, "SP", "São Paulo").any { it.id == atual.id })
            assertTrue(query, RadioSearchEngine.matchesMultiToken(atual, query))
        }
    }
}
