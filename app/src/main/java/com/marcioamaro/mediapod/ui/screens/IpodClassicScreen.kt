package com.marcioamaro.mediapod.ui.screens

import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.marcioamaro.mediapod.ui.components.AmbilWarnaColorPickerDialog
import com.marcioamaro.mediapod.ui.components.ColorPickerTarget
import com.marcioamaro.mediapod.ui.theme.IpodColorContrastUtil
import com.marcioamaro.mediapod.util.BackupRestoreManager
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.marcioamaro.mediapod.ui.RadioViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.marcioamaro.mediapod.util.BatteryOptimizationHelper
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.marcioamaro.mediapod.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.data.preferences.IpodFontSizeScale
import com.marcioamaro.mediapod.data.preferences.IpodFontType
import com.marcioamaro.mediapod.data.preferences.IpodWheelPreset
import com.marcioamaro.mediapod.data.preferences.toFontFamily
import com.marcioamaro.mediapod.data.repository.CuratedData
import com.marcioamaro.mediapod.player.RadioPlaybackStatus
import com.marcioamaro.mediapod.player.RdsInfo
import com.marcioamaro.mediapod.ui.DisplayMode
import com.marcioamaro.mediapod.ui.IpodChassisTheme
import com.marcioamaro.mediapod.ui.IpodScreenDestination
import com.marcioamaro.mediapod.ui.LcdBacklight
import com.marcioamaro.mediapod.ui.UiState
import com.marcioamaro.mediapod.ui.components.ClickWheel
import com.marcioamaro.mediapod.ui.components.IpodHeader
import com.marcioamaro.mediapod.ui.components.RdsDisplay
import com.marcioamaro.mediapod.ui.components.MonochromeFlag
import com.marcioamaro.mediapod.util.IpodSoundAndHaptics
import android.webkit.WebView

@Composable
fun IpodClassicScreen(
    uiState: UiState,
    playbackStatus: RadioPlaybackStatus,
    currentStation: RadioStation?,
    rdsInfo: RdsInfo,
    visualizerAmplitudes: List<Float>,
    volume: Float = 0.8f,
    favorites: List<RadioStation>,
    sleepTimerMinutes: Int,
    sleepTimerSecondsRemaining: Long = 0L,
    brickGameCenterAction: Long = 0L,
    onRotaryScroll: (Int) -> Unit,
    onCenterClick: () -> Unit,
    onMenuClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleDisplayMode: () -> Unit,
    onSelectDestination: (IpodScreenDestination) -> Unit,
    onSelectStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchCountryChange: (String) -> Unit = {},
    onSearchGenreChange: (String) -> Unit = {},
    onSearchStateChange: (String) -> Unit = {},
    onSearchCityChange: (String) -> Unit = {},
    onStepVolumeUp: () -> Unit = {},
    onStepVolumeDown: () -> Unit = {},
    onPaddleMove: (Float) -> Unit = {},
    soundAndHaptics: IpodSoundAndHaptics? = null,
    onSelectGenre: (com.marcioamaro.mediapod.data.repository.GenreCategory) -> Unit,
    onSelectCountry: (com.marcioamaro.mediapod.data.repository.CountryCategory) -> Unit,
    onSetChassisTheme: (IpodChassisTheme) -> Unit,
    onSetCustomBodyColor: (Long) -> Unit = {},
    onSetBacklight: (LcdBacklight) -> Unit,
    onSetWheelPreset: (IpodWheelPreset) -> Unit = {},
    onSetCustomWheelColors: (Long, Long, Long) -> Unit = { _, _, _ -> },
    onSetFontType: (IpodFontType) -> Unit = {},
    onSetFontSizeScale: (IpodFontSizeScale) -> Unit = {},
    onSetFontBold: (Boolean) -> Unit = {},
    onSetAutoPlay: (Boolean) -> Unit = {},
    onToggleSound: () -> Unit = {},
    onToggleHaptics: () -> Unit = {},
    onSetSleepTimer: (Int) -> Unit = {},
    currentLocalAudio: com.marcioamaro.mediapod.data.model.LocalAudioTrack? = null,
    audioPositionMs: Long = 0L,
    audioDurationMs: Long = 0L,
    videoPlayerManager: com.marcioamaro.mediapod.player.LocalVideoPlayerManager? = null,
    onSelectAudioFolder: (com.marcioamaro.mediapod.data.model.MediaFolder) -> Unit = {},
    onSelectAudioTrack: (com.marcioamaro.mediapod.data.model.LocalAudioTrack) -> Unit = {},
    onSelectVideoFolder: (com.marcioamaro.mediapod.data.model.MediaFolder) -> Unit = {},
    onSelectVideoTrack: (com.marcioamaro.mediapod.data.model.LocalVideoTrack) -> Unit = {},
    onToggleVideoFullscreen: () -> Unit = {},
    isEqualizerEnabled: Boolean = true,
    onToggleEqualizerEnabled: (Boolean) -> Unit = {},
    equalizerPreset: String = "Rock",
    onSelectEqualizerPreset: (String) -> Unit = {},
    equalizerBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    onEqualizerBandLevelChange: (Int, Float) -> Unit = { _, _ -> },
    availableAudioDevices: List<com.marcioamaro.mediapod.player.AudioRouteDevice> = emptyList(),
    selectedAudioDevice: com.marcioamaro.mediapod.player.AudioRouteDevice? = null,
    onSelectAudioDevice: (com.marcioamaro.mediapod.player.AudioRouteDevice) -> Unit = {},
    onOpenNativeAudioChooser: () -> Unit = {},
    viewModel: com.marcioamaro.mediapod.ui.RadioViewModel? = null,
    onShowChassisBack: () -> Unit = {},
    sharedYouTubeWebView: WebView? = null,
    modifier: Modifier = Modifier
) {
    val chassisTheme = uiState.chassisTheme
    val backlight = uiState.backlight

    val resolvedPalette = uiState.appearanceSettings.activePalette
    val bodyColor = Color(resolvedPalette.bodyColor)
    val wheelColor = Color(resolvedPalette.wheelColor)
    val wheelTextColor = Color(resolvedPalette.wheelTextColor)
    val centerButtonColor = Color(resolvedPalette.centerButtonColor)

    val fontFamily = uiState.fontType.toFontFamily()
    val fontScale = uiState.fontSizeScale.scale
    val isBold = uiState.isFontBold

    val backlightBg = Color(backlight.background)
    val backlightTextPrimary = Color(backlight.textPrimary)
    val backlightTextSecondary = Color(backlight.textSecondary)
    val backlightHighlight = Color(backlight.highlight)

    val context = LocalContext.current
    val effectiveSoundAndHaptics = soundAndHaptics ?: remember { IpodSoundAndHaptics.getInstance(context) }

    var sleepBannerMessage by remember { mutableStateOf<String?>(null) }
    var sleepBannerTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(sleepBannerTrigger) {
        if (sleepBannerTrigger > 0L) {
            delay(2500L)
            sleepBannerTrigger = 0L
            sleepBannerMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B12))
            .padding(vertical = 4.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // iPod Chassis Container (Max width for tablet & foldables)
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxHeight()
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bodyColor,
                            bodyColor.copy(alpha = 0.95f),
                            bodyColor.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(2.5.dp, Color(0xFFCBD5E1).copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Chassis Controls (Hold Switch & Car Mode Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Authentic iPod HOLD Physical Toggle Switch (Red Track, White Thumb)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleHold)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HOLD",
                        color = if (uiState.isHoldLocked) Color(0xFFEF4444) else Color(0xFF94A3B8),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))

                    // Hardware Switch Track: Red when HOLD ON, Dark Gray when OFF
                    val trackColor = if (uiState.isHoldLocked) Color(0xFFDC2626) else Color(0xFF475569)
                    val borderColor = if (uiState.isHoldLocked) Color(0xFFB91C1C) else Color(0xFF334155)

                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(trackColor)
                            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
                            .padding(2.dp),
                        contentAlignment = if (uiState.isHoldLocked) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        // Pure White Selector Thumb
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(0.5.dp, Color(0xFFCBD5E1), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isHoldLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Hold On",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Modo Dormir (Sleep Timer) Pill Button
                    val isSleepActive = sleepTimerMinutes != 0 || sleepTimerSecondsRemaining > 0L
                    val sleepSec = sleepTimerSecondsRemaining
                    val sleepFormatted = if (sleepTimerMinutes == -1) {
                        "Fim Ep."
                    } else if (sleepSec > 0L) {
                        val m = sleepSec / 60
                        val s = sleepSec % 60
                        String.format(java.util.Locale.US, "%02d:%02d", m, s)
                    } else if (sleepTimerMinutes > 0) {
                        "${sleepTimerMinutes}:00"
                    } else null

                    // Flat harmonized Click Wheel colors for Sleep Mode (sem vermelho)
                    val sleepPillColor = if (isSleepActive) wheelTextColor else wheelTextColor.copy(alpha = 0.65f)
                    val sleepBorderColor = if (isSleepActive) wheelTextColor else wheelColor
                    val sleepBgColor = if (isSleepActive) wheelTextColor.copy(alpha = 0.15f) else wheelColor.copy(alpha = 0.22f)

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(sleepBgColor)
                            .border(1.2.dp, sleepBorderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                val nextTimer = when (sleepTimerMinutes) {
                                    0 -> 15
                                    15 -> 30
                                    30 -> 45
                                    45 -> 60
                                    60 -> 90
                                    90 -> 120
                                    120 -> -1
                                    else -> 0
                                }
                                onSetSleepTimer(nextTimer)
                                sleepBannerMessage = when (nextTimer) {
                                    -1 -> "MODO DORMIR: AO FIM DO EPISÓDIO"
                                    in 1..Int.MAX_VALUE -> "MODO DORMIR: $nextTimer MIN"
                                    else -> "MODO DORMIR: DESLIGADO"
                                }
                                sleepBannerTrigger = System.currentTimeMillis()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Modo Dormir",
                            tint = sleepPillColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (sleepFormatted != null) "SONO $sleepFormatted" else "DORMIR",
                            color = sleepPillColor,
                            fontSize = (9f * fontScale).sp,
                            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    // Car / Fullscreen Mode Toggle Pill (harmonized with Click Wheel colors)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(wheelColor.copy(alpha = 0.22f))
                            .border(1.2.dp, wheelColor, RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleDisplayMode)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("toggle_car_mode_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Modo Carro",
                            tint = wheelTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "CARRO",
                            color = wheelTextColor,
                            fontSize = (9f * fontScale).sp,
                            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // iPod LCD Screen Frame (Bezel + Glass Display)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF040A10))
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(3.dp)
            ) {
                // LCD Inner Canvas with Screen Header
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(backlightBg)
                ) {
                    // Header Bar com velocidade dinâmica
                    val headerSpeed = if (uiState.currentScreen in listOf(
                            IpodScreenDestination.PODCAST_NOW_PLAYING,
                            IpodScreenDestination.MP3_NOW_PLAYING,
                            IpodScreenDestination.VIDEO_PLAYER
                        )) {
                        if (uiState.currentScreen == IpodScreenDestination.VIDEO_PLAYER) {
                            videoPlayerManager?.playbackSpeed?.collectAsState(initial = 1.0f)?.value ?: 1.0f
                        } else {
                            viewModel?.playbackSpeed?.collectAsState(initial = 1.0f)?.value ?: 1.0f
                        }
                    } else 1.0f

                    val isVideoPlaying = videoPlayerManager?.isPlaying?.collectAsState()?.value ?: false
                    val currentVideoTrack = videoPlayerManager?.currentVideo?.collectAsState()?.value
                    val currentEp = viewModel?.currentPodcastEpisode?.collectAsState(initial = null)?.value

                    val computedTicker = when {
                        isVideoPlaying && currentVideoTrack != null -> {
                            "Reproduzindo Vídeo: ${currentVideoTrack.title}"
                        }
                        playbackStatus == RadioPlaybackStatus.PLAYING -> {
                            when {
                                currentLocalAudio != null -> "Reproduzindo MP3: ${currentLocalAudio.title}"
                                currentEp != null -> "Reproduzindo Podcast: ${currentEp.title}"
                                currentStation != null -> {
                                    val citySuffix = if (!currentStation.city.isNullOrBlank()) " (${currentStation.city})" else ""
                                    "Reproduzindo Rádio: ${currentStation.name}$citySuffix"
                                }
                                uiState.currentScreen == IpodScreenDestination.YOUTUBE_PLAYER && uiState.currentYouTubeVideo != null -> {
                                    "Reproduzindo YouTube: ${uiState.currentYouTubeVideo.title}"
                                }
                                else -> null
                            }
                        }
                        else -> null
                    }

                    IpodHeader(
                        title = if (uiState.currentScreen == IpodScreenDestination.PERSONAL_LIBRARY) stringResource(R.string.library_title) else getScreenTitle(uiState),
                        status = playbackStatus,
                        isHoldLocked = uiState.isHoldLocked,
                        sleepTimerMinutes = sleepTimerMinutes,
                        backlightTextPrimary = backlightTextPrimary,
                        backlightHighlight = backlightHighlight,
                        backlightBg = backlightBg,
                        fontFamily = fontFamily,
                        fontScale = fontScale,
                        isBold = isBold,
                        playbackSpeed = headerSpeed,
                        nowPlayingTicker = computedTicker,
                        is24HourClock = uiState.is24HourClock,
                        showAudioOutputIcon = uiState.currentScreen in listOf(
                            IpodScreenDestination.NOW_PLAYING_RDS,
                            IpodScreenDestination.MP3_NOW_PLAYING,
                            IpodScreenDestination.PODCAST_NOW_PLAYING,
                            IpodScreenDestination.VIDEO_PLAYER
                        ),
                        onAudioOutputClick = {
                            onSelectDestination(IpodScreenDestination.AUDIO_OUTPUT_MENU)
                        },
                        onNowPlayingClick = if (computedTicker != null) {
                            {
                                // Navega para o player "Tocando Agora" correto conforme o tipo de mídia ativa
                                val destination = when {
                                    isVideoPlaying && currentVideoTrack != null -> IpodScreenDestination.VIDEO_PLAYER
                                    currentLocalAudio != null -> IpodScreenDestination.MP3_NOW_PLAYING
                                    currentEp != null -> IpodScreenDestination.PODCAST_NOW_PLAYING
                                    currentStation != null -> IpodScreenDestination.NOW_PLAYING_RDS
                                    uiState.currentScreen == IpodScreenDestination.YOUTUBE_PLAYER -> IpodScreenDestination.YOUTUBE_PLAYER
                                    else -> IpodScreenDestination.NOW_PLAYING_RDS
                                }
                                onSelectDestination(destination)
                            }
                        } else null
                    )

                    // Active Screen Content
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (uiState.currentScreen) {
                            IpodScreenDestination.PERSONAL_LIBRARY -> {
                                com.marcioamaro.mediapod.ui.components.LcdFeatureTheme(backlightBg, backlightTextPrimary) {
                                    if (viewModel != null) PersonalLibraryScreen(viewModel)
                                }
                            }
                            IpodScreenDestination.MAIN_MENU -> {
                                IpodRootHomeScreen(
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectItem = { destIdx ->
                                        viewModel?.selectMenuItemDirect(destIdx)
                                    },
                                    isLocalAudio = currentLocalAudio != null,
                                    currentArtUrl = currentLocalAudio?.albumArtUrl,
                                    nowPlayingTitle = currentLocalAudio?.title ?: currentStation?.name,
                                    nowPlayingSubtitle = currentLocalAudio?.artist
                                        ?: if (rdsInfo.hasRealRds && rdsInfo.radioText.isNotBlank() && !rdsInfo.radioText.equals("[sem informações]", ignoreCase = true)) {
                                            rdsInfo.radioText
                                        } else {
                                            "[sem informações]"
                                        },
                                    isPlaying = playbackStatus == RadioPlaybackStatus.PLAYING,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.RADIO_MENU -> {
                                IpodClassicRadioMenuScreen(
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectIndex = { idx ->
                                        when (idx) {
                                            0 -> onSelectDestination(IpodScreenDestination.NOW_PLAYING_RDS)
                                            1 -> onSelectDestination(IpodScreenDestination.FAVORITES)
                                            2 -> onSelectDestination(IpodScreenDestination.RECENTS)
                                            3 -> {
                                                viewModel?.loadTopBrazilStations()
                                                onSelectDestination(IpodScreenDestination.TOP_BRAZIL)
                                            }
                                            4 -> {
                                                viewModel?.loadTopStations()
                                                onSelectDestination(IpodScreenDestination.TOP_WORLD)
                                            }
                                            5 -> onSelectDestination(IpodScreenDestination.GENRES_LIST)
                                            6 -> {
                                                viewModel?.resetSearchFiltersToDefault()
                                                viewModel?.executeSearch()
                                                onSelectDestination(IpodScreenDestination.SEARCH)
                                            }
                                            7 -> {
                                                viewModel?.loadCustomStations()
                                                onSelectDestination(IpodScreenDestination.RADIO_CUSTOM_LIST)
                                            }
                                        }
                                    },
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.MP3_FOLDERS -> {
                                IpodMp3FoldersScreen(
                                    library = viewModel?.mediaLibrary,
                                    folders = uiState.localAudioFolders,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectFolder = onSelectAudioFolder,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.MP3_TRACKS_LIST -> {
                                IpodMp3TracksListScreen(
                                    library = viewModel?.mediaLibrary,
                                    collectionPath = uiState.currentAudioFolder?.path,
                                    title = uiState.currentAudioFolder?.name ?: "Todas as Músicas",
                                    tracks = uiState.localAudioTracks,
                                    currentTrackId = currentLocalAudio?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectTrack = onSelectAudioTrack,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.MP3_NOW_PLAYING -> {
                                val speed = viewModel?.playbackSpeed?.collectAsState(initial = 1.0f)?.value ?: 1.0f
                                IpodMp3NowPlayingScreen(
                                    track = currentLocalAudio,
                                    isPlaying = playbackStatus == RadioPlaybackStatus.PLAYING,
                                    positionMs = audioPositionMs,
                                    durationMs = audioDurationMs,
                                    visualizerAmplitudes = visualizerAmplitudes,
                                    volume = volume,
                                    onStepVolumeUp = onStepVolumeUp,
                                    onStepVolumeDown = onStepVolumeDown,
                                    onSeekTo = { posMs -> viewModel?.seekToPosition(posMs) },
                                    playbackSpeed = speed,
                                    onCycleSpeed = { viewModel?.cyclePlaybackSpeed(false) },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.VIDEO_FOLDERS -> {
                                IpodVideoFoldersScreen(
                                    library = viewModel?.mediaLibrary,
                                    folders = uiState.localVideoFolders,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectFolder = onSelectVideoFolder,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.VIDEO_LIST -> {
                                IpodVideoListScreen(
                                    library = viewModel?.mediaLibrary,
                                    collectionPath = uiState.currentVideoFolder?.path,
                                    title = uiState.currentVideoFolder?.name ?: "Vídeos",
                                    videos = uiState.localVideoTracks,
                                    currentVideoId = videoPlayerManager?.currentVideo?.collectAsState()?.value?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectVideo = onSelectVideoTrack,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.VIDEO_PLAYER -> {
                                if (videoPlayerManager != null) {
                                    IpodVideoPlayerScreen(
                                        videoPlayerManager = videoPlayerManager,
                                        isFullscreen = uiState.isVideoFullscreen,
                                        onToggleFullscreen = onToggleVideoFullscreen,
                                        onNextVideo = onNextClick,
                                        onPrevVideo = onPrevClick,
                                        backlightBg = backlightBg,
                                        backlightTextPrimary = backlightTextPrimary,
                                        backlightTextSecondary = backlightTextSecondary,
                                        backlightHighlight = backlightHighlight,
                                        fontFamily = fontFamily,
                                        fontScale = fontScale,
                                        isBold = isBold
                                    )
                                }
                            }
                            IpodScreenDestination.GAME_BRICK -> {
                                IpodBrickGameScreen(
                                    paddlePositionRatio = uiState.gamePaddlePosition,
                                    gameWheelTicks = uiState.gameWheelTicks,
                                    onPaddleMove = onPaddleMove,
                                    centerActionTrigger = brickGameCenterAction,
                                    soundAndHaptics = effectiveSoundAndHaptics,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight
                                )
                            }
                            IpodScreenDestination.NOW_PLAYING_RDS -> {
                                val liveSeconds = viewModel?.liveSessionDurationSeconds?.collectAsState(initial = 0L)?.value ?: 0L
                                RdsDisplay(
                                    station = currentStation,
                                    rdsInfo = rdsInfo,
                                    status = playbackStatus,
                                    visualizerAmplitudes = visualizerAmplitudes,
                                    volume = volume,
                                    onStepVolumeUp = onStepVolumeUp,
                                    onStepVolumeDown = onStepVolumeDown,
                                    isFavorite = currentStation?.let { st -> favorites.any { it.id == st.id } } ?: false,
                                    onToggleFavorite = { currentStation?.let(onToggleFavorite) },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold,
                                    liveSessionDurationSeconds = liveSeconds,
                                    onRetry = onPlayPauseClick
                                )
                            }
                            IpodScreenDestination.FAVORITES -> {
                                FavoritesScreen(
                                    favorites = favorites,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectStation = onSelectStation,
                                    onDeleteFavorite = onDeleteFavorite,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.RECENTS -> {
                                StationsListScreen(
                                    title = "Recentes",
                                    stations = uiState.recentsList,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    isLoading = false,
                                    showSearchBar = true,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSelectStation = onSelectStation,
                                    onToggleFavorite = onToggleFavorite,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold,
                                    onClearAll = { viewModel?.clearRecentStations() }
                                )
                            }
                            IpodScreenDestination.TOP_BRAZIL -> {
                                StationsListScreen(
                                    title = "Top 20 Brasil",
                                    rankingInfo = com.marcioamaro.mediapod.data.repository.PublishedRankings.info("radio_brazil"),
                                    stations = uiState.stationsList,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    isLoading = uiState.isLoadingList,
                                    showSearchBar = true,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSelectStation = onSelectStation,
                                    onToggleFavorite = onToggleFavorite,
                                    favorites = favorites,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.TOP_WORLD -> {
                                StationsListScreen(
                                    title = "Top 20 Mundial",
                                    rankingInfo = com.marcioamaro.mediapod.data.repository.PublishedRankings.info("radio_world"),
                                    stations = uiState.stationsList,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    isLoading = uiState.isLoadingList,
                                    showSearchBar = true,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSelectStation = onSelectStation,
                                    onToggleFavorite = onToggleFavorite,
                                    favorites = favorites,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.GENRES_LIST -> {
                                IpodGenresSplitView(
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectGenre = onSelectGenre,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.STATIONS_BY_GENRE -> {
                                StationsListScreen(
                                    title = uiState.activeGenre?.name ?: "Gênero",
                                    stations = uiState.stationsList,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    isLoading = uiState.isLoadingList,
                                    showSearchBar = true,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSelectStation = onSelectStation,
                                    onToggleFavorite = onToggleFavorite,
                                    favorites = favorites,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.COUNTRIES_LIST -> {
                                IpodCountriesSplitView(
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectCountry = onSelectCountry,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.STATIONS_BY_COUNTRY -> {
                                StationsListScreen(
                                    title = uiState.activeCountry?.name ?: "País",
                                    stations = uiState.stationsList,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    isLoading = uiState.isLoadingList,
                                    showSearchBar = true,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSelectStation = onSelectStation,
                                    onToggleFavorite = onToggleFavorite,
                                    favorites = favorites,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.SEARCH -> {
                                IpodSearchScreen(
                                    stations = uiState.stationsList,
                                    currentStationId = currentStation?.id,
                                    selectedIndex = uiState.selectedIndex,
                                    isLoading = uiState.isLoadingList,
                                    searchQuery = uiState.searchQuery,
                                    selectedCountryCode = uiState.searchCountryCode,
                                    selectedGenreTag = uiState.searchGenreTag,
                                    selectedStateCode = uiState.searchStateCode,
                                    selectedCity = uiState.searchCity,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onSearchCountryChange = onSearchCountryChange,
                                    onSearchGenreChange = onSearchGenreChange,
                                    onSearchStateChange = onSearchStateChange,
                                    onSearchCityChange = onSearchCityChange,
                                    onSelectStation = onSelectStation,
                                    onToggleFavorite = onToggleFavorite,
                                    favorites = favorites,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    availableCities = uiState.availableCities,
                                    isLoadingCities = uiState.isLoadingCities,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.RADIO_CUSTOM_LIST -> {
                                val customItems = uiState.customStations.map { it.name to it.streamUrl }
                                IpodCustomItemsListScreen(
                                    title = "Minhas Rádios",
                                    items = customItems,
                                    selectedIndex = uiState.selectedIndex,
                                    onAddNew = { onSelectDestination(IpodScreenDestination.ADD_CUSTOM_RADIO) },
                                    onSelectItem = { idx ->
                                        val station = uiState.customStations.getOrNull(idx)
                                        if (station != null) {
                                            onSelectStation(station)
                                        }
                                    },
                                    onDeleteItem = { idx ->
                                        val station = uiState.customStations.getOrNull(idx)
                                        if (station != null) {
                                            viewModel?.removeCustomStation(station.id)
                                        }
                                    },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.ADD_CUSTOM_RADIO -> {
                                IpodAddCustomUrlScreen(
                                    target = CustomUrlTarget.RADIO,
                                    onSaveSuccess = { name, url ->
                                        viewModel?.addCustomStation(name, url)
                                        onSelectDestination(IpodScreenDestination.RADIO_CUSTOM_LIST)
                                    },
                                    onCancel = { onSelectDestination(IpodScreenDestination.RADIO_CUSTOM_LIST) },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.PODCASTS_MENU -> {
                                IpodPodcastMenuScreen(
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectIndex = { idx ->
                                        when (idx) {
                                            0 -> onSelectDestination(IpodScreenDestination.PODCAST_NOW_PLAYING)
                                            1 -> onSelectDestination(IpodScreenDestination.PODCASTS_FAVORITES)
                                            2 -> onSelectDestination(IpodScreenDestination.PODCASTS_RECENTS)
                                            3 -> {
                                                viewModel?.loadPodcastTopBrazil()
                                                onSelectDestination(IpodScreenDestination.PODCASTS_TOP_BRAZIL)
                                            }
                                            4 -> {
                                                viewModel?.loadPodcastTopWorld()
                                                onSelectDestination(IpodScreenDestination.PODCASTS_TOP_WORLD)
                                            }
                                            5 -> {
                                                viewModel?.loadPodcastCountries()
                                                onSelectDestination(IpodScreenDestination.PODCASTS_COUNTRIES)
                                            }
                                            6 -> onSelectDestination(IpodScreenDestination.PODCASTS_SEARCH)
                                            7 -> {
                                                viewModel?.loadCustomPodcasts()
                                                onSelectDestination(IpodScreenDestination.PODCASTS_CUSTOM_LIST)
                                            }
                                        }
                                    },
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.PODCASTS_TOP_BRAZIL,
                            IpodScreenDestination.PODCASTS_TOP_WORLD,
                            IpodScreenDestination.PODCASTS_BY_CATEGORY,
                            IpodScreenDestination.PODCASTS_BY_COUNTRY -> {
                                val favList = viewModel?.podcastFavorites?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                IpodPodcastShowsScreen(
                                    title = uiState.activeCategoryName,
                                    rankingInfo = when (uiState.currentScreen) {
                                        IpodScreenDestination.PODCASTS_TOP_BRAZIL -> com.marcioamaro.mediapod.data.repository.PublishedRankings.info("podcast_brazil")
                                        IpodScreenDestination.PODCASTS_TOP_WORLD -> com.marcioamaro.mediapod.data.repository.PublishedRankings.info("podcast_world")
                                        else -> null
                                    },
                                    shows = uiState.podcastShows,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectShow = { show ->
                                        if (viewModel?.selectPodcastShow(show) == true) onSelectDestination(IpodScreenDestination.PODCAST_EPISODES_LIST)
                                    },
                                    isFavorite = { id -> favList.any { it.id == id } },
                                    onToggleFavorite = { show -> viewModel?.togglePodcastFavorite(show) },
                                    isLoading = uiState.isPodcastLoading,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.PODCASTS_SEARCH -> {
                                val favList = viewModel?.podcastFavorites?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                IpodPodcastSearchScreen(
                                    searchQuery = uiState.podcastSearchQuery,
                                    onSearchQueryChange = { q -> viewModel?.searchPodcasts(q) },
                                    shows = uiState.podcastSearchResults,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectShow = { show ->
                                        if (viewModel?.selectPodcastShow(show) == true) onSelectDestination(IpodScreenDestination.PODCAST_EPISODES_LIST)
                                    },
                                    isFavorite = { id -> favList.any { it.id == id } },
                                    onToggleFavorite = { show -> viewModel?.togglePodcastFavorite(show) },
                                    isLoading = uiState.isPodcastSearchLoading,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.PODCASTS_FAVORITES -> {
                                val favList = viewModel?.podcastFavorites?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                IpodPodcastShowsScreen(
                                    title = "Podcasts Favoritos",
                                    shows = favList,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectShow = { show ->
                                        if (viewModel?.selectPodcastShow(show) == true) onSelectDestination(IpodScreenDestination.PODCAST_EPISODES_LIST)
                                    },
                                    isFavorite = { true },
                                    onToggleFavorite = { show -> viewModel?.togglePodcastFavorite(show) },
                                    isLoading = false,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.PODCASTS_RECENTS -> {
                                val recList = viewModel?.podcastRecents?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                val favList = viewModel?.podcastFavorites?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                IpodPodcastShowsScreen(
                                    title = "Recentes",
                                    shows = recList,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectShow = { show ->
                                        if (viewModel?.selectPodcastShow(show) == true) onSelectDestination(IpodScreenDestination.PODCAST_EPISODES_LIST)
                                    },
                                    isFavorite = { id -> favList.any { it.id == id } },
                                    onToggleFavorite = { show -> viewModel?.togglePodcastFavorite(show) },
                                    isLoading = false,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold,
                                    onClearAll = { viewModel?.clearRecentPodcasts() }
                                )
                            }
                            IpodScreenDestination.PODCASTS_CATEGORIES -> {
                                val list = uiState.podcastCategories
                                com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
                                    items = list,
                                    selectedIndex = uiState.selectedIndex,
                                    key = { _, cat -> cat.name },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(backlightBg)
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) { index, cat, isSelected ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) backlightHighlight else Color.Transparent)
                                            .clickable {
                                                viewModel?.loadPodcastsByCategory(cat.name)
                                                onSelectDestination(IpodScreenDestination.PODCASTS_BY_CATEGORY)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = cat.name,
                                                color = if (isSelected) Color.White else backlightTextPrimary,
                                                fontSize = (11.5f * fontScale).sp,
                                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = fontFamily
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
                            IpodScreenDestination.PODCASTS_COUNTRIES -> {
                                val list = uiState.podcastCountries
                                com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
                                    items = list,
                                    selectedIndex = uiState.selectedIndex,
                                    key = { _, country -> country.code },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(backlightBg)
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) { index, country, isSelected ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) backlightHighlight else Color.Transparent)
                                            .clickable {
                                                viewModel?.loadPodcastsByCountry(country.code, country.name)
                                                onSelectDestination(IpodScreenDestination.PODCASTS_BY_COUNTRY)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = country.flagEmoji,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                                Text(
                                                    text = country.name,
                                                    color = if (isSelected) Color.White else backlightTextPrimary,
                                                    fontSize = (11.5f * fontScale).sp,
                                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                                                    fontFamily = fontFamily
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
                            IpodScreenDestination.PODCAST_EPISODES_LIST -> {
                                uiState.currentPodcastShow?.let { show ->
                                    IpodPodcastEpisodesScreen(
                                        onTogglePlayed = { viewModel?.togglePodcastPlayed(it) },
                                        show = show,
                                        episodes = uiState.podcastEpisodes,
                                        selectedIndex = uiState.selectedIndex,
                                        onSelectEpisode = { ep ->
                                            viewModel?.playPodcastEpisode(ep)
                                            onSelectDestination(IpodScreenDestination.PODCAST_NOW_PLAYING)
                                        },
                                        isLoading = uiState.isPodcastLoading,
                                        backlightBg = backlightBg,
                                        backlightTextPrimary = backlightTextPrimary,
                                        backlightTextSecondary = backlightTextSecondary,
                                        backlightHighlight = backlightHighlight,
                                        fontFamily = fontFamily,
                                        fontScale = fontScale,
                                        isBold = isBold
                                    )
                                }
                            }
                            IpodScreenDestination.PODCAST_NOW_PLAYING -> {
                                val currentEp = viewModel?.currentPodcastEpisode?.collectAsState(initial = null)?.value
                                val speed = viewModel?.playbackSpeed?.collectAsState(initial = 1.0f)?.value ?: 1.0f
                                val currentChapter = viewModel?.currentChapter?.collectAsState(initial = null)?.value
                                val chapters = viewModel?.currentPodcastChapters?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                IpodPodcastNowPlayingScreen(
                                    episode = currentEp,
                                    isPlaying = playbackStatus == RadioPlaybackStatus.PLAYING,
                                    positionMs = audioPositionMs,
                                    durationMs = audioDurationMs,
                                    onTogglePlayPause = onPlayPauseClick,
                                    onSeekRelative = { delta -> viewModel?.seekRelative(delta) },
                                    onSeekTo = { posMs -> viewModel?.seekToPosition(posMs) },
                                    currentChapter = currentChapter,
                                    chapters = chapters,
                                    playbackSpeed = speed,
                                    onCycleSpeed = { viewModel?.cyclePlaybackSpeed(true) },
                                    onOpenChaptersList = { onSelectDestination(IpodScreenDestination.PODCAST_CHAPTERS) },
                                    volume = volume,
                                    onStepVolumeUp = onStepVolumeUp,
                                    onStepVolumeDown = onStepVolumeDown,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold,
                                    status = playbackStatus
                                )
                            }
                            IpodScreenDestination.PODCAST_CHAPTERS -> {
                                val chapters = viewModel?.currentPodcastChapters?.collectAsState(initial = emptyList())?.value ?: emptyList()
                                val currentChapter = viewModel?.currentChapter?.collectAsState(initial = null)?.value
                                IpodChaptersListScreen(
                                    chapters = chapters,
                                    currentChapter = currentChapter,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectChapter = { ch ->
                                        viewModel?.selectPodcastChapter(ch)
                                    },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.PODCASTS_CUSTOM_LIST -> {
                                val customItems = uiState.customPodcasts.map { it.title to it.feedUrl }
                                IpodCustomItemsListScreen(
                                    title = "Meus Podcasts",
                                    items = customItems,
                                    selectedIndex = uiState.selectedIndex,
                                    onAddNew = { onSelectDestination(IpodScreenDestination.ADD_CUSTOM_PODCAST) },
                                    onSelectItem = { idx ->
                                        val show = uiState.customPodcasts.getOrNull(idx)
                                        if (show != null) {
                                            viewModel?.selectPodcastShow(show)
                                            onSelectDestination(IpodScreenDestination.PODCAST_EPISODES_LIST)
                                        }
                                    },
                                    onDeleteItem = { idx ->
                                        val show = uiState.customPodcasts.getOrNull(idx)
                                        if (show != null) {
                                            viewModel?.removeCustomPodcast(show.id)
                                        }
                                    },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.ADD_CUSTOM_PODCAST -> {
                                IpodAddCustomUrlScreen(
                                    target = CustomUrlTarget.PODCAST,
                                    onSaveSuccess = { name, url ->
                                        viewModel?.addCustomPodcast(name, url)
                                        onSelectDestination(IpodScreenDestination.PODCASTS_CUSTOM_LIST)
                                    },
                                    onCancel = { onSelectDestination(IpodScreenDestination.PODCASTS_CUSTOM_LIST) },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.YOUTUBE_VIDEOS_LIST -> {
                                IpodYouTubeListScreen(
                                    videos = uiState.customYouTubeVideos,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectAddVideo = { onSelectDestination(IpodScreenDestination.ADD_CUSTOM_YOUTUBE) },
                                    onSelectVideo = { video ->
                                        viewModel?.playYouTubeVideo(video)
                                    },
                                    onDeleteVideo = { id ->
                                        viewModel?.removeYouTubeVideo(id)
                                    },
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.ADD_CUSTOM_YOUTUBE -> {
                                IpodAddYouTubeUrlScreen(
                                    onSaveSuccess = { title, url ->
                                        viewModel?.addYouTubeVideo(title, url)
                                        onSelectDestination(IpodScreenDestination.YOUTUBE_VIDEOS_LIST)
                                    },
                                    onCancel = { onSelectDestination(IpodScreenDestination.YOUTUBE_VIDEOS_LIST) },
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.YOUTUBE_PLAYER -> {
                                uiState.currentYouTubeVideo?.let { video ->
                                    IpodYouTubePlayerScreen(
                                        video = video,
                                        onBack = { onSelectDestination(IpodScreenDestination.YOUTUBE_VIDEOS_LIST) },
                                        onToggleFullscreen = {
                                            val activity = context as? android.app.Activity
                                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        },
                                        initialStartSeconds = viewModel?.youTubePlaybackPositionSeconds ?: 0,
                                        onTimeUpdate = { viewModel?.updateYouTubePlaybackPosition(it) },
                                        backlightTextPrimary = backlightTextPrimary,
                                        backlightTextSecondary = backlightTextSecondary,
                                        backlightHighlight = backlightHighlight,
                                        fontFamily = fontFamily,
                                        fontScale = fontScale,
                                        isBold = isBold,
                                        sharedWebView = sharedYouTubeWebView
                                    )
                                }
                            }
                            IpodScreenDestination.SETTINGS_THEMES -> {
                                IpodSettingsScreen(
                                    uiState = uiState,
                                    sleepTimerMinutes = sleepTimerMinutes,
                                    onSetChassisTheme = onSetChassisTheme,
                                    onSetCustomBodyColor = onSetCustomBodyColor,
                                    onSetBacklight = onSetBacklight,
                                    onSetWheelPreset = onSetWheelPreset,
                                    onSetCustomWheelColors = onSetCustomWheelColors,
                                    onSetFontType = onSetFontType,
                                    onSetFontSizeScale = onSetFontSizeScale,
                                    onSetFontBold = onSetFontBold,
                                    onSetAutoPlay = onSetAutoPlay,
                                    onToggleSound = onToggleSound,
                                    onToggleHaptics = onToggleHaptics,
                                    onSetSleepTimer = onSetSleepTimer,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    backlightBg = backlightBg,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold,
                                    viewModel = viewModel
                                )
                            }
                            IpodScreenDestination.EQUALIZER -> {
                                IpodEqualizerScreen(
                                    isEnabled = isEqualizerEnabled,
                                    onToggleEnabled = onToggleEqualizerEnabled,
                                    currentPreset = equalizerPreset,
                                    onSelectPreset = onSelectEqualizerPreset,
                                    bandLevels = equalizerBands,
                                    onBandLevelChange = onEqualizerBandLevelChange,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.AUDIO_OUTPUT_MENU -> {
                                IpodAudioOutputScreen(
                                    devices = availableAudioDevices,
                                    selectedDevice = selectedAudioDevice,
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectDevice = onSelectAudioDevice,
                                    onOpenNativeChooser = onOpenNativeAudioChooser,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.ABOUT -> {
                                IpodAboutScreen(
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight,
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold,
                                    onShowChassisBack = onShowChassisBack
                                )
                            }
                        }
                    }
                }

                // Banner LCD retrô para feedback do Modo Dormir (Inversão monocromática com alto contraste - Flat LCD)
                androidx.compose.animation.AnimatedVisibility(
                    visible = sleepBannerTrigger > 0L && sleepBannerMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(backlightTextPrimary)
                            .border(1.dp, backlightBg, RoundedCornerShape(4.dp))
                            .clickable {
                                sleepBannerTrigger = 0L
                                sleepBannerMessage = null
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Dormir",
                            tint = backlightBg,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = sleepBannerMessage ?: "",
                            color = backlightBg,
                            fontSize = (10.5f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Click Wheel at Bottom with dynamic user-configured colors
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                ClickWheel(
                    onRotaryScroll = onRotaryScroll,
                    onCenterClick = onCenterClick,
                    onMenuClick = onMenuClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onPrevClick = onPrevClick,
                    onNextClick = onNextClick,
                    wheelColor = wheelColor,
                    textColor = wheelTextColor,
                    centerButtonColor = centerButtonColor
                )
            }
        }
    }
}

private fun getScreenTitle(uiState: UiState): String {
    return when (uiState.currentScreen) {
        IpodScreenDestination.MAIN_MENU -> "MediaPod + Radio / Podcast"
        IpodScreenDestination.PERSONAL_LIBRARY -> "Minha biblioteca"
        IpodScreenDestination.AUDIO_OUTPUT_MENU -> "Saída de Áudio"
        IpodScreenDestination.RADIO_MENU -> "Rádio"
        IpodScreenDestination.NOW_PLAYING_RDS -> "Agora Tocando"
        IpodScreenDestination.FAVORITES -> "Favoritos"
        IpodScreenDestination.RECENTS -> "Recentes"
        IpodScreenDestination.TOP_BRAZIL -> "Top 20 Brasil"
        IpodScreenDestination.TOP_WORLD -> "Top 20 Mundial"
        IpodScreenDestination.GENRES_LIST -> "Gêneros Musicais"
        IpodScreenDestination.STATIONS_BY_GENRE -> uiState.activeGenre?.name ?: "Gênero"
        IpodScreenDestination.COUNTRIES_LIST -> "Países"
        IpodScreenDestination.STATIONS_BY_COUNTRY -> uiState.activeCountry?.name ?: "País"
        IpodScreenDestination.SEARCH -> "Busca de Emissoras"
        IpodScreenDestination.RADIO_CUSTOM_LIST -> "Minhas Rádios"
        IpodScreenDestination.ADD_CUSTOM_RADIO -> "Adicionar Rádio"
        IpodScreenDestination.PODCASTS_MENU -> "Podcasts"
        IpodScreenDestination.PODCASTS_FAVORITES -> "Podcasts Favoritos"
        IpodScreenDestination.PODCASTS_RECENTS -> "Recentes (Podcasts)"
        IpodScreenDestination.PODCASTS_TOP_BRAZIL -> "Top Brasil (Podcasts)"
        IpodScreenDestination.PODCASTS_TOP_WORLD -> "Top Mundial (Podcasts)"
        IpodScreenDestination.PODCASTS_CATEGORIES -> "Categorias de Podcasts"
        IpodScreenDestination.PODCASTS_BY_CATEGORY -> uiState.activeCategoryName
        IpodScreenDestination.PODCASTS_COUNTRIES -> "Países (Podcasts)"
        IpodScreenDestination.PODCASTS_BY_COUNTRY -> uiState.activeCategoryName
        IpodScreenDestination.PODCASTS_SEARCH -> "Buscar Podcasts"
        IpodScreenDestination.PODCASTS_CUSTOM_LIST -> "Meus Podcasts"
        IpodScreenDestination.ADD_CUSTOM_PODCAST -> "Adicionar Podcast"
        IpodScreenDestination.PODCAST_EPISODES_LIST -> uiState.currentPodcastShow?.title ?: "Episódios"
        IpodScreenDestination.PODCAST_NOW_PLAYING -> "Agora Tocando (Podcast)"
        IpodScreenDestination.PODCAST_CHAPTERS -> "Capítulos do Podcast"
        IpodScreenDestination.MP3_FOLDERS -> "Músicas"
        IpodScreenDestination.MP3_TRACKS_LIST -> uiState.currentAudioFolder?.name ?: "Músicas"
        IpodScreenDestination.MP3_NOW_PLAYING -> "Agora Tocando (MP3)"
        IpodScreenDestination.VIDEO_FOLDERS -> "Vídeos"
        IpodScreenDestination.VIDEO_LIST -> uiState.currentVideoFolder?.name ?: "Vídeos"
        IpodScreenDestination.VIDEO_PLAYER -> "Vídeo Player"
        IpodScreenDestination.YOUTUBE_VIDEOS_LIST -> "Vídeos no YouTube"
        IpodScreenDestination.ADD_CUSTOM_YOUTUBE -> "Adicionar Vídeo YouTube"
        IpodScreenDestination.YOUTUBE_PLAYER -> uiState.currentYouTubeVideo?.title ?: "YouTube Player"
        IpodScreenDestination.EQUALIZER -> "Equalizador"
        IpodScreenDestination.GAME_BRICK -> "Brick Game"
        IpodScreenDestination.SETTINGS_THEMES -> "Configurações"
        IpodScreenDestination.ABOUT -> "Sobre o Aplicativo"
    }
}

@Composable
fun IpodClassicRadioMenuScreen(
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val topBrazilLabel = stringResource(com.marcioamaro.mediapod.R.string.radio_top_brazil)
    val topWorldLabel = stringResource(com.marcioamaro.mediapod.R.string.radio_top_world)
    val menuItems = remember(topBrazilLabel, topWorldLabel) {
        listOf(
            "Agora Tocando" to Icons.Default.PlayArrow,
            "Rádios Favoritas" to Icons.Default.Favorite,
            "Recentes" to Icons.Default.History,
            topBrazilLabel to Icons.Default.Public,
            topWorldLabel to Icons.Default.Public,
            "Gêneros Musicais" to Icons.Default.MusicNote,
            "Buscar Estação" to Icons.Default.Search,
            "Minhas Rádios" to Icons.Default.Radio
        )
    }

    com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                        fontSize = (12f * fontScale).sp,
                        lineHeight = (14.5f * fontScale).sp,
                        fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

@Composable
private fun IpodMainMenuSplitView(
    selectedIndex: Int,
    currentStation: RadioStation?,
    rdsInfo: RdsInfo,
    playbackStatus: RadioPlaybackStatus,
    onSelectIndex: (Int) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    val context = LocalContext.current
    val menuItems = listOf(
        "Agora Tocando" to Icons.Default.PlayArrow,
        "Rádios Favoritas" to Icons.Default.Favorite,
        "Recentes" to Icons.Default.History,
        "Top Mundial" to Icons.Default.Public,
        "Gêneros Musicais" to Icons.Default.MusicNote,
        "Buscar Estação" to Icons.Default.Search,
        "Brick Game" to Icons.Default.Gamepad,
        "Configurações" to Icons.Default.Settings
    )

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: Menu Items with Monochrome LCD Icons
        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .padding(vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            menuItems.forEachIndexed { index, (title, icon) ->
                val isSelected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) backlightHighlight else Color.Transparent)
                        .clickable { onSelectIndex(index) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
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
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            lineHeight = (13.5f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Split Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(backlightHighlight.copy(alpha = 0.3f))
        )

        // Right Column: Artwork & Live Preview with monochrome styling
        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, backlightHighlight, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (playbackStatus == RadioPlaybackStatus.NO_INTERNET) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi_off_alert),
                        contentDescription = "Sem Internet",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentStation?.name ?: "IPod Class + Radio",
                color = backlightTextPrimary,
                fontSize = (11f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val liveText = stringResource(R.string.status_live)
            val bufferingText = stringResource(R.string.status_buffering)
            val noInternetText = stringResource(R.string.msg_connection_error).uppercase()
            Text(
                text = when (playbackStatus) {
                    RadioPlaybackStatus.NO_INTERNET -> noInternetText
                    RadioPlaybackStatus.PLAYING -> "$liveText • ${currentStation?.displayFrequency ?: ""}"
                    RadioPlaybackStatus.BUFFERING -> bufferingText
                    else -> "$liveText • ${currentStation?.displayFrequency ?: ""}"
                },
                color = backlightTextSecondary,
                fontSize = (9f * fontScale).sp,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IpodGenresSplitView(
    selectedIndex: Int,
    onSelectGenre: (com.marcioamaro.mediapod.data.repository.GenreCategory) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    var searchQuery by remember { mutableStateOf("") }
    val genres = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CuratedData.GENRES
        } else {
            val normQ = com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(searchQuery)
            CuratedData.GENRES.filter {
                com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.name).contains(normQ) ||
                com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.tag).contains(normQ) ||
                com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.description).contains(normQ)
            }
        }
    }
    val activeGenre = if (selectedIndex in genres.indices) genres[selectedIndex] else genres.firstOrNull()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = (11f * fontScale).sp,
                    lineHeight = (16f * fontScale).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                placeholder = {
                    Text(
                        text = "Filtrar gêneros...",
                        fontSize = (10.5f * fontScale).sp,
                        color = backlightTextSecondary.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar",
                                tint = backlightTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = backlightHighlight,
                    unfocusedBorderColor = backlightHighlight.copy(alpha = 0.4f),
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedContainerColor = Color(0x33000000),
                    unfocusedContainerColor = Color(0x22000000)
                )
            )

            com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
                items = genres,
                selectedIndex = selectedIndex,
                key = { _, genre -> genre.name },
                modifier = Modifier.fillMaxSize()
            ) { index, genre, isSelected ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) backlightHighlight else Color.Transparent)
                            .clickable { onSelectGenre(genre) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = genre.name,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(backlightHighlight.copy(alpha = 0.3f))
        )

        // Right Preview Pane
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = backlightHighlight,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = activeGenre?.name ?: "",
                color = backlightTextPrimary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeGenre?.description ?: "",
                color = backlightTextSecondary,
                fontSize = (9.5f * fontScale).sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = (13f * fontScale).sp
            )
        }
    }
}

@Composable
private fun IpodCountriesSplitView(
    selectedIndex: Int,
    onSelectCountry: (com.marcioamaro.mediapod.data.repository.CountryCategory) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    var searchQuery by remember { mutableStateOf("") }
    val countries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CuratedData.COUNTRIES
        } else {
            val normQ = com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(searchQuery)
            CuratedData.COUNTRIES.filter {
                com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.name).contains(normQ) ||
                com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.code).contains(normQ) ||
                com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.region).contains(normQ)
            }
        }
    }
    val activeCountry = if (selectedIndex in countries.indices) countries[selectedIndex] else countries.firstOrNull()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = (11f * fontScale).sp,
                    lineHeight = (16f * fontScale).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                placeholder = {
                    Text(
                        text = "Filtrar países...",
                        fontSize = (10.5f * fontScale).sp,
                        color = backlightTextSecondary.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar",
                                tint = backlightTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = backlightHighlight,
                    unfocusedBorderColor = backlightHighlight.copy(alpha = 0.4f),
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedContainerColor = Color(0x33000000),
                    unfocusedContainerColor = Color(0x22000000)
                )
            )

            com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
                items = countries,
                selectedIndex = selectedIndex,
                key = { _, country -> country.code },
                modifier = Modifier.fillMaxSize()
            ) { index, country, isSelected ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) backlightHighlight else Color.Transparent)
                            .clickable { onSelectCountry(country) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp, 14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(backlightTextPrimary.copy(alpha = 0.1f))
                                    .border(0.8.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (country.code.equals("ALL", ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else backlightTextPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                } else {
                                    Text(
                                        text = country.code,
                                        color = if (isSelected) Color.White else backlightTextPrimary,
                                        fontSize = (8.5f * fontScale).sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = country.name,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(backlightHighlight.copy(alpha = 0.3f))
        )

        // Right Country Info Pane with Grayscale Flag
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp, 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(backlightTextPrimary.copy(alpha = 0.12f))
                    .border(1.2.dp, backlightTextPrimary.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (activeCountry?.code.equals("ALL", ignoreCase = true) || activeCountry?.code.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = backlightHighlight,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp, 44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(backlightTextPrimary.copy(alpha = 0.12f))
                            .border(1.2.dp, backlightHighlight, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[${activeCountry?.code}]",
                            color = backlightHighlight,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activeCountry?.name ?: "",
                color = backlightTextPrimary,
                fontSize = (13f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = activeCountry?.region ?: "",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun IpodSettingsScreen(
    uiState: UiState,
    sleepTimerMinutes: Int,
    onSetChassisTheme: (IpodChassisTheme) -> Unit,
    onSetCustomBodyColor: (Long) -> Unit = {},
    onSetBacklight: (LcdBacklight) -> Unit,
    onSetWheelPreset: (IpodWheelPreset) -> Unit,
    onSetCustomWheelColors: (Long, Long, Long) -> Unit,
    onSetFontType: (IpodFontType) -> Unit,
    onSetFontSizeScale: (IpodFontSizeScale) -> Unit,
    onSetFontBold: (Boolean) -> Unit,
    onSetAutoPlay: (Boolean) -> Unit,
    onToggleSound: () -> Unit,
    onToggleHaptics: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    backlightBg: Color = Color.Black,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    viewModel: RadioViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var isBackupError by remember { mutableStateOf(false) }
    var activeColorPickerTarget by remember { mutableStateOf<ColorPickerTarget?>(null) }

    val isManualColorEditingEnabled = uiState.appearanceSettings.isManualColorEditingEnabled

    if (activeColorPickerTarget != null && isManualColorEditingEnabled) {
        val target = activeColorPickerTarget!!
        val (pickerTitle, initialColor) = when (target) {
            ColorPickerTarget.CHASSIS -> "Cor da Carcaça (Cor Personalizada)" to uiState.customBodyColor
            ColorPickerTarget.CLICK_WHEEL -> "Cor da Click Wheel (Cor Personalizada)" to uiState.customWheelColor
            ColorPickerTarget.CENTER_BUTTON -> "Cor do Botão Central (Cor Personalizada)" to uiState.customCenterButtonColor
        }
        AmbilWarnaColorPickerDialog(
            title = pickerTitle,
            initialColor = initialColor,
            target = target,
            currentPalette = uiState.appearanceSettings.activePalette,
            onColorSelected = { selectedColor ->
                when (target) {
                    ColorPickerTarget.CHASSIS -> {
                        viewModel?.setCustomBodyColor(selectedColor) ?: onSetCustomBodyColor(selectedColor)
                    }
                    ColorPickerTarget.CLICK_WHEEL -> {
                        if (viewModel != null) {
                            viewModel.setCustomWheelColor(selectedColor)
                        } else {
                            val optText = IpodColorContrastUtil.getOptimalWheelTextColor(selectedColor)
                            onSetCustomWheelColors(selectedColor, optText, uiState.customCenterButtonColor)
                        }
                    }
                    ColorPickerTarget.CENTER_BUTTON -> {
                        if (viewModel != null) {
                            viewModel.setCustomCenterButtonColor(selectedColor)
                        } else {
                            onSetCustomWheelColors(uiState.customWheelColor, uiState.customWheelTextColor, selectedColor)
                        }
                    }
                }
            },
            onDismissRequest = { activeColorPickerTarget = null }
        )
    }

    val backupActions = com.marcioamaro.mediapod.ui.components.rememberSecureBackupActions { success, message ->
        backupStatusMessage = message
        isBackupError = !success
        if (success) viewModel?.reloadPreferencesFromStorage()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 0. ASSISTENTE DE CONFIGURAÇÃO (WIZARD) ---
        item {
            Text(
                text = stringResource(R.string.settings_group_wizard),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x28000000))
                    .clickable {
                        coroutineScope.launch {
                            com.marcioamaro.mediapod.data.prefs.OnboardingPreferencesRepository.getInstance(context).resetOnboarding()
                        }
                    }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = com.marcioamaro.mediapod.R.string.settings_rerun_wizard),
                        color = backlightTextPrimary,
                        fontSize = (12f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = com.marcioamaro.mediapod.R.string.settings_rerun_wizard_summary),
                        color = backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        lineHeight = (15f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_wizard_run_btn),
                    color = backlightHighlight,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 1. FONTE DO MODO IPOD (ESTILO, TAMANHO, NEGRITO) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_group_fonts),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        // Estilo da Fonte
        item {
            Text(
                text = stringResource(R.string.settings_font_style_label),
                color = backlightTextPrimary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IpodFontType.values().forEach { type ->
                    val isSelected = uiState.fontType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSetFontType(type) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.displayName,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = type.toFontFamily(),
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text(
                                text = stringResource(R.string.onboarding_active_tag),
                                color = Color.White,
                                fontSize = (11f * fontScale).sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }
        }

        // Tamanho da Fonte
        item {
            Text(
                text = stringResource(R.string.settings_font_size_label),
                color = backlightTextPrimary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IpodFontSizeScale.values().forEach { scale ->
                    val isSelected = uiState.fontSizeScale == scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSetFontSizeScale(scale) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scale.displayName,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (11f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }

        // Negrito vs Normal
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22000000))
                    .clickable { onSetFontBold(!uiState.isFontBold) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_font_bold_label),
                    color = backlightTextPrimary,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = if (uiState.isFontBold) FontWeight.Black else FontWeight.Normal,
                    fontFamily = fontFamily,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (uiState.isFontBold) stringResource(R.string.settings_font_bold_active) else stringResource(R.string.settings_font_bold_normal),
                    color = if (uiState.isFontBold) Color.White else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 1. MODO ALEATÓRIO DE CORES ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_group_random_colors),
                color = backlightTextSecondary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            val randomEnabled = uiState.appearanceSettings.randomHardwareColorsEnabled
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x28000000))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            viewModel?.setRandomHardwareColorsEnabled(!randomEnabled)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_group_random_colors),
                            color = backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_random_colors_desc),
                            color = backlightTextSecondary,
                            fontSize = (11f * fontScale).sp,
                            lineHeight = (15f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = randomEnabled,
                        onCheckedChange = { checked ->
                            viewModel?.setRandomHardwareColorsEnabled(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = backlightHighlight
                        )
                    )
                }

                // Banner de Status / Bloqueio
                if (randomEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33DC2626))
                            .border(1.dp, Color(0x66DC2626), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_manual_edit_locked),
                            color = Color.White,
                            fontSize = (11f * fontScale).sp,
                            lineHeight = (15f * fontScale).sp,
                            fontFamily = fontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(backlightHighlight)
                            .clickable { viewModel?.generateNewRandomHardwarePalette() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_random_reroll_btn),
                            color = Color.White,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2210B981))
                            .border(1.dp, Color(0x5510B981), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_manual_edit_unlocked),
                            color = backlightTextPrimary,
                            fontSize = (11f * fontScale).sp,
                            lineHeight = (15f * fontScale).sp,
                            fontFamily = fontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- 2. PERSONALIZE AO SEU GOSTO (CORES PERSONALIZADAS) ---
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_group_custom_colors),
                color = backlightTextSecondary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isManualColorEditingEnabled) 1f else 0.45f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x28000000))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // A. Cor da Carcaça
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .clickable(enabled = isManualColorEditingEnabled) {
                            activeColorPickerTarget = ColorPickerTarget.CHASSIS
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(uiState.customBodyColor))
                                .border(1.2.dp, Color.White, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_custom_chassis_btn),
                            color = backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                    Text(
                        text = if (isManualColorEditingEnabled) stringResource(R.string.settings_color_adjust) else stringResource(R.string.settings_color_locked),
                        color = if (isManualColorEditingEnabled) backlightHighlight else backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }

                // B. Cor da Roda
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .clickable(enabled = isManualColorEditingEnabled) {
                            activeColorPickerTarget = ColorPickerTarget.CLICK_WHEEL
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(uiState.customWheelColor))
                                .border(1.2.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_custom_wheel_btn),
                            color = backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                    Text(
                        text = if (isManualColorEditingEnabled) stringResource(R.string.settings_color_adjust) else stringResource(R.string.settings_color_locked),
                        color = if (isManualColorEditingEnabled) backlightHighlight else backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }

                // C. Cor do Botão Central
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33000000))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .clickable(enabled = isManualColorEditingEnabled) {
                            activeColorPickerTarget = ColorPickerTarget.CENTER_BUTTON
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(uiState.customCenterButtonColor))
                                .border(1.2.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_custom_center_button_btn),
                            color = backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                    Text(
                        text = if (isManualColorEditingEnabled) stringResource(R.string.settings_color_adjust) else stringResource(R.string.settings_color_locked),
                        color = if (isManualColorEditingEnabled) backlightHighlight else backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        // --- 3. CORES E TEMAS PREDEFINIDOS ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_group_presets),
                color = backlightTextSecondary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        // 3A. Temas da Carcaça
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isManualColorEditingEnabled) 1f else 0.45f)
            ) {
                Text(
                    text = stringResource(R.string.settings_chassis_presets) + ":",
                    color = backlightTextPrimary,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IpodChassisTheme.values().take(3).forEach { theme ->
                            val isSelected = uiState.chassisTheme == theme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) backlightHighlight else Color(0x33000000))
                                    .clickable(enabled = isManualColorEditingEnabled) { onSetChassisTheme(theme) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = theme.displayName.take(12),
                                    color = if (isSelected) Color.White else backlightTextPrimary,
                                    fontSize = (11f * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IpodChassisTheme.values().drop(3).forEach { theme ->
                            val isSelected = uiState.chassisTheme == theme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) backlightHighlight else Color(0x33000000))
                                    .clickable(enabled = isManualColorEditingEnabled) { onSetChassisTheme(theme) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = theme.displayName.take(12),
                                    color = if (isSelected) Color.White else backlightTextPrimary,
                                    fontSize = (11f * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // Paleta de Cores do Corpo
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isManualColorEditingEnabled) 1f else 0.45f)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                val bodyColors = listOf(
                    "Branco" to 0xFFFFFFFF,
                    "Prata" to 0xFFF1F5F9,
                    "Cinza" to 0xFFCBD5E1,
                    "Grafite" to 0xFF334155,
                    "Preto" to 0xFF0F172A,
                    "Vermelho" to 0xFFDC2626,
                    "Dourado" to 0xFFD4AF37,
                    "Azul" to 0xFF0284C7,
                    "Verde" to 0xFF059669,
                    "Roxo" to 0xFF7C3AED,
                    "Laranja" to 0xFFEA580C,
                    "Rosa" to 0xFFDB2777
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    bodyColors.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowColors.forEach { (name, colorVal) ->
                                val isSel = uiState.customBodyColor == colorVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(colorVal))
                                        .border(
                                            width = if (isSel) 2.5.dp else 1.dp,
                                            color = if (isSel) backlightHighlight else Color(0x66888888),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable(enabled = isManualColorEditingEnabled) {
                                            onSetCustomBodyColor(colorVal)
                                        }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isSel) "✓ $name" else name,
                                        color = if (colorVal == 0xFFFFFFFF || colorVal == 0xFFF1F5F9 || colorVal == 0xFFCBD5E1 || colorVal == 0xFFD4AF37) Color.Black else Color.White,
                                        fontSize = (11f * fontScale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3B. Estilos da Roda (Presets Click Wheel)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isManualColorEditingEnabled) 1f else 0.45f)
            ) {
                Text(
                    text = stringResource(R.string.settings_wheel_presets) + ":",
                    color = backlightTextPrimary,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IpodWheelPreset.values().forEach { preset ->
                        val isSelected = uiState.wheelPreset == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) backlightHighlight else Color(0x22000000))
                                .clickable(enabled = isManualColorEditingEnabled) { onSetWheelPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.wheelColor))
                                        .border(1.dp, Color(preset.textColor), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preset.displayName,
                                    color = if (isSelected) Color.White else backlightTextPrimary,
                                    fontSize = (11.5f * fontScale).sp,
                                    fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = fontFamily
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "✓ " + stringResource(R.string.onboarding_active_tag).replace("✓", "").trim(),
                                    color = Color.White,
                                    fontSize = (10.5f * fontScale).sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3C. Iluminação da Tela LCD
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_lcd_presets) + ":",
                color = backlightTextPrimary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LcdBacklight.values().forEach { bl ->
                    val isSelected = uiState.backlight == bl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSetBacklight(bl) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(bl.background))
                                    .border(1.dp, Color(bl.highlight), RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = bl.displayName,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = fontFamily
                            )
                        }
                        if (isSelected) {
                            Text(
                                text = "✓ " + stringResource(R.string.onboarding_active_tag).replace("✓", "").trim(),
                                color = Color.White,
                                fontSize = (10.5f * fontScale).sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }
        }

        // --- 4.5. VELOCIDADE DA CLICK WHEEL & TESTE INTERATIVO ---
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_step_wheel_title),
                color = backlightTextSecondary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            val clickWheelPrefs by (viewModel?.clickWheelPreferences ?: remember {
                kotlinx.coroutines.flow.MutableStateFlow(com.marcioamaro.mediapod.data.prefs.ClickWheelPreferences())
            }).collectAsState()

            var testIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            val testItems = remember { (1..50).map { context.getString(R.string.settings_wheel_test_item_prefix, it) } }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x28000000))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_wheel_fixed_speed_label),
                    color = backlightTextPrimary,
                    fontSize = (12f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = fontFamily
                )

                // Modo Progressiva
                val isProgressive = clickWheelPrefs.mode == com.marcioamaro.mediapod.data.prefs.ClickWheelMode.PROGRESSIVE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isProgressive) backlightHighlight.copy(alpha = 0.85f) else Color(0x22000000))
                        .clickable { viewModel?.setClickWheelMode(com.marcioamaro.mediapod.data.prefs.ClickWheelMode.PROGRESSIVE) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isProgressive) "●" else "○",
                        color = if (isProgressive) Color.White else backlightTextSecondary,
                        fontSize = (14f * fontScale).sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_wheel_mode_progressive),
                            color = if (isProgressive) Color.White else backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.onboarding_wheel_progressive_desc),
                            color = if (isProgressive) Color.White.copy(alpha = 0.9f) else backlightTextSecondary,
                            fontSize = (11f * fontScale).sp,
                            lineHeight = (15f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                }

                // Modo Fixa
                val isFixed = clickWheelPrefs.mode == com.marcioamaro.mediapod.data.prefs.ClickWheelMode.FIXED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isFixed) backlightHighlight.copy(alpha = 0.85f) else Color(0x22000000))
                        .clickable { viewModel?.setClickWheelMode(com.marcioamaro.mediapod.data.prefs.ClickWheelMode.FIXED) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFixed) "●" else "○",
                        color = if (isFixed) Color.White else backlightTextSecondary,
                        fontSize = (14f * fontScale).sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_wheel_mode_fixed),
                            color = if (isFixed) Color.White else backlightTextPrimary,
                            fontSize = (12f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.onboarding_wheel_fixed_desc),
                            color = if (isFixed) Color.White.copy(alpha = 0.9f) else backlightTextSecondary,
                            fontSize = (11f * fontScale).sp,
                            lineHeight = (15f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                }

                // Níveis de velocidade fixa
                AnimatedVisibility(visible = isFixed) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.onboarding_wheel_fixed_speed_label),
                            color = backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.marcioamaro.mediapod.data.prefs.FixedSpeed.values().forEach { speed ->
                                val isSpeedSelected = clickWheelPrefs.fixedSpeed == speed
                                val speedLabel = when (speed) {
                                    com.marcioamaro.mediapod.data.prefs.FixedSpeed.SLOW -> stringResource(R.string.onboarding_speed_slow)
                                    com.marcioamaro.mediapod.data.prefs.FixedSpeed.STANDARD -> stringResource(R.string.onboarding_speed_standard)
                                    com.marcioamaro.mediapod.data.prefs.FixedSpeed.FAST -> stringResource(R.string.onboarding_speed_fast)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSpeedSelected) backlightHighlight else Color(0x22000000))
                                        .border(
                                            1.dp,
                                            if (isSpeedSelected) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel?.setFixedSpeed(speed) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = speedLabel,
                                        color = if (isSpeedSelected) Color.White else backlightTextPrimary,
                                        fontSize = (11f * fontScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontFamily
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Área de Teste Interativa
                Text(
                    text = stringResource(R.string.settings_wheel_test_title),
                    color = backlightTextPrimary,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )

                Row(
                    modifier = Modifier
                        .height(170.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(backlightBg.copy(alpha = 0.9f))
                        .border(1.2.dp, backlightHighlight.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lista interativa no lado esquerdo com SelectableLazyColumn
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x18000000))
                            .padding(2.dp)
                    ) {
                        com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
                            items = testItems,
                            selectedIndex = testIndex,
                            onSelectedIndexChange = { testIndex = it },
                            modifier = Modifier.fillMaxSize()
                        ) { index, item, isSelected ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSelected) backlightHighlight else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item,
                                    color = if (isSelected) Color.White else backlightTextPrimary,
                                    fontSize = (11f * fontScale).sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }

                    // Mini Click Wheel funcional no lado direito
                    Box(
                        modifier = Modifier.size(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.marcioamaro.mediapod.ui.components.ClickWheel(
                            onRotaryScroll = { steps ->
                                testIndex = (testIndex + steps).coerceIn(0, testItems.lastIndex)
                            },
                            onCenterClick = {
                                testIndex = 0
                            },
                            onMenuClick = {
                                testIndex = 0
                            },
                            onPlayPauseClick = {
                                testIndex = testItems.lastIndex
                            },
                            onPrevClick = {
                                testIndex = (testIndex - 1).coerceAtLeast(0)
                            },
                            onNextClick = {
                                testIndex = (testIndex + 1).coerceAtMost(testItems.lastIndex)
                            },
                            wheelColor = Color(uiState.customWheelColor),
                            textColor = Color(uiState.customWheelTextColor),
                            centerButtonColor = Color(uiState.customCenterButtonColor),
                            wheelSize = 125.dp
                        )
                    }
                }
            }
        }

        // --- 5. INICIALIZAÇÃO & PREFERÊNCIAS ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_group_startup_prefs),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .clickable { onSetAutoPlay(!uiState.autoPlayOnLaunch) }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_startup_autoplay_title),
                        color = backlightTextPrimary,
                        fontSize = (12f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_startup_autoplay_desc),
                        color = backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        lineHeight = (15f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.autoPlayOnLaunch) stringResource(R.string.settings_tag_enabled) else stringResource(R.string.settings_tag_disabled),
                    color = if (uiState.autoPlayOnLaunch) backlightHighlight else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // Animação da Traseira do MediaPod (Easter Egg)
        item {
            val isChassisAnimEnabled = viewModel?.isChassisBackAnimationEnabled?.collectAsState()?.value ?: false
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .clickable { viewModel?.toggleChassisBackAnimationEnabled() }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_chassis_anim_title),
                        color = backlightTextPrimary,
                        fontSize = (12f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_chassis_anim_desc),
                        color = backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        lineHeight = (15f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isChassisAnimEnabled) stringResource(R.string.settings_tag_enabled) else stringResource(R.string.settings_tag_disabled),
                    color = if (isChassisAnimEnabled) backlightHighlight else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 6. ESTABILIDADE & MODO STREAMING PURO ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_group_streaming_stability),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            val isPureAudio = viewModel?.isPureAudioModeEnabled?.collectAsState()?.value ?: false
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .clickable { viewModel?.togglePureAudioMode() }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_pure_audio_title),
                        color = backlightTextPrimary,
                        fontSize = (12f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_pure_audio_desc),
                        color = backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        lineHeight = (15f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPureAudio) stringResource(R.string.settings_tag_enabled) else stringResource(R.string.settings_tag_disabled),
                    color = if (isPureAudio) backlightHighlight else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 7. SEGUNDO PLANO E BATERIA ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_group_battery_bg),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            val isExempted = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .clickable {
                        BatteryOptimizationHelper.requestDisableBatteryOptimization(context)
                    }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_battery_bg_title),
                        color = backlightTextPrimary,
                        fontSize = (12f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExempted) stringResource(R.string.settings_tag_enabled) else stringResource(R.string.settings_tag_configure),
                        color = if (isExempted) backlightHighlight else backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
                Text(
                    text = stringResource(R.string.settings_battery_bg_desc),
                    color = backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    lineHeight = (15f * fontScale).sp,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 8. TIMER DE DESLIGAMENTO ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .clickable {
                        val next = when (sleepTimerMinutes) {
                            0 -> 15
                            15 -> 30
                            30 -> 45
                            45 -> 60
                            60 -> -1
                            else -> 0
                        }
                        onSetSleepTimer(next)
                    }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_group_sleep_timer),
                    color = backlightTextPrimary,
                    fontSize = (12f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = fontFamily,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (sleepTimerMinutes) {
                        -1 -> stringResource(R.string.settings_sleep_timer_end_of_track)
                        in 1..Int.MAX_VALUE -> stringResource(R.string.settings_sleep_timer_min_format, sleepTimerMinutes)
                        else -> stringResource(R.string.settings_sleep_timer_off)
                    },
                    color = backlightHighlight,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 9. MODO DOCK (CABECEIRA / NIGHTSTAND) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_group_dock_mode),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        // Escala de Tamanho da Fonte da Hora: 100%, 125%, 150%, 175%, 200%
        item {
            val prefs = remember { com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager.getInstance(context) }
            val currentScale = uiState.dockClockScale
            Text(
                text = stringResource(R.string.settings_dock_clock_size_label),
                color = backlightTextPrimary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                com.marcioamaro.mediapod.data.preferences.DockClockScale.values().forEach { scale ->
                    val isSelected = currentScale == scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable {
                                prefs.dockClockScale = scale
                                viewModel?.setDockClockScale(scale)
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scale.displayName.replace(" (Padrão)", ""),
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (11f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }

        // Toggle Exibir Segundos
        item {
            val prefs = remember { com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager.getInstance(context) }
            val showSecs = uiState.dockShowSeconds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33000000))
                    .clickable {
                        val next = !showSecs
                        prefs.dockShowSeconds = next
                        viewModel?.setDockShowSeconds(next)
                    }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_dock_show_seconds_title),
                        color = backlightTextPrimary,
                        fontSize = (12f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_dock_show_seconds_desc),
                        color = backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        lineHeight = (15f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showSecs) stringResource(R.string.settings_tag_enabled) else stringResource(R.string.settings_tag_disabled),
                    color = if (showSecs) backlightHighlight else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 10. CÓPIA DE SEGURANÇA (BACKUP) ---
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_group_backup_title),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x28000000))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_desc),
                    color = backlightTextPrimary,
                    fontSize = (11f * fontScale).sp,
                    lineHeight = (15f * fontScale).sp,
                    fontFamily = fontFamily
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Botão Exportar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(backlightHighlight.copy(alpha = 0.85f))
                            .clickable {
                                val timestamp = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                backupActions.export()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_backup_export_btn),
                            color = Color.White,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    // Botão Restaurar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(backlightTextPrimary.copy(alpha = 0.18f))
                            .clickable {
                                backupActions.restore()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_backup_restore_btn),
                            color = backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }

                com.marcioamaro.mediapod.ui.components.LcdFeatureTheme(backlightBg, backlightTextPrimary) {
                    com.marcioamaro.mediapod.ui.components.CatalogUpdateControl()
                    com.marcioamaro.mediapod.ui.components.DiagnosticsControl()
                    com.marcioamaro.mediapod.ui.components.DataUsageControl()
                }

                if (backupStatusMessage != null) {
                    Text(
                        text = backupStatusMessage!!,
                        color = if (isBackupError) Color(0xFFDC2626) else backlightHighlight,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        // --- 11. GERENCIAMENTO DE HISTÓRICO (LIMPAR RECENTES) ---
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_group_history),
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        item {
            var clearHistoryMsg by remember { mutableStateOf<String?>(null) }
            val radiosClearedText = stringResource(R.string.settings_history_radios_cleared)
            val podcastsClearedText = stringResource(R.string.settings_history_podcasts_cleared)
            val allClearedText = stringResource(R.string.settings_history_all_cleared)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x28000000))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_history_desc),
                    color = backlightTextPrimary,
                    fontSize = (11f * fontScale).sp,
                    lineHeight = (15f * fontScale).sp,
                    fontFamily = fontFamily
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Limpar Rádios Recentes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33000000))
                            .border(0.8.dp, backlightHighlight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel?.clearRecentStations()
                                clearHistoryMsg = radiosClearedText
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_history_clear_radios),
                            color = backlightTextPrimary,
                            fontSize = (11f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    // Limpar Podcasts Recentes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33000000))
                            .border(0.8.dp, backlightHighlight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel?.clearRecentPodcasts()
                                clearHistoryMsg = podcastsClearedText
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_history_clear_podcasts),
                            color = backlightTextPrimary,
                            fontSize = (11f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }
                }

                // Limpar Tudo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(backlightHighlight.copy(alpha = 0.75f))
                        .clickable {
                            viewModel?.clearAllRecents()
                            clearHistoryMsg = allClearedText
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_history_clear_all),
                        color = Color.White,
                        fontSize = (11.5f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }

                if (clearHistoryMsg != null) {
                    Text(
                        text = clearHistoryMsg!!,
                        color = backlightHighlight,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }
        }

    }
}

@Composable
private fun IpodAboutScreen(
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    onShowChassisBack: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(backlightTextPrimary.copy(alpha = 0.08f))
                .border(1.dp, backlightTextPrimary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .clickable { onShowChassisBack() }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logotipo do Aplicativo
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.marcioamaro.mediapod.R.drawable.ic_pear_logo),
                contentDescription = "Logotipo do Aplicativo",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(backlightTextPrimary),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .width(44.dp)
                    .height(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Nome do Aplicativo
            Text(
                text = "MediaPod + Radio / Podcast",
                color = backlightTextPrimary,
                fontSize = (16f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Data da versão aaaa.mm.dd e número da versão
            Text(
                text = "2026.09.21 - Versão 0.3.2 (84 Inl-PRA)",
                color = backlightTextSecondary,
                fontSize = (12f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Autor
            Text(
                text = "autor: Márcio Amaro marcio.amaro@gmail.com",
                color = backlightHighlight,
                fontSize = (11f * fontScale).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = fontFamily,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun IpodSearchScreen(
    stations: List<RadioStation>,
    currentStationId: String?,
    selectedIndex: Int,
    isLoading: Boolean,
    searchQuery: String,
    selectedCountryCode: String?,
    selectedGenreTag: String?,
    selectedStateCode: String? = null,
    selectedCity: String? = null,
    availableCities: List<String> = emptyList(),
    isLoadingCities: Boolean = false,
    onSearchQueryChange: (String) -> Unit,
    onSearchCountryChange: (String) -> Unit,
    onSearchGenreChange: (String) -> Unit,
    onSearchStateChange: (String) -> Unit = {},
    onSearchCityChange: (String) -> Unit = {},
    onSelectStation: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    favorites: List<RadioStation> = emptyList(),
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isBrazil = selectedCountryCode?.equals("BR", ignoreCase = true) == true

    var isCitySelectorOpen by remember { mutableStateOf(false) }
    var cityFilterQuery by remember { mutableStateOf("") }

    val displayedStations = remember(stations, searchQuery, selectedCountryCode, selectedGenreTag, selectedStateCode, selectedCity, isBrazil) {
        var list = stations
        if (selectedCountryCode != null && selectedCountryCode.isNotBlank() && !selectedCountryCode.equals("ALL", ignoreCase = true)) {
            if (isBrazil) {
                list = list.filter { it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true) }
            } else {
                list = list.filter { it.countryCode.equals(selectedCountryCode, ignoreCase = true) }
            }
        }
        if (selectedGenreTag != null && selectedGenreTag.isNotBlank() && !selectedGenreTag.equals("ALL", ignoreCase = true)) {
            list = list.filter { com.marcioamaro.mediapod.util.RadioSearchEngine.matchesGenre(it, selectedGenreTag) }
        }
        // Filtro de Estado e Cidade aplicados EXCLUSIVAMENTE quando o país é o Brasil
        if (isBrazil) {
            if (selectedStateCode != null && selectedStateCode.isNotBlank() && !selectedStateCode.equals("ALL", ignoreCase = true)) {
                list = list.filter { com.marcioamaro.mediapod.util.RadioSearchEngine.matchesUf(it, selectedStateCode) }
            }
            if (selectedCity != null && selectedCity.isNotBlank() && !selectedCity.equals("ALL", ignoreCase = true)) {
                val c = com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(selectedCity)
                list = list.filter {
                    com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.city).contains(c) ||
                    com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it.name).contains(c)
                }
            }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, searchQuery) }
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backlightBg)
            .testTag("ipod_search_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            // Barra de Busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("search_text_input"),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = (11.5f * fontScale).sp,
                    lineHeight = (15f * fontScale).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                placeholder = {
                    Text(
                        text = "Buscar emissoras mundiais...",
                        fontSize = (11f * fontScale).sp,
                        color = backlightTextSecondary.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar",
                                tint = backlightTextSecondary,
                                modifier = Modifier.size(15.dp)
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

            Spacer(modifier = Modifier.height(3.dp))

            // Seletor de Países
            val countryOptions = remember {
                CuratedData.COUNTRIES.map { it.code to it.name }
            }
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 2.dp)
            ) {
                items(countryOptions.size) { idx ->
                    val (code, label) = countryOptions[idx]
                    val isSelected = if (code == "ALL") {
                        selectedCountryCode.isNullOrEmpty() || selectedCountryCode.equals("ALL", ignoreCase = true)
                    } else {
                        selectedCountryCode.equals(code, ignoreCase = true)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable {
                                onSearchCountryChange(code)
                                if (code.equals("BR", ignoreCase = true)) {
                                    onSearchStateChange("SP")
                                    onSearchCityChange("São Paulo")
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MonochromeFlag(
                                code = code,
                                tint = if (isSelected) Color.White else backlightTextPrimary,
                                size = 12.dp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (10f * fontScale).sp,
                                fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Seletor de Gêneros Musicais
            val searchGenres = remember {
                listOf("" to "Todos os Gêneros") + CuratedData.GENRES.map { it.tag to it.name }
            }
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 2.dp)
            ) {
                items(searchGenres.size) { idx ->
                    val (tag, label) = searchGenres[idx]
                    val isSelected = if (tag.isEmpty()) selectedGenreTag.isNullOrEmpty() || selectedGenreTag == "ALL" else selectedGenreTag.equals(tag, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSearchGenreChange(tag) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (10f * fontScale).sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = fontFamily
                        )
                    }
                }
            }

            // Exibição condicional estrita: Estados e Cidades APENAS para o BRASIL
            if (isBrazil) {
                Spacer(modifier = Modifier.height(2.dp))

                // Linha de Estados (UFs do Brasil)
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 2.dp)
                ) {
                    items(CuratedData.BRAZILIAN_STATES.size) { idx ->
                        val (code, label) = CuratedData.BRAZILIAN_STATES[idx]
                        val isSelected = if (code.isEmpty()) selectedStateCode.isNullOrEmpty() || selectedStateCode == "ALL" else selectedStateCode.equals(code, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) backlightHighlight else Color(0x22000000))
                                .clickable { onSearchStateChange(if (isSelected) "ALL" else code) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (code.isNotEmpty()) {
                                    MonochromeFlag(
                                        code = code,
                                        tint = if (isSelected) Color.White else backlightTextPrimary,
                                        size = 12.dp
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else backlightTextPrimary,
                                    fontSize = (10f * fontScale).sp,
                                    fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }

                // Combo Box iPod LCD para Cidades (ativado quando um estado específico é selecionado)
                if (!selectedStateCode.isNullOrEmpty() && !selectedStateCode.equals("ALL", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val hasCitySelected = !selectedCity.isNullOrEmpty() && !selectedCity.equals("ALL", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasCitySelected) backlightHighlight.copy(alpha = 0.25f) else Color(0x22000000))
                                .border(1.dp, if (hasCitySelected) backlightHighlight else backlightHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .clickable { isCitySelectorOpen = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val stateName = CuratedData.getStateFullName(selectedStateCode)
                                Text(
                                    text = if (isLoadingCities) "Carregando municípios..."
                                    else if (hasCitySelected) "Cidade: $selectedCity ($stateName)"
                                    else "Cidade: Todas as Cidades de $stateName",
                                    color = backlightTextPrimary,
                                    fontSize = (10f * fontScale).sp,
                                    fontWeight = if (hasCitySelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = fontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "▼",
                                    color = backlightHighlight,
                                    fontSize = (10f * fontScale).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (hasCitySelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(backlightHighlight.copy(alpha = 0.25f))
                                    .clickable { onSearchCityChange("ALL") }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✕",
                                    color = backlightTextPrimary,
                                    fontSize = (10f * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Banner de contagem de resultados
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESULTADOS (${displayedStations.size})",
                    color = backlightTextSecondary,
                    fontSize = (9f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
                if (isBrazil && (!selectedStateCode.isNullOrEmpty() || !selectedCity.isNullOrEmpty())) {
                    Text(
                        text = "FILTRADO: $selectedStateCode ${selectedCity?.takeIf { it != "ALL" } ?: ""}".trim(),
                        color = backlightHighlight,
                        fontSize = (9f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = backlightHighlight,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (displayedStations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = backlightHighlight.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nenhuma emissora localizada",
                            color = backlightTextPrimary,
                            fontSize = (11f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = if (isBrazil) "Tente alterar o estado/cidade ou limpar o filtro" else "Selecione 'Todos os Países' ou limpe a busca",
                            color = backlightTextSecondary,
                            fontSize = (9.5f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                }
            } else {
                com.marcioamaro.mediapod.ui.components.SelectableLazyColumn(
                    items = displayedStations,
                    selectedIndex = selectedIndex,
                    key = { _, st -> st.id },
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
                ) { index, station, isSelected ->
                    val isPlayingThis = station.id == currentStationId
                    val isFav = favorites.any { it.id == station.id }

                    com.marcioamaro.mediapod.ui.components.StationItemView(
                        station = station,
                        isSelected = isSelected,
                        isPlaying = isPlayingThis,
                        isFavorite = isFav,
                        onClick = { onSelectStation(station) },
                        onToggleFavorite = { onToggleFavorite(station) },
                        backlightTextPrimary = backlightTextPrimary,
                        backlightTextSecondary = backlightTextSecondary,
                        backlightHighlight = backlightHighlight
                    )
                }
            }
        }

        // Overlay do Seletor LCD de Cidades (quando o usuário clica no combo de cidades)
        if (isCitySelectorOpen && isBrazil) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backlightBg)
                    .padding(6.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val stateFullName = CuratedData.getStateFullName(selectedStateCode)
                    // Top Bar do Seletor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cidades de $stateFullName",
                                color = backlightTextPrimary,
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = fontFamily
                            )
                            Text(
                                text = if (isLoadingCities) "Carregando municípios..." else "${availableCities.size} municípios disponíveis",
                                color = backlightTextSecondary,
                                fontSize = (9f * fontScale).sp,
                                fontFamily = fontFamily
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(backlightHighlight)
                                .clickable {
                                    isCitySelectorOpen = false
                                    cityFilterQuery = ""
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "FECHAR ✕",
                                color = Color.White,
                                fontSize = (9.5f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Mini campo de busca para filtrar cidades
                    OutlinedTextField(
                        value = cityFilterQuery,
                        onValueChange = { cityFilterQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = (10.5f * fontScale).sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        placeholder = {
                            Text(
                                text = "Filtrar por nome de cidade...",
                                fontSize = (10f * fontScale).sp,
                                color = backlightTextSecondary.copy(alpha = 0.7f)
                            )
                        },
                        singleLine = true,
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

                    val filteredCities = remember(availableCities, cityFilterQuery) {
                        val base = if (cityFilterQuery.isBlank()) availableCities
                        else {
                            val normQ = com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(cityFilterQuery)
                            availableCities.filter { com.marcioamaro.mediapod.util.RadioSearchEngine.normalize(it).contains(normQ) }
                        }
                        val spIndex = base.indexOfFirst { it.equals("São Paulo", ignoreCase = true) }
                        if (spIndex > 0) {
                            val mutable = base.toMutableList()
                            val sp = mutable.removeAt(spIndex)
                            listOf(sp) + mutable
                        } else {
                            base
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            val isAllSelected = selectedCity.isNullOrEmpty() || selectedCity.equals("ALL", ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isAllSelected) backlightHighlight else Color(0x18000000))
                                    .clickable {
                                        onSearchCityChange("ALL")
                                        isCitySelectorOpen = false
                                        cityFilterQuery = ""
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MonochromeFlag(
                                        code = selectedStateCode ?: "BR",
                                        tint = if (isAllSelected) Color.White else backlightTextPrimary,
                                        size = 12.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Todas as Cidades de $stateFullName",
                                        color = if (isAllSelected) Color.White else backlightTextPrimary,
                                        fontSize = (11f * fontScale).sp,
                                        fontWeight = if (isAllSelected) FontWeight.Black else FontWeight.Bold,
                                        fontFamily = fontFamily
                                    )
                                }
                            }
                        }

                        itemsIndexed(filteredCities) { _, city ->
                            val isSelected = selectedCity?.equals(city, ignoreCase = true) == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) backlightHighlight else Color(0x12000000))
                                    .clickable {
                                        onSearchCityChange(city)
                                        isCitySelectorOpen = false
                                        cityFilterQuery = ""
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MonochromeFlag(
                                        code = selectedStateCode ?: "BR",
                                        tint = if (isSelected) Color.White else backlightTextPrimary,
                                        size = 11.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = city,
                                        color = if (isSelected) Color.White else backlightTextPrimary,
                                        fontSize = (10.5f * fontScale).sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                        fontFamily = fontFamily
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        fontSize = (11f * fontScale).sp,
                                        fontWeight = FontWeight.Black
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

