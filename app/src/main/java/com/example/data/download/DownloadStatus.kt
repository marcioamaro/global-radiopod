package com.example.data.download

/**
 * Estados do ciclo de vida de download de episódios de podcasts.
 */
sealed interface DownloadStatus {
    data object NotDownloaded : DownloadStatus
    data class Queued(val episodeId: String) : DownloadStatus
    data class Downloading(
        val episodeId: String,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadStatus
    data class Completed(
        val episodeId: String,
        val localFilePath: String,
        val fileSize: Long
    ) : DownloadStatus
    data class Failed(
        val episodeId: String,
        val reason: String
    ) : DownloadStatus
    data class Paused(
        val episodeId: String,
        val progressPercent: Int
    ) : DownloadStatus
}
