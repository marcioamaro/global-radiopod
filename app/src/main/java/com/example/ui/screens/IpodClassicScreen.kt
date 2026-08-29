package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.util.BatteryOptimizationHelper
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.RadioStation
import com.example.data.preferences.IpodFontSizeScale
import com.example.data.preferences.IpodFontType
import com.example.data.preferences.IpodWheelPreset
import com.example.data.preferences.toFontFamily
import com.example.data.repository.CuratedData
import com.example.player.RadioPlaybackStatus
import com.example.player.RdsInfo
import com.example.ui.DisplayMode
import com.example.ui.IpodChassisTheme
import com.example.ui.IpodScreenDestination
import com.example.ui.LcdBacklight
import com.example.ui.UiState
import com.example.ui.components.ClickWheel
import com.example.ui.components.IpodHeader
import com.example.ui.components.RdsDisplay
import com.example.util.IpodSoundAndHaptics

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
    onSelectGenre: (com.example.data.repository.GenreCategory) -> Unit,
    onSelectCountry: (com.example.data.repository.CountryCategory) -> Unit,
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
    currentLocalAudio: com.example.data.model.LocalAudioTrack? = null,
    audioPositionMs: Long = 0L,
    audioDurationMs: Long = 0L,
    videoPlayerManager: com.example.player.LocalVideoPlayerManager? = null,
    onSelectAudioFolder: (com.example.data.model.MediaFolder) -> Unit = {},
    onSelectAudioTrack: (com.example.data.model.LocalAudioTrack) -> Unit = {},
    onSelectVideoFolder: (com.example.data.model.MediaFolder) -> Unit = {},
    onSelectVideoTrack: (com.example.data.model.LocalVideoTrack) -> Unit = {},
    onToggleVideoFullscreen: () -> Unit = {},
    isEqualizerEnabled: Boolean = true,
    onToggleEqualizerEnabled: (Boolean) -> Unit = {},
    equalizerPreset: String = "Rock",
    onSelectEqualizerPreset: (String) -> Unit = {},
    equalizerBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    onEqualizerBandLevelChange: (Int, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val chassisTheme = uiState.chassisTheme
    val backlight = uiState.backlight

    val bodyColor = Color(uiState.customBodyColor)
    val wheelColor = Color(uiState.customWheelColor)
    val wheelTextColor = Color(uiState.customWheelTextColor)
    val centerButtonColor = Color(uiState.customCenterButtonColor)

    val fontFamily = uiState.fontType.toFontFamily()
    val fontScale = uiState.fontSizeScale.scale
    val isBold = uiState.isFontBold

    val backlightBg = Color(backlight.background)
    val backlightTextPrimary = Color(backlight.textPrimary)
    val backlightTextSecondary = Color(backlight.textSecondary)
    val backlightHighlight = Color(backlight.highlight)

    val context = LocalContext.current
    val effectiveSoundAndHaptics = soundAndHaptics ?: remember { IpodSoundAndHaptics.getInstance(context) }

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

                // Car / Fullscreen Mode Toggle Pill (harmonized with Click Wheel colors)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(wheelColor.copy(alpha = 0.22f))
                        .border(1.2.dp, wheelColor, RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleDisplayMode)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("toggle_car_mode_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Modo Carro",
                        tint = wheelTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "MODO CARRO",
                        color = wheelTextColor,
                        fontSize = (9.5f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                        fontFamily = fontFamily
                    )
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
                    // Header Bar
                    IpodHeader(
                        title = getScreenTitle(uiState),
                        status = playbackStatus,
                        isHoldLocked = uiState.isHoldLocked,
                        sleepTimerMinutes = sleepTimerMinutes,
                        backlightTextPrimary = backlightTextPrimary,
                        backlightHighlight = backlightHighlight,
                        fontFamily = fontFamily,
                        fontScale = fontScale,
                        isBold = isBold
                    )

                    // Active Screen Content
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (uiState.currentScreen) {
                            IpodScreenDestination.MAIN_MENU -> {
                                IpodRootHomeScreen(
                                    selectedIndex = uiState.selectedIndex,
                                    onSelectItem = { destIdx ->
                                        when (destIdx) {
                                            0 -> onSelectDestination(IpodScreenDestination.RADIO_MENU)
                                            1 -> onSelectDestination(IpodScreenDestination.MP3_FOLDERS)
                                            2 -> onSelectDestination(IpodScreenDestination.VIDEO_FOLDERS)
                                            3 -> onSelectDestination(IpodScreenDestination.EQUALIZER)
                                            4 -> onSelectDestination(IpodScreenDestination.GAME_BRICK)
                                            5 -> onToggleDisplayMode()
                                            6 -> onSelectDestination(IpodScreenDestination.SETTINGS_THEMES)
                                            7 -> onSelectDestination(IpodScreenDestination.ABOUT)
                                        }
                                    },
                                    isLocalAudio = currentLocalAudio != null,
                                    currentArtUrl = currentLocalAudio?.albumArtUrl,
                                    nowPlayingTitle = currentLocalAudio?.title ?: currentStation?.name,
                                    nowPlayingSubtitle = currentLocalAudio?.artist ?: rdsInfo.radioText,
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
                                            3 -> onSelectDestination(IpodScreenDestination.TOP_WORLD)
                                            4 -> onSelectDestination(IpodScreenDestination.GENRES_LIST)
                                            5 -> onSelectDestination(IpodScreenDestination.COUNTRIES_LIST)
                                            6 -> onSelectDestination(IpodScreenDestination.SEARCH)
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
                                IpodMp3NowPlayingScreen(
                                    track = currentLocalAudio,
                                    isPlaying = playbackStatus == RadioPlaybackStatus.PLAYING,
                                    positionMs = audioPositionMs,
                                    durationMs = audioDurationMs,
                                    visualizerAmplitudes = visualizerAmplitudes,
                                    volume = volume,
                                    onStepVolumeUp = onStepVolumeUp,
                                    onStepVolumeDown = onStepVolumeDown,
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
                                    title = uiState.currentVideoFolder?.name ?: "Vídeos",
                                    videos = uiState.localVideoTracks,
                                    currentVideoId = videoPlayerManager?.currentVideo?.value?.id,
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
                                        fontFamily = fontFamily,
                                        fontScale = fontScale,
                                        isBold = isBold
                                    )
                                }
                            }
                            IpodScreenDestination.GAME_BRICK -> {
                                IpodBrickGameScreen(
                                    paddlePositionRatio = uiState.gamePaddlePosition,
                                    onPaddleMove = onPaddleMove,
                                    soundAndHaptics = effectiveSoundAndHaptics,
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight
                                )
                            }
                            IpodScreenDestination.NOW_PLAYING_RDS -> {
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
                                    isBold = isBold
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
                                    isBold = isBold
                                )
                            }
                            IpodScreenDestination.TOP_WORLD -> {
                                StationsListScreen(
                                    title = "Top Mundial",
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
                                    backlightHighlight = backlightHighlight
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
                                    backlightHighlight = backlightHighlight
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
                                    backlightHighlight = backlightHighlight
                                )
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
                                    fontFamily = fontFamily,
                                    fontScale = fontScale,
                                    isBold = isBold
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
                            IpodScreenDestination.ABOUT -> {
                                IpodAboutScreen(
                                    backlightBg = backlightBg,
                                    backlightTextPrimary = backlightTextPrimary,
                                    backlightTextSecondary = backlightTextSecondary,
                                    backlightHighlight = backlightHighlight
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Click Wheel at Bottom with dynamic user-configured colors
            ClickWheel(
                onRotaryScroll = onRotaryScroll,
                onCenterClick = onCenterClick,
                onMenuClick = onMenuClick,
                onPlayPauseClick = onPlayPauseClick,
                onPrevClick = onPrevClick,
                onNextClick = onNextClick,
                wheelColor = wheelColor,
                textColor = wheelTextColor,
                centerButtonColor = centerButtonColor,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

private fun getScreenTitle(uiState: UiState): String {
    return when (uiState.currentScreen) {
        IpodScreenDestination.MAIN_MENU -> "IPod Class + Radio"
        IpodScreenDestination.RADIO_MENU -> "Rádio"
        IpodScreenDestination.NOW_PLAYING_RDS -> "Agora Tocando"
        IpodScreenDestination.FAVORITES -> "Favoritos"
        IpodScreenDestination.RECENTS -> "Recentes"
        IpodScreenDestination.TOP_WORLD -> "Top Mundial"
        IpodScreenDestination.GENRES_LIST -> "Gêneros"
        IpodScreenDestination.STATIONS_BY_GENRE -> uiState.activeGenre?.name ?: "Gênero"
        IpodScreenDestination.COUNTRIES_LIST -> "Países & Regiões"
        IpodScreenDestination.STATIONS_BY_COUNTRY -> uiState.activeCountry?.name ?: "País"
        IpodScreenDestination.SEARCH -> "Busca Mundial"
        IpodScreenDestination.MP3_FOLDERS -> "Pastas de Músicas"
        IpodScreenDestination.MP3_TRACKS_LIST -> uiState.currentAudioFolder?.name ?: "Músicas"
        IpodScreenDestination.MP3_NOW_PLAYING -> "Agora Tocando (MP3)"
        IpodScreenDestination.VIDEO_FOLDERS -> "Pastas de Vídeos"
        IpodScreenDestination.VIDEO_LIST -> uiState.currentVideoFolder?.name ?: "Vídeos"
        IpodScreenDestination.VIDEO_PLAYER -> "Vídeo Player"
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
    val menuItems = remember {
        listOf(
            "Agora Tocando" to Icons.Default.PlayArrow,
            "Rádios Favoritas" to Icons.Default.Favorite,
            "Recentes" to Icons.Default.History,
            "Top Mundial" to Icons.Default.Public,
            "Gêneros Musicais" to Icons.Default.MusicNote,
            "Países & Cidades" to Icons.Default.LocationOn,
            "Buscar Estação" to Icons.Default.Search
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in menuItems.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        itemsIndexed(menuItems) { index, (title, icon) ->
            val isSelected = index == selectedIndex
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
                        fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
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
        "Países & Cidades" to Icons.Default.LocationOn,
        "Buscar Estação" to Icons.Default.Search,
        "Brick Game" to Icons.Default.Gamepad,
        "Configurações" to Icons.Default.Settings,
        "Sobre o Aplicativo" to Icons.Default.Info
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
                            fontWeight = if (isSelected || isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily,
                            maxLines = 1,
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
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(38.dp)
                )
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

            Text(
                text = if (playbackStatus == RadioPlaybackStatus.PLAYING) "AO VIVO • ${currentStation?.displayFrequency ?: ""}" else "Pronto para tocar",
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
    onSelectGenre: (com.example.data.repository.GenreCategory) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color
) {
    var searchQuery by remember { mutableStateOf("") }
    val genres = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CuratedData.GENRES
        } else {
            val q = searchQuery.trim().lowercase()
            CuratedData.GENRES.filter {
                it.name.lowercase().contains(q) || it.tag.lowercase().contains(q) || it.description.lowercase().contains(q)
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
                    .height(42.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                placeholder = {
                    Text(
                        text = "Filtrar gêneros...",
                        fontSize = 10.5.sp,
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(genres) { index, genre ->
                    val isSelected = index == selectedIndex
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
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = activeGenre?.description ?: "",
                color = backlightTextSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun IpodCountriesSplitView(
    selectedIndex: Int,
    onSelectCountry: (com.example.data.repository.CountryCategory) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color
) {
    var searchQuery by remember { mutableStateOf("") }
    val countries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CuratedData.COUNTRIES
        } else {
            val q = searchQuery.trim().lowercase()
            CuratedData.COUNTRIES.filter {
                it.name.lowercase().contains(q) || it.code.lowercase().contains(q) || it.region.lowercase().contains(q)
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
                    .height(42.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                placeholder = {
                    Text(
                        text = "Filtrar países...",
                        fontSize = 10.5.sp,
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(countries) { index, country ->
                    val isSelected = index == selectedIndex
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
                                    .size(20.dp, 14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(backlightTextPrimary.copy(alpha = 0.1f))
                                    .border(0.8.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = "https://flagcdn.com/w80/${country.code.lowercase()}.png",
                                    contentDescription = country.name,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = country.name,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
                if (!activeCountry?.code.isNullOrBlank()) {
                    AsyncImage(
                        model = "https://flagcdn.com/w160/${activeCountry?.code?.lowercase()}.png",
                        contentDescription = activeCountry?.name,
                        contentScale = ContentScale.Crop,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = backlightHighlight,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activeCountry?.name ?: "",
                color = backlightTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = activeCountry?.region ?: "",
                color = backlightTextSecondary,
                fontSize = 10.sp,
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
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 1. FONTE DO MODO IPOD (ESTILO, TAMANHO, NEGRITO) ---
        item {
            Text(
                text = "FONTES DO MODO IPOD",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        // Estilo da Fonte
        item {
            Text(
                text = "Estilo da Tipografia:",
                color = backlightTextPrimary,
                fontSize = (10f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                IpodFontType.values().forEach { type ->
                    val isSelected = uiState.fontType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSetFontType(type) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type.displayName,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (10.5f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = type.toFontFamily()
                        )
                        if (isSelected) {
                            Text(
                                text = "✓ ATIVO",
                                color = Color.White,
                                fontSize = 9.sp,
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
                text = "Tamanho do Texto:",
                color = backlightTextPrimary,
                fontSize = (10f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
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
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scale.displayName,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = 10.5.sp,
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Texto em Negrito (Bold)",
                    color = backlightTextPrimary,
                    fontSize = (10.5f * fontScale).sp,
                    fontWeight = if (uiState.isFontBold) FontWeight.Black else FontWeight.Normal,
                    fontFamily = fontFamily
                )
                Text(
                    text = if (uiState.isFontBold) "✓ NEGRITO" else "NORMAL",
                    color = if (uiState.isFontBold) Color.White else backlightTextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 2. SLIDECIRCLE CLICK (CORES E SELETORES) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SLIDECIRCLE CLICK (CORES E SELETORES)",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }

        // Presets de SlideCircle Click
        item {
            Text(
                text = "Presets do SlideCircle Click:",
                color = backlightTextPrimary,
                fontSize = (10f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                IpodWheelPreset.values().forEach { preset ->
                    val isSelected = uiState.wheelPreset == preset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSetWheelPreset(preset) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Mini Color Dot
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.wheelColor))
                                    .border(1.dp, Color(preset.textColor), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = preset.displayName,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = (10.5f * fontScale).sp,
                                fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = fontFamily
                            )
                        }
                        if (isSelected) {
                            Text(
                                text = "✓ ATIVO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }
        }



        // --- 3. COR DA TELA LCD ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "COR DA TELA LCD",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                LcdBacklight.values().forEach { bl ->
                    val isSelected = uiState.backlight == bl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSetBacklight(bl) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bl.displayName,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (10.5f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = fontFamily
                        )
                        if (isSelected) {
                            Text(
                                text = "✓ ATIVO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }
        }

        // --- 4. CORPO DO IPOD CLASS (CHASSIS) ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "CORPO DO IPOD CLASS (CHASSIS)",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
        item {
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
                            .clickable { onSetChassisTheme(theme) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = theme.displayName.take(10),
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        item {
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
                            .clickable { onSetChassisTheme(theme) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = theme.displayName.take(12),
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Personalizar Cores do Corpo do IPod Class (aplicado diretamente no corpo do aparelho)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personalizar Cores do Corpo:",
                color = backlightTextPrimary,
                fontSize = (10.5f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(3.dp))

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
                                    .clickable {
                                        onSetCustomBodyColor(colorVal)
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isSel) "✓ $name" else name,
                                    color = if (colorVal == 0xFFFFFFFF || colorVal == 0xFFF1F5F9 || colorVal == 0xFFCBD5E1 || colorVal == 0xFFD4AF37) Color.Black else Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. INICIALIZAÇÃO & PREFERÊNCIAS ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "INICIALIZAÇÃO & PREFERÊNCIAS",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
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
                        text = "Sempre abrir com última rádio",
                        color = backlightTextPrimary,
                        fontSize = (10.5f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Text(
                        text = "Salva preferências e inicia tocando automaticamente",
                        color = backlightTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = fontFamily
                    )
                }
                Text(
                    text = if (uiState.autoPlayOnLaunch) "✓ ATIVO" else "DESATIVADO",
                    color = if (uiState.autoPlayOnLaunch) backlightHighlight else backlightTextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 6. SEGUNDO PLANO E BATERIA ---
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SEGUNDO PLANO E BATERIA",
                color = backlightTextSecondary,
                fontSize = (10f * fontScale).sp,
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
                        text = "Tocar em 2º Plano sem Interrupção",
                        color = backlightTextPrimary,
                        fontSize = (10.5f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fontFamily
                    )
                    Text(
                        text = if (isExempted) "✓ ATIVADO" else "CONFIGURAR",
                        color = if (isExempted) backlightHighlight else backlightTextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
                Text(
                    text = "Evita que o Android pause a rádio quando a tela estiver desligada, bloqueada ou economizando energia.",
                    color = backlightTextSecondary,
                    fontSize = 9.sp,
                    fontFamily = fontFamily
                )
            }
        }

        // --- 7. TIMER DE DESLIGAMENTO ---
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
                            else -> 0
                        }
                        onSetSleepTimer(next)
                    }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIMER DE DESLIGAMENTO",
                    color = backlightTextPrimary,
                    fontSize = (10.5f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = fontFamily
                )
                Text(
                    text = if (sleepTimerMinutes > 0) "$sleepTimerMinutes min" else "Desativado",
                    color = backlightHighlight,
                    fontSize = (10.5f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

@Composable
private fun IpodAboutScreen(
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33000000))
                    .border(1.5.dp, backlightHighlight.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = backlightHighlight,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "IPod Class + Radio",
                    color = backlightTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Versão 24 RC • Estabilidade Contínua de Streaming",
                    color = backlightHighlight,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        item {
            Text(
                text = "INFORMAÇÕES DO AUTOR",
                color = backlightTextSecondary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x28000000))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Autor:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Márcio Amaro",
                        color = backlightTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(backlightTextSecondary.copy(alpha = 0.3f))
                )

                Column {
                    Text(
                        text = "E-mail:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "marcio.amaro@gmail.com",
                        color = backlightHighlight,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(backlightTextSecondary.copy(alpha = 0.3f))
                )

                Column {
                    Text(
                        text = "Localização:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Araras - SP / Brasil",
                        color = backlightTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        item {
            Text(
                text = "ESPECIFICAÇÕES DO SISTEMA",
                color = backlightTextSecondary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x28000000))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Controle:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "SlideCircle Click Háptica",
                        color = backlightTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(backlightTextSecondary.copy(alpha = 0.3f))
                )

                Column {
                    Text(
                        text = "Decodificador:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "RDS / Radiotext (RT+)",
                        color = backlightTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(backlightTextSecondary.copy(alpha = 0.3f))
                )

                Column {
                    Text(
                        text = "Integração Veicular:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Modo Carro & Android Auto",
                        color = backlightTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(backlightTextSecondary.copy(alpha = 0.3f))
                )

                Column {
                    Text(
                        text = "Versão Atual:",
                        color = backlightTextSecondary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Versão 20 Beta",
                        color = backlightHighlight,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
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
    modifier: Modifier = Modifier
) {
    val displayedStations = remember(stations, searchQuery, selectedCountryCode, selectedGenreTag, selectedStateCode, selectedCity) {
        var list = stations
        if (selectedCountryCode != null && selectedCountryCode.isNotBlank() && !selectedCountryCode.equals("ALL", ignoreCase = true)) {
            list = list.filter { it.countryCode.equals(selectedCountryCode, ignoreCase = true) }
        }
        if (selectedGenreTag != null && selectedGenreTag.isNotBlank() && !selectedGenreTag.equals("ALL", ignoreCase = true)) {
            list = list.filter {
                it.tags.contains(selectedGenreTag, ignoreCase = true) ||
                it.primaryGenre.contains(selectedGenreTag, ignoreCase = true)
            }
        }
        if (selectedStateCode != null && selectedStateCode.isNotBlank() && !selectedStateCode.equals("ALL", ignoreCase = true)) {
            val st = selectedStateCode.trim().lowercase()
            list = list.filter {
                it.state.lowercase().contains(st) ||
                it.tags.lowercase().contains(st)
            }
        }
        if (selectedCity != null && selectedCity.isNotBlank() && !selectedCity.equals("ALL", ignoreCase = true)) {
            val c = selectedCity.trim().lowercase()
            list = list.filter {
                it.city.lowercase().contains(c) ||
                it.state.lowercase().contains(c) ||
                it.name.lowercase().contains(c) ||
                it.tags.lowercase().contains(c)
            }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                it.country.lowercase().contains(q) ||
                it.city.lowercase().contains(q) ||
                it.state.lowercase().contains(q) ||
                it.primaryGenre.lowercase().contains(q) ||
                it.tags.lowercase().contains(q)
            }
        }
        list
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("ipod_search_screen")
    ) {
        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("search_text_input"),
            placeholder = {
                Text(
                    text = "Buscar emissoras mundiais...",
                    fontSize = 11.sp,
                    color = backlightTextSecondary.copy(alpha = 0.7f)
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

        // Country Filter Chips (Including "Todos" - requirement 4)
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            val countries = listOf(
                "" to "Todos os Países",
                "BR" to "Brasil",
                "US" to "EUA",
                "PT" to "Portugal",
                "GB" to "Reino Unido",
                "DE" to "Alemanha",
                "IT" to "Itália",
                "ES" to "Espanha",
                "AR" to "Argentina",
                "FR" to "França",
                "JP" to "Japão"
            )
            items(countries.size) { idx ->
                val (code, label) = countries[idx]
                val isSelected = if (code.isEmpty()) selectedCountryCode.isNullOrEmpty() || selectedCountryCode == "ALL" else selectedCountryCode.equals(code, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) backlightHighlight else Color(0x22000000))
                        .clickable { onSearchCountryChange(code) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Genre Filter Chips (Including "Todos" - requirement 4)
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            val genres = listOf(
                "" to "Todos os Gêneros",
                "sertanejo" to "Sertanejo",
                "pop" to "Pop",
                "rock" to "Rock",
                "mpb" to "MPB",
                "gospel" to "Gospel",
                "dance" to "Dance / EDM",
                "news" to "Notícias",
                "pagode" to "Pagode",
                "jazz" to "Jazz",
                "classical" to "Clássica"
            )
            items(genres.size) { idx ->
                val (tag, label) = genres[idx]
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
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // State (UF) Filter Chips - Requirement: "antes do filtro de cidade aplicar filtro de estado"
        val isBrazil = selectedCountryCode.isNullOrEmpty() || selectedCountryCode.equals("BR", ignoreCase = true) || selectedCountryCode.equals("ALL", ignoreCase = true)
        if (isBrazil) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                items(CuratedData.BRAZILIAN_STATES.size) { idx ->
                    val (code, label) = CuratedData.BRAZILIAN_STATES[idx]
                    val isSelected = if (code.isEmpty()) selectedStateCode.isNullOrEmpty() || selectedStateCode == "ALL" else selectedStateCode.equals(code, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .clickable { onSearchStateChange(if (isSelected) "" else code) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // City Filter Chips (Dynamic based on selected state)
        val availableCities = remember(selectedCountryCode, selectedStateCode) {
            if (isBrazil) {
                CuratedData.getCitiesForState(selectedStateCode).filter { it != "Todas as Cidades" }
            } else {
                CuratedData.getCitiesForCountry(selectedCountryCode)
            }
        }
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            // First item: "Todas as Cidades"
            item {
                val isAllSelected = selectedCity.isNullOrEmpty() || selectedCity.equals("ALL", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAllSelected) backlightHighlight else Color(0x22000000))
                        .clickable { onSearchCityChange("") }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Todas as Cidades",
                        color = if (isAllSelected) Color.White else backlightTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            items(availableCities.size) { idx ->
                val cityName = availableCities[idx]
                val isSelected = selectedCity.equals(cityName, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) backlightHighlight else Color(0x22000000))
                        .clickable { onSearchCityChange(if (isSelected) "" else cityName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cityName,
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Stations Count Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RESULTADOS (${displayedStations.size})",
                color = backlightTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (!selectedCountryCode.isNullOrEmpty() || !selectedGenreTag.isNullOrEmpty()) {
                Text(
                    text = "FILTRADO",
                    color = backlightHighlight,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
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
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp
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
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nenhuma emissora localizada",
                        color = backlightTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Selecione 'Todos os Países' ou 'Todos os Gêneros'",
                        color = backlightTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)
            ) {
                itemsIndexed(displayedStations, key = { _, st -> st.id }) { index, station ->
                    val isSelected = index == selectedIndex
                    val isPlayingThis = station.id == currentStationId
                    val isFav = favorites.any { it.id == station.id }

                    com.example.ui.components.StationItemView(
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
    }
}

