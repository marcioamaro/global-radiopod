package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.LocalAudioTrack
import com.example.data.model.MediaFolder
import com.example.data.repository.libraryKey
import com.example.ui.components.IpodInteractiveProgressBar

@Composable
fun IpodMp3FoldersScreen(
    folders: List<MediaFolder>,
    selectedIndex: Int,
    onSelectFolder: (MediaFolder) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean,
    library: com.example.data.repository.MediaLibraryRepository? = null,
    collectionPath: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
    ) {
        library?.let { com.example.ui.components.MediaLibraryToolbar(it, com.example.data.repository.LibraryKind.AUDIO, collectionPath, backlightTextPrimary) }
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightHighlight.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = backlightTextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Biblioteca de músicas",
                color = backlightTextPrimary,
                fontSize = (13f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily
            )
        }

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma pasta com arquivos de áudio encontrada no dispositivo.",
                    color = backlightTextSecondary,
                    fontSize = (12f * fontScale).sp,
                    fontFamily = fontFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = folders,
                selectedIndex = selectedIndex,
                key = { _, folder -> folder.path },
                modifier = Modifier.fillMaxSize()
            ) { index, folder, isSelected ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) backlightHighlight.copy(alpha = 0.85f)
                            else Color.Transparent
                        )
                        .clickable { onSelectFolder(folder) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else backlightTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = if (isBold || isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${folder.itemCount} faixas",
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                            fontSize = (10f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else backlightTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IpodMp3TracksListScreen(
    title: String,
    tracks: List<LocalAudioTrack>,
    currentTrackId: Long?,
    selectedIndex: Int,
    onSelectTrack: (LocalAudioTrack) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean,
    library: com.example.data.repository.MediaLibraryRepository? = null,
    collectionPath: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
    ) {
        library?.let { com.example.ui.components.MediaLibraryToolbar(it, com.example.data.repository.LibraryKind.AUDIO, collectionPath, backlightTextPrimary) }
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightHighlight.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = backlightTextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$title (${tracks.size})",
                color = backlightTextPrimary,
                fontSize = (13f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma música nesta pasta.",
                    color = backlightTextSecondary,
                    fontSize = (12f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = tracks,
                selectedIndex = selectedIndex,
                key = { _, track -> track.id },
                modifier = Modifier.fillMaxSize()
            ) { index, track, isSelected ->
                    val isCurrentPlaying = track.id == currentTrackId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) backlightHighlight.copy(alpha = 0.85f)
                                else if (isCurrentPlaying) backlightHighlight.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { onSelectTrack(track) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrentPlaying) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else backlightTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (12f * fontScale).sp,
                                fontWeight = if (isBold || isSelected || isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • ${track.displayDuration}",
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                fontSize = (9.5f * fontScale).sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        library?.let { com.example.ui.components.MediaItemActions(it,
                            com.example.data.repository.LibraryKind.AUDIO, track.libraryKey(), collectionPath,
                            if (isSelected) Color.White else backlightTextPrimary) }
                    }
                }
            }
        }
    }

@Composable
fun IpodMp3NowPlayingScreen(
    track: LocalAudioTrack?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    visualizerAmplitudes: List<Float>,
    volume: Float,
    onStepVolumeUp: () -> Unit = {},
    onStepVolumeDown: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onCycleSpeed: () -> Unit = {},
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    if (track == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backlightBg)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nenhuma música sendo reproduzida.\nSelecione uma faixa no MP3 Player.",
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontFamily = fontFamily,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    val bwColorMatrix = remember { ColorMatrix().apply { setToSaturation(0f) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status bar inside Now Playing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPlaying) "REPRODUZINDO" else "PAUSADO",
                    color = backlightTextPrimary,
                    fontSize = (9f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playbackSpeed != 1.0f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(backlightTextPrimary)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = backlightBg,
                            fontSize = (7.5f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "MP3 / ÁUDIO",
                    color = backlightTextSecondary,
                    fontSize = (9f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        }

        // Center Content: Album Artwork (or iPod Cassette fallback) + Track Details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Box (Pure Monochrome LCD vector styling)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(backlightTextPrimary.copy(alpha = 0.08f))
                    .border(1.2.dp, backlightTextPrimary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = "Álbum / MP3",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    color = backlightTextPrimary,
                    fontSize = (13f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = track.artist,
                    color = backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.album,
                    color = backlightTextSecondary.copy(alpha = 0.8f),
                    fontSize = (9.5f * fontScale).sp,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom Progress Bar & Visualizer
        Column(modifier = Modifier.fillMaxWidth()) {
            // Audio visualizer bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                visualizerAmplitudes.take(20).forEach { amp ->
                    val barHeight = (amp.coerceIn(0.08f, 1f) * 14).dp
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(barHeight)
                            .background(backlightTextPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Progress bar interativa com suporte a click-to-seek e scrubbing
            IpodInteractiveProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                backlightTextPrimary = backlightTextPrimary,
                backlightTextSecondary = backlightTextSecondary,
                fontFamily = fontFamily
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Row de velocidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isCustomSpeed = playbackSpeed != 1.0f
                val chipContentColor = if (isCustomSpeed) backlightBg else backlightTextPrimary
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isCustomSpeed) backlightTextPrimary else backlightTextPrimary.copy(alpha = 0.12f))
                        .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .clickable { onCycleSpeed() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Velocidade de Reprodução",
                            tint = chipContentColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Velocidade: ${playbackSpeed}x",
                            color = chipContentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Volume Level Bar with interactive [-] and [+] adjustment commands
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(backlightTextPrimary.copy(alpha = 0.1f))
                    .border(1.dp, backlightTextPrimary.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(backlightTextPrimary)
                            .clickable { onStepVolumeDown() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "VOL -",
                            color = backlightBg,
                            fontSize = (9f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = backlightTextPrimary,
                        fontSize = (10.5f * fontScale).sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(backlightTextPrimary)
                            .clickable { onStepVolumeUp() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "VOL +",
                            color = backlightBg,
                            fontSize = (9f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }
                }

                // Real-time dynamic volume bar with explicit width calculation
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val totalWidth = 76.dp
                    val filledWidth = totalWidth * volume.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(totalWidth)
                            .height(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(backlightTextPrimary.copy(alpha = 0.2f))
                            .border(1.dp, backlightTextPrimary, RoundedCornerShape(2.dp))
                    ) {
                        if (filledWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(filledWidth)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(backlightTextPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}
