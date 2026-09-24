package com.marcioamaro.mediapod.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val feedUrl: String,
    val artworkUrl: String = "",
    val country: String = "BR",
    val category: String = "Geral",
    val episodeCount: Int = 0,
    val latestReleaseDate: String = "",
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val isSubscribed: Boolean = false,
    val unreadCount: Int = 0,
    val rankPosition: Int? = null,
    val externalUrl: String = ""
) {
    val displayCountry: String
        get() = when (country.uppercase()) {
            "BR" -> "Brasil"
            "US" -> "Estados Unidos"
            "GB" -> "Reino Unido"
            "PT" -> "Portugal"
            "ES" -> "Espanha"
            "AR" -> "Argentina"
            else -> country
        }
}

@JsonClass(generateAdapter = true)
data class PodcastChapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L,
    val imageUrl: String = "",
    val linkUrl: String = ""
) {
    val displayTime: String
        get() {
            val totalSecs = (startTimeMs / 1000).coerceAtLeast(0)
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            val secs = totalSecs % 60
            return if (hours > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hours, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
            }
        }
}

@JsonClass(generateAdapter = true)
data class PodcastEpisode(
    val id: String,
    val showId: String,
    val showTitle: String,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val durationMs: Long = 0L,
    val publishDate: String = "",
    val artworkUrl: String = "",
    val playbackPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val chaptersUrl: String = "",
    val chapters: List<PodcastChapter> = emptyList(),
    val localFilePath: String? = null,
    val isPlayed: Boolean = false
) {
    val isDownloaded: Boolean
        get() = !localFilePath.isNullOrBlank()

    val displayDuration: String
        get() {
            if (durationMs <= 0) return "Áudio"
            val totalSecs = durationMs / 1000
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            val secs = totalSecs % 60
            return if (hours > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hours, mins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
            }
        }
}

data class PodcastCategory(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val description: String
)

data class PodcastCountry(
    val code: String,
    val name: String,
    val flagEmoji: String
)
