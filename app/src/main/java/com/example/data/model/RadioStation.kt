package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RadioStationDto(
    @field:Json(name = "stationuuid") val stationUuid: String? = null,
    @field:Json(name = "name") val name: String? = null,
    @field:Json(name = "url") val url: String? = null,
    @field:Json(name = "url_resolved") val urlResolved: String? = null,
    @field:Json(name = "homepage") val homepage: String? = null,
    @field:Json(name = "favicon") val favicon: String? = null,
    @field:Json(name = "tags") val tags: String? = null,
    @field:Json(name = "country") val country: String? = null,
    @field:Json(name = "countrycode") val countryCode: String? = null,
    @field:Json(name = "state") val state: String? = null,
    @field:Json(name = "language") val language: String? = null,
    @field:Json(name = "votes") val votes: Int? = null,
    @field:Json(name = "codec") val codec: String? = null,
    @field:Json(name = "bitrate") val bitrate: Int? = null,
    @field:Json(name = "clickcount") val clickCount: Int? = null
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

        val baseRaw = streamUrl.trim()
        if (baseRaw.isBlank()) return list

        // 1. STREAMTHEWORLD OFFICIAL CANDIDATES GENERATOR
        if (baseRaw.contains("streamtheworld.com", ignoreCase = true)) {
            val mountPattern = Regex("(?:livestream-redirect/|/)([A-Za-z0-9_-]+)(?:\\.aac|\\.mp3|_SC|_ADP|\\.m3u8)?(?:\\?.*)?$", RegexOption.IGNORE_CASE)
            val match = mountPattern.find(baseRaw)
            val rawMount = match?.groupValues?.getOrNull(1)
            if (!rawMount.isNullOrBlank()) {
                val cleanMount = rawMount
                    .removeSuffix("_ADP")
                    .removeSuffix("_SC")
                    .removeSuffix("AAC")
                    .removeSuffix("_AAC")
                    .removeSuffix("AAC1")

                val stwVariations = listOf(
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${rawMount}.aac",
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${rawMount}.mp3",
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${cleanMount}.mp3",
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${cleanMount}.aac",
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${cleanMount}_ADP.aac",
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${cleanMount}AAC.aac",
                    "https://playerservices.streamtheworld.com/api/livestream-redirect/${cleanMount}_SC",
                    "http://playerservices.streamtheworld.com/api/livestream-redirect/${cleanMount}.mp3",
                    "http://playerservices.streamtheworld.com/api/livestream-redirect/${rawMount}.aac"
                )
                for (v in stwVariations) {
                    if (!list.contains(v)) {
                        list.add(v)
                    }
                }
            }
        }

        // 2. ZENO.FM OFFICIAL CANDIDATES GENERATOR
        if (baseRaw.contains("zeno.fm", ignoreCase = true)) {
            val zenoPattern = Regex("zeno\\.fm/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            val match = zenoPattern.find(baseRaw)
            val mount = match?.groupValues?.getOrNull(1)
            if (!mount.isNullOrBlank()) {
                val cleanMount = mount.removeSuffix(".aac").removeSuffix(".mp3")
                val zenoVariations = listOf(
                    "https://stream.zeno.fm/$cleanMount",
                    "https://stream-relay-geo.zeno.fm/$cleanMount",
                    "http://stream.zeno.fm/$cleanMount",
                    "https://stream.zeno.fm/$cleanMount.aac",
                    "https://stream.zeno.fm/$cleanMount.mp3"
                )
                for (v in zenoVariations) {
                    if (!list.contains(v)) {
                        list.add(v)
                    }
                }
            }
        }

        // 3. HTTP / HTTPS CROSS-PROTOCOL & STANDARD ICECAST/SHOUTCAST CANDIDATES
        val cleanUrlNoQuery = baseRaw.substringBefore('?')
        val altProtocol = if (cleanUrlNoQuery.startsWith("http://", ignoreCase = true)) {
            "https://" + cleanUrlNoQuery.substring(7)
        } else if (cleanUrlNoQuery.startsWith("https://", ignoreCase = true)) {
            "http://" + cleanUrlNoQuery.substring(8)
        } else null

        if (altProtocol != null && !list.contains(altProtocol)) {
            list.add(altProtocol)
        }

        val base = cleanUrlNoQuery.trimEnd('/')
        if (base.isNotBlank() && !base.endsWith(".m3u8", ignoreCase = true)) {
            val cleanBase = base
                .removeSuffix("/1")
                .removeSuffix("/;")
                .removeSuffix("/stream")
                .removeSuffix("/live")
                .removeSuffix("/audio")
                .removeSuffix(".aac")
                .removeSuffix(".mp3")

            val variations = listOf(
                "$cleanBase/stream",
                "$cleanBase/1",
                "$cleanBase.aac",
                "$cleanBase.mp3",
                "$cleanBase/live",
                "$cleanBase/;",
                "$cleanBase/;stream.mp3",
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

    fun penalizeStreamUrl(failedUrl: String): RadioStation {
        val currentAll = getAllStreamCandidates().toMutableList()
        if (currentAll.remove(failedUrl)) {
            currentAll.add(failedUrl)
        }
        val newPrimary = currentAll.firstOrNull() ?: streamUrl
        val newAlts = if (currentAll.size > 1) currentAll.drop(1) else emptyList()
        return this.copy(
            streamUrl = newPrimary,
            alternativeStreamUrls = newAlts
        )
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
