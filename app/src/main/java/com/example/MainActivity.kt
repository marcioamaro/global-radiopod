package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ui.DisplayMode
import com.example.ui.RadioViewModel
import com.example.ui.screens.CarModeScreen
import com.example.ui.screens.IpodClassicScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF070B12)
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: RadioViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackStatus by viewModel.playbackStatus.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val rdsInfo by viewModel.rdsInfo.collectAsState()
    val visualizerAmplitudes by viewModel.visualizerAmplitudes.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val currentLocalAudio by viewModel.currentLocalAudio.collectAsState()
    val audioPositionMs by viewModel.audioPositionMs.collectAsState()
    val audioDurationMs by viewModel.audioDurationMs.collectAsState()
    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()

    val context = LocalContext.current

    // Request Notification & Media storage permissions every time the app opens or resumes
    val mediaPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.loadAudioFolders()
        viewModel.loadVideoFolders()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    viewModel.setUiActive(true)
                    val permissions = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                        permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    val needed = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (needed.isNotEmpty()) {
                        mediaPermissionsLauncher.launch(needed.toTypedArray())
                    } else {
                        viewModel.loadAudioFolders()
                        viewModel.loadVideoFolders()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    viewModel.setUiActive(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android device back button navigates up through the iPod menu hierarchy
    BackHandler(enabled = true) {
        viewModel.navigateBack()
    }

    val displayMode = uiState.displayMode
    LaunchedEffect(displayMode) {
        val activity = context as? android.app.Activity
        if (displayMode == DisplayMode.CAR_FULLSCREEN_RDS) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape && uiState.currentScreen == com.example.ui.IpodScreenDestination.VIDEO_PLAYER) {
        com.example.ui.screens.FullscreenLandscapeVideoPlayer(
            videoPlayerManager = viewModel.videoPlayerManager,
            onBack = { viewModel.navigateBack() },
            onNext = { viewModel.onNextTrackPress() },
            onPrev = { viewModel.onPrevTrackPress() }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .background(Color(0xFF070B12))
        ) {
            when (uiState.displayMode) {
                DisplayMode.IPOD_CLASSIC -> {
                    IpodClassicScreen(
                        uiState = uiState,
                        playbackStatus = playbackStatus,
                        currentStation = currentStation,
                        rdsInfo = rdsInfo,
                        visualizerAmplitudes = visualizerAmplitudes,
                        volume = volume,
                        favorites = favorites,
                        sleepTimerMinutes = sleepTimerMinutes,
                        onRotaryScroll = { steps -> viewModel.onRotaryScroll(steps) },
                        onCenterClick = { viewModel.onCenterButtonPress() },
                        onMenuClick = { viewModel.navigateBack() },
                        onPlayPauseClick = { viewModel.onPlayPausePress() },
                        onPrevClick = { viewModel.onPrevTrackPress() },
                        onNextClick = { viewModel.onNextTrackPress() },
                        onToggleHold = { viewModel.toggleHoldSwitch() },
                        onToggleDisplayMode = { viewModel.toggleDisplayMode() },
                        onSelectDestination = { dest -> viewModel.navigateTo(dest) },
                        onSelectStation = { station ->
                            viewModel.playStation(station)
                            viewModel.navigateTo(com.example.ui.IpodScreenDestination.NOW_PLAYING_RDS)
                        },
                        onToggleFavorite = { station -> viewModel.toggleFavorite(station) },
                        onDeleteFavorite = { id -> viewModel.deleteFavorite(id) },
                        onSearchQueryChange = { query -> viewModel.onSearchQueryChanged(query) },
                        onSearchCountryChange = { code -> viewModel.onSearchCountryChanged(code) },
                        onSearchGenreChange = { tag -> viewModel.onSearchGenreChanged(tag) },
                        onSearchStateChange = { state -> viewModel.onSearchStateChanged(state) },
                        onSearchCityChange = { city -> viewModel.onSearchCityChanged(city) },
                        onStepVolumeUp = { viewModel.adjustVolume(0.05f) },
                        onStepVolumeDown = { viewModel.adjustVolume(-0.05f) },
                        onPaddleMove = { pos -> viewModel.setPaddlePosition(pos) },
                        soundAndHaptics = (LocalContext.current.applicationContext as com.example.RadioApp).soundAndHaptics,
                        onSelectGenre = { genre -> viewModel.selectGenre(genre) },
                        onSelectCountry = { country -> viewModel.selectCountry(country) },
                        onSetChassisTheme = { theme -> viewModel.setChassisTheme(theme) },
                        onSetCustomBodyColor = { color -> viewModel.setCustomBodyColor(color) },
                        onSetBacklight = { backlight -> viewModel.setBacklight(backlight) },
                        onSetWheelPreset = { preset -> viewModel.setWheelPreset(preset) },
                        onSetCustomWheelColors = { wheel, text, center -> viewModel.setCustomWheelColors(wheel, text, center) },
                        onSetFontType = { fontType -> viewModel.setFontType(fontType) },
                        onSetFontSizeScale = { scale -> viewModel.setFontSizeScale(scale) },
                        onSetFontBold = { bold -> viewModel.setFontBold(bold) },
                        onSetAutoPlay = { autoPlay -> viewModel.setAutoPlayOnLaunch(autoPlay) },
                        onToggleSound = { viewModel.toggleSound() },
                        onToggleHaptics = { viewModel.toggleHaptics() },
                        onSetSleepTimer = { mins -> viewModel.setSleepTimer(mins) },
                        currentLocalAudio = currentLocalAudio,
                        audioPositionMs = audioPositionMs,
                        audioDurationMs = audioDurationMs,
                        videoPlayerManager = viewModel.videoPlayerManager,
                        onSelectAudioFolder = { folder ->
                            viewModel.selectAudioFolder(folder)
                            viewModel.navigateTo(com.example.ui.IpodScreenDestination.MP3_TRACKS_LIST)
                        },
                        onSelectAudioTrack = { track ->
                            viewModel.playLocalAudio(track, uiState.localAudioTracks)
                            viewModel.navigateTo(com.example.ui.IpodScreenDestination.MP3_NOW_PLAYING)
                        },
                        onSelectVideoFolder = { folder ->
                            viewModel.selectVideoFolder(folder)
                            viewModel.navigateTo(com.example.ui.IpodScreenDestination.VIDEO_LIST)
                        },
                        onSelectVideoTrack = { video ->
                            viewModel.playLocalVideo(video)
                            viewModel.navigateTo(com.example.ui.IpodScreenDestination.VIDEO_PLAYER)
                        },
                        onToggleVideoFullscreen = { viewModel.toggleVideoFullscreen() },
                        isEqualizerEnabled = isEqualizerEnabled,
                        onToggleEqualizerEnabled = { viewModel.setEqualizerEnabled(it) },
                        equalizerPreset = equalizerPreset,
                        onSelectEqualizerPreset = { viewModel.setEqualizerPreset(it) },
                        equalizerBands = equalizerBands,
                        onEqualizerBandLevelChange = { idx, lvl -> viewModel.setEqualizerBandLevel(idx, lvl) }
                    )
                }
            DisplayMode.CAR_FULLSCREEN_RDS -> {
                CarModeScreen(
                    currentStation = currentStation,
                    rdsInfo = rdsInfo,
                    playbackStatus = playbackStatus,
                    visualizerAmplitudes = visualizerAmplitudes,
                    volume = volume,
                    favorites = favorites,
                    recentsList = uiState.recentsList,
                    onTogglePlayPause = { viewModel.onPlayPausePress() },
                    onNextStation = { viewModel.onNextTrackPress() },
                    onPrevStation = { viewModel.onPrevTrackPress() },
                    onToggleFavorite = { station -> viewModel.toggleFavorite(station) },
                    onSelectStation = { station -> viewModel.playStation(station) },
                    onVolumeChange = { vol -> viewModel.setVolume(vol) },
                    onRotaryScroll = { delta -> viewModel.adjustVolume(delta * 0.03f) },
                    onToggleDisplayMode = { viewModel.toggleDisplayMode() },
                    carModeSource = uiState.carModeSource,
                    onToggleCarModeSource = { viewModel.toggleCarModeSource() },
                    backlight = uiState.backlight,
                    customBodyColor = uiState.customBodyColor,
                    customWheelColor = uiState.customWheelColor,
                    customWheelTextColor = uiState.customWheelTextColor,
                    customCenterButtonColor = uiState.customCenterButtonColor,
                    fontType = uiState.fontType,
                    fontScale = uiState.fontSizeScale.scale,
                    isBold = uiState.isFontBold,
                    currentLocalAudio = currentLocalAudio,
                    audioPositionMs = audioPositionMs,
                    audioDurationMs = audioDurationMs,
                    localAudioFolders = uiState.localAudioFolders,
                    localAudioTracks = uiState.localAudioTracks,
                    onSelectAudioFolder = { folder -> viewModel.selectAudioFolder(folder) },
                    onSelectAudioTrack = { track -> viewModel.playLocalAudio(track, uiState.localAudioTracks) }
                )
            }
        }
    }
}
}
