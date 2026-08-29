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

data class RootMenuItem(
    val title: String,
    val icon: ImageVector,
    val destination: Int // 0: Radio, 1: MP3, 2: Video, 3: Game, 4: Car, 5: About
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
    val menuItems = remember {
        listOf(
            RootMenuItem("Rádio", Icons.Default.Radio, 0),
            RootMenuItem("Mp3 Player", Icons.Default.LibraryMusic, 1),
            RootMenuItem("Video Player", Icons.Default.VideoLibrary, 2),
            RootMenuItem("Equalizador", Icons.Default.GraphicEq, 3),
            RootMenuItem("Jogo", Icons.Default.SportsEsports, 4),
            RootMenuItem("Modo Carro", Icons.Default.DirectionsCar, 5),
            RootMenuItem("Configurações", Icons.Default.Settings, 6),
            RootMenuItem("Sobre", Icons.Default.Info, 7)
        )
    }

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
        // Top iPod Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightHighlight.copy(alpha = 0.22f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "IPod Class + Radio",
                color = backlightTextPrimary,
                fontSize = (13f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = "Battery",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
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
                        AsyncImage(
                            model = currentArtUrl,
                            contentDescription = "Cover Art",
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(bwMatrix),
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        if (!nowPlayingTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = nowPlayingTitle,
                                color = backlightTextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily,
                                maxLines = 1
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
                                .background(Color(0xFF0F172A))
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
                                fontSize = (10f * fontScale).sp,
                                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                                fontFamily = fontFamily,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GLOBAL\nRADIOPOD",
                                color = backlightTextPrimary.copy(alpha = 0.6f),
                                fontSize = 8.5.sp,
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
                            text = "GLOBAL\nRADIOPOD",
                            color = backlightTextPrimary.copy(alpha = 0.5f),
                            fontSize = 8.5.sp,
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
