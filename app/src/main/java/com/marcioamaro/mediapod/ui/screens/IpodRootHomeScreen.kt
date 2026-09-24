package com.marcioamaro.mediapod.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

import androidx.compose.ui.res.stringResource
import com.marcioamaro.mediapod.R

data class RootMenuItem(
    val title: String,
    val icon: ImageVector,
    val destination: Int // 0: Radio, 1: MP3, 2: Video, 3: Game, 4: Car, 5: About, 12: Close App
)

@Composable
fun IpodRootHomeScreen(
    selectedIndex: Int,
    onSelectItem: (Int) -> Unit,
    currentArtUrl: String?,
    nowPlayingTitle: String?,
    nowPlayingSubtitle: String?,
    isLocalAudio: Boolean = false,
    isPlaying: Boolean,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val menuItems = listOf(
        RootMenuItem(stringResource(R.string.menu_radio), Icons.Default.Radio, 0),
        RootMenuItem(stringResource(R.string.menu_podcasts), Icons.Default.Podcasts, 1),
        RootMenuItem(stringResource(R.string.menu_mp3), Icons.Default.LibraryMusic, 2),
        RootMenuItem(stringResource(R.string.menu_video), Icons.Default.VideoLibrary, 3),
        RootMenuItem(stringResource(R.string.menu_youtube), Icons.Default.SmartDisplay, 4),
        RootMenuItem(stringResource(R.string.menu_equalizer), Icons.Default.GraphicEq, 5),
        RootMenuItem(stringResource(R.string.menu_audio_output), Icons.Default.SpeakerGroup, 6),
        RootMenuItem(stringResource(R.string.menu_game), Icons.Default.SportsEsports, 7),
        RootMenuItem(stringResource(R.string.menu_car_mode), Icons.Default.DirectionsCar, 8),
        RootMenuItem(stringResource(R.string.menu_dock_mode), Icons.Default.Schedule, 9),
        RootMenuItem(stringResource(R.string.menu_settings), Icons.Default.Settings, 10),
        RootMenuItem(stringResource(R.string.menu_about), Icons.Default.Info, 11),
        RootMenuItem(stringResource(R.string.library_title), Icons.Default.Bookmarks, 12),
        RootMenuItem(stringResource(R.string.menu_close_app), Icons.Default.PowerSettingsNew, 13)
    )

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in menuItems.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    val bwMatrix = remember { ColorMatrix().apply { setToSaturation(0f) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
    ) {
        // Top iPod Sub-Header Bar (Title only, avoiding icon redundancy with upper status bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightHighlight.copy(alpha = 0.22f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MediaPod + Radio / Podcast",
                color = backlightTextPrimary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        // Split View: Left List (Menu) & Right Panel (Album Art / Preview)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Half: Menu List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
            ) {
                itemsIndexed(menuItems) { index, item ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) backlightHighlight.copy(alpha = 0.88f)
                                else Color.Transparent
                            )
                            .clickable { onSelectItem(item.destination) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else backlightTextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = item.title,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (12.5f * fontScale).sp,
                            fontWeight = if (isBold || isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(backlightTextPrimary.copy(alpha = 0.2f))
            )

            // Right Half: iPod Artwork / Dynamic Display
            Box(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
                    .background(backlightHighlight.copy(alpha = 0.08f))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLocalAudio && !currentArtUrl.isNullOrBlank()) {
                    // Local MP3 Album Artwork
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Música em Reprodução",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(52.dp)
                        )
                        if (!nowPlayingTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = nowPlayingTitle,
                                color = backlightTextPrimary,
                                fontSize = (11f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (!nowPlayingTitle.isNullOrBlank() || selectedIndex == 0) {
                    // RÁDIO: NUNCA exibe logotipo original da internet.
                    // Exibe SEMPRE o logotipo monocromático com caixa retro de LCD
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33000000))
                                .border(1.2.dp, backlightHighlight, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = backlightTextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        if (!nowPlayingTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = nowPlayingTitle,
                                color = backlightTextPrimary,
                                fontSize = (11f * fontScale).sp,
                                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                                fontFamily = fontFamily,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "MEDIAPOD\nRADIO / PODCAST",
                                color = backlightTextPrimary.copy(alpha = 0.6f),
                                fontSize = (10.5f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (selectedIndex) {
                                1 -> Icons.Default.LibraryMusic
                                2 -> Icons.Default.VideoLibrary
                                3 -> Icons.Default.SportsEsports
                                4 -> Icons.Default.DirectionsCar
                                else -> Icons.Default.GraphicEq
                            },
                            contentDescription = null,
                            tint = backlightTextPrimary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "MEDIAPOD\nRADIO / PODCAST",
                            color = backlightTextPrimary.copy(alpha = 0.5f),
                            fontSize = (10.5f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
