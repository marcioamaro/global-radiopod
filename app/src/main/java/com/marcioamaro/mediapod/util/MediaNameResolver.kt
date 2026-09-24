package com.marcioamaro.mediapod.util

import com.marcioamaro.mediapod.data.remote.RssFeedParser
import com.marcioamaro.mediapod.data.repository.CuratedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object MediaNameResolver {

    private val URL_REGEX = Pattern.compile(
        "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val HTML_TITLE_REGEX = Pattern.compile(
        "<title(?:\\s+[^>]*)?>([^<]+)</title>",
        Pattern.CASE_INSENSITIVE
    )

    private val JSON_TITLE_REGEX = Pattern.compile(
        "\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
        Pattern.CASE_INSENSITIVE
    )

    private val UNICODE_ESCAPE_REGEX = Pattern.compile(
        "\\\\u([0-9a-fA-F]{4})"
    )

    /**
     * Extrai uma URL limpa a partir de qualquer texto (por exemplo, mensagens com texto compartilhado).
     */
    fun extractUrlFromText(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""

        val matcher = URL_REGEX.matcher(trimmed)
        if (matcher.find()) {
            var url = matcher.group(1).orEmpty()
            // Limpa pontuações adjacentes que possam ter sido capturadas
            while (url.isNotEmpty() && (url.endsWith(")") || url.endsWith("]") || url.endsWith(">") ||
                        url.endsWith("\"") || url.endsWith("'") || url.endsWith(".") || url.endsWith(","))) {
                url = url.substring(0, url.length - 1)
            }
            return url
        }

        // Se o texto não começou com http mas é algo como youtu.be/xxx ou www.xxx
        if (trimmed.startsWith("youtu.be/", ignoreCase = true) ||
            trimmed.startsWith("www.youtube.com/", ignoreCase = true) ||
            trimmed.startsWith("youtube.com/", ignoreCase = true) ||
            trimmed.startsWith("www.", ignoreCase = true)) {
            return "https://$trimmed"
        }

        return trimmed
    }

    /**
     * Tenta resolver o nome da rádio a partir da URL do stream.
     */
    suspend fun resolveRadioName(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = extractUrlFromText(rawUrl)
        if (url.isBlank() || (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))) {
            return@withContext null
        }

        // 1. Procura nas rádios curadas locais
        try {
            val normalizedUrl = url.trimEnd('/')
            val curated = CuratedData.CURATED_GLOBAL_STATIONS.find { station ->
                station.streamUrl.trimEnd('/').equals(normalizedUrl, ignoreCase = true) ||
                        station.alternativeStreamUrls.any { it.trimEnd('/').equals(normalizedUrl, ignoreCase = true) }
            }
            if (curated != null && curated.name.isNotBlank()) {
                return@withContext curated.name
            }
        } catch (_: Exception) {}

        // 2. Conecta ao stream com suporte a ICY Metadata
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 MediaPod/30.0")
                setRequestProperty("Icy-MetaData", "1")
                setRequestProperty("Accept", "audio/*,*/*;q=0.9")
            }

            // Headers ICY padrão de servidores Shoutcast / Icecast
            val icyName = connection.getHeaderField("icy-name")
                ?: connection.getHeaderField("ice-name")
                ?: connection.getHeaderField("x-audiocast-name")
                ?: connection.getHeaderField("icy-description")

            if (!icyName.isNullOrBlank()) {
                val clean = cleanStationName(icyName)
                if (clean.isNotBlank()) {
                    return@withContext clean
                }
            }

            // Se retornar HTML (ex: página do webplayer ou streaming server)
            val contentType = connection.contentType?.lowercase().orEmpty()
            if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var lines = 0
                var line: String? = reader.readLine()
                while (line != null && lines < 60) {
                    sb.append(line).append(" ")
                    lines++
                    if (sb.contains("</title>", ignoreCase = true)) break
                    line = reader.readLine()
                }
                val titleMatcher = HTML_TITLE_REGEX.matcher(sb.toString())
                if (titleMatcher.find()) {
                    val htmlTitle = cleanHtmlTitle(titleMatcher.group(1).orEmpty())
                    if (htmlTitle.isNotBlank()) {
                        return@withContext htmlTitle
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }

        // 3. Fallback: formata a partir do caminho da URL se tiver nome amigável
        try {
            val uri = java.net.URI(url)
            val path = uri.path.orEmpty().trim('/')
            if (path.isNotEmpty()) {
                val segments = path.split('/')
                val last = segments.lastOrNull()?.substringBeforeLast('.').orEmpty()
                if (last.length in 3..25 && !last.matches(Regex("^[0-9a-fA-F\\-]+$")) && !last.equals("stream", ignoreCase = true) && !last.equals("live", ignoreCase = true)) {
                    val formatted = last.replace('-', ' ').replace('_', ' ').split(' ')
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    if (formatted.isNotBlank()) {
                        return@withContext formatted
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Tenta resolver o nome do Podcast a partir do feed RSS ou link de áudio.
     */
    suspend fun resolvePodcastName(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = extractUrlFromText(rawUrl)
        if (url.isBlank() || (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))) {
            return@withContext null
        }

        // 1. Tenta extrair título pelo parser oficial de RSS
        try {
            val show = RssFeedParser.parseShowMetadata(url)
            if (show != null && show.title.isNotBlank()) {
                return@withContext show.title.trim()
            }
        } catch (_: Exception) {}

        // 2. Tenta extrair título de página HTML caso seja link de episódio ou web feed
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 MediaPod/30.0")
            }

            val contentType = connection.contentType?.lowercase().orEmpty()
            if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var lines = 0
                var line: String? = reader.readLine()
                while (line != null && lines < 60) {
                    sb.append(line).append(" ")
                    lines++
                    if (sb.contains("</title>", ignoreCase = true)) break
                    line = reader.readLine()
                }
                val titleMatcher = HTML_TITLE_REGEX.matcher(sb.toString())
                if (titleMatcher.find()) {
                    val htmlTitle = cleanHtmlTitle(titleMatcher.group(1).orEmpty())
                    if (htmlTitle.isNotBlank()) {
                        return@withContext htmlTitle
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }

        null
    }

    /**
     * Tenta resolver o título do vídeo do YouTube via API pública oEmbed oficial.
     */
    suspend fun resolveYouTubeTitle(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val videoId = YouTubeUrlValidator.extractVideoId(rawUrl) ?: return@withContext null

        var connection: HttpURLConnection? = null
        try {
            val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            connection = (URL(oEmbedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) MediaPod/30.0")
            }

            if (connection.responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val title = extractTitleFromJson(responseText)
                if (title.isNotBlank()) {
                    return@withContext title
                }
            }
        } catch (_: Exception) {
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }

        null
    }

    private fun extractTitleFromJson(json: String): String {
        try {
            val matcher = JSON_TITLE_REGEX.matcher(json)
            if (matcher.find()) {
                var rawTitle = matcher.group(1).orEmpty()
                rawTitle = rawTitle.replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\")
                val unicodeMatcher = UNICODE_ESCAPE_REGEX.matcher(rawTitle)
                val sb = StringBuffer()
                while (unicodeMatcher.find()) {
                    val hex = unicodeMatcher.group(1)
                    val codePoint = hex?.toIntOrNull(16) ?: 0
                    if (codePoint > 0) {
                        unicodeMatcher.appendReplacement(sb, codePoint.toChar().toString())
                    }
                }
                unicodeMatcher.appendTail(sb)
                val decoded = sb.toString().trim()
                if (decoded.isNotBlank()) {
                    return decoded
                }
            }
        } catch (_: Exception) {}

        try {
            val obj = JSONObject(json)
            val t = obj.optString("title", "").trim()
            if (t.isNotBlank()) return t
        } catch (_: Exception) {}

        return ""
    }

    private fun cleanStationName(name: String): String {
        var clean = name.trim()
        val ignoreKeywords = listOf("unnamed", "icecast", "shoutcast", "mountpoint", "stream", "live stream", "no title")
        if (ignoreKeywords.any { clean.equals(it, ignoreCase = true) }) {
            return ""
        }
        return clean
    }

    private fun cleanHtmlTitle(title: String): String {
        var clean = title.replace("\r", " ").replace("\n", " ").trim()
        // Remove sufixos comuns em títulos de rádio/player web
        val suffixes = listOf(
            " - Ao Vivo", " | Ao Vivo", " - Ouça Ao Vivo", " - Rádio Ao Vivo",
            " - YouTube", " | YouTube", " | Podcast on Spotify", " - Apple Podcasts"
        )
        for (suffix in suffixes) {
            if (clean.endsWith(suffix, ignoreCase = true)) {
                clean = clean.substring(0, clean.length - suffix.length).trim()
            }
        }
        return clean
    }
}
