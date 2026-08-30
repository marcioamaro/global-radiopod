package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.RadioStation

@Composable
fun StationLargeCard(
    station: RadioStation,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val borderModifier = if (isPlaying) {
        Modifier.border(
            2.dp,
            backlightTextPrimary,
            RoundedCornerShape(10.dp)
        )
    } else if (isSelected) {
        Modifier.border(1.5.dp, backlightTextPrimary, RoundedCornerShape(10.dp))
    } else {
        Modifier.border(1.dp, backlightTextPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
    }

    val cardBg = if (isSelected) {
        backlightHighlight.copy(alpha = 0.28f)
    } else {
        Color(0x22000000)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(borderModifier)
            .clickable(onClick = onClick)
            .testTag("favorite_large_card_${station.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extra-Large Icon/Logo (72dp) - Padrão Flat Monocromático LCD puro
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x18000000))
                    .border(1.5.dp, backlightTextPrimary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = "Logotipo Rádio",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details and actions
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = station.name,
                        color = backlightTextPrimary,
                        fontSize = (15 * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remover dos Favoritos",
                            tint = backlightTextPrimary.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${station.country} • ${station.primaryGenre}",
                    color = backlightTextPrimary.copy(alpha = 0.8f),
                    fontSize = (12 * fontScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Linha de baixo padronizada: os 3 itens com o mesmo formato, cor e borda
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chipShape = RoundedCornerShape(4.dp)
                    val chipBg = Color(0xFF0F172A)
                    val chipBorder = 0.8.dp
                    val chipBorderColor = backlightHighlight.copy(alpha = 0.4f)
                    val chipPadding = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)

                    // 1. Frequency Chip (Mesmo padrão dos dois itens à direita)
                    val freqText = station.displayFrequency.ifBlank { "FM STEREO" }
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(chipBg)
                            .border(chipBorder, chipBorderColor, chipShape)
                            .then(chipPadding)
                    ) {
                        Text(
                            text = freqText,
                            color = backlightTextSecondary,
                            fontSize = (10 * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    // 2. Bitrate Chip
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(chipBg)
                            .border(chipBorder, chipBorderColor, chipShape)
                            .then(chipPadding)
                    ) {
                        Text(
                            text = "${station.bitrate}k ${station.codec}".trim().ifEmpty { "128k MP3" },
                            color = backlightTextSecondary,
                            fontSize = (10 * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    // 3. Status Chip (Ao Vivo)
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(chipBg)
                            .border(chipBorder, if (isPlaying) backlightTextPrimary else chipBorderColor, chipShape)
                            .then(chipPadding)
                    ) {
                        Text(
                            text = if (isPlaying) stringResource(R.string.status_live) else "AO VIVO",
                            color = if (isPlaying) backlightTextPrimary else backlightTextSecondary,
                            fontSize = (9.5f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }
    }
}
