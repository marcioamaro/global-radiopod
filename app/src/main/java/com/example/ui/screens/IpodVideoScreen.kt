package com.example.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
    isBold: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in folders.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
    ) {
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
                text = "Pastas de Vídeos (${folders.size})",
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(folders) { index, folder ->
                    val isSelected = index == selectedIndex
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
    isBold: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in videos.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
    ) {
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(videos) { index, video ->
                    val isSelected = index == selectedIndex
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
                    }
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
    val posFormatted = String.format(java.util.Locale.US, "%d:%02d", posSecs / 60, posSecs % 60)
    val durSecs = (durationMs / 1000).coerceAtLeast(0)
    val durFormatted = String.format(java.util.Locale.US, "%d:%02d", durSecs / 60, durSecs % 60)

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
            // Seek bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x55FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Color(0xFF00E5FF))
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = posFormatted,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontFamily = fontFamily
                )

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

                Text(
                    text = durFormatted,
                    color = Color(0xFFAAAAAA),
                    fontSize = 9.sp,
                    fontFamily = fontFamily
                )
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
    onPrev: () -> Unit
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
    val posFormatted = String.format(java.util.Locale.US, "%d:%02d", posSecs / 60, posSecs % 60)
    val durSecs = (durationMs / 1000).coerceAtLeast(0)
    val durFormatted = String.format(java.util.Locale.US, "%d:%02d", durSecs / 60, durSecs % 60)

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
            modifier = Modifier.fillMaxSize()
        )

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
                            color = Color(0xFF94A3B8),
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

                    // Play/Pause
                    IconButton(
                        onClick = { videoPlayerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xCC00E5FF))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
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
                    Slider(
                        value = positionMs.toFloat(),
                        onValueChange = { newPos -> videoPlayerManager.seekTo(newPos.toLong()) },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = posFormatted,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = durFormatted,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
