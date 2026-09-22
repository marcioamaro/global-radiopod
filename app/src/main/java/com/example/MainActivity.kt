package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    private var volumeObserver: android.database.ContentObserver? = null

    private fun registerVolumeObserver() {
        if (volumeObserver == null) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            volumeObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val vm = com.example.audio.VolumeManager.getInstance(applicationContext)
                    vm.setSystemVolume(vm.getSystemVolume())
                }
            }
            try {
                contentResolver.registerContentObserver(
                    android.provider.Settings.System.CONTENT_URI,
                    true,
                    volumeObserver!!
                )
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        registerVolumeObserver()

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

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val volumeManager = com.example.audio.VolumeManager.getInstance(this)
            val coordinator = (applicationContext as? com.example.RadioApp)?.playbackCoordinator
            if (volumeManager.activeRoute.value == com.example.audio.VolumeManager.AudioRoute.CAST) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    val delta = if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) 0.05f else -0.05f
                    val current = volumeManager.getActiveVolume()
                    coordinator?.setActiveVolume((current + delta).coerceIn(0f, 1f))
                }
                return true
            }
            val handled = super.dispatchKeyEvent(event)
            viewModel.syncVolumeFromSystem()
            return handled
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncVolumeFromSystem()
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (_: Exception) {}
            volumeObserver = null
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
    val sleepTimerSecondsRemaining by viewModel.sleepTimerSecondsRemaining.collectAsState()
    val brickGameCenterAction by viewModel.brickGameCenterAction.collectAsState()
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

    val onboardingRepo = remember { com.example.data.prefs.OnboardingPreferencesRepository.getInstance(context) }
    val onboardingConfig by onboardingRepo.onboardingConfig.collectAsState(initial = null)

    if (onboardingConfig == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B12))
        )
        return
    }

    val currentLanguageTag = onboardingConfig?.languageTag?.takeIf { it.isNotBlank() }
        ?: com.example.util.AppLocaleManager.getCurrentLanguageTag()

    val rawConfig = androidx.compose.ui.platform.LocalConfiguration.current

    val localizedConfig = remember(rawConfig, currentLanguageTag) {
        val locale = if (currentLanguageTag.contains("-")) {
            val parts = currentLanguageTag.split("-")
            java.util.Locale(parts[0], parts[1])
        } else {
            java.util.Locale(currentLanguageTag)
        }
        android.content.res.Configuration(rawConfig).apply {
            setLocale(locale)
        }
    }

    val localizedContext = remember(currentLanguageTag, context, rawConfig) {
        context.createConfigurationContext(localizedConfig)
    }

    val activityResultOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
        ?: (context as? androidx.activity.result.ActivityResultRegistryOwner)

    val providers = remember(localizedContext, localizedConfig, activityResultOwner) {
        listOfNotNull(
            androidx.compose.ui.platform.LocalContext provides localizedContext,
            androidx.compose.ui.platform.LocalConfiguration provides localizedConfig,
            activityResultOwner?.let { androidx.activity.compose.LocalActivityResultRegistryOwner provides it }
        ).toTypedArray()
    }

    androidx.compose.runtime.CompositionLocalProvider(*providers) {
        if (!onboardingConfig!!.isOnboardingCompleted) {
            val app = context.applicationContext as android.app.Application
            val onboardingViewModel: com.example.ui.onboarding.OnboardingWizardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.ui.onboarding.OnboardingWizardViewModel.provideFactory(app)
            )
            com.example.ui.onboarding.OnboardingWizardScreen(
                viewModel = onboardingViewModel,
                onFinish = {
                    viewModel.reloadPreferencesFromStorage()
                }
            )
            return@CompositionLocalProvider
        }

    // Easter Egg: Virar o celular com a tela para baixo mostra a traseira de aço inox do iPod
    var isChassisBackShowing by remember { mutableStateOf(false) }
    val isChassisAnimEnabled by viewModel.isChassisBackAnimationEnabled.collectAsState()

    // Sensor de Gravidade (flip para mostrar traseira) + Shake Detector (reset do easter egg)
    DisposableEffect(uiState.displayMode) {
        if (uiState.displayMode != DisplayMode.IPOD_CLASSIC) {
            isChassisBackShowing = false
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Variáveis para detecção de shake
        var lastShakeTime = 0L
        var lastAccelX = 0f
        var lastAccelY = 0f
        var lastAccelZ = 0f
        var isFirstAccelReading = true
        val shakeThreshold = 28f // m/s² — sacudida forte
        val shakeCooldownMs = 3000L // 3s entre shakes

        val gravityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                // Eixo Z: quando virado com a tela para baixo z se torna negativo (ex: -9.8 m/s²)
                val z = event.values[2]
                if (z < -3.5f) {
                    // Só ativa o flip se a animação estiver habilitada
                    if (!isChassisBackShowing && isChassisAnimEnabled) {
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

        val shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                if (isFirstAccelReading) {
                    lastAccelX = x; lastAccelY = y; lastAccelZ = z
                    isFirstAccelReading = false
                    return
                }

                val deltaX = x - lastAccelX
                val deltaY = y - lastAccelY
                val deltaZ = z - lastAccelZ
                lastAccelX = x; lastAccelY = y; lastAccelZ = z

                val acceleration = kotlin.math.sqrt(
                    (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()
                ).toFloat()

                val now = System.currentTimeMillis()
                if (acceleration > shakeThreshold && (now - lastShakeTime) > shakeCooldownMs) {
                    lastShakeTime = now
                    // Shake detectado! Reabilita o easter egg
                    if (!isChassisAnimEnabled) {
                        viewModel.setChassisBackAnimationEnabled(true)
                        Toast.makeText(
                            context,
                            "Easter Egg reativado! 🎉",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(gravityListener, gravitySensor, SensorManager.SENSOR_DELAY_UI)
        if (accelSensor != null) {
            sensorManager?.registerListener(shakeListener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(gravityListener)
            sensorManager?.unregisterListener(shakeListener)
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

    // Calcula as cores do tema LCD atual para passar aos players fullscreen
    val backlightHighlight = remember(uiState.backlight) { Color(uiState.backlight.highlight) }
    val backlightTextPrimary = remember(uiState.backlight) { Color(uiState.backlight.textPrimary) }

    // WebView compartilhado para o player YouTube — criado uma vez e reutilizado ao girar o celular,
    // evitando que o vídeo reinicie ao trocar de portrait para landscape e vice-versa.
    val isYouTubePlayerActive = uiState.currentScreen == com.example.ui.IpodScreenDestination.YOUTUBE_PLAYER &&
            uiState.currentYouTubeVideo != null
    val sharedYouTubeWebView = remember { mutableStateOf<WebView?>(null) }

    // Cria o WebView compartilhado quando entra no player YouTube; destrói ao sair
    DisposableEffect(isYouTubePlayerActive, uiState.currentYouTubeVideo?.id) {
        if (isYouTubePlayerActive && uiState.currentYouTubeVideo != null) {
            val video = uiState.currentYouTubeVideo!!
            val startSeconds = viewModel.youTubePlaybackPositionSeconds
            val wv = WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = userAgentString.replace("; wv", "")
                }
                addJavascriptInterface(
                    com.example.ui.screens.YouTubeJsBridge { s -> viewModel.updateYouTubePlaybackPosition(s) },
                    "AndroidBridge"
                )
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        return !(url.startsWith("http://") || url.startsWith("https://"))
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            (function() {
                                function autoPlayVideo() {
                                    var v = document.querySelector('video');
                                    if (v) {
                                        if (v.paused) { v.play().catch(function(e){}); }
                                    } else {
                                        setTimeout(autoPlayVideo, 400);
                                    }
                                }
                                autoPlayVideo();
                                if (!window._ipodTimeInterval) {
                                    window._ipodTimeInterval = setInterval(function() {
                                        try {
                                            var v = document.querySelector('video');
                                            if (v && !v.paused && window.AndroidBridge) {
                                                window.AndroidBridge.onTimeUpdate(Math.floor(v.currentTime));
                                            }
                                        } catch(e) {}
                                    }, 1000);
                                }
                            })();
                            """.trimIndent(), null
                        )
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(com.example.ui.screens.buildYouTubeWatchUrl(video.id, startSeconds))
            }
            sharedYouTubeWebView.value = wv
        }
        onDispose {
            if (!isYouTubePlayerActive) {
                sharedYouTubeWebView.value?.let { wv ->
                    (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
                sharedYouTubeWebView.value = null
            }
        }
    }

    val isFullscreenVideo = isLandscape && (
        uiState.currentScreen == com.example.ui.IpodScreenDestination.VIDEO_PLAYER ||
        (uiState.currentScreen == com.example.ui.IpodScreenDestination.YOUTUBE_PLAYER && uiState.currentYouTubeVideo != null)
    )

    val composeView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(isFullscreenVideo) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, composeView)
            if (isFullscreenVideo) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, composeView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (isLandscape && uiState.currentScreen == com.example.ui.IpodScreenDestination.VIDEO_PLAYER) {
        com.example.ui.screens.FullscreenLandscapeVideoPlayer(
            videoPlayerManager = viewModel.videoPlayerManager,
            onBack = {
                val activity = context as? android.app.Activity
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity?.window?.decorView?.postDelayed({
                    if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    }
                }, 1000L)
            },
            onNext = { viewModel.onNextTrackPress() },
            onPrev = { viewModel.onPrevTrackPress() },
            backlightHighlight = backlightHighlight,
            backlightTextPrimary = backlightTextPrimary
        )
    } else if (isLandscape && uiState.currentScreen == com.example.ui.IpodScreenDestination.YOUTUBE_PLAYER && uiState.currentYouTubeVideo != null) {
        com.example.ui.screens.FullscreenLandscapeYouTubePlayer(
            video = uiState.currentYouTubeVideo!!,
            onBack = {
                val activity = context as? android.app.Activity
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity?.window?.decorView?.postDelayed({
                    if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    }
                }, 1000L)
            },
            initialStartSeconds = viewModel.youTubePlaybackPositionSeconds,
            onTimeUpdate = { viewModel.updateYouTubePlaybackPosition(it) },
            sharedWebView = sharedYouTubeWebView.value
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
                                sleepTimerSecondsRemaining = sleepTimerSecondsRemaining,
                                brickGameCenterAction = brickGameCenterAction,
                                onRotaryScroll = { steps -> viewModel.onRotaryScroll(steps) },
                                onCenterClick = { viewModel.onCenterButtonPress() },
                                onMenuClick = { viewModel.navigateBack() },
                                onPlayPauseClick = {
                                    if (uiState.currentScreen == com.example.ui.IpodScreenDestination.YOUTUBE_PLAYER) {
                                        sharedYouTubeWebView.value?.evaluateJavascript(
                                            "var v = document.querySelector('video'); " +
                                            "if (v) { " +
                                            "  if (v.paused) { v.play(); } else { v.pause(); } " +
                                            "} else if (typeof player !== 'undefined' && player && typeof player.getPlayerState === 'function') { " +
                                            "  var s = player.getPlayerState(); " +
                                            "  if (s === 1) { player.pauseVideo(); } else { player.playVideo(); } " +
                                            "}", null
                                        )
                                    } else {
                                        viewModel.onPlayPausePress()
                                    }
                                },
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
                                onToggleVideoFullscreen = {
                                    viewModel.toggleVideoFullscreen()
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                },
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
                                onShowChassisBack = {
                                    if (viewModel.isChassisBackAnimationEnabled.value) {
                                        isChassisBackShowing = true
                                    }
                                },
                                sharedYouTubeWebView = sharedYouTubeWebView.value
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
                                    onFlipBack = { isChassisBackShowing = false },
                                    isAnimationEnabled = isChassisAnimEnabled,
                                    onToggleAnimationEnabled = { enabled ->
                                        viewModel.setChassisBackAnimationEnabled(enabled)
                                    }
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
}
