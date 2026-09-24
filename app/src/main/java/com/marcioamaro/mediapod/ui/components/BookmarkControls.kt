package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.repository.EpisodeBookmarks

@Composable
fun BookmarkEditor(position: Long, note: String, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    val context = LocalContext.current
    var seconds by remember { mutableStateOf((position / 1000).toString()) }
    var text by remember { mutableStateOf(note) }
    val valid = seconds.toLongOrNull()?.let { it in 0..604800 } == true && text.length <= 2000
    AlertDialog(onDismissRequest = onDismiss, title = { Text(context.getString(com.marcioamaro.mediapod.R.string.bookmark_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = seconds, onValueChange = { seconds = it }, label = { Text(context.getString(com.marcioamaro.mediapod.R.string.bookmark_seconds)) }, singleLine = true)
            OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, label = { Text(context.getString(com.marcioamaro.mediapod.R.string.bookmark_note)) })
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(seconds.toLong() * 1000, text) }) { Text(context.getString(com.marcioamaro.mediapod.R.string.feature_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(context.getString(com.marcioamaro.mediapod.R.string.feature_cancel)) } })
}

@Composable
fun AddBookmarkControl(episode: PodcastEpisode, position: Long) {
    val context = LocalContext.current
    val bookmarks = remember(context) { EpisodeBookmarks(context) }
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(context.getString(com.marcioamaro.mediapod.R.string.bookmark_add)) }
    if (open) BookmarkEditor(position, "", { open = false }) { time, note ->
        bookmarks.reload()
        bookmarks.save(episode, time, note)
        open = false
    }
}
