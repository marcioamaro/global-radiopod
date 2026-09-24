package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.RadioStation

@Composable
fun StationItemView(
    station: RadioStation,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean = station.isFavorite,
    showLogo: Boolean = false,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    modifier: Modifier = Modifier,
    rankPosition: Int? = null
) {
    val context = LocalContext.current

    val bg = if (isSelected) backlightHighlight else Color.Transparent
    val borderModifier = if (isSelected) {
        Modifier.border(1.dp, backlightTextPrimary, RoundedCornerShape(4.dp))
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .then(borderModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .testTag("station_item_${station.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Compact logo (40dp) - LCD Monocromático ou ícone clássico
        val darkTone = if (isSelected) Color.White else backlightTextPrimary
        val favicon = station.effectiveFavicon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(backlightTextPrimary.copy(alpha = 0.14f))
                .border(1.2.dp, if (isPlaying) backlightTextPrimary else backlightTextPrimary.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (showLogo && favicon.isNotBlank()) {
                var loadFailed by remember(favicon, isSelected) { mutableStateOf(false) }
                if (!loadFailed) {
                    val imageRequest = remember(favicon, darkTone) {
                        ImageRequest.Builder(context)
                            .data(favicon)
                            .transformations(
                                LcdMonochromeTransformation(
                                    darkColor = darkTone,
                                    lightColor = Color.Transparent,
                                    dither = true,
                                    targetResolution = 64
                                )
                            )
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = station.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        contentScale = ContentScale.Fit,
                        onError = { loadFailed = true }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Rádio",
                        tint = darkTone,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = "Rádio",
                    tint = darkTone,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Station Details with dynamic font family and scale
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = rankPosition?.let { "$it. ${station.name}" } ?: station.name,
                color = if (isSelected) Color.White else backlightTextPrimary,
                fontSize = (13.5f * fontScale).sp,
                fontWeight = if (isSelected || isPlaying || isBold) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${station.country} • ${station.primaryGenre}",
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else backlightTextSecondary,
                    fontSize = (10.5f * fontScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${station.bitrate}k",
                    color = if (isSelected) Color.White else backlightTextPrimary,
                    fontSize = (9.5f * fontScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Favorito" else "Favoritar",
                tint = if (isSelected) Color.White else backlightTextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
