package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.data.repository.LibraryKind
import com.marcioamaro.mediapod.data.repository.MediaLibraryRepository

data class LcdPlaylistPalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val highlight: Color
)

private class LcdPlaylistModalHostState {
    var modal by mutableStateOf<(@Composable () -> Unit)?>(null)
}

private val LocalLcdPlaylistModalHost = staticCompositionLocalOf<LcdPlaylistModalHostState?> { null }

/** Hosts playlist dialogs inside the LCD content bounds instead of the Android window. */
@Composable
fun LcdPlaylistModalHost(content: @Composable () -> Unit) {
    val host = remember { LcdPlaylistModalHostState() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLcdPlaylistModalHost provides host) {
            content()
        }
        host.modal?.invoke()
    }
}

@Composable
private fun LcdPlaylistDialog(title: String, palette: LcdPlaylistPalette, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalLcdPlaylistModalHost.current
    val titleState = rememberUpdatedState(title)
    val paletteState = rememberUpdatedState(palette)
    val contentState = rememberUpdatedState(content)
    val dialog = remember {
        @Composable {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                PlaylistDialogCard(titleState.value, paletteState.value, contentState.value)
            }
        }
    }
    if (host == null) {
        PlaylistDialogCard(title, palette, content)
    } else {
        DisposableEffect(host) {
            host.modal = dialog
            onDispose { host.modal = null }
        }
    }
}

@Composable
private fun PlaylistDialogCard(title: String, palette: LcdPlaylistPalette, content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth(0.88f).widthIn(max = 360.dp).heightIn(max = 280.dp)
                .background(palette.background, RoundedCornerShape(8.dp))
                .border(1.dp, palette.highlight, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title.uppercase(), color = palette.primary, fontSize = 13.sp)
            content()
        }
}

@Composable
private fun LcdPlaylistAction(label: String, palette: LcdPlaylistPalette, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label.uppercase(), color = if (enabled) palette.primary else palette.secondary.copy(alpha = 0.5f), fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, palette.highlight.copy(alpha = if (enabled) 0.7f else 0.25f), RoundedCornerShape(4.dp))
            .background(palette.highlight.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp))
}

@Composable
private fun PlaylistNameDialog(initial: String, palette: LcdPlaylistPalette, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    LcdPlaylistDialog(if (initial.isEmpty()) "Criar playlist" else "Renomear playlist", palette, onDismiss) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(80); error = null }, label = { Text("Nome", color = palette.secondary) }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.primary, unfocusedTextColor = palette.primary, focusedBorderColor = palette.highlight, unfocusedBorderColor = palette.secondary, cursorColor = palette.highlight))
        error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 11.sp) }
        LcdPlaylistAction("Salvar", palette, name.isNotBlank()) { try { onSave(name); onDismiss() } catch (e: IllegalArgumentException) { error = e.message } }
        LcdPlaylistAction("Cancelar", palette, onClick = onDismiss)
    }
}

@Composable
fun MediaLibraryToolbar(repository: MediaLibraryRepository, kind: LibraryKind, path: String?, color: Color, palette: LcdPlaylistPalette) {
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
    if (create) PlaylistNameDialog("", palette, { create = false }) { repository.createPlaylist(it, kind) }
    if (rename && playlist != null) PlaylistNameDialog(playlist.name, palette, { rename = false }) { repository.renamePlaylist(playlist.id, it) }
}

@Composable
fun MediaItemActions(repository: MediaLibraryRepository, kind: LibraryKind, mediaKey: String, path: String?, color: Color, palette: LcdPlaylistPalette) {
    val state by repository.state.collectAsState()
    var actions by remember { mutableStateOf(false) }
    var choosePlaylist by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    val favorite = mediaKey in state.favorites
    val playlistId = path?.takeIf { it.startsWith("playlist:") }?.removePrefix("playlist:")
    IconButton(onClick = { repository.toggleFavorite(mediaKey) }, modifier = Modifier.size(48.dp)) {
        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (favorite) "Remover dos favoritos" else "Favoritar", tint = color, modifier = Modifier.size(17.dp))
    }
    Box {
        IconButton(onClick = { actions = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreVert, "Opções do arquivo", tint = color, modifier = Modifier.size(17.dp))
        }
    }
    if (actions) LcdPlaylistDialog("Opções do arquivo", palette, { actions = false }) {
        LcdPlaylistAction("Adicionar à playlist", palette) { actions = false; choosePlaylist = true }
        if (playlistId != null) {
            LcdPlaylistAction("Mover para cima", palette) { repository.moveInPlaylist(playlistId, mediaKey, -1); actions = false }
            LcdPlaylistAction("Mover para baixo", palette) { repository.moveInPlaylist(playlistId, mediaKey, 1); actions = false }
            LcdPlaylistAction("Remover da playlist", palette) { repository.removeFromPlaylist(playlistId, mediaKey); actions = false }
        }
        LcdPlaylistAction("Fechar", palette, onClick = { actions = false })
    }
    if (choosePlaylist) LcdPlaylistDialog("Adicionar à playlist", palette, { choosePlaylist = false }) {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            state.playlists.filter { it.kind == kind }.forEach { playlist ->
                LcdPlaylistAction(playlist.name, palette) { repository.addToPlaylist(playlist.id, mediaKey); choosePlaylist = false }
            }
            LcdPlaylistAction("Criar playlist", palette) { choosePlaylist = false; create = true }
        }
        LcdPlaylistAction("Fechar", palette, onClick = { choosePlaylist = false })
    }
    if (create) PlaylistNameDialog("", palette, { create = false }) { repository.createPlaylist(it, kind, mediaKey) }
}
