package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.R
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.preferences.IpodFontType
import com.example.ui.LcdBacklight
import com.example.data.preferences.toFontFamily
import com.example.data.repository.CuratedData
import com.example.player.RadioPlaybackStatus
import com.example.player.RdsInfo
import com.example.ui.components.ClickWheel

enum class CarTab {
    FAVORITES,
    BRAZIL,
    TOP_WORLD,
    GENRES,
    RECENTS
}

@Composable
fun CarModeScreen(
    currentStation: RadioStation?,
    rdsInfo: RdsInfo,
    playbackStatus: RadioPlaybackStatus,
    visualizerAmplitudes: List<Float>,
    volume: Float,
    favorites: List<RadioStation>,
    recentsList: List<RadioStation> = emptyList(),
    onTogglePlayPause: () -> Unit,
    onNextStation: () -> Unit,
    onPrevStation: () -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onSelectStation: (RadioStation) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRotaryScroll: (stepDelta: Int) -> Unit = {},
    onToggleDisplayMode: () -> Unit,
    carModeSource: com.example.ui.CarModeSource = com.example.ui.CarModeSource.RADIO,
    onToggleCarModeSource: () -> Unit = {},
    backlight: LcdBacklight = LcdBacklight.RETRO_IPOD_LCD,
    customBodyColor: Long = 0xFFE2E4E8,
    customWheelColor: Long = 0xFFE2E4E8,
    customWheelTextColor: Long = 0xFF475569,
    customCenterButtonColor: Long = 0xFFFFFFFF,
    fontType: IpodFontType = IpodFontType.MONOSPACE,
    fontScale: Float = 1.5f,
    isBold: Boolean = true,
    currentLocalAudio: com.example.data.model.LocalAudioTrack? = null,
    audioPositionMs: Long = 0L,
    audioDurationMs: Long = 0L,
    localAudioFolders: List<com.example.data.model.MediaFolder> = emptyList(),
    localAudioTracks: List<com.example.data.model.LocalAudioTrack> = emptyList(),
    onSelectAudioFolder: (com.example.data.model.MediaFolder) -> Unit = {},
    onSelectAudioTrack: (com.example.data.model.LocalAudioTrack) -> Unit = {},
    liveSessionDurationSeconds: Long = 0L,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(CarTab.FAVORITES) }
    var selectedGenreTag by remember { mutableStateOf<String?>(null) }
    var selectedCity by remember { mutableStateOf<String?>(null) }

    var currentTime by remember {
        mutableStateOf(
            if (DateFormat.is24HourFormat(context)) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            } else {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            }
        )
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            val nextMinute = 60_000L - (now % 60_000L)
            delay(nextMinute.coerceAtLeast(1000L))
            currentTime = if (DateFormat.is24HourFormat(context)) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            } else {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            }
        }
    }

    val isFavorite = currentStation?.let { st -> favorites.any { it.id == st.id } } ?: false

    // Color synchronization with iPod settings
    val bodyColor = Color(customBodyColor)
    val wheelColor = Color(customWheelColor)
    val wheelTextColor = Color(customWheelTextColor)
    val centerButtonColor = Color(customCenterButtonColor)

    val backlightBg = Color(backlight.background)
    val backlightTextPrimary = Color(backlight.textPrimary)
    val backlightTextSecondary = Color(backlight.textSecondary)
    val backlightHighlight = Color(backlight.highlight)
    val fontFamily = fontType.toFontFamily()

    val carHours = liveSessionDurationSeconds / 3600
    val carMinutes = (liveSessionDurationSeconds % 3600) / 60
    val carSeconds = liveSessionDurationSeconds % 60
    val carLiveTimerFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", carHours, carMinutes, carSeconds)

    // ColorMatrix for strict authentic iPod monochrome logo rendering
    val bwMatrix = remember { ColorMatrix().apply { setToSaturation(0f) } }

    val infiniteTransition = rememberInfiniteTransition(label = "car_rds_pulse")
    val bufferPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffer_pulse"
    )

    // Outer dark car frame
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .padding(6.dp)
    ) {
        // Physical Chassis Shell with iPod-style bevel & dynamic user body color
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            bodyColor.copy(alpha = 0.96f),
                            bodyColor,
                            bodyColor.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(8.dp)
        ) {
            // Horizontal Wide Layout: Left 30% (Click Wheel) & Right 70% (LCD Screen)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // =========================================================================
                // 1. LEFT COLUMN (~30% WIDTH): Click Wheel + Botão MODO POD abaixo
                // =========================================================================
                Column(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f, fill = false),
                        contentAlignment = Alignment.Center
                    ) {
                        val calculatedSize = minOf(maxHeight * 0.85f, maxWidth * 0.95f)

                        ClickWheel(
                            onRotaryScroll = onRotaryScroll,
                            onCenterClick = onTogglePlayPause,
                            onMenuClick = {
                                selectedTab = when (selectedTab) {
                                    CarTab.FAVORITES -> CarTab.BRAZIL
                                    CarTab.BRAZIL -> CarTab.TOP_WORLD
                                    CarTab.TOP_WORLD -> CarTab.GENRES
                                    CarTab.GENRES -> CarTab.RECENTS
                                    CarTab.RECENTS -> CarTab.FAVORITES
                                }
                            },
                            onPlayPauseClick = onTogglePlayPause,
                            onPrevClick = onPrevStation,
                            onNextClick = onNextStation,
                            wheelColor = wheelColor,
                            textColor = wheelTextColor,
                            centerButtonColor = centerButtonColor,
                            wheelSize = calculatedSize
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Botão MODO POD abaixo do Click Wheel com a mesma aparência do botão MODO CARRO
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(wheelColor.copy(alpha = 0.22f))
                            .border(1.2.dp, wheelColor, RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleDisplayMode)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("exit_to_pod_mode_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = "Modo Padrão",
                            tint = wheelTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "MODO PADRÃO",
                            color = wheelTextColor,
                            fontSize = (9.5f * fontScale).sp,
                            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }

                // =========================================================================
                // 2. RIGHT COLUMN (~70% WIDTH): Retro LCD Screen (iPod Authenticity)
                // =========================================================================
                Box(
                    modifier = Modifier
                        .weight(0.70f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(backlightBg)
                        .border(2.5.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // -----------------------------------------------------------------
                        // ZONE A: TOP HEADER & MONOCHROME NAV ICONS INSIDE LCD (~15% height)
                        // -----------------------------------------------------------------
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backlightHighlight.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Status info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "IPod Class Car",
                                    color = backlightTextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = fontFamily
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (playbackStatus == RadioPlaybackStatus.PLAYING) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Tocando",
                                        tint = backlightTextPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else if (playbackStatus == RadioPlaybackStatus.BUFFERING) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_hourglass_flat),
                                        contentDescription = "Bufferizando",
                                        tint = backlightHighlight.copy(alpha = bufferPulse),
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else if (playbackStatus == RadioPlaybackStatus.NO_INTERNET) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_wifi_off_alert),
                                        contentDescription = "Sem Internet",
                                        tint = backlightTextPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0x33000000))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "STEREO • RDS",
                                        color = backlightTextSecondary,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontFamily
                                    )
                                }
                            }

                            // Right: 6 Monochrome Navigation Icons Inside LCD
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CarLcdNavTabButton(
                                    icon = Icons.Default.Star,
                                    label = "Fav",
                                    isSelected = selectedTab == CarTab.FAVORITES,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    onClick = { selectedTab = CarTab.FAVORITES }
                                )

                                CarLcdNavTabButton(
                                    icon = Icons.Default.Flag,
                                    label = "BR",
                                    isSelected = selectedTab == CarTab.BRAZIL,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    onClick = { selectedTab = CarTab.BRAZIL }
                                )

                                CarLcdNavTabButton(
                                    icon = Icons.Default.Public,
                                    label = "Top",
                                    isSelected = selectedTab == CarTab.TOP_WORLD,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    onClick = { selectedTab = CarTab.TOP_WORLD }
                                )

                                CarLcdNavTabButton(
                                    icon = Icons.Default.Category,
                                    label = "Gên",
                                    isSelected = selectedTab == CarTab.GENRES,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    onClick = { selectedTab = CarTab.GENRES }
                                )

                                CarLcdNavTabButton(
                                    icon = Icons.Default.History,
                                    label = "Rec",
                                    isSelected = selectedTab == CarTab.RECENTS,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    onClick = { selectedTab = CarTab.RECENTS }
                                )

                                // Exit back to portrait iPod mode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x33000000))
                                        .clickable(onClick = onToggleDisplayMode)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = "Modo IPod Class Retrato",
                                        tint = backlightTextPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                Text(
                                    text = currentTime,
                                    color = backlightTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontFamily,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = "Bateria",
                                    tint = backlightTextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // -----------------------------------------------------------------
                        // ZONE B: STATION LOGO (MONOCHROME) & NAME (~30% height)
                        // -----------------------------------------------------------------
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.30f)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Monochromatic Station Logo (Retro LCD style)
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(backlightTextPrimary.copy(alpha = 0.14f))
                                        .border(1.2.dp, backlightTextPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = "Logo Rádio",
                                        tint = backlightTextPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    // Big, bold, harmonious station title
                                    Text(
                                        text = currentStation?.name ?: "Selecione uma Estação",
                                        color = backlightTextPrimary,
                                        fontSize = (18f * fontScale).coerceIn(16f, 26f).sp,
                                        fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                                        fontFamily = fontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentStation?.displayFrequency ?: (rdsInfo.frequencyMhz.ifBlank { "Ao Vivo" }),
                                            color = backlightHighlight,
                                            fontSize = (11f * fontScale).coerceIn(10f, 15f).sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = fontFamily
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• ${currentStation?.city.orEmpty()} ${currentStation?.country.orEmpty()}",
                                            color = backlightTextSecondary,
                                            fontSize = (10.5f * fontScale).coerceIn(9.5f, 14f).sp,
                                            fontFamily = fontFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Favorite Star Button
                                IconButton(
                                    onClick = { currentStation?.let { onToggleFavorite(it) } },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (isFavorite) backlightHighlight else backlightTextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // -----------------------------------------------------------------
                        // ZONE C: RDS DIGITAL SECTION (~20% height)
                        // -----------------------------------------------------------------
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.20f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x22000000))
                                .border(1.dp, backlightHighlight.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
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
                                        painter = painterResource(id = R.drawable.ic_rds_pixel),
                                        contentDescription = "RDS",
                                        tint = backlightHighlight,
                                        modifier = Modifier.size(width = 30.dp, height = 12.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    if (playbackStatus == RadioPlaybackStatus.NO_INTERNET) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_wifi_off_alert),
                                            contentDescription = "Sem Conexão",
                                            tint = backlightTextPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_live_antenna),
                                            contentDescription = "Ao Vivo",
                                            tint = backlightHighlight,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        if (playbackStatus == RadioPlaybackStatus.PLAYING) {
                                            Text(
                                                text = carLiveTimerFormatted,
                                                color = backlightTextPrimary,
                                                fontSize = (11f * fontScale).coerceIn(10f, 14f).sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "•",
                                                color = backlightTextSecondary,
                                                fontSize = (11f * fontScale).coerceIn(10f, 14f).sp
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                    }

                                    val noInternetMsg = stringResource(R.string.msg_connection_error).uppercase()
                                    val rdsDisplay = if (playbackStatus == RadioPlaybackStatus.NO_INTERNET) {
                                        noInternetMsg
                                    } else if (rdsInfo.hasRealRds && rdsInfo.radioText.isNotBlank() && !rdsInfo.radioText.equals("[sem informações]", ignoreCase = true)) {
                                        rdsInfo.radioText
                                    } else {
                                        "[sem informações]"
                                    }

                                    Text(
                                        text = rdsDisplay,
                                        color = backlightTextPrimary,
                                        fontSize = (12f * fontScale).coerceIn(11f, 16f).sp,
                                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = fontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // LCD Volume Gauge / Meter
                                    CarLcdVolumeIndicator(
                                        volume = volume,
                                        onVolumeChange = onVolumeChange,
                                        backlightTextPrimary = backlightTextPrimary,
                                        backlightHighlight = backlightHighlight,
                                        fontFamily = fontFamily
                                    )

                                    // Mini Animated VU Equalizer (5 bars)
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .height(14.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val bars = 5
                                            val spacing = 2.dp.toPx()
                                            val barWidth = (size.width - (spacing * (bars - 1))) / bars
                                            for (i in 0 until bars) {
                                                val amp = if (playbackStatus == RadioPlaybackStatus.PLAYING) {
                                                    visualizerAmplitudes.getOrElse(i) { 0.3f }
                                                } else 0.1f
                                                val barH = (size.height * amp).coerceAtLeast(2.dp.toPx())
                                                drawRoundRect(
                                                    color = backlightHighlight,
                                                    topLeft = Offset(i * (barWidth + spacing), size.height - barH),
                                                    size = Size(barWidth, barH),
                                                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // -----------------------------------------------------------------
                        // ZONE D: ACTIVE TAB STATIONS CAROUSEL (~35% height)
                        // -----------------------------------------------------------------
                        val allBrazilStations = remember { CuratedData.CURATED_GLOBAL_STATIONS.filter { it.countryCode == "BR" } }
                        val currentTabStations: List<RadioStation> = when (selectedTab) {
                            CarTab.FAVORITES -> favorites
                            CarTab.BRAZIL -> {
                                if (selectedCity.isNullOrBlank() || selectedCity.equals("ALL", ignoreCase = true)) {
                                    allBrazilStations
                                } else {
                                    val c = selectedCity!!.trim().lowercase()
                                    allBrazilStations.filter {
                                        it.city.lowercase().contains(c) ||
                                        it.state.lowercase().contains(c) ||
                                        it.name.lowercase().contains(c) ||
                                        it.tags.lowercase().contains(c)
                                    }
                                }
                            }
                            CarTab.TOP_WORLD -> CuratedData.CURATED_GLOBAL_STATIONS
                            CarTab.GENRES -> {
                                if (selectedGenreTag == null || selectedGenreTag.equals("ALL", ignoreCase = true)) {
                                    allBrazilStations
                                } else {
                                    CuratedData.CURATED_GLOBAL_STATIONS
                                        .filter { it.tags.contains(selectedGenreTag!!, ignoreCase = true) || it.primaryGenre.contains(selectedGenreTag!!, ignoreCase = true) }
                                }
                            }
                            CarTab.RECENTS -> {
                                if (recentsList.isNotEmpty()) recentsList
                                else com.example.data.preferences.IpodPreferencesManager.getInstance(context).getRecentStations()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.35f)
                                .padding(top = 3.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (currentTabStations.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (selectedTab) {
                                            CarTab.FAVORITES -> "Nenhuma rádio favorita adicionada."
                                            CarTab.RECENTS -> "Nenhuma rádio recente sintonizada."
                                            else -> "Nenhuma emissora nesta categoria."
                                        },
                                        color = backlightTextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = fontFamily
                                    )
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(currentTabStations, key = { it.id }) { station ->
                                        val isPlayingThis = station.id == currentStation?.id
                                        CarLcdStationCard(
                                            station = station,
                                            isPlaying = isPlayingThis,
                                            backlightBg = backlightBg,
                                            backlightTextPrimary = backlightTextPrimary,
                                            backlightTextSecondary = backlightTextSecondary,
                                            backlightHighlight = backlightHighlight,
                                            fontFamily = fontFamily,
                                            fontScale = fontScale,
                                            bwMatrix = bwMatrix,
                                            onClick = { onSelectStation(station) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarLcdNavTabButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    backlightTextPrimary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) backlightHighlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else backlightTextPrimary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else backlightTextPrimary,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                fontFamily = fontFamily
            )
        }
    }
}

@Composable
private fun CarLcdStationCard(
    station: RadioStation,
    isPlaying: Boolean,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    bwMatrix: ColorMatrix,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .width(160.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPlaying) backlightHighlight else Color(0x18000000))
            .border(
                1.dp,
                if (isPlaying) backlightHighlight else backlightHighlight.copy(alpha = 0.25f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini Monochrome Station Logo
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(backlightTextPrimary.copy(alpha = 0.14f))
                .border(0.8.dp, backlightTextPrimary.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = if (isPlaying) Color.White else backlightTextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                color = if (isPlaying) Color.White else backlightTextPrimary,
                fontSize = (11f * fontScale).coerceIn(10f, 13.5f).sp,
                fontWeight = if (isPlaying) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = station.displayFrequency.ifBlank { station.country },
                color = if (isPlaying) Color.White.copy(alpha = 0.85f) else backlightTextSecondary,
                fontSize = 9.sp,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CarLcdVolumeIndicator(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    backlightTextPrimary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier
) {
    val volPercent = (volume * 100).toInt().coerceIn(0, 100)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x33000000))
            .border(0.8.dp, backlightHighlight.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // Step Down Button
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable { onVolumeChange((volume - 0.05f).coerceAtLeast(0f)) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "-",
                color = backlightTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
        }

        // Speaker Icon
        Icon(
            imageVector = when {
                volPercent == 0 -> Icons.Default.VolumeMute
                volPercent < 50 -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            },
            contentDescription = "Volume",
            tint = backlightHighlight,
            modifier = Modifier.size(12.dp)
        )

        // Segmented Matrix Level Bars (8 bars)
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            val totalBars = 8
            val filledBars = ((volPercent / 100f) * totalBars).toInt()
            for (i in 0 until totalBars) {
                val isFilled = i < filledBars
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(9.dp)
                        .background(if (isFilled) backlightHighlight else backlightTextPrimary.copy(alpha = 0.2f))
                )
            }
        }

        // Percentage Text
        Text(
            text = "$volPercent%",
            color = backlightTextPrimary,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily
        )

        // Step Up Button
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable { onVolumeChange((volume + 0.05f).coerceAtMost(1f)) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = backlightTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
        }
    }
}
