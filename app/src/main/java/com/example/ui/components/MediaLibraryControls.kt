package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.LibraryKind
import com.example.data.repository.MediaLibraryRepository

@Composable
private fun PlaylistNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.isEmpty()) "Criar playlist" else "Renomear playlist") },
        text = { Column {
            OutlinedTextField(value = name, onValueChange = { name = it.take(80); error = null }, label = { Text("Nome") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            try { onSave(name); onDismiss() } catch (e: IllegalArgumentException) { error = e.message }
        }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun MediaLibraryToolbar(repository: MediaLibraryRepository, kind: LibraryKind, path: String?, color: Color) {
    val state by repository.state.collectAsState()
    val playlist = state.playlists.firstOrNull { "playlist:${it.id}" == path }
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { create = true }) { Text("+ Playlist", color = color, fontSize = 10.sp) }
        if (playlist != null) {
            IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Renomear playlist", tint = color) }
            IconButton(onClick = { repository.deletePlaylist(playlist.id) }) { Icon(Icons.Default.Delete, "Excluir playlist", tint = color) }
        }
        if (path == "library:recents") {
            TextButton(onClick = { repository.clearRecents(kind) }) { Text("Limpar recentes", color = color, fontSize = 10.sp) }
        }
    }
    if (create) PlaylistNameDialog("", { create = false }) { repository.createPlaylist(it, kind) }
    if (rename && playlist != null) PlaylistNameDialog(playlist.name, { rename = false }) { repository.renamePlaylist(playlist.id, it) }
}

@Composable
fun MediaItemActions(repository: MediaLibraryRepository, kind: LibraryKind, mediaKey: String, path: String?, color: Color) {
    val state by repository.state.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var choosePlaylist by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    val favorite = mediaKey in state.favorites
    val playlistId = path?.takeIf { it.startsWith("playlist:") }?.removePrefix("playlist:")
    IconButton(onClick = { repository.toggleFavorite(mediaKey) }, modifier = Modifier.size(32.dp)) {
        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (favorite) "Remover dos favoritos" else "Favoritar", tint = color, modifier = Modifier.size(17.dp))
    }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, "Opções do arquivo", tint = color, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Adicionar à playlist") }, onClick = { menu = false; choosePlaylist = true })
            if (playlistId != null) {
                DropdownMenuItem(text = { Text("Mover para cima") }, onClick = { repository.moveInPlaylist(playlistId, mediaKey, -1); menu = false })
                DropdownMenuItem(text = { Text("Mover para baixo") }, onClick = { repository.moveInPlaylist(playlistId, mediaKey, 1); menu = false })
                DropdownMenuItem(text = { Text("Remover da playlist") }, onClick = { repository.removeFromPlaylist(playlistId, mediaKey); menu = false })
            }
        }
    }
    if (choosePlaylist) AlertDialog(onDismissRequest = { choosePlaylist = false }, title = { Text("Adicionar à playlist") },
        text = { Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            state.playlists.filter { it.kind == kind }.forEach { playlist ->
                TextButton(onClick = { repository.addToPlaylist(playlist.id, mediaKey); choosePlaylist = false }) { Text(playlist.name) }
            }
            TextButton(onClick = { choosePlaylist = false; create = true }) { Text("+ Criar playlist") }
        } }, confirmButton = { TextButton(onClick = { choosePlaylist = false }) { Text("Fechar") } })
    if (create) PlaylistNameDialog("", { create = false }) { repository.createPlaylist(it, kind, mediaKey) }
}
