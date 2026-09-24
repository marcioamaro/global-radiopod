package com.example.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.data.model.LocalVideoTrack
import com.example.data.model.MediaFolder
import com.example.player.LocalVideoPlayerManager
import com.example.data.repository.libraryKey
import com.example.ui.components.IpodInteractiveProgressBar

@Composable
fun IpodVideoFoldersScreen(
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
        library?.let { com.example.ui.components.MediaLibraryToolbar(it, com.example.data.repository.LibraryKind.VIDEO, collectionPath, backlightTextPrimary) }
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightHighlight.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = backlightTextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Biblioteca de vídeos",
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
                    text = "Nenhum arquivo de vídeo encontrado no dispositivo.",
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
                        imageVector = Icons.Default.Folder,
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
                            text = "${folder.itemCount} vídeos",
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
fun IpodVideoListScreen(
    title: String,
    videos: List<LocalVideoTrack>,
    currentVideoId: Long?,
    selectedIndex: Int,
    onSelectVideo: (LocalVideoTrack) -> Unit,
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
        library?.let { com.example.ui.components.MediaLibraryToolbar(it, com.example.data.repository.LibraryKind.VIDEO, collectionPath, backlightTextPrimary) }
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightHighlight.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = backlightTextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$title (${videos.size})",
                color = backlightTextPrimary,
                fontSize = (13f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum vídeo nesta pasta.",
                    color = backlightTextSecondary,
                    fontSize = (12f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = videos,
                selectedIndex = selectedIndex,
                key = { _, video -> video.id },
                modifier = Modifier.fillMaxSize()
            ) { index, video, isSelected ->
                    val isCurrentPlaying = video.id == currentVideoId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) backlightHighlight.copy(alpha = 0.85f)
                                else if (isCurrentPlaying) backlightHighlight.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { onSelectVideo(video) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrentPlaying) Icons.Default.PlayArrow else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else backlightTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (12f * fontScale).sp,
                                fontWeight = if (isBold || isSelected || isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${video.displayDuration} ${if (video.resolution.isNotBlank()) "• ${video.resolution}" else ""}",
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                fontSize = (9.5f * fontScale).sp,
                                fontFamily = fontFamily
                            )
                        }
                        library?.let { com.example.ui.components.MediaItemActions(it,
                            com.example.data.repository.LibraryKind.VIDEO, video.libraryKey(), collectionPath,
                            if (isSelected) Color.White else backlightTextPrimary) }
                    }
                }
            }
        }
    }

@OptIn(UnstableApi::class)
@Composable
fun IpodVideoPlayerScreen(
    videoPlayerManager: LocalVideoPlayerManager,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onNextVideo: () -> Unit = {},
    onPrevVideo: () -> Unit = {},
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color = Color(0xFF00E5FF),
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val currentVideo by videoPlayerManager.currentVideo.collectAsState()
    val isPlaying by videoPlayerManager.isPlaying.collectAsState()
    val positionMs by videoPlayerManager.currentPositionMs.collectAsState()
    val durationMs by videoPlayerManager.durationMs.collectAsState()

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val posSecs = (positionMs / 1000).coerceAtLeast(0)
    val posFormatted = if (posSecs >= 3600) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", posSecs / 3600, (posSecs % 3600) / 60, posSecs % 60)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", posSecs / 60, posSecs % 60)
    }
    val remainingSecs = ((durationMs - positionMs) / 1000).coerceAtLeast(0)
    val remainingFormatted = if (remainingSecs >= 3600) {
        String.format(java.util.Locale.US, "-%d:%02d:%02d", remainingSecs / 3600, (remainingSecs % 3600) / 60, remainingSecs % 60)
    } else {
        String.format(java.util.Locale.US, "-%02d:%02d", remainingSecs / 60, remainingSecs % 60)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ExoPlayer Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayerManager.exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = videoPlayerManager.exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Video Title Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentVideo?.title ?: "Reproduzindo Vídeo",
                color = Color.White,
                fontSize = (11f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Tela Cheia",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Bottom Progress Bar & Controls Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IpodInteractiveProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = { targetMs -> videoPlayerManager.seekToPosition(targetMs) },
                backlightTextPrimary = backlightHighlight,
                backlightTextSecondary = backlightTextPrimary.copy(alpha = 0.65f),
                fontFamily = fontFamily
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentSpeed by videoPlayerManager.playbackSpeed.collectAsState()
                val isCustomSpeed = currentSpeed != 1.0f
                val chipContentColor = if (isCustomSpeed) {
                    val luminance = 0.299f * backlightHighlight.red + 0.587f * backlightHighlight.green + 0.114f * backlightHighlight.blue
                    if (luminance > 0.5f) Color.Black else Color.White
                } else Color.White
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isCustomSpeed) backlightHighlight else Color(0x33FFFFFF))
                        .border(1.dp, backlightHighlight.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                        .clickable {
                            val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                            val idx = speeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.05f }
                            val next = if (idx in 0 until speeds.size - 1) speeds[idx + 1] else speeds[0]
                            videoPlayerManager.setPlaybackSpeed(next)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Velocidade",
                            tint = chipContentColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${currentSpeed}x",
                            color = chipContentColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPrevVideo,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Vídeo Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { videoPlayerManager.togglePlayPause() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextVideo,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Próximo Vídeo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FullscreenLandscapeVideoPlayer(
    videoPlayerManager: LocalVideoPlayerManager,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    backlightHighlight: Color = Color(0xFF00E5FF),
    backlightTextPrimary: Color = Color.White
) {
    val isPlaying by videoPlayerManager.isPlaying.collectAsState()
    val currentVideo by videoPlayerManager.currentVideo.collectAsState()
    val positionMs by videoPlayerManager.currentPositionMs.collectAsState()
    val durationMs by videoPlayerManager.durationMs.collectAsState()

    var showControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(3000L)
            showControls = false
        }
    }

    val posSecs = (positionMs / 1000).coerceAtLeast(0)
    val posFormatted = if (posSecs >= 3600) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", posSecs / 3600, (posSecs % 3600) / 60, posSecs % 60)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", posSecs / 60, posSecs % 60)
    }
    val remainingSecs = ((durationMs - positionMs) / 1000).coerceAtLeast(0)
    val remainingFormatted = if (remainingSecs >= 3600) {
        String.format(java.util.Locale.US, "-%d:%02d:%02d", remainingSecs / 3600, (remainingSecs % 3600) / 60, remainingSecs % 60)
    } else {
        String.format(java.util.Locale.US, "-%02d:%02d", remainingSecs / 60, remainingSecs % 60)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // 100% Fullscreen Video surface without any chassis
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayerManager.exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = videoPlayerManager.exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Calcula cor e tint do botão Play no escopo da função para reutilização
        val playBtnBg = backlightHighlight.copy(alpha = 0.85f)
        val playIconTint = if (
            0.299f * backlightHighlight.red +
            0.587f * backlightHighlight.green +
            0.114f * backlightHighlight.blue > 0.5f
        ) Color.Black else Color.White

        // Floating Controls Overlay (Visible upon tap and auto-hides after 3s)
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x44000000))
            ) {
                // Top Bar: Back button and Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xEE000000), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = currentVideo?.title ?: "Reproduzindo Vídeo",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentVideo?.folderName ?: "Vídeos",
                            color = backlightTextPrimary.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Center Playback Controls: Previous, Play/Pause, Next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Video in current folder
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0x77000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Vídeo Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause — cor do tema atual
                    IconButton(
                        onClick = { videoPlayerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(playBtnBg)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = playIconTint,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next Video in current folder
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0x77000000))
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Próximo Vídeo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Bar: Timeline Progress Slider & Time stamps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xEE000000))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Barra de progresso com cor do tema
                    Slider(
                        value = positionMs.toFloat(),
                        onValueChange = { newPos -> videoPlayerManager.seekTo(newPos.toLong()) },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = backlightHighlight,
                            activeTrackColor = backlightHighlight,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = posFormatted,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        // Controle de velocidade com cor do tema
                        val currentSpeed by videoPlayerManager.playbackSpeed.collectAsState()
                        val isCustomSpeed = currentSpeed != 1.0f
                        val chipBg = if (isCustomSpeed) backlightHighlight else Color(0x44FFFFFF)
                        val chipContentColor = if (isCustomSpeed) playIconTint else Color.White
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(chipBg)
                                .clickable {
                                    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                                    val idx = speeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.05f }
                                    val next = if (idx in 0 until speeds.size - 1) speeds[idx + 1] else speeds[0]
                                    videoPlayerManager.setPlaybackSpeed(next)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Velocidade",
                                    tint = chipContentColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currentSpeed}x",
                                    color = chipContentColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = remainingFormatted,
                            color = backlightTextPrimary.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
