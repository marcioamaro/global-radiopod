package com.example.util

import android.util.Xml
import com.example.data.model.PodcastShow
import org.xmlpull.v1.XmlPullParser
import java.io.StringWriter
import java.net.URI

object PodcastOpml {
    fun parse(bytes: ByteArray): List<PodcastShow> {
        require(bytes.size <= 2 * 1024 * 1024) { "OPML excede 2 MB" }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "Declarações XML não permitidas" }
        val parser = Xml.newPullParser()
        parser.setInput(text.reader())
        val result = linkedMapOf<String, PodcastShow>()
        var rootSeen = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            require(parser.depth <= 64)
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.depth == 1) { require(parser.name == "opml"); rootSeen = true }
                if (parser.name == "outline") {
                    val feed = parser.getAttributeValue(null, "xmlUrl")
                    if (feed != null) {
                        val uri = URI(feed.trim()).normalize()
                        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
                        require(uri.port == -1 || uri.port in 1..65535)
                        val canonical = buildString {
                            append(uri.scheme.lowercase()); append("://"); append(uri.host.lowercase())
                            if (uri.port != -1) { append(':'); append(uri.port) }
                            append(uri.rawPath.ifBlank { "/" })
                            uri.rawQuery?.let { append('?'); append(it) }
                        }
                        val title = (parser.getAttributeValue(null, "title") ?: parser.getAttributeValue(null, "text") ?: uri.host).take(300)
                        result.putIfAbsent(canonical, PodcastShow(id = "opml_" + java.util.UUID.nameUUIDFromBytes(canonical.toByteArray()),
                            title = title, author = "", description = "", feedUrl = canonical, artworkUrl = "", isCustom = true))
                        require(result.size <= 2000)
                    }
                }
            }
            parser.next()
        }
        require(rootSeen && result.isNotEmpty()) { "Nenhuma assinatura válida" }
        return result.values.toList()
    }

    fun export(shows: List<PodcastShow>): String {
        val output = StringWriter()
        val xml = Xml.newSerializer().apply { setOutput(output) }
        xml.startDocument("UTF-8", true)
        xml.startTag(null, "opml").attribute(null, "version", "2.0")
        xml.startTag(null, "head").startTag(null, "title").text("MediaPod").endTag(null, "title").endTag(null, "head")
        xml.startTag(null, "body")
        shows.distinctBy { it.feedUrl }.forEach {
            xml.startTag(null, "outline").attribute(null, "type", "rss").attribute(null, "text", it.title)
                .attribute(null, "title", it.title).attribute(null, "xmlUrl", it.feedUrl).endTag(null, "outline")
        }
        xml.endTag(null, "body").endTag(null, "opml").endDocument()
        return output.toString()
    }
}
