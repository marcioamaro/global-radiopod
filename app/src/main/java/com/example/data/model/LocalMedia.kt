package com.example.data.model

import android.net.Uri

data class LocalAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val filePath: String,
    val folderName: String,
    val albumId: Long,
    val albumArtUrl: String? = null
) {
    val displayDuration: String
        get() {
            val totalSecs = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSecs / 60
            val seconds = totalSecs % 60
            return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }
}

data class LocalVideoTrack(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val contentUri: Uri,
    val filePath: String,
    val folderName: String,
    val resolution: String = ""
) {
    val displayDuration: String
        get() {
            val totalSecs = (durationMs / 1000).coerceAtLeast(0)
            val hours = totalSecs / 3600
            val minutes = (totalSecs % 3600) / 60
            val seconds = totalSecs % 60
            return if (hours > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
            }
        }
}

data class MediaFolder(
    val name: String,
    val path: String,
    val itemCount: Int,
    val isVideo: Boolean = false
)
