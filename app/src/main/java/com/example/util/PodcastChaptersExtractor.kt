package com.example.util

import com.example.data.model.PodcastChapter
import com.example.data.model.PodcastEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object PodcastChaptersExtractor {

    private val TIMESTAMP_REGEX = Pattern.compile(
        """(?:^|\n)\s*[\(\[]?(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[\)\]]?\s*[-–—:.]?\s*(.+)"""
    )

    /**
     * Extrai capítulos assincronamente:
     * 1. Se o episódio já tiver capítulos embutidos/processados, retorna-os.
     * 2. Se possuir URL Podcasting 2.0 (<podcast:chapters>), baixa e converte o JSON.
     * 3. Fallback: analisa a descrição do episódio procurando marcações de tempo.
     */
    suspend fun getOrFetchChapters(episode: PodcastEpisode): List<PodcastChapter> = withContext(Dispatchers.IO) {
        if (episode.chapters.isNotEmpty()) {
            return@withContext episode.chapters
        }

        // 1. Podcasting 2.0 Chapters JSON
        if (episode.chaptersUrl.isNotBlank()) {
            try {
                val remoteChapters = fetchPodcasting2Chapters(episode.chaptersUrl)
                if (remoteChapters.isNotEmpty()) {
                    return@withContext remoteChapters
                }
            } catch (e: Exception) {
                android.util.Log.d("PodcastChaptersExtractor", "Failed to fetch Podcasting 2.0 chapters: ${e.message}")
            }
        }

        // 2. Fallback: regex na descrição
        if (episode.description.isNotBlank()) {
            val descriptionChapters = extractChaptersFromDescription(episode.description, episode.durationMs)
            if (descriptionChapters.isNotEmpty()) {
                return@withContext descriptionChapters
            }
        }

        emptyList()
    }

    /**
     * Faz o download e parse de arquivos JSON no formato Podcasting 2.0
     */
    suspend fun fetchPodcasting2Chapters(chaptersUrl: String): List<PodcastChapter> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(chaptersUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MediaPod/26.0 (Android; Podcasting 2.0 Chapters)")
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val chaptersArray = root.optJSONArray("chapters") ?: return@withContext emptyList()

            val list = mutableListOf<PodcastChapter>()
            for (i in 0 until chaptersArray.length()) {
                val chObj = chaptersArray.getJSONObject(i)
                val startTimeRaw = chObj.optDouble("startTime", 0.0)
                val startTimeMs = (startTimeRaw * 1000.0).toLong().coerceAtLeast(0L)
                val title = chObj.optString("title", "Capítulo ${i + 1}").trim()
                val img = chObj.optString("img", chObj.optString("image", ""))
                val urlLink = chObj.optString("url", "")
                val endTimeRaw = chObj.optDouble("endTime", 0.0)
                val endTimeMs = if (endTimeRaw > 0) (endTimeRaw * 1000.0).toLong() else 0L

                list.add(
                    PodcastChapter(
                        title = title,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        imageUrl = img,
                        linkUrl = urlLink
                    )
                )
            }

            // Ordena cronologicamente e preenche endTimeMs se faltar
            val sorted = list.sortedBy { it.startTimeMs }
            val result = mutableListOf<PodcastChapter>()
            for (idx in sorted.indices) {
                val current = sorted[idx]
                val end = if (current.endTimeMs > current.startTimeMs) {
                    current.endTimeMs
                } else if (idx < sorted.size - 1) {
                    sorted[idx + 1].startTimeMs
                } else {
                    0L
                }
                result.add(current.copy(endTimeMs = end))
            }
            result
        } catch (e: Exception) {
            android.util.Log.w("PodcastChaptersExtractor", "Error parsing Podcasting 2.0 JSON: $chaptersUrl", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Extrai capítulos da descrição com base em marcações de tempo (hh:mm:ss ou mm:ss)
     */
    fun extractChaptersFromDescription(description: String, episodeDurationMs: Long = 0L): List<PodcastChapter> {
        val list = mutableListOf<PodcastChapter>()
        try {
            val matcher = TIMESTAMP_REGEX.matcher(description)
            while (matcher.find()) {
                val hoursStr = matcher.group(1)
                val minutesStr = matcher.group(2)
                val secondsStr = matcher.group(3)
                val titleRaw = matcher.group(4)?.trim().orEmpty()

                if (minutesStr != null && secondsStr != null && titleRaw.isNotBlank()) {
                    val h = hoursStr?.toLongOrNull() ?: 0L
                    val m = minutesStr.toLongOrNull() ?: 0L
                    val s = secondsStr.toLongOrNull() ?: 0L
                    val timeMs = (h * 3600 + m * 60 + s) * 1000L

                    // Limpa marcadores e pontuação extra no início do título
                    val cleanTitle = titleRaw.replaceFirst("^[-–—:.]+\\s*".toRegex(), "").trim()
                    if (cleanTitle.isNotBlank()) {
                        list.add(
                            PodcastChapter(
                                title = cleanTitle,
                                startTimeMs = timeMs
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        if (list.size < 2) return emptyList()

        val sorted = list.sortedBy { it.startTimeMs }
        val result = mutableListOf<PodcastChapter>()
        for (idx in sorted.indices) {
            val current = sorted[idx]
            val end = if (idx < sorted.size - 1) {
                sorted[idx + 1].startTimeMs
            } else {
                episodeDurationMs.coerceAtLeast(current.startTimeMs)
            }
            result.add(current.copy(endTimeMs = end))
        }
        return result
    }
}
