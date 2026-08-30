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
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.DisplayMode
import com.example.ui.RadioViewModel
import com.example.ui.screens.CarModeScreen
import com.example.ui.screens.DockModeScreen
import com.example.ui.screens.IpodChassisBackScreen
import com.example.ui.screens.IpodClassicScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
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

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val audioRouteManager = com.example.player.AudioRouteManager.getInstance(this)
        if (audioRouteManager.isCastingActive()) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    audioRouteManager.adjustVolumeDelta(0.05f)
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    audioRouteManager.adjustVolumeDelta(-0.05f)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
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
    val liveSessionDurationSeconds by viewModel.liveSessionDurationSeconds.collectAsState()
    val currentLocalAudio by viewModel.currentLocalAudio.collectAsState()
    val audioPositionMs by viewModel.audioPositionMs.collectAsState()
    val audioDurationMs by viewModel.audioDurationMs.collectAsState()
    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
    val equalizerPreset by viewModel.equalizerPreset.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
    val selectedAudioDevice by viewModel.selectedAudioDevice.collectAsState()

    val context = LocalContext.current

    // Easter Egg: Virar o celular com a tela para baixo mostra a traseira de aço inox do iPod
    var isChassisBackShowing by remember { mutableStateOf(false) }

    DisposableEffect(uiState.displayMode) {
        if (uiState.displayMode != DisplayMode.IPOD_CLASSIC) {
            isChassisBackShowing = false
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                // Eixo Z: quando virado com a tela para baixo z se torna negativo (ex: -9.8 m/s²)
                val z = event.values[2]
                if (z < -3.5f) {
                    if (!isChassisBackShowing) {
                        isChassisBackShowing = true
                    }
                } else if (z > -1.0f) {
                    if (isChassisBackShowing) {
                        isChassisBackShowing = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    BackHandler(enabled = isChassisBackShowing) {
        isChassisBackShowing = false
    }

    val chassisFlipAngle by animateFloatAsState(
        targetValue = if (isChassisBackShowing) 180f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "chassis_flip"
    )

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

    // Android device back button navigates up through the iPod menu hierarchy or exits Dock mode
    BackHandler(enabled = true) {
        if (uiState.displayMode == DisplayMode.DOCK_STANDBY) {
            viewModel.setDisplayMode(DisplayMode.IPOD_CLASSIC)
        } else {
            viewModel.navigateBack()
        }
    }

    val displayMode = uiState.displayMode
    val currentScreen = uiState.currentScreen
    LaunchedEffect(displayMode, currentScreen) {
        val activity = context as? android.app.Activity
        if (displayMode == DisplayMode.CAR_FULLSCREEN_RDS) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (displayMode == DisplayMode.DOCK_STANDBY) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else if (currentScreen == com.example.ui.IpodScreenDestination.VIDEO_PLAYER ||
                   currentScreen == com.example.ui.IpodScreenDestination.YOUTUBE_PLAYER) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
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
    } else if (isLandscape && uiState.currentScreen == com.example.ui.IpodScreenDestination.YOUTUBE_PLAYER && uiState.currentYouTubeVideo != null) {
        com.example.ui.screens.FullscreenLandscapeYouTubePlayer(
            video = uiState.currentYouTubeVideo!!,
            onBack = { viewModel.navigateBack() },
            initialStartSeconds = viewModel.youTubePlaybackPositionSeconds,
            onTimeUpdate = { viewModel.updateYouTubePlaybackPosition(it) }
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = chassisFlipAngle
                                cameraDistance = 16f * density
                            }
                    ) {
                        if (chassisFlipAngle <= 90f) {
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
                                onEqualizerBandLevelChange = { idx, lvl -> viewModel.setEqualizerBandLevel(idx, lvl) },
                                availableAudioDevices = availableAudioDevices,
                                selectedAudioDevice = selectedAudioDevice,
                                onSelectAudioDevice = { dev -> viewModel.selectAudioDevice(dev) },
                                onOpenNativeAudioChooser = { viewModel.showNativeAudioChooserDialog(context) },
                                viewModel = viewModel,
                                onShowChassisBack = { isChassisBackShowing = true }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = 180f
                                    }
                            ) {
                                IpodChassisBackScreen(
                                    onFlipBack = { isChassisBackShowing = false }
                                )
                            }
                        }
                    }
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
                    onSelectAudioTrack = { track -> viewModel.playLocalAudio(track, uiState.localAudioTracks) },
                    liveSessionDurationSeconds = liveSessionDurationSeconds
                )
            }
            DisplayMode.DOCK_STANDBY -> {
                val currentPodcastEp = viewModel.currentPodcastEpisode.collectAsState(initial = null).value
                DockModeScreen(
                    currentStation = currentStation,
                    currentLocalAudio = currentLocalAudio,
                    currentPodcastEpisode = currentPodcastEp,
                    rdsInfo = rdsInfo,
                    playbackStatus = playbackStatus,
                    audioPositionMs = audioPositionMs,
                    audioDurationMs = audioDurationMs,
                    colorTheme = uiState.dockColorTheme,
                    dockClockScale = uiState.dockClockScale,
                    dockShowSeconds = uiState.dockShowSeconds,
                    onCycleColorTheme = { viewModel.cycleDockColorTheme() },
                    onCycleClockScale = { viewModel.cycleDockClockScale() },
                    onTogglePlayPause = { viewModel.onPlayPausePress() },
                    onNextTrack = { viewModel.onNextTrackPress() },
                    onOpenAudioOutput = { viewModel.showNativeAudioChooserDialog(context) },
                    onExitDockMode = { viewModel.setDisplayMode(DisplayMode.IPOD_CLASSIC) }
                )
            }
        }
    }
}
}
