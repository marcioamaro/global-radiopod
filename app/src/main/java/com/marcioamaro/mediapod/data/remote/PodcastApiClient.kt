package com.marcioamaro.mediapod.data.remote

import com.marcioamaro.mediapod.data.model.PodcastShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object PodcastApiClient {

    private const val TIMEOUT_MS = 12000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 MediaPod/30.0"

    suspend fun searchPodcasts(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        // iTunes permite até 200 itens por chamada. Buscamos na loja BR e Global/US para abranger o máximo de resultados.
        val targetUrlBR = "https://itunes.apple.com/search?media=podcast&entity=podcast&term=$encoded&country=BR&limit=200"
        val targetUrlGlobal = "https://itunes.apple.com/search?media=podcast&entity=podcast&term=$encoded&limit=200"
        val showsBR = fetchShowsFromItunesUrl(targetUrlBR)
        val showsGlobal = fetchShowsFromItunesUrl(targetUrlGlobal)

        val combinedShows = (showsBR + showsGlobal).distinctBy { it.feedUrl.lowercase() }

        // Ordenação inteligente: correspondência de título primeiro, depois contagem de episódios
        val qLower = query.trim().lowercase(Locale.ROOT)
        combinedShows.sortedWith(
            compareByDescending<PodcastShow> { it.title.lowercase(Locale.ROOT).startsWith(qLower) }
                .thenByDescending { it.title.lowercase(Locale.ROOT).contains(qLower) }
                .thenByDescending { it.episodeCount }
        )
    }

    suspend fun fetchPodcastsByGenre(genreId: String, categoryName: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val targetUrlBR = "https://itunes.apple.com/search?media=podcast&entity=podcast&genreId=$genreId&country=BR&limit=200"
        val targetUrlGlobal = "https://itunes.apple.com/search?media=podcast&entity=podcast&genreId=$genreId&limit=200"
        val shows = (fetchShowsFromItunesUrl(targetUrlBR) + fetchShowsFromItunesUrl(targetUrlGlobal)).distinctBy { it.feedUrl.lowercase() }
        shows.map { it.copy(category = categoryName) }
    }

    suspend fun fetchTopShowsByCountry(countryCode: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val country = countryCode.uppercase(Locale.ROOT)
        val targetUrl = "https://itunes.apple.com/search?media=podcast&entity=podcast&term=podcast&country=$country&limit=200"
        fetchShowsFromItunesUrl(targetUrl)
    }

    private fun fetchShowsFromItunesUrl(targetUrl: String): List<PodcastShow> {
        val results = mutableListOf<PodcastShow>()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val jsonArray = root.optJSONArray("results")
                if (jsonArray != null) {
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val feedUrl = item.optString("feedUrl", "").trim()
                        val title = item.optString("collectionName", "").trim()
                        val trackCount = item.optInt("trackCount", 0)

                        // Descartar podcasts vazios ou sem link de feed
                        if (feedUrl.isNotBlank() && title.isNotBlank() && trackCount > 0) {
                            val id = "itunes_" + item.optLong("collectionId", Math.abs(feedUrl.hashCode()).toLong())
                            val author = item.optString("artistName", "").trim()
                            val artwork = item.optString("artworkUrl600", item.optString("artworkUrl100", ""))
                            val genre = item.optString("primaryGenreName", "Geral")
                            val country = item.optString("country", "GLOBAL").uppercase(Locale.ROOT)
                            val rawRelease = item.optString("releaseDate", "").trim()
                            val releaseDate = if (rawRelease.length >= 10) {
                                try {
                                    val y = rawRelease.substring(0, 4)
                                    val m = rawRelease.substring(5, 7)
                                    val d = rawRelease.substring(8, 10)
                                    "$d/$m/$y"
                                } catch (_: Exception) {
                                    rawRelease.substring(0, 10)
                                }
                            } else ""

                            results.add(
                                PodcastShow(
                                    id = id,
                                    title = title,
                                    author = author,
                                    description = genre,
                                    feedUrl = feedUrl,
                                    artworkUrl = artwork,
                                    country = country,
                                    category = genre,
                                    episodeCount = trackCount,
                                    latestReleaseDate = releaseDate
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("PodcastApiClient", "Erro na API do iTunes para URL: $targetUrl", e)
        } finally {
            connection?.disconnect()
        }
        return results
    }
}
