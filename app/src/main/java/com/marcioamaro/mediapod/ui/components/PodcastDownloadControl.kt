package com.marcioamaro.mediapod.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import com.marcioamaro.mediapod.data.download.DownloadStatus
import com.marcioamaro.mediapod.data.download.PodcastDownloadManager
import com.marcioamaro.mediapod.data.model.PodcastEpisode

@Composable
fun PodcastDownloadControl(episode: PodcastEpisode) {
    val context = LocalContext.current
    val manager = remember(context) { PodcastDownloadManager.getInstance(context) }
    val states by manager.statusMap.collectAsState()
    val state = states[episode.id] ?: DownloadStatus.NotDownloaded
    var expanded by remember { mutableStateOf(false) }
    val description = when (state) {
        is DownloadStatus.Downloading -> context.getString(com.marcioamaro.mediapod.R.string.download_progress, state.progressPercent)
        is DownloadStatus.Queued -> context.getString(com.marcioamaro.mediapod.R.string.download_queued)
        is DownloadStatus.Paused -> context.getString(com.marcioamaro.mediapod.R.string.download_paused)
        is DownloadStatus.Completed -> context.getString(com.marcioamaro.mediapod.R.string.download_offline)
        is DownloadStatus.Failed -> state.reason
        else -> context.getString(com.marcioamaro.mediapod.R.string.download_episode)
    }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, context.getString(com.marcioamaro.mediapod.R.string.download_description, description)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(description) }, enabled = false, onClick = {})
            if (state is DownloadStatus.Downloading || state is DownloadStatus.Queued) {
                DropdownMenuItem(text = { Text(context.getString(com.marcioamaro.mediapod.R.string.download_pause)) }, onClick = { manager.pauseDownload(episode.id); expanded = false })
            } else if (state is DownloadStatus.Completed) {
                DropdownMenuItem(text = { Text(context.getString(com.marcioamaro.mediapod.R.string.download_delete)) }, onClick = { manager.deleteDownload(episode.id); expanded = false })
            } else {
                DropdownMenuItem(text = { Text(if (state is DownloadStatus.NotDownloaded) context.getString(com.marcioamaro.mediapod.R.string.download_start) else context.getString(com.marcioamaro.mediapod.R.string.download_resume)) },
                    onClick = { manager.startDownload(episode); expanded = false })
            }
            if (state is DownloadStatus.Paused || state is DownloadStatus.Failed || state is DownloadStatus.Queued || state is DownloadStatus.Downloading) {
                DropdownMenuItem(text = { Text(context.getString(com.marcioamaro.mediapod.R.string.download_cancel)) }, onClick = { manager.cancelDownload(episode.id); expanded = false })
            }
        }
    }
}
