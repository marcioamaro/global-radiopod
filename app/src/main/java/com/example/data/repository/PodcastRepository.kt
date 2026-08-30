package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.PodcastCategory
import com.example.data.model.PodcastCountry
import com.example.data.model.PodcastEpisode
import com.example.data.model.PodcastShow
import com.example.data.remote.PodcastApiClient
import com.example.data.remote.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class PodcastRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("podcast_preferences", Context.MODE_PRIVATE)

    private val _curatedShows = mutableListOf<PodcastShow>()
    private val episodeCache = mutableMapOf<String, List<PodcastEpisode>>()

    private val _favoritesFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val favoritesFlow: StateFlow<List<PodcastShow>> = _favoritesFlow.asStateFlow()
    val favoriteShowsFlow: StateFlow<List<PodcastShow>> get() = _favoritesFlow.asStateFlow()

    private val _recentEpisodesFlow = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val recentEpisodesFlow: StateFlow<List<PodcastEpisode>> = _recentEpisodesFlow.asStateFlow()

    private val _recentShowsFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val recentShowsFlow: StateFlow<List<PodcastShow>> = _recentShowsFlow.asStateFlow()

    private val _customShowsFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val customShowsFlow: StateFlow<List<PodcastShow>> = _customShowsFlow.asStateFlow()

    fun getCustomPodcasts(): List<PodcastShow> = _customShowsFlow.value

    fun addCustomPodcast(title: String, feedUrl: String): PodcastShow {
        val show = PodcastShow(
            id = "custom_" + java.util.UUID.randomUUID().toString().take(8),
            title = title,
            feedUrl = feedUrl,
            category = "Personalizado",
            isCustom = true
        )
        addCustomPodcast(show)
        return show
    }

    private val genreIdMap = mapOf(
        "noticias" to "1311",
        "tecnologia" to "1318",
        "cultura_pop" to "1301",
        "negocios" to "1321",
        "ciencia" to "1533",
        "historia" to "1487",
        "comedia" to "1303",
        "sociedade" to "1324",
        "esportes" to "1545",
        "musica" to "1310",
        "educacao" to "1304"
    )

    suspend fun getTopPodcasts(countryCode: String, limit: Int = 100): List<PodcastShow> = withContext(Dispatchers.IO) {
        val favIds = _favoritesFlow.value.map { it.id }.toSet()
        val local = if (countryCode.equals("GLOBAL", ignoreCase = true)) {
            _curatedShows.filter { !it.country.equals("BR", ignoreCase = true) }
        } else {
            _curatedShows.filter { it.country.equals(countryCode, ignoreCase = true) }
        }

        val online = try {
            val queryCountry = if (countryCode.equals("GLOBAL", ignoreCase = true)) "US" else countryCode
            PodcastApiClient.fetchTopShowsByCountry(queryCountry)
        } catch (_: Exception) {
            emptyList()
        }

        val combined = (local + online)
            .distinctBy { it.feedUrl.lowercase() }
            .filter { it.feedUrl.isNotBlank() && it.episodeCount > 0 }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }

        combined.take(limit)
    }

    suspend fun getPodcastsByCategory(categoryKeyword: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val q = categoryKeyword.trim().lowercase()
        val favIds = _favoritesFlow.value.map { it.id }.toSet()

        // 1. Matches no catálogo offline
        val localMatches = _curatedShows.filter {
            it.category.lowercase().contains(q) || it.description.lowercase().contains(q) || it.title.lowercase().contains(q)
        }

        // 2. Busca online complementar na API do iTunes por genreId
        val matchedGenreId = genreIdMap.entries.firstOrNull { q.contains(it.key) || it.key.contains(q) }?.value ?: "1311"
        val onlineMatches = try {
            PodcastApiClient.fetchPodcastsByGenre(matchedGenreId, categoryKeyword)
        } catch (_: Exception) {
            emptyList()
        }

        (localMatches + onlineMatches)
            .distinctBy { it.feedUrl.lowercase() }
            .filter { it.feedUrl.isNotBlank() && it.episodeCount > 0 }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
    }

    suspend fun getEpisodes(show: PodcastShow): List<PodcastEpisode> = getEpisodesForShow(show)

    fun addToRecents(show: PodcastShow) {
        val current = _recentShowsFlow.value.toMutableList()
        current.removeAll { it.id == show.id || it.feedUrl.equals(show.feedUrl, ignoreCase = true) }
        current.add(0, show)
        if (current.size > 30) current.removeAt(current.size - 1)
        _recentShowsFlow.value = current
    }

    fun clearRecents() {
        _recentShowsFlow.value = emptyList()
        _recentEpisodesFlow.value = emptyList()
    }

    fun toggleFavorite(show: PodcastShow) = toggleFavoriteShow(show)

    val categories = listOf(
        PodcastCategory("noticias", "Notícias & Atualidades", "📰", "Cobertura diária e política"),
        PodcastCategory("tecnologia", "Tecnologia & Inovação", "💻", "IA, programação e gadgets"),
        PodcastCategory("cultura_pop", "Cultura Pop & Geek", "🎮", "Cinema, quadrinhos e séries"),
        PodcastCategory("negocios", "Negócios & Finanças", "📈", "Mercado, investimentos e carreira"),
        PodcastCategory("ciencia", "Ciência & Conhecimento", "🔬", "Astrofísica, biologia e descobertas"),
        PodcastCategory("historia", "História & Documentários", "🏛️", "Grandes momentos e biografias"),
        PodcastCategory("comedia", "Humor & Comédia", "🎭", "Conversas leves e sátira"),
        PodcastCategory("sociedade", "Sociedade & Comportamento", "👥", "Debates humanos e reflexões"),
        PodcastCategory("esportes", "Esportes & Futebol", "⚽", "Análises, bastidores e resenhas"),
        PodcastCategory("musica", "Música & Cultura", "🎵", "História das canções e entrevistas")
    )

    val countries = listOf(
        PodcastCountry("BR", "Brasil", "🇧🇷"),
        PodcastCountry("US", "Estados Unidos", "🇺🇸"),
        PodcastCountry("GB", "Reino Unido", "🇬🇧"),
        PodcastCountry("PT", "Portugal", "🇵🇹"),
        PodcastCountry("ES", "Espanha / Latam", "🇪🇸")
    )

    init {
        loadCatalogFromAssets()
        loadFavoritesFromPrefs()
        loadRecentsFromPrefs()
        loadCustomShowsFromPrefs()
    }

    private fun loadCatalogFromAssets() {
        try {
            val jsonString = context.assets.open("podcasts_catalog.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            _curatedShows.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val feed = obj.getString("feedUrl").trim()
                if (feed.isNotBlank()) {
                    _curatedShows.add(
                        PodcastShow(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            author = obj.optString("author", ""),
                            description = obj.optString("description", ""),
                            feedUrl = feed,
                            artworkUrl = obj.optString("artworkUrl", ""),
                            country = obj.optString("country", "BR"),
                            category = obj.optString("category", "Geral"),
                            episodeCount = obj.optInt("episodeCount", 50),
                            isCustom = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Erro ao carregar catálogo em assets/podcasts_catalog.json", e)
        }
    }

    suspend fun searchPodcasts(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        val favIds = _favoritesFlow.value.map { it.id }.toSet()

        // 1. Filtro local no catálogo offline
        val localMatches = (_curatedShows + _customShowsFlow.value).filter {
            it.title.lowercase().contains(q) ||
            it.author.lowercase().contains(q) ||
            it.category.lowercase().contains(q) ||
            it.description.lowercase().contains(q)
        }

        // 2. Busca online global na API do iTunes
        val onlineMatches = try {
            PodcastApiClient.searchPodcasts(query)
        } catch (_: Exception) {
            emptyList()
        }

        (localMatches + onlineMatches)
            .distinctBy { it.feedUrl.lowercase() }
            .filter { it.feedUrl.isNotBlank() && it.episodeCount > 0 }
            .map { it.copy(isFavorite = favIds.contains(it.id)) }
    }

    suspend fun getEpisodesForShow(show: PodcastShow): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        episodeCache[show.id]?.let { return@withContext it }
        var episodes = RssFeedParser.fetchEpisodes(show.feedUrl, show.id, show.title, show.artworkUrl)

        // Auto-Cura Dinâmica: Se o feed estiver quebrado, migrado ou vazio, busca link oficial atualizado na API do iTunes
        if (episodes.isEmpty()) {
            try {
                val liveResults = PodcastApiClient.searchPodcasts(show.title)
                val match = liveResults.firstOrNull {
                    it.title.equals(show.title, ignoreCase = true) ||
                    it.title.lowercase(Locale.ROOT).contains(show.title.lowercase(Locale.ROOT)) ||
                    show.title.lowercase(Locale.ROOT).contains(it.title.lowercase(Locale.ROOT))
                }
                if (match != null && match.feedUrl.isNotBlank() && !match.feedUrl.equals(show.feedUrl, ignoreCase = true)) {
                    android.util.Log.i("PodcastRepository", "Auto-Cura ativada para '${show.title}': tentando novo feed oficial ${match.feedUrl}")
                    val recovered = RssFeedParser.fetchEpisodes(match.feedUrl, show.id, show.title, match.artworkUrl.ifBlank { show.artworkUrl })
                    if (recovered.isNotEmpty()) {
                        episodes = recovered
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("PodcastRepository", "Falha na auto-cura de feed para '${show.title}'", e)
            }
        }

        if (episodes.isNotEmpty()) {
            episodeCache[show.id] = episodes
        }
        episodes
    }

    // --- Meus Podcasts (Custom) ---

    fun addCustomPodcast(show: PodcastShow) {
        val current = _customShowsFlow.value.toMutableList()
        current.removeAll { it.feedUrl.equals(show.feedUrl, ignoreCase = true) }
        current.add(0, show)
        _customShowsFlow.value = current
        saveCustomShowsToPrefs()
    }

    fun removeCustomPodcast(showId: String) {
        val current = _customShowsFlow.value.toMutableList()
        current.removeAll { it.id == showId }
        _customShowsFlow.value = current
        saveCustomShowsToPrefs()
    }

    private fun saveCustomShowsToPrefs() {
        val array = JSONArray()
        for (item in _customShowsFlow.value) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("author", item.author)
                put("description", item.description)
                put("feedUrl", item.feedUrl)
                put("artworkUrl", item.artworkUrl)
                put("country", item.country)
                put("category", item.category)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_podcasts_json", array.toString()).apply()
    }

    private fun loadCustomShowsFromPrefs() {
        val json = prefs.getString("custom_podcasts_json", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<PodcastShow>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PodcastShow(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        author = obj.optString("author", ""),
                        description = obj.optString("description", ""),
                        feedUrl = obj.getString("feedUrl"),
                        artworkUrl = obj.optString("artworkUrl", ""),
                        country = obj.optString("country", "BR"),
                        category = obj.optString("category", "Personalizado"),
                        isCustom = true
                    )
                )
            }
            _customShowsFlow.value = list
        } catch (_: Exception) {}
    }

    // --- Favoritos ---

    fun toggleFavoriteShow(show: PodcastShow) {
        val current = _favoritesFlow.value.toMutableList()
        val exists = current.any { it.id == show.id || it.feedUrl.equals(show.feedUrl, ignoreCase = true) }
        if (exists) {
            current.removeAll { it.id == show.id || it.feedUrl.equals(show.feedUrl, ignoreCase = true) }
        } else {
            current.add(0, show.copy(isFavorite = true))
        }
        _favoritesFlow.value = current
        saveFavoritesToPrefs()
    }

    private fun saveFavoritesToPrefs() {
        val array = JSONArray()
        for (item in _favoritesFlow.value) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("author", item.author)
                put("description", item.description)
                put("feedUrl", item.feedUrl)
                put("artworkUrl", item.artworkUrl)
                put("country", item.country)
                put("category", item.category)
            }
            array.put(obj)
        }
        prefs.edit().putString("podcast_favorites_json", array.toString()).apply()
    }

    private fun loadFavoritesFromPrefs() {
        val json = prefs.getString("podcast_favorites_json", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<PodcastShow>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PodcastShow(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        author = obj.optString("author", ""),
                        description = obj.optString("description", ""),
                        feedUrl = obj.getString("feedUrl"),
                        artworkUrl = obj.optString("artworkUrl", ""),
                        country = obj.optString("country", "BR"),
                        category = obj.optString("category", "Geral"),
                        isFavorite = true
                    )
                )
            }
            _favoritesFlow.value = list
        } catch (_: Exception) {}
    }

    // --- Recentes & Posição de Reprodução ---

    fun addRecentEpisode(episode: PodcastEpisode) {
        val current = _recentEpisodesFlow.value.toMutableList()
        current.removeAll { it.id == episode.id }
        current.add(0, episode)
        if (current.size > 40) current.removeAt(current.size - 1)
        _recentEpisodesFlow.value = current
        saveRecentsToPrefs()
    }

    fun savePlaybackPosition(episodeId: String, positionMs: Long) {
        prefs.edit().putLong("pos_$episodeId", positionMs).apply()
    }

    fun getSavedPlaybackPosition(episodeId: String): Long {
        return prefs.getLong("pos_$episodeId", 0L)
    }

    private fun saveRecentsToPrefs() {
        val array = JSONArray()
        for (ep in _recentEpisodesFlow.value) {
            val obj = JSONObject().apply {
                put("id", ep.id)
                put("showId", ep.showId)
                put("showTitle", ep.showTitle)
                put("title", ep.title)
                put("description", ep.description)
                put("audioUrl", ep.audioUrl)
                put("durationMs", ep.durationMs)
                put("publishDate", ep.publishDate)
                put("artworkUrl", ep.artworkUrl)
            }
            array.put(obj)
        }
        prefs.edit().putString("podcast_recents_json", array.toString()).apply()
    }

    private fun loadRecentsFromPrefs() {
        val json = prefs.getString("podcast_recents_json", null) ?: return
        try {
            val array = JSONArray(json)
            val list = mutableListOf<PodcastEpisode>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PodcastEpisode(
                        id = obj.getString("id"),
                        showId = obj.getString("showId"),
                        showTitle = obj.optString("showTitle", ""),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        audioUrl = obj.getString("audioUrl"),
                        durationMs = obj.optLong("durationMs", 0L),
                        publishDate = obj.optString("publishDate", ""),
                        artworkUrl = obj.optString("artworkUrl", "")
                    )
                )
            }
            _recentEpisodesFlow.value = list
        } catch (_: Exception) {}
    }

    companion object {
        @Volatile
        private var INSTANCE: PodcastRepository? = null

        fun getInstance(context: Context): PodcastRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PodcastRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
