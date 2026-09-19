package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SignalCellularAlt
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.RadioStation
import com.example.player.RadioPlaybackStatus
import com.example.player.RdsInfo
import androidx.compose.ui.res.painterResource
import com.example.R

@Composable
fun RdsDisplay(
    station: RadioStation?,
    rdsInfo: RdsInfo,
    status: RadioPlaybackStatus,
    visualizerAmplitudes: List<Float>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    volume: Float = 0.8f,
    onStepVolumeUp: () -> Unit = {},
    onStepVolumeDown: () -> Unit = {},
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    liveSessionDurationSeconds: Long = 0L,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val hours = liveSessionDurationSeconds / 3600
    val minutes = (liveSessionDurationSeconds % 3600) / 60
    val seconds = liveSessionDurationSeconds % 60
    val sessionTimerFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "rds_marquee")
    val bufferPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffer_pulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top RDS Badges Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(backlightTextPrimary.copy(alpha = 0.12f))
                .border(1.dp, backlightTextPrimary.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Flat Pixelated RDS Icon matching theme color
                Icon(
                    painter = painterResource(id = R.drawable.ic_rds_pixel),
                    contentDescription = "RDS Ativo",
                    tint = if (status == RadioPlaybackStatus.PLAYING) backlightTextPrimary else backlightTextPrimary.copy(alpha = 0.35f),
                    modifier = Modifier.size(width = 30.dp, height = 12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                // STEREO pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (rdsInfo.isStereo) backlightTextPrimary else backlightTextPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "ST",
                        color = if (rdsInfo.isStereo) backlightBg else backlightTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // TP (Traffic Program) pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(backlightTextPrimary.copy(alpha = 0.15f))
                        .border(0.8.dp, backlightTextPrimary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "TP",
                        color = backlightTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Signal bars & Frequency
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = station?.displayFrequency ?: rdsInfo.frequencyMhz,
                    color = backlightTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "Sinal",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Center Content: Station Logo + Main RDS Data Matrix
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio Logo / Artwork with authentic retro iPod LCD badge
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backlightTextPrimary.copy(alpha = 0.14f))
                    .border(1.2.dp, backlightTextPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val favicon = station?.favicon?.trim().orEmpty()
                if (favicon.isNotBlank()) {
                    var loadFailed by remember(favicon) { mutableStateOf(false) }

                    if (!loadFailed) {
                        val imageRequest = remember(favicon, backlightTextPrimary, backlightBg) {
                            ImageRequest.Builder(context)
                                .data(favicon)
                                .transformations(
                                    LcdMonochromeTransformation(
                                        darkColor = backlightTextPrimary,
                                        lightColor = Color.Transparent,
                                        dither = true,
                                        targetResolution = 128
                                    )
                                )
                                .crossfade(false)
                                .build()
                        }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = station?.name ?: "Logotipo da Rádio",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentScale = ContentScale.Fit,
                            onError = { loadFailed = true }
                        )
                    } else {
                        // Fallback do LCD: mantém o ícone clássico do display físico
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Logotipo da Rádio",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                } else {
                    // Sem logo remoto: mantém o ícone clássico do display físico
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Logotipo da Rádio",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // RDS Info Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(backlightTextPrimary.copy(alpha = 0.08f))
                    .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Program Service (PS) / Station Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PS: ${station?.name?.uppercase() ?: rdsInfo.programService}",
                        color = backlightTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remover dos Favoritos" else "Adicionar aos Favoritos",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Program Type (PTY) & Country
                Text(
                    text = "PTY: [${station?.primaryGenre?.uppercase() ?: "GERAL"}] • ${station?.country ?: "Mundial"}",
                    color = backlightTextSecondary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Bitrate & Codec
                Text(
                    text = "AUDIO: ${station?.bitrate ?: 128} kbps ${station?.codec ?: "MP3"} • DIGITAL HD",
                    color = backlightTextSecondary.copy(alpha = 0.9f),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val rdsTextToDisplay = if (station == null) {
            "SINTONIZE UMA EMISSORA"
        } else if (rdsInfo.hasRealRds && rdsInfo.radioText.isNotBlank() && !rdsInfo.radioText.equals("[sem informações]", ignoreCase = true)) {
            rdsInfo.radioText.replace("/RDS", "", ignoreCase = true)
                .replace("/ RDS", "", ignoreCase = true)
                .trim()
        } else {
            "[SEM INFORMAÇÕES]"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(backlightTextPrimary.copy(alpha = 0.08f))
                .border(1.dp, backlightTextPrimary.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = rdsTextToDisplay,
                color = backlightTextPrimary,
                fontSize = (10.5f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Barra de Reprodução / Indicadores de Status e Conexão (Live Streaming)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(backlightTextPrimary.copy(alpha = 0.12f))
                .border(1.dp, backlightTextPrimary.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            if (status == RadioPlaybackStatus.NO_INTERNET) {
                // Perda de conexão: Ícone sem conexão + VERIFIQUE A CONEXÃO COM A INTERNET
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_wifi_off_alert),
                            contentDescription = "Sem Conexão",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "VERIFIQUE A CONEXÃO COM A INTERNET",
                            color = backlightTextPrimary,
                            fontSize = (7.8f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(backlightTextPrimary)
                            .clickable { onRetry() }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "RECONECTAR",
                            color = backlightBg,
                            fontSize = (7.5f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }
                }
            } else {
                // Modo Rádio Normal (Live Streaming):
                // Lado esquerdo: ícone monocromático de antena/transmissão + AO VIVO
                // Lado direito: tempo de reprodução contínua da sessão atual (hh:mm:ss)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_live_antenna),
                            contentDescription = "Transmissão Ao Vivo",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (status == RadioPlaybackStatus.BUFFERING) stringResource(R.string.status_tuning) else stringResource(R.string.status_live),
                            color = backlightTextPrimary,
                            fontSize = (8.5f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }

                    Text(
                        text = sessionTimerFormatted,
                        color = backlightTextPrimary,
                        fontSize = (9f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
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
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightTextPrimary)
                        .clickable { onStepVolumeDown() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "VOL -",
                        color = backlightBg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
                    color = backlightTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightTextPrimary)
                        .clickable { onStepVolumeUp() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "VOL +",
                        color = backlightBg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Real-time dynamic volume bar with explicit width calculation
            Row(verticalAlignment = Alignment.CenterVertically) {
                val totalWidth = 90.dp
                val filledWidth = totalWidth * volume.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(backlightTextPrimary.copy(alpha = 0.2f))
                        .border(1.dp, backlightTextPrimary, RoundedCornerShape(2.dp))
                ) {
                    if (filledWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(filledWidth)
                                .height(8.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(backlightTextPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Animated VU Audio Meter Visualizer (Monochrome LCD pixel bars)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(backlightTextPrimary.copy(alpha = 0.1f))
                .border(1.dp, backlightTextPrimary.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = visualizerAmplitudes.size
                val spacing = 4.dp.toPx()
                val totalSpacing = spacing * (barCount - 1)
                val barWidth = (size.width - totalSpacing) / barCount

                for (i in 0 until barCount) {
                    val amp = if (status == RadioPlaybackStatus.PLAYING) visualizerAmplitudes[i] else 0.08f
                    val barHeight = (size.height * amp).coerceAtLeast(3.dp.toPx())
                    val left = i * (barWidth + spacing)
                    val top = size.height - barHeight

                    drawRoundRect(
                        color = backlightTextPrimary,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
        }
    }
}
