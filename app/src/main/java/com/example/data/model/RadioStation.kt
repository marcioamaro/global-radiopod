package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RadioStationDto(
    @Json(name = "stationuuid") val stationUuid: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "url_resolved") val urlResolved: String? = null,
    @Json(name = "homepage") val homepage: String? = null,
    @Json(name = "favicon") val favicon: String? = null,
    @Json(name = "tags") val tags: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "countrycode") val countryCode: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "votes") val votes: Int? = null,
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "bitrate") val bitrate: Int? = null,
    @Json(name = "clickcount") val clickCount: Int? = null
)

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val alternativeStreamUrls: List<String> = emptyList(),
    val favicon: String = "",
    val homepage: String = "",
    val tags: String = "",
    val country: String = "Global",
    val countryCode: String = "XX",
    val state: String = "",
    val city: String = "",
    val codec: String = "MP3",
    val bitrate: Int = 128,
    val votes: Int = 0,
    val isFavorite: Boolean = false
) {
    fun getAllStreamCandidates(): List<String> {
        val list = mutableListOf<String>()
        if (streamUrl.isNotBlank()) list.add(streamUrl)
        for (alt in alternativeStreamUrls) {
            if (alt.isNotBlank() && !list.contains(alt)) {
                list.add(alt)
            }
        }
        val base = streamUrl.trimEnd('/')
        if (base.isNotBlank() && !base.endsWith(".m3u8", ignoreCase = true)) {
            val cleanBase = base
                .removeSuffix("/1")
                .removeSuffix(".aac")
                .removeSuffix(".mp3")
            val variations = listOf(
                "$cleanBase/1",
                "$cleanBase.aac",
                "$cleanBase.mp3",
                "$cleanBase/stream",
                "$cleanBase/stream/1",
                "$cleanBase/stream.aac",
                "$cleanBase/stream.mp3",
                "$cleanBase/hls/stream.m3u8"
            )
            for (v in variations) {
                if (!list.contains(v)) {
                    list.add(v)
                }
            }
        }
        return list
    }
    val displayFrequency: String
        get() {
            val hash = Math.abs(id.hashCode())
            val freq = 87.5 + (hash % 205) * 0.1
            return String.format(java.util.Locale.US, "%.1f MHz", freq)
        }

    val primaryGenre: String
        get() {
            if (tags.isBlank()) return "Geral"
            val first = tags.split(",", ";", " ").firstOrNull { it.isNotBlank() } ?: "Geral"
            return first.trim().replaceFirstChar { it.uppercase() }
        }

    val locationLabel: String
        get() {
            val parts = mutableListOf<String>()
            if (city.isNotBlank()) parts.add(city)
            if (state.isNotBlank() && state != city) parts.add(state)
            if (country.isNotBlank()) parts.add(country)
            return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Mundial"
        }
}

fun RadioStationDto.toDomain(isFavorite: Boolean = false): RadioStation {
    val bestUrl = urlResolved?.takeIf { it.isNotBlank() } ?: url.orEmpty()
    val st = state?.trim().orEmpty()
    return RadioStation(
        id = stationUuid ?: bestUrl.hashCode().toString(),
        name = name?.trim()?.takeIf { it.isNotBlank() } ?: "Rádio Sem Nome",
        streamUrl = bestUrl,
        favicon = favicon?.trim().orEmpty(),
        homepage = homepage?.trim().orEmpty(),
        tags = tags?.trim().orEmpty(),
        country = country?.trim()?.takeIf { it.isNotBlank() } ?: "Global",
        countryCode = countryCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "XX",
        state = st,
        city = st,
        codec = codec?.trim()?.takeIf { it.isNotBlank() } ?: "MP3",
        bitrate = bitrate ?: 128,
        votes = votes ?: 0,
        isFavorite = isFavorite
    )
}
