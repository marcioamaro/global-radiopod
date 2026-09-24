package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.util.DataUsagePolicy
import com.example.data.download.PodcastDownloadManager

@Composable
fun DataUsageControl() {
    val context = LocalContext.current
    val policy = remember { DataUsagePolicy(context) }
    val downloads = remember { PodcastDownloadManager.getInstance(context) }
    var artwork by remember { mutableStateOf(policy.remoteArtwork) }
    var wifi by remember { mutableStateOf(downloads.isWifiOnlyEnabled()) }
    var bitrate by remember { mutableIntStateOf(policy.preferredBitrate) }
    Text(context.getString(com.example.R.string.data_title))
    PreferenceToggle(context.getString(com.example.R.string.data_artwork), artwork) { artwork = it; policy.remoteArtwork = it }
    PreferenceToggle(context.getString(com.example.R.string.data_wifi), wifi) { wifi = it; downloads.setWifiOnlyPreference(it) }
    listOf(0 to context.getString(com.example.R.string.data_auto), 64000 to context.getString(com.example.R.string.data_64), 128000 to context.getString(com.example.R.string.data_128)).forEach { (value, title) ->
        TextButton(onClick = {
            bitrate = value; policy.preferredBitrate = value
            com.example.player.RadioPlayerManager.getInstance(context).getPlayer()
        }) { Text(if (bitrate == value) "• $title" else title) }
    }
    Text(context.getString(com.example.R.string.data_help))
}
