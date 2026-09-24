package com.marcioamaro.mediapod.data.repository

import android.content.Context
import com.marcioamaro.mediapod.data.model.RadioStation
import com.squareup.moshi.JsonReader
import okio.buffer
import okio.source
import java.io.InputStream
import java.security.MessageDigest

/** One bundled source for search, country lists and playback. Parsed lazily off the UI thread. */
object RadioCatalog {
    @Volatile private var openAsset: (() -> InputStream)? = null

    fun initialize(context: Context) {
        val app = context.applicationContext
        openAsset = { CatalogUpdates.open(app, "radio_catalog.json") }
    }

    val stations: List<RadioStation> by lazy {
        val input = openAsset?.invoke() ?: requireNotNull(javaClass.classLoader?.getResourceAsStream("radio_catalog.json")) {
            "RadioCatalog must be initialized with application assets"
        }
        input.use(::read)
    }

    val version: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        stations.forEach { digest.update((it.toString() + "\n").toByteArray(Charsets.UTF_8)) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun read(input: InputStream): List<RadioStation> {
        val result = ArrayList<RadioStation>()
        JsonReader.of(input.source().buffer()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() != "stations") { reader.skipValue(); continue }
                reader.beginArray()
                while (reader.hasNext()) {
                    require(result.size < 100_000) { "Catalog exceeds station limit" }
                    result.add(readStation(reader))
                }
                reader.endArray()
            }
            reader.endObject()
        }
        require(result.isNotEmpty()) { "Empty radio catalog" }
        require(result.map { it.id }.toSet().size == result.size) { "Duplicate radio identities" }
        return result
    }

    private fun readStation(reader: JsonReader): RadioStation {
        val fields = mutableMapOf<String, String>()
        var alternatives = emptyList<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when {
                key == "alternative_stream_urls" && reader.peek() == JsonReader.Token.BEGIN_ARRAY -> {
                    val urls = mutableListOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) urls.add(reader.nextString())
                    reader.endArray()
                    alternatives = urls
                }
                key == "tags" && reader.peek() == JsonReader.Token.BEGIN_ARRAY -> {
                    val tags = mutableListOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) tags.add(reader.nextString())
                    reader.endArray()
                    fields[key] = tags.joinToString(", ")
                }
                key in setOf("id", "name", "primary_stream_url", "favicon_url", "favicon", "homepage", "tags",
                    "country", "country_code", "state", "city", "codec", "bitrate_kbps", "votes") &&
                    reader.peek() in setOf(JsonReader.Token.STRING, JsonReader.Token.NUMBER) -> fields[key] = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        require(!fields["id"].isNullOrBlank() && !fields["name"].isNullOrBlank() &&
            fields["primary_stream_url"]?.startsWith("http") == true) { "Invalid radio catalog entry" }
        val stream = java.net.URL(fields.getValue("primary_stream_url"))
        require(stream.protocol in setOf("http", "https") && stream.host.isNotBlank() && stream.userInfo == null) { "Invalid radio stream URL" }
        return RadioStation(id = fields.getValue("id"), name = fields.getValue("name"),
            streamUrl = fields.getValue("primary_stream_url"), alternativeStreamUrls = alternatives,
            favicon = fields["favicon_url"] ?: fields["favicon"].orEmpty(), homepage = fields["homepage"].orEmpty(),
            tags = fields["tags"].orEmpty(), country = fields["country"].orEmpty(), countryCode = fields["country_code"].orEmpty(),
            state = fields["state"].orEmpty(), city = fields["city"].orEmpty(), codec = fields["codec"].orEmpty(),
            bitrate = fields["bitrate_kbps"]?.toIntOrNull() ?: 0, votes = fields["votes"]?.toIntOrNull() ?: 0)
    }
}
