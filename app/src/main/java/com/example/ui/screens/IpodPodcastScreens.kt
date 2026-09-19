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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PodcastChapter
import com.example.data.model.PodcastEpisode
import com.example.data.model.PodcastShow
import com.example.ui.components.IpodInteractiveProgressBar
import androidx.compose.ui.res.stringResource
import java.util.Locale

@Composable
fun IpodPodcastMenuScreen(
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val menuItems = listOf(
        stringResource(R.string.podcast_now_playing) to Icons.Default.PlayArrow,
        stringResource(R.string.podcast_favorites) to Icons.Default.Favorite,
        stringResource(R.string.podcast_recents) to Icons.Default.History,
        stringResource(R.string.podcast_top_brazil) to Icons.Default.Star,
        stringResource(R.string.podcast_top_world) to Icons.Default.Public,
        stringResource(R.string.podcast_by_country) to Icons.Default.LocationOn,
        stringResource(R.string.podcast_search) to Icons.Default.Search,
        stringResource(R.string.podcast_custom) to Icons.Default.Podcasts
    )

    com.example.ui.components.SelectableLazyColumn(
        items = menuItems,
        selectedIndex = selectedIndex,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp)
    ) { index, (title, icon), isSelected ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) backlightHighlight else Color.Transparent)
                    .clickable { onSelectIndex(index) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else backlightTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        lineHeight = (13.5f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else backlightTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

@Composable
fun IpodPodcastShowsScreen(
    title: String,
    shows: List<PodcastShow>,
    selectedIndex: Int,
    onSelectShow: (PodcastShow) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (PodcastShow) -> Unit,
    isLoading: Boolean = false,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean,
    onClearAll: (() -> Unit)? = null,
    clearAllLabel: String = "Limpar Histórico Recente"
) {
    val bwColorMatrix = remember { ColorMatrix().apply { setToSaturation(0f) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        if (onClearAll != null && shows.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .border(0.8.dp, backlightHighlight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onClearAll() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = clearAllLabel,
                        tint = backlightHighlight,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = clearAllLabel,
                        color = backlightTextPrimary,
                        fontSize = (10f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily
                    )
                }
                Text(
                    text = "${shows.size} itens",
                    color = backlightTextSecondary,
                    fontSize = (9f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = backlightTextPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Carregando catálogo de podcasts...",
                        color = backlightTextPrimary,
                        fontSize = (10f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
            }
        } else if (shows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhum podcast encontrado",
                    color = backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = shows,
                selectedIndex = selectedIndex,
                key = { _, show -> show.id },
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) { index, show, isSelected ->
                    val fav = isFavorite(show.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) backlightHighlight else Color.Transparent)
                            .clickable { onSelectShow(show) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Monochromatic Retro Podcast Icon
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(backlightTextPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_podcast_retro),
                                contentDescription = null,
                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = show.title,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (11f * fontScale).sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metaParts = mutableListOf<String>()
                            if (show.author.isNotBlank()) metaParts.add(show.author)
                            if (show.episodeCount > 0) metaParts.add("${show.episodeCount} ep")
                            if (show.latestReleaseDate.isNotBlank()) metaParts.add(show.latestReleaseDate)
                            val subtitle = if (metaParts.isNotEmpty()) metaParts.joinToString(" • ") else show.country
                            Text(
                                text = subtitle,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                fontSize = (9f * fontScale).sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onToggleFavorite(show) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
fun IpodPodcastEpisodesScreen(
    show: PodcastShow,
    episodes: List<PodcastEpisode>,
    selectedIndex: Int,
    onSelectEpisode: (PodcastEpisode) -> Unit,
    isLoading: Boolean,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = backlightTextPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Obtendo episódios do feed RSS...",
                        color = backlightTextPrimary,
                        fontSize = (10f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
            }
        } else if (episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhum episódio disponível no momento",
                    color = backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = episodes,
                selectedIndex = selectedIndex,
                key = { _, ep -> ep.id },
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) { index, ep, isSelected ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) backlightHighlight else Color.Transparent)
                            .clickable { onSelectEpisode(ep) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ep.title,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (11f * fontScale).sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = fontFamily,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ep.publishDate.ifBlank { "Episódio" },
                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                    fontSize = (9f * fontScale).sp,
                                    fontFamily = fontFamily
                                )
                                if (ep.durationMs > 0) {
                                    val mins = ep.durationMs / 60000
                                    Text(
                                        text = " • ${mins}m",
                                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                        fontSize = (9f * fontScale).sp,
                                        fontFamily = fontFamily
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproduzir",
                            tint = if (isSelected) Color.White else backlightTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

@Composable
fun IpodPodcastNowPlayingScreen(
    episode: PodcastEpisode?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSeekTo: (Long) -> Unit = {},
    currentChapter: PodcastChapter? = null,
    chapters: List<PodcastChapter> = emptyList(),
    playbackSpeed: Float = 1.0f,
    onCycleSpeed: () -> Unit = {},
    onOpenChaptersList: () -> Unit = {},
    volume: Float,
    onStepVolumeUp: () -> Unit,
    onStepVolumeDown: () -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean,
    status: com.example.player.RadioPlaybackStatus = com.example.player.RadioPlaybackStatus.PLAYING
) {
    val bwColorMatrix = remember { ColorMatrix().apply { setToSaturation(0f) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == com.example.player.RadioPlaybackStatus.NO_INTERNET) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi_off_alert),
                        contentDescription = "Sem Conexão",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "VERIFIQUE A CONEXÃO COM A INTERNET",
                        color = backlightTextPrimary,
                        fontSize = (8f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
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
                    text = "PODCAST",
                    color = backlightTextSecondary,
                    fontSize = (9f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        }

        // Center Artwork and Metadata
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(backlightTextPrimary.copy(alpha = 0.12f))
                    .border(1.2.dp, backlightTextPrimary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Podcasts,
                    contentDescription = "Podcast",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = episode?.title ?: "Episódio de Podcast",
                    color = backlightTextPrimary,
                    fontSize = (12f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode?.showTitle ?: "Podcast",
                    color = backlightTextSecondary,
                    fontSize = (10f * fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentChapter != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• ${currentChapter.title}",
                        color = backlightTextPrimary,
                        fontSize = (9f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = episode?.publishDate.orEmpty(),
                    color = backlightTextSecondary.copy(alpha = 0.8f),
                    fontSize = (8.5f * fontScale).sp,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Timeline Progress Bar & Time Stamps com suporte a touch-to-seek e scrubbing
        Column(modifier = Modifier.fillMaxWidth()) {
            IpodInteractiveProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                backlightTextPrimary = backlightTextPrimary,
                backlightTextSecondary = backlightTextSecondary,
                fontFamily = fontFamily
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Seek Controls Row: -15s, +30s, Velocidade e Capítulos (100% Monocromático Flat, zero emojis coloridos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightTextPrimary.copy(alpha = 0.12f))
                        .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .clickable { onSeekRelative(-15000L) }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "-15s",
                            color = backlightTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightTextPrimary.copy(alpha = 0.12f))
                        .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .clickable { onSeekRelative(30000L) }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "+30s",
                            color = backlightTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Seletor de Velocidade (0.5x, 1.0x, 1.5x, 2.0x)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (playbackSpeed != 1.0f) backlightTextPrimary else backlightTextPrimary.copy(alpha = 0.12f))
                        .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .clickable { onCycleSpeed() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (playbackSpeed != 1.0f) backlightBg else backlightTextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${playbackSpeed}x",
                            color = if (playbackSpeed != 1.0f) backlightBg else backlightTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }

                if (chapters.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(backlightTextPrimary.copy(alpha = 0.12f))
                            .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                            .clickable { onOpenChaptersList() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notes,
                                contentDescription = null,
                                tint = backlightTextPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.chapters),
                                color = backlightTextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Volume bar
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = backlightTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "CONTROLE WHEEL",
                    color = backlightTextSecondary,
                    fontSize = 8.sp,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

@Composable
fun IpodCustomItemsListScreen(
    title: String,
    items: List<Pair<String, String>>, // (Name, Url)
    selectedIndex: Int,
    onAddNew: () -> Unit,
    onSelectItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val allEntries: List<Pair<String, String>?> = remember(items) {
        listOf<Pair<String, String>?>(null) + items
    }

    com.example.ui.components.SelectableLazyColumn(
        items = allEntries,
        selectedIndex = selectedIndex,
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { index, entry, isSelected ->
        if (index == 0 || entry == null) {
            // Item 0: Botão de Adicionar Personalizado
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) backlightHighlight else backlightTextPrimary.copy(alpha = 0.08f))
                    .border(1.dp, if (isSelected) backlightHighlight else backlightTextPrimary.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .clickable { onAddNew() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else backlightTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+ Adicionar URL Personalizada",
                    color = if (isSelected) Color.White else backlightTextPrimary,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily
                )
            }
        } else {
            val (name, url) = entry
            val itemIdx = index - 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) backlightHighlight else Color.Transparent)
                    .clickable { onSelectItem(itemIdx) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = url,
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                        fontSize = (9f * fontScale).sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onDeleteItem(itemIdx) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = if (isSelected) Color.White else backlightTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IpodChaptersListScreen(
    chapters: List<PodcastChapter>,
    currentChapter: PodcastChapter?,
    selectedIndex: Int,
    onSelectChapter: (PodcastChapter) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    if (chapters.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backlightBg)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nenhum capítulo disponível para este episódio.",
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontFamily = fontFamily,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    com.example.ui.components.SelectableLazyColumn(
        items = chapters,
        selectedIndex = selectedIndex,
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) { index, chapter, isSelected ->
            val isCurrent = currentChapter?.startTimeMs == chapter.startTimeMs || currentChapter?.title == chapter.title

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) backlightHighlight else Color.Transparent)
                    .clickable { onSelectChapter(chapter) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chapter.displayTime,
                    color = if (isSelected) Color.White else backlightTextSecondary,
                    fontSize = (9.5f * fontScale).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = chapter.title,
                    color = if (isSelected) Color.White else backlightTextPrimary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = if (isBold || isCurrent) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isCurrent) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "▶",
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = (10f * fontScale).sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

@Composable
fun IpodPodcastSearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    shows: List<PodcastShow>,
    selectedIndex: Int,
    onSelectShow: (PodcastShow) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (PodcastShow) -> Unit,
    isLoading: Boolean = false,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        // Campo de entrada de texto interativo
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            placeholder = {
                Text(
                    text = "Buscar podcasts no iTunes...",
                    fontSize = (11f * fontScale).sp,
                    color = backlightTextSecondary.copy(alpha = 0.7f),
                    fontFamily = fontFamily
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpar",
                            tint = backlightTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = backlightHighlight,
                unfocusedBorderColor = backlightHighlight.copy(alpha = 0.4f),
                focusedTextColor = backlightTextPrimary,
                unfocusedTextColor = backlightTextPrimary,
                focusedContainerColor = Color(0x33000000),
                unfocusedContainerColor = Color(0x22000000)
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Status banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22000000))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "DIGITE PARA BUSCAR" else "RESULTADOS ITUNES",
                color = backlightTextSecondary,
                fontSize = (8.5f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
            Text(
                text = "${shows.size} podcasts",
                color = backlightHighlight,
                fontSize = (9f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = backlightTextPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Buscando podcasts no iTunes...",
                        color = backlightTextPrimary,
                        fontSize = (10f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
            }
        } else if (shows.isEmpty() && searchQuery.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhum podcast encontrado para '$searchQuery'",
                    color = backlightTextSecondary,
                    fontSize = (10.5f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        } else if (shows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Digite acima para buscar globalmente...",
                    color = backlightTextSecondary,
                    fontSize = (10.5f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = shows,
                selectedIndex = selectedIndex,
                key = { _, show -> show.id },
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) { index, show, isSelected ->
                    val fav = isFavorite(show.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) backlightHighlight else Color.Transparent)
                            .clickable { onSelectShow(show) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(backlightTextPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_podcast_retro),
                                contentDescription = null,
                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = show.title,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (11f * fontScale).sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val metaParts = mutableListOf<String>()
                            if (show.author.isNotBlank()) metaParts.add(show.author)
                            if (show.episodeCount > 0) metaParts.add("${show.episodeCount} ep")
                            if (show.latestReleaseDate.isNotBlank()) metaParts.add(show.latestReleaseDate)
                            val subtitle = if (metaParts.isNotEmpty()) metaParts.joinToString(" • ") else show.country
                            Text(
                                text = subtitle,
                                color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                fontSize = (9f * fontScale).sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onToggleFavorite(show) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
