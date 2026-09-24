package com.marcioamaro.mediapod.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.marcioamaro.mediapod.data.model.PodcastCategory
import com.marcioamaro.mediapod.data.model.PodcastCountry
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.model.PodcastShow
import com.marcioamaro.mediapod.data.remote.PodcastApiClient
import com.marcioamaro.mediapod.data.remote.RssFeedParser
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
    private val _playedEpisodeIds = MutableStateFlow(prefs.all.filter { it.key.startsWith("played_") && it.value == true }
        .keys.map { it.removePrefix("played_") }.toSet())
    val playedEpisodeIds: StateFlow<Set<String>> = _playedEpisodeIds.asStateFlow()

    private val _favoritesFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val favoritesFlow: StateFlow<List<PodcastShow>> = _favoritesFlow.asStateFlow()
    val favoriteShowsFlow: StateFlow<List<PodcastShow>> get() = _favoritesFlow.asStateFlow()

    private val _recentEpisodesFlow = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val recentEpisodesFlow: StateFlow<List<PodcastEpisode>> = _recentEpisodesFlow.asStateFlow()

    private val _recentShowsFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val recentShowsFlow: StateFlow<List<PodcastShow>> = _recentShowsFlow.asStateFlow()

    private val _customShowsFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val customShowsFlow: StateFlow<List<PodcastShow>> = _customShowsFlow.asStateFlow()

    private val _subscriptionsFlow = MutableStateFlow<List<PodcastShow>>(emptyList())
    val subscriptionsFlow: StateFlow<List<PodcastShow>> = _subscriptionsFlow.asStateFlow()

    fun getCustomPodcasts(): List<PodcastShow> = _customShowsFlow.value

    fun getCuratedShows(): List<PodcastShow> = _curatedShows.toList()

    fun reloadFromStorage() {
        loadFavoritesFromPrefs()
        loadRecentsFromPrefs()
        loadCustomShowsFromPrefs()
        loadSubscriptionsFromPrefs()
        _playedEpisodeIds.value = prefs.all.filter { it.key.startsWith("played_") && it.value == true }
            .keys.map { it.removePrefix("played_") }.toSet()
    }

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

    // Country browsing is a catalog view, separate from source-published rankings.
    suspend fun getTopPodcasts(countryCode: String, limit: Int = 500): List<PodcastShow> = withContext(Dispatchers.Default) {
        val global = countryCode.equals("GLOBAL", true) || countryCode.equals("ALL", true)
        val favorites = _favoritesFlow.value.map { it.id }.toSet()
        val shows = _curatedShows.filter { global || it.country.equals(countryCode, true) }
            .distinctBy { it.feedUrl }.sortedBy { it.title.lowercase(Locale.ROOT) }
            .map { it.copy(isFavorite = it.id in favorites) }
        if (limit > 0) shows.take(limit) else shows
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
        PodcastCountry("BR", "Brasil", "[BR]"),
        PodcastCountry("US", "Estados Unidos", "[US]"),
        PodcastCountry("GB", "Reino Unido", "[GB]"),
        PodcastCountry("PT", "Portugal", "[PT]"),
        PodcastCountry("ES", "Espanha / Latam", "[ES]")
    )

    init {
        loadCatalogFromAssets()
        loadFavoritesFromPrefs()
        loadRecentsFromPrefs()
        loadCustomShowsFromPrefs()
        loadSubscriptionsFromPrefs()
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

    fun searchCatalog(query: String): List<PodcastShow> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it) }
        val favIds = _favoritesFlow.value.map { it.id }.toSet()
        return (_curatedShows + _customShowsFlow.value + _favoritesFlow.value + _subscriptionsFlow.value).filter { show ->
            val corpus = com.marcioamaro.mediapod.util.RadioSearchEngine.normalize("${show.title} ${show.author} ${show.category} ${show.description}")
            tokens.all { token -> corpus.contains(token) }
        }.filter { it.feedUrl.isNotBlank() }.distinctBy { it.feedUrl.trim() }
            .map { it.copy(isFavorite = it.id in favIds, rankPosition = null) }
    }

    suspend fun searchPodcasts(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val localMatches = searchCatalog(query)
        if (query.isBlank()) return@withContext localMatches
        val favIds = _favoritesFlow.value.map { it.id }.toSet()

        // 2. Busca online global na API do iTunes
        val onlineMatches = try {
            PodcastApiClient.searchPodcasts(query)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

        (localMatches + onlineMatches)
            .distinctBy { it.feedUrl.trim() }
            .filter { it.feedUrl.isNotBlank() }
            .map { it.copy(isFavorite = favIds.contains(it.id), rankPosition = null) }
    }

    suspend fun getEpisodesForShow(show: PodcastShow): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        episodeCache[show.id]?.let { return@withContext it.map { ep -> ep.copy(isPlayed = isEpisodePlayed(ep.id)) } }
        var episodes = RssFeedParser.fetchEpisodes(show.feedUrl, show.id, show.title, show.artworkUrl)

        // Auto-Cura Dinâmica: Se o feed estiver quebrado, migrado ou vazio, busca link oficial atualizado na API do iTunes
        if (episodes.isEmpty()) {
            try {
                val liveResults = PodcastApiClient.searchPodcasts(show.title)
                val match = liveResults.firstOrNull {
                    com.marcioamaro.mediapod.util.PodcastIdentity.samePublisherAndTitle(show, it)
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
        episodes.map { it.copy(isPlayed = isEpisodePlayed(it.id)) }
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

    // --- Assinaturas de Podcasts (Subscriptions) & Badges ---

    fun isSubscribed(showId: String): Boolean {
        return _subscriptionsFlow.value.any { it.id == showId || it.feedUrl.equals(showId, ignoreCase = true) }
    }

    fun subscribe(show: PodcastShow) {
        val current = _subscriptionsFlow.value.toMutableList()
        val exists = current.any { it.id == show.id || it.feedUrl.equals(show.feedUrl, ignoreCase = true) }
        if (!exists) {
            current.add(0, show.copy(isSubscribed = true))
            _subscriptionsFlow.value = current
            saveSubscriptionsToPrefs()
        }
    }

    fun unsubscribe(showId: String) {
        val current = _subscriptionsFlow.value.toMutableList()
        current.removeAll { it.id == showId || it.feedUrl.equals(showId, ignoreCase = true) }
        _subscriptionsFlow.value = current
        saveSubscriptionsToPrefs()
    }

    fun toggleSubscription(show: PodcastShow) {
        if (isSubscribed(show.id)) {
            unsubscribe(show.id)
        } else {
            subscribe(show)
        }
    }

    fun markEpisodePlayed(episodeId: String, played: Boolean = true) {
        if (episodeId.isBlank()) return
        prefs.edit().putBoolean("played_$episodeId", played).apply()
        _playedEpisodeIds.value = if (played) _playedEpisodeIds.value + episodeId else _playedEpisodeIds.value - episodeId
    }

    fun restorePlayedEpisodes(ids: Set<String>) {
        val merged = _playedEpisodeIds.value + ids.filter { it.isNotBlank() }
        val editor = prefs.edit()
        merged.forEach { editor.putBoolean("played_$it", true) }
        editor.apply()
        _playedEpisodeIds.value = merged
    }

    fun isEpisodePlayed(episodeId: String): Boolean {
        return prefs.getBoolean("played_$episodeId", false)
    }

    fun getUnreadCount(episodes: List<PodcastEpisode>): Int {
        return episodes.count { !isEpisodePlayed(it.id) }
    }

    private fun saveSubscriptionsToPrefs() {
        val array = JSONArray()
        for (item in _subscriptionsFlow.value) {
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
        prefs.edit().putString("podcast_subscriptions_json", array.toString()).commit()
    }

    private fun loadSubscriptionsFromPrefs() {
        val json = prefs.getString("podcast_subscriptions_json", null) ?: return
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
                        isSubscribed = true
                    )
                )
            }
            _subscriptionsFlow.value = list
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

        @androidx.annotation.VisibleForTesting
        fun clearInstanceForTesting() {
            INSTANCE = null
        }
    }
}
