package com.example.data

import com.example.data.db.FavoriteStationDao
import com.example.data.db.FavoriteStationEntity
import com.example.data.repository.CuratedData
import com.example.data.repository.RadioRepository
import com.example.util.RadioSearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadioAuditAndSearchTest {

    private lateinit var fakeDao: FavoriteStationDao
    private lateinit var repository: RadioRepository

    @Before
    fun setup() {
        fakeDao = object : FavoriteStationDao {
            override fun getAllFavorites(): Flow<List<FavoriteStationEntity>> = flowOf(emptyList())
            override suspend fun getAllFavoritesDirect(): List<FavoriteStationEntity> = emptyList()
            override fun isFavorite(stationId: String): Flow<Boolean> = flowOf(false)
            override suspend fun isFavoriteDirect(stationId: String): Boolean = false
            override suspend fun insertFavorite(entity: FavoriteStationEntity) {}
            override suspend fun deleteFavorite(entity: FavoriteStationEntity) {}
            override suspend fun deleteFavoriteById(stationId: String) {}
        }
        repository = RadioRepository(fakeDao)
    }

    @Test
    fun testTotalBrazilStationsCountIs1321() {
        val allStations = CuratedData.CURATED_GLOBAL_STATIONS
        assertEquals("Total catalog should have exactly 1549 stations", 1549, allStations.size)

        val brazilStations = allStations.filter {
            it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true)
        }
        assertEquals("Total Brazilian stations must be exactly 1321", 1321, brazilStations.size)

        val internationalStations = allStations.size - brazilStations.size
        assertEquals("Total international stations must be 228", 228, internationalStations)
    }

    @Test
    fun testGetStationsByCountryReturnsFull1321ForBrazil() = runBlocking {
        val brStations = repository.getStationsByCountry("BR")
        assertEquals("getStationsByCountry('BR') must return all 1321 active Brazilian stations", 1321, brStations.size)
    }

    @Test
    fun testGetStationsByCountryReturnsAll1549ForALL() = runBlocking {
        val allStations = repository.getStationsByCountry("ALL")
        assertEquals("getStationsByCountry('ALL') must return all 1549 global stations", 1549, allStations.size)
    }

    @Test
    fun testTodasAsUFsIncludesStationsWithoutState() = runBlocking {
        // "Todas as UFs" (stateCode = "ALL" or null) must return all 1321 stations, including the 163 without state
        val resultsAllUfs = repository.searchStations(
            query = "",
            countryCode = "BR",
            stateCode = "ALL"
        )
        assertEquals("Todas as UFs must return 1321 stations", 1321, resultsAllUfs.size)

        val emptyStateCount = resultsAllUfs.count { it.state.isBlank() }
        assertTrue("Must include stations without registered UF (at least 150)", emptyStateCount >= 150)
    }

    @Test
    fun testGeracaoStationsFromLimeiraAreRestoredAndSearchable() = runBlocking {
        val geracaoRock = repository.searchStations("geracao rock limeira")
        assertTrue("Must find Geração Rock Limeira", geracaoRock.any { it.id == "geracao_rock_limeira" })

        val geracaoSertaneja = repository.searchStations("geracao sertaneja")
        assertTrue("Must find Geração Sertaneja Limeira", geracaoSertaneja.any { it.id == "geracao_sertaneja_limeira" })

        val allGeracao = CuratedData.CURATED_GLOBAL_STATIONS.filter { it.id.startsWith("geracao_") }
        assertEquals("All 14 Geração stations from Limeira must be present", 14, allGeracao.size)
    }

    @Test
    fun testSpecificUfFilterSP() = runBlocking {
        val spStations = repository.searchStations(
            query = "",
            countryCode = "BR",
            stateCode = "SP"
        )
        assertTrue("SP stations count must be greater than 300", spStations.size >= 350)
        // All returned stations must belong to SP or have SP in their state/city
        spStations.forEach { s ->
            assertTrue("Station ${s.name} must match SP UF", RadioSearchEngine.matchesUf(s, "SP"))
        }
    }

    @Test
    fun testMultiTokenSearchWithAccents() = runBlocking {
        // Test query with accents and multiple words: "brasil mix fm são paulo"
        val query = "brasil mix fm são paulo"
        val results = repository.searchStations(query = query)
        assertTrue("Multi-token search for '$query' must find matching station", results.isNotEmpty())
        assertTrue("First result should contain Mix in name", results.any { it.name.contains("Mix", ignoreCase = true) })
    }

    @Test
    fun testMultiTokenSearchSertanejoGoias() = runBlocking {
        val query = "sertanejo goias"
        val results = repository.searchStations(query = query)
        assertTrue("Multi-token search for '$query' must find results", results.isNotEmpty())
    }

    @Test
    fun testMultiTokenSearchRockCuritiba() = runBlocking {
        val query = "rock curitiba"
        val results = repository.searchStations(query = query)
        assertTrue("Multi-token search for '$query' must find results", results.isNotEmpty())
    }

    @Test
    fun testTaxonomyGenresCoverage() = runBlocking {
        // Test that our 39 taxonomy genres in CuratedData.GENRES are sorted alphabetically
        val genres = CuratedData.GENRES
        assertEquals("Must have 39 categories", 39, genres.size)

        val names = genres.map { it.name }
        val sortedNames = names.sortedWith(java.text.Collator.getInstance(java.util.Locale("pt", "BR")))
        assertEquals("Genres must be in strict alphabetical order", sortedNames, names)

        // Verify key genres have plenty of indexed stations
        val sertanejoStations = repository.getStationsByGenre("sertanejo")
        assertTrue("Sertanejo stations must have >= 50", sertanejoStations.size >= 50)

        val rockStations = repository.getStationsByGenre("rock")
        assertTrue("Rock stations must have >= 50", rockStations.size >= 50)

        val mpbStations = repository.getStationsByGenre("mpb")
        assertTrue("MPB stations must have >= 200", mpbStations.size >= 200)

        val newsStations = repository.getStationsByGenre("noticias_talk")
        assertTrue("Notícias stations must have >= 50", newsStations.size >= 50)

        val sportsStations = repository.getStationsByGenre("esportes")
        assertTrue("Esportes stations must have >= 30", sportsStations.size >= 30)

        val gospelStations = repository.getStationsByGenre("gospel")
        assertTrue("Gospel stations must have >= 50", gospelStations.size >= 50)

        val retroStations = repository.getStationsByGenre("retro")
        assertTrue("Flashback/Retrô stations must have >= 50", retroStations.size >= 50)
    }

    @Test
    fun testAll1307BrazilStationsAreIndexedInAtLeastOneGenre() {
        val brStations = CuratedData.CURATED_GLOBAL_STATIONS.filter {
            it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true)
        }

        val allGenreTags = CuratedData.GENRES.map { it.tag }
        var unindexedCount = 0

        brStations.forEach { s ->
            val matchesAny = allGenreTags.any { tag -> RadioSearchEngine.matchesGenre(s, tag) }
            if (!matchesAny) {
                unindexedCount++
            }
        }

        assertEquals("Zero Brazilian stations should be omitted from genres", 0, unindexedCount)
    }

    @Test
    fun testAndroidAutoStatesNavigation() {
        // Test Android Auto "Todas as UFs"
        val allBrazil = CuratedData.CURATED_GLOBAL_STATIONS.filter {
            it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true)
        }
        assertEquals(1321, allBrazil.size)

        // Test AA cap of 100 on large aggregate list
        val aaAllUfsCap = allBrazil.take(100)
        assertEquals(100, aaAllUfsCap.size)

        // Test individual state folder in AA, e.g. SP, RJ, MG
        val spList = allBrazil.filter { RadioSearchEngine.matchesUf(it, "SP") }
        assertTrue("SP must have more than 350 stations", spList.size >= 350)

        val rjList = allBrazil.filter { RadioSearchEngine.matchesUf(it, "RJ") }
        assertTrue("RJ must have more than 50 stations", rjList.size >= 50)
    }

    @Test
    fun testAndroidAutoGenreNavigation39Categories() {
        // Test that AA can serve all 39 categories without truncation
        val genres = CuratedData.GENRES
        assertEquals(39, genres.size)

        // Test filtering stations for each of the 39 categories
        genres.forEach { genre ->
            val matching = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                RadioSearchEngine.matchesGenre(it, genre.tag)
            }
            assertTrue("Genre '${genre.name}' should have matching stations", matching.isNotEmpty())
        }
    }

    @Test
    fun testAndroidAutoMultiTokenSearch() {
        val query = "brasil mix fm são paulo"
        val results = CuratedData.CURATED_GLOBAL_STATIONS.filter {
            RadioSearchEngine.matchesMultiToken(it, query)
        }
        assertTrue("Android Auto search for '$query' must find results", results.isNotEmpty())
        assertTrue("Results should contain Mix FM", results.any { it.name.contains("Mix", ignoreCase = true) })
    }

    @Test
    fun testMonochromeCountryBadgesDoNotContainColoredFlags() {
        val countries = CuratedData.COUNTRIES
        assertTrue(countries.isNotEmpty())
        countries.forEach { c ->
            assertFalse("Country flag '${c.flag}' should not contain colored flag emoji", c.flag.contains("🇧🇷") || c.flag.contains("🇺🇸"))
            assertTrue("Country flag should be formatted as monochrome [CODE]", c.flag.startsWith("[") && c.flag.endsWith("]"))
        }
    }


    @Test
    fun testAccentInsensitiveNormalization() {
        val queryWithAccents = "SÃO PAULO NOTÍCIAS"
        val normalized = RadioSearchEngine.normalize(queryWithAccents)
        assertEquals("sao paulo noticias", normalized)
    }

    @Test
    fun testBrazilianStatesSortedAlphabeticallyWithCollator() {
        assertEquals("São Paulo deve estar no topo absoluto (índice 0)", "SP", CuratedData.BRAZILIAN_STATES[0].first)
        assertEquals("Opção Todos os Estados deve estar no índice 1", "", CuratedData.BRAZILIAN_STATES[1].first)
        val otherStates = CuratedData.BRAZILIAN_STATES.filter { it.first.isNotEmpty() && it.first != "SP" }.map { it.second }
        val collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("pt-BR"))
        val sorted = otherStates.sortedWith(collator)
        assertEquals("Demais estados brasileiros devem estar ordenados alfabeticamente A-Z via collator pt-BR", sorted, otherStates)
    }

    @Test
    fun testCountriesSortedAlphabeticallyWithCollator() {
        assertEquals("Brasil deve estar estritamente no topo (índice 0)", "BR", CuratedData.COUNTRIES[0].code)
        assertEquals("Opção Todos os Países deve estar no índice 1", "ALL", CuratedData.COUNTRIES[1].code)
        val otherCountries = CuratedData.COUNTRIES.filter { it.code != "ALL" && it.code != "BR" }.map { it.name }
        val collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("pt-BR"))
        val sorted = otherCountries.sortedWith(collator)
        assertEquals("Demais países devem estar ordenados alfabeticamente A-Z via collator pt-BR", sorted, otherCountries)
        assertEquals("Brasil não deve estar duplicado", 1, CuratedData.COUNTRIES.count { it.code == "BR" })
    }

    @Test
    fun testGetStateFullName() {
        assertEquals("São Paulo", CuratedData.getStateFullName("SP"))
        assertEquals("Rio de Janeiro", CuratedData.getStateFullName("RJ"))
        assertEquals("Minas Gerais", CuratedData.getStateFullName("MG"))
        assertEquals("Distrito Federal", CuratedData.getStateFullName("DF"))
    }
}
