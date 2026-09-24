package com.marcioamaro.mediapod.util

import com.marcioamaro.mediapod.data.model.PodcastShow
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.data.remote.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed class ValidationResult {
    data class RadioSuccess(val station: RadioStation) : ValidationResult()
    data class PodcastSuccess(val show: PodcastShow) : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

object UrlStreamValidator {

    suspend fun validateRadioUrl(customName: String, rawUrl: String): ValidationResult = withContext(Dispatchers.IO) {
        val trimmedUrl = rawUrl.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext ValidationResult.Error("URL inválida: O endereço deve começar com http:// ou https://")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(trimmedUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:115.0) Gecko/115.0 Firefox/115.0")
                setRequestProperty("Icy-MetaData", "1")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..399) {
                return@withContext ValidationResult.Error("Servidor retornou erro HTTP $responseCode. Verifique se o stream está online.")
            }

            val contentType = connection.contentType?.lowercase() ?: ""
            val icyName = connection.getHeaderField("icy-name")
            val icyBr = connection.getHeaderField("icy-br")?.toIntOrNull() ?: 128

            val isAudioContent = contentType.contains("audio") ||
                    contentType.contains("application/ogg") ||
                    contentType.contains("application/vnd.apple.mpegurl") ||
                    contentType.contains("application/x-mpegurl") ||
                    contentType.contains("audio/x-scpls") ||
                    connection.getHeaderField("icy-metaint") != null ||
                    trimmedUrl.contains(".mp3", ignoreCase = true) ||
                    trimmedUrl.contains(".aac", ignoreCase = true) ||
                    trimmedUrl.contains(".m3u8", ignoreCase = true) ||
                    trimmedUrl.contains("/stream", ignoreCase = true) ||
                    trimmedUrl.contains("/live", ignoreCase = true)

            if (!isAudioContent) {
                return@withContext ValidationResult.Error("URL incompatível: O endereço não retornou um formato de transmissão de áudio suportado.")
            }

            val finalName = when {
                customName.isNotBlank() -> customName.trim()
                !icyName.isNullOrBlank() -> icyName.trim()
                else -> "Rádio Personalizada"
            }

            val station = RadioStation(
                id = "custom_" + UUID.randomUUID().toString().take(8),
                name = finalName,
                streamUrl = trimmedUrl,
                alternativeStreamUrls = emptyList(),
                favicon = "",
                country = "Personalizada",
                countryCode = "BR",
                state = "",
                city = "Custom Stream",
                tags = "personalizada,stream",
                bitrate = icyBr,
                codec = if (contentType.contains("aac")) "AAC" else "MP3",
                votes = 999
            )

            ValidationResult.RadioSuccess(station)
        } catch (e: Exception) {
            android.util.Log.e("UrlStreamValidator", "Falha ao validar URL de rádio: $trimmedUrl", e)
            ValidationResult.Error("Não foi possível conectar ao endereço informado: ${e.localizedMessage ?: "Verifique sua conexão ou a URL."}")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun validatePodcastUrl(customName: String, rawUrl: String): ValidationResult = withContext(Dispatchers.IO) {
        val trimmedUrl = rawUrl.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext ValidationResult.Error("URL inválida: O endereço deve começar com http:// ou https://")
        }

        // 1. Tenta fazer parse como Feed RSS
        val showFromRss = RssFeedParser.parseShowMetadata(trimmedUrl)
        if (showFromRss != null && showFromRss.title.isNotBlank()) {
            val episodes = RssFeedParser.fetchEpisodes(trimmedUrl, showFromRss.id, showFromRss.title, showFromRss.artworkUrl)
            if (episodes.isNotEmpty()) {
                val finalShow = if (customName.isNotBlank()) {
                    showFromRss.copy(title = customName.trim())
                } else showFromRss
                return@withContext ValidationResult.PodcastSuccess(finalShow)
            }
        }

        // 2. Se não for RSS, testa se é link direto de áudio (MP3/M4A/AAC)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(trimmedUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MediaPod/26.0 (Android; RetroPod Player)")
            }

            val responseCode = connection.responseCode
            val contentType = connection.contentType?.lowercase() ?: ""
            if (responseCode in 200..399 && (contentType.contains("audio") || trimmedUrl.contains(".mp3", ignoreCase = true) || trimmedUrl.contains(".m4a", ignoreCase = true))) {
                val safeTitle = if (customName.isNotBlank()) customName.trim() else "Podcast Personalizado"
                val directShow = PodcastShow(
                    id = "custom_" + Math.abs(trimmedUrl.hashCode()),
                    title = safeTitle,
                    author = "Feed Direto",
                    description = "Áudio personalizado adicionado pelo usuário",
                    feedUrl = trimmedUrl,
                    artworkUrl = "",
                    category = "Personalizado",
                    isCustom = true
                )
                return@withContext ValidationResult.PodcastSuccess(directShow)
            }
        } catch (e: Exception) {
            android.util.Log.e("UrlStreamValidator", "Falha ao validar URL de podcast: $trimmedUrl", e)
        } finally {
            connection?.disconnect()
        }

        ValidationResult.Error("URL inválida ou incompatível: Não foi possível detectar um feed RSS de podcast ou arquivo de áudio válido.")
    }
}
