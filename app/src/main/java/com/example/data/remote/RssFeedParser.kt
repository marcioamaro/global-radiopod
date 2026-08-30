package com.example.data.remote

import android.text.Html
import android.util.Xml
import com.example.data.model.PodcastEpisode
import com.example.data.model.PodcastShow
import com.example.util.PodcastChaptersExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

object RssFeedParser {

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 15000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 MediaPod/30.0"

    suspend fun fetchEpisodes(
        feedUrl: String,
        showId: String,
        showTitle: String,
        fallbackArtworkUrl: String = ""
    ): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<PodcastEpisode>()
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            connection = openConnectionWithRedirects(feedUrl)
            if (connection != null && connection.responseCode in 200..299) {
                inputStream = getDecompressedStream(connection.inputStream, connection.contentEncoding)
                parseFeedXml(inputStream, showId, showTitle, fallbackArtworkUrl, episodes)
            }
        } catch (e: Exception) {
            android.util.Log.e("RssFeedParser", "Erro ao carregar feed: $feedUrl", e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
        episodes
    }

    suspend fun parseShowMetadata(feedUrl: String): PodcastShow? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            connection = openConnectionWithRedirects(feedUrl)
            if (connection == null || connection.responseCode !in 200..299) return@withContext null

            inputStream = getDecompressedStream(connection.inputStream, connection.contentEncoding)

            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var title = ""
            var author = ""
            var description = ""
            var artworkUrl = ""
            var inChannelOrFeed = false
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase(Locale.ROOT) ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "channel" || tag == "feed") {
                            inChannelOrFeed = true
                        } else if (inChannelOrFeed && (tag == "item" || tag == "entry")) {
                            // Começou a lista de episódios; os metadados do programa já passaram
                            break
                        } else if (inChannelOrFeed) {
                            when {
                                tag == "title" && title.isBlank() -> {
                                    title = safeNextText(parser)
                                }
                                (tag == "author" || tag == "itunes:author" || tag == "dc:creator") && author.isBlank() -> {
                                    author = safeNextText(parser)
                                }
                                (tag == "description" || tag == "itunes:summary" || tag == "subtitle") && description.isBlank() -> {
                                    description = safeNextText(parser)
                                }
                                (tag == "itunes:image" || tag == "image") && artworkUrl.isBlank() -> {
                                    artworkUrl = parser.getAttributeValue(null, "href")
                                        ?: parser.getAttributeValue(null, "url")
                                        ?: ""
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (title.isNotBlank()) {
                val safeId = "custom_" + Math.abs(feedUrl.hashCode())
                PodcastShow(
                    id = safeId,
                    title = title,
                    author = author,
                    description = cleanHtml(description),
                    feedUrl = feedUrl,
                    artworkUrl = artworkUrl,
                    isCustom = true
                )
            } else null
        } catch (e: Exception) {
            android.util.Log.e("RssFeedParser", "Erro ao extrair metadados: $feedUrl", e)
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
    }

    private fun parseFeedXml(
        inputStream: InputStream,
        showId: String,
        showTitle: String,
        fallbackArtwork: String,
        resultList: MutableList<PodcastEpisode>
    ) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var inItem = false
        var itemTitle = ""
        var itemDesc = ""
        var itemAudioUrl = ""
        var itemDate = ""
        var itemDurationMs = 0L
        var itemArtwork = fallbackArtwork
        var itemChaptersUrl = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.lowercase(Locale.ROOT) ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tag == "item" || tag == "entry") {
                        inItem = true
                        itemTitle = ""
                        itemDesc = ""
                        itemAudioUrl = ""
                        itemDate = ""
                        itemDurationMs = 0L
                        itemArtwork = fallbackArtwork
                        itemChaptersUrl = ""
                    } else if (inItem) {
                        when {
                            tag == "title" -> {
                                itemTitle = safeNextText(parser)
                            }
                            tag == "description" || tag == "itunes:summary" || tag == "summary" || tag == "content:encoded" -> {
                                if (itemDesc.isBlank()) {
                                    itemDesc = safeNextText(parser)
                                }
                            }
                            tag == "pubdate" || tag == "published" || tag == "updated" || tag == "dc:date" -> {
                                if (itemDate.isBlank()) {
                                    itemDate = safeNextText(parser)
                                }
                            }
                            tag == "enclosure" -> {
                                val urlAttr = parser.getAttributeValue(null, "url")
                                    ?: parser.getAttributeValue(null, "href")
                                    ?: ""
                                val typeAttr = parser.getAttributeValue(null, "type") ?: ""
                                if (isAudioEnclosure(urlAttr, typeAttr)) {
                                    itemAudioUrl = urlAttr
                                }
                            }
                            tag == "media:content" -> {
                                val urlAttr = parser.getAttributeValue(null, "url") ?: ""
                                val medium = parser.getAttributeValue(null, "medium") ?: ""
                                val typeAttr = parser.getAttributeValue(null, "type") ?: ""
                                if (medium.equals("audio", ignoreCase = true) || isAudioEnclosure(urlAttr, typeAttr)) {
                                    itemAudioUrl = urlAttr
                                }
                            }
                            tag == "link" -> {
                                val rel = parser.getAttributeValue(null, "rel") ?: ""
                                val typeAttr = parser.getAttributeValue(null, "type") ?: ""
                                val href = parser.getAttributeValue(null, "href") ?: ""
                                if ((rel.equals("enclosure", ignoreCase = true) || isAudioEnclosure(href, typeAttr)) && href.isNotBlank()) {
                                    itemAudioUrl = href
                                }
                            }
                            tag == "itunes:duration" || tag == "duration" -> {
                                val durStr = safeNextText(parser)
                                itemDurationMs = parseDurationToMs(durStr)
                            }
                            tag == "itunes:image" -> {
                                val href = parser.getAttributeValue(null, "href")
                                    ?: parser.getAttributeValue(null, "url")
                                if (!href.isNullOrBlank()) {
                                    itemArtwork = href
                                }
                            }
                            tag == "podcast:chapters" || tag == "chapters" -> {
                                val chUrl = parser.getAttributeValue(null, "url")
                                if (!chUrl.isNullOrBlank()) {
                                    itemChaptersUrl = chUrl
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ((tag == "item" || tag == "entry") && inItem) {
                        inItem = false
                        if (itemAudioUrl.isNotBlank()) {
                            val cleanDescription = cleanHtml(itemDesc)
                            val epId = "ep_" + Math.abs((showId + itemAudioUrl).hashCode())
                            val initialChapters = PodcastChaptersExtractor.extractChaptersFromDescription(cleanDescription, itemDurationMs)
                            resultList.add(
                                PodcastEpisode(
                                    id = epId,
                                    showId = showId,
                                    showTitle = showTitle,
                                    title = if (itemTitle.isNotBlank()) itemTitle else "Episódio",
                                    description = cleanDescription,
                                    audioUrl = itemAudioUrl.trim(),
                                    durationMs = itemDurationMs,
                                    publishDate = formatUniversalDate(itemDate),
                                    artworkUrl = itemArtwork,
                                    chaptersUrl = itemChaptersUrl,
                                    chapters = initialChapters
                                )
                            )
                            if (resultList.size >= 200) {
                                return
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun getDecompressedStream(rawStream: InputStream, contentEncoding: String?): InputStream {
        if ("gzip".equals(contentEncoding, ignoreCase = true)) {
            return GZIPInputStream(rawStream)
        }
        val pushback = java.io.PushbackInputStream(rawStream, 2)
        val header = ByteArray(2)
        val n = pushback.read(header)
        if (n == 2 && header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()) {
            pushback.unread(header, 0, n)
            return GZIPInputStream(pushback)
        } else if (n > 0) {
            pushback.unread(header, 0, n)
        }
        return pushback
    }

    private fun isAudioEnclosure(url: String, type: String): Boolean {
        if (url.isBlank()) return false
        val lowerType = type.lowercase(Locale.ROOT)
        val lowerUrl = url.lowercase(Locale.ROOT)

        if (lowerType.contains("audio") || lowerType.contains("mpeg") || lowerType.contains("mp3") ||
            lowerType.contains("m4a") || lowerType.contains("aac") || lowerType.contains("ogg") ||
            lowerType.contains("octet-stream")) {
            return true
        }

        if (lowerUrl.contains(".mp3") || lowerUrl.contains(".m4a") || lowerUrl.contains(".aac") ||
            lowerUrl.contains(".ogg") || lowerUrl.contains(".wav") || lowerUrl.contains(".opus")) {
            return true
        }

        // Podcasts em CDNs com URLs de streaming / redirect sem extensão direta
        if (lowerUrl.contains("traffic.omny.fm") || lowerUrl.contains("traffic.megaphone.fm") ||
            lowerUrl.contains("anchor.fm") || lowerUrl.contains("dts.podtrac.com") ||
            lowerUrl.contains("feeds.podbean.com") || lowerUrl.contains("pdst.fm") ||
            lowerUrl.contains("chrt.fm") || lowerUrl.contains("simplecast.com") ||
            lowerUrl.contains("spreaker.com") || lowerUrl.contains("cloudfront.net") ||
            lowerUrl.contains("libsyn.com") || lowerUrl.contains("buzzsprout.com") ||
            lowerUrl.contains("transistor.fm") || lowerUrl.contains("sounder.fm") ||
            lowerUrl.contains("acast.com") || lowerUrl.contains("audioboom.com") ||
            lowerUrl.contains("/download/") || lowerUrl.contains("/play/") ||
            lowerUrl.contains("deviante.com.br") || lowerUrl.contains("jovemnerd.com.br")) {
            return true
        }

        return false
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (_: Exception) {
            raw.trim()
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection? {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                setRequestProperty("Accept-Encoding", "gzip")
            }

            val status = conn.responseCode
            if (status in 300..399) {
                val newUrl = conn.getHeaderField("Location") ?: conn.getHeaderField("location")
                conn.disconnect()
                if (newUrl.isNullOrBlank()) return null
                currentUrl = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                    newUrl
                } else {
                    URL(url, newUrl).toString()
                }
                redirects++
            } else {
                return conn
            }
        }
        return null
    }

    private fun parseDurationToMs(durationStr: String): Long {
        return try {
            val clean = durationStr.trim()
            if (clean.contains(":")) {
                val parts = clean.split(":")
                when (parts.size) {
                    2 -> {
                        val m = parts[0].trim().toLong()
                        val s = parts[1].trim().toLong()
                        (m * 60 + s) * 1000L
                    }
                    3 -> {
                        val h = parts[0].trim().toLong()
                        val m = parts[1].trim().toLong()
                        val s = parts[2].trim().toLong()
                        (h * 3600 + m * 60 + s) * 1000L
                    }
                    else -> 0L
                }
            } else {
                clean.toLongOrNull()?.times(1000L) ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun formatUniversalDate(rawDate: String): String {
        if (rawDate.isBlank()) return ""
        val clean = rawDate.trim()

        val rfcFormats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )

        for (fmt in rfcFormats) {
            try {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val date = fmt.parse(clean)
                if (date != null) {
                    val outFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    return outFmt.format(date)
                }
            } catch (_: Exception) {}
        }

        return if (clean.length > 16) clean.substring(0, 16) else clean
    }
}
