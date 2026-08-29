package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RadioApp
import com.example.data.model.RadioStation
import com.example.data.preferences.IpodFontSizeScale
import com.example.data.preferences.IpodFontType
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.preferences.IpodWheelPreset
import com.example.data.repository.CountryCategory
import com.example.data.repository.CuratedData
import com.example.data.repository.GenreCategory
import com.example.data.repository.RadioRepository
import com.example.player.RadioPlaybackStatus
import com.example.player.RadioPlayerManager
import com.example.player.RdsInfo
import com.example.util.IpodSoundAndHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class IpodScreenDestination {
    MAIN_MENU,
    RADIO_MENU,
    NOW_PLAYING_RDS,
    FAVORITES,
    RECENTS,
    TOP_WORLD,
    GENRES_LIST,
    STATIONS_BY_GENRE,
    COUNTRIES_LIST,
    STATIONS_BY_COUNTRY,
    SEARCH,
    MP3_FOLDERS,
    MP3_TRACKS_LIST,
    MP3_NOW_PLAYING,
    VIDEO_FOLDERS,
    VIDEO_LIST,
    VIDEO_PLAYER,
    EQUALIZER,
    GAME_BRICK,
    SETTINGS_THEMES,
    ABOUT
}

enum class DisplayMode {
    IPOD_CLASSIC,
    CAR_FULLSCREEN_RDS
}

enum class CarModeSource {
    RADIO,
    MP3
}

enum class IpodChassisTheme(val displayName: String, val bodyColor: Long, val wheelColor: Long, val accentColor: Long) {
    CLASSIC_SILVER("Classic Silver (Flat White)", 0xFFF1F5F9, 0xFFE2E4E8, 0xFF0284C7),
    SPACE_GRAY("Space Gray", 0xFF1E293B, 0xFF334155, 0xFF38BDF8),
    STEALTH_BLACK("Stealth Black", 0xFF0F172A, 0xFF1E293B, 0xFF00E5FF),
    U2_SPECIAL("U2 Edition (Red/Black)", 0xFF111111, 0xFFDC2626, 0xFFEF4444),
    RETRO_GOLD("Retro Champagne", 0xFFD4AF37, 0xFFFAF5E4, 0xFFB45309),
    ICE_BLUE("iClassic Mini Blue", 0xFF0284C7, 0xFFE0F2FE, 0xFF0369A1)
}

enum class LcdBacklight(val displayName: String, val background: Long, val textPrimary: Long, val textSecondary: Long, val highlight: Long) {
    RETRO_IPOD_LCD("iClassic Mono LCD (Original)", 0xFF8E9E76, 0xFF142010, 0xFF384A2C, 0xFF142010),
    CLASSIC_BLUE("Classic Blue LCD", 0xFF0A2239, 0xFFE0F2FE, 0xFF7DD3FC, 0xFF0284C7),
    VINTAGE_AMBER("Vintage Amber RDS", 0xFF2A1B0A, 0xFFFEF3C7, 0xFFFBBF24, 0xFFD97706),
    MONOCHROME_GREY("Monochrome LCD", 0xFF8A9A86, 0xFF142411, 0xFF2D4629, 0xFF4A6B44),
    OLED_MATRIX("OLED Matrix Dark", 0xFF050505, 0xFF00E5FF, 0xFF0284C7, 0xFF007799),
    EMERALD_GREEN("Retro Emerald", 0xFF062817, 0xFF86EFAC, 0xFF4ADE80, 0xFF16A34A)
}

data class UiState(
    val currentScreen: IpodScreenDestination = IpodScreenDestination.MAIN_MENU,
    val displayMode: DisplayMode = DisplayMode.IPOD_CLASSIC,
    val carModeSource: CarModeSource = CarModeSource.RADIO,
    val chassisTheme: IpodChassisTheme = IpodChassisTheme.CLASSIC_SILVER,
    val customBodyColor: Long = 0xFFF1F5F9,
    val backlight: LcdBacklight = LcdBacklight.RETRO_IPOD_LCD,
    val wheelPreset: IpodWheelPreset = IpodWheelPreset.CLASSIC_GREY,
    val customWheelColor: Long = 0xFFE2E4E8,
    val customWheelTextColor: Long = 0xFF475569,
    val customCenterButtonColor: Long = 0xFFFFFFFF,
    val fontType: IpodFontType = IpodFontType.MONOSPACE,
    val fontSizeScale: IpodFontSizeScale = IpodFontSizeScale.SCALE_150,
    val isFontBold: Boolean = true,
    val autoPlayOnLaunch: Boolean = true,
    val selectedIndex: Int = 0,
    val isHoldLocked: Boolean = false,
    val isLoadingList: Boolean = false,
    val searchQuery: String = "",
    val searchCountryCode: String = "ALL",
    val searchGenreTag: String = "ALL",
    val searchStateCode: String = "ALL",
    val searchCity: String = "ALL",
    val activeGenre: GenreCategory? = null,
    val activeCountry: CountryCategory? = null,
    val stationsList: List<RadioStation> = emptyList(),
    val recentsList: List<RadioStation> = emptyList(),
    val activeCategoryName: String = "Top Mundial",
    val isSearchActive: Boolean = false,
    val gamePaddlePosition: Float = 0.5f,
    // MP3 Player Local Media
    val localAudioFolders: List<com.example.data.model.MediaFolder> = emptyList(),
    val localAudioTracks: List<com.example.data.model.LocalAudioTrack> = emptyList(),
    val currentAudioFolder: com.example.data.model.MediaFolder? = null,
    // Video Player Local Media
    val localVideoFolders: List<com.example.data.model.MediaFolder> = emptyList(),
    val localVideoTracks: List<com.example.data.model.LocalVideoTrack> = emptyList(),
    val currentVideoFolder: com.example.data.model.MediaFolder? = null,
    val isVideoFullscreen: Boolean = false
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val radioApp = application as RadioApp
    private val repository: RadioRepository = radioApp.repository
    private val playerManager: RadioPlayerManager = radioApp.playerManager
    val soundAndHaptics: IpodSoundAndHaptics = radioApp.soundAndHaptics
    private val prefs: IpodPreferencesManager = IpodPreferencesManager.getInstance(application)
    private val localMediaRepo: com.example.data.repository.LocalMediaRepository = radioApp.localMediaRepository
    val videoPlayerManager: com.example.player.LocalVideoPlayerManager = com.example.player.LocalVideoPlayerManager.getInstance(application)

    val currentLocalAudio = playerManager.currentLocalAudio
    val audioPositionMs = playerManager.audioPositionMs
    val audioDurationMs = playerManager.audioDurationMs

    // Global Equalizer
    val isEqualizerEnabled = playerManager.isEqualizerEnabled
    val equalizerPreset = playerManager.equalizerPreset
    val equalizerBands = playerManager.equalizerBands

    fun setEqualizerEnabled(enabled: Boolean) {
        playerManager.setEqualizerEnabled(enabled)
        soundAndHaptics.performClickHaptic()
    }

    fun setEqualizerPreset(preset: String) {
        playerManager.setEqualizerPreset(preset)
        soundAndHaptics.performClickHaptic()
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelDb: Float) {
        playerManager.setEqualizerBandLevel(bandIndex, levelDb)
    }

    val favorites: StateFlow<List<RadioStation>> = repository.favoritesFlow
        .map { list -> list.sortedBy { it.name.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackStatus: StateFlow<RadioPlaybackStatus> = playerManager.playbackStatus
    val currentStation: StateFlow<RadioStation?> = playerManager.currentStation
    val rdsInfo: StateFlow<RdsInfo> = playerManager.rdsInfo
    val visualizerAmplitudes: StateFlow<List<Float>> = playerManager.visualizerAmplitudes
    val volume: StateFlow<Float> = playerManager.volume
    val sleepTimerMinutes: StateFlow<Int> = playerManager.sleepTimerMinutes
    val errorMessage: StateFlow<String?> = playerManager.errorMessage

    private val _uiState = MutableStateFlow(
        UiState(
            chassisTheme = prefs.chassisTheme,
            customBodyColor = prefs.customBodyColor,
            backlight = prefs.lcdBacklight,
            wheelPreset = prefs.wheelPreset,
            customWheelColor = prefs.customWheelColor,
            customWheelTextColor = prefs.customWheelTextColor,
            customCenterButtonColor = prefs.customCenterButtonColor,
            fontType = prefs.fontType,
            fontSizeScale = prefs.fontSizeScale,
            isFontBold = prefs.isFontBold,
            autoPlayOnLaunch = prefs.isAutoPlayOnLaunch,
            recentsList = prefs.getRecentStations()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Holds the playback queue corresponding to the last list user played from
    private var playbackQueue: List<RadioStation> = emptyList()

    private val navigationHistory = ArrayDeque<IpodScreenDestination>()

    init {
        // Restore sound and haptics preferences
        soundAndHaptics.isSoundEnabled = prefs.isSoundEnabled
        soundAndHaptics.isHapticsEnabled = prefs.isHapticsEnabled
        playerManager.setVolumeLevel(prefs.volumeLevel)

        // Wire folder-spanning navigation callbacks
        playerManager.onFolderWrapNext = { nextLocalTrackWithFolderWrap() }
        playerManager.onFolderWrapPrev = { prevLocalTrackWithFolderWrap() }

        // Load stations
        loadTopStations()

        // Continuously refresh recents whenever any station plays (from UI, AA, lockscreen, etc.)
        viewModelScope.launch {
            playerManager.currentStation.collect { station ->
                if (station != null) {
                    _uiState.value = _uiState.value.copy(
                        recentsList = prefs.getRecentStations()
                    )
                }
            }
        }

        // Always auto-play on launch: either last played station or top curated station!
        val lastStation = prefs.getLastPlayedStation() ?: CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull()
        if (lastStation != null) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(400) // slight buffer for service initialization
                playStation(lastStation)
            }
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        soundAndHaptics.performHeavyHaptic()
    }

    fun toggleDisplayMode() {
        val nextMode = if (_uiState.value.displayMode == DisplayMode.IPOD_CLASSIC) {
            DisplayMode.CAR_FULLSCREEN_RDS
        } else {
            DisplayMode.IPOD_CLASSIC
        }
        setDisplayMode(nextMode)
    }

    fun toggleHoldSwitch() {
        val newHold = !_uiState.value.isHoldLocked
        _uiState.value = _uiState.value.copy(isHoldLocked = newHold)
        soundAndHaptics.performHeavyHaptic()
    }

    fun setUiActive(active: Boolean) {
        playerManager.setUiActive(active)
    }

    fun setChassisTheme(theme: IpodChassisTheme) {
        _uiState.value = _uiState.value.copy(
            chassisTheme = theme,
            customBodyColor = theme.bodyColor
        )
        prefs.chassisTheme = theme
        prefs.customBodyColor = theme.bodyColor
        soundAndHaptics.performClickHaptic()
    }

    fun setCustomBodyColor(color: Long) {
        _uiState.value = _uiState.value.copy(customBodyColor = color)
        prefs.customBodyColor = color
        soundAndHaptics.performClickHaptic()
    }

    fun setBacklight(backlight: LcdBacklight) {
        _uiState.value = _uiState.value.copy(backlight = backlight)
        prefs.lcdBacklight = backlight
        soundAndHaptics.performClickHaptic()
    }

    fun setWheelPreset(preset: IpodWheelPreset) {
        _uiState.value = _uiState.value.copy(
            wheelPreset = preset,
            customWheelColor = preset.wheelColor,
            customWheelTextColor = preset.textColor,
            customCenterButtonColor = preset.centerButtonColor
        )
        prefs.wheelPreset = preset
        prefs.customWheelColor = preset.wheelColor
        prefs.customWheelTextColor = preset.textColor
        prefs.customCenterButtonColor = preset.centerButtonColor
        soundAndHaptics.performClickHaptic()
    }

    fun setCustomWheelColors(wheel: Long, text: Long, center: Long) {
        _uiState.value = _uiState.value.copy(
            wheelPreset = IpodWheelPreset.CUSTOM,
            customWheelColor = wheel,
            customWheelTextColor = text,
            customCenterButtonColor = center
        )
        prefs.wheelPreset = IpodWheelPreset.CUSTOM
        prefs.customWheelColor = wheel
        prefs.customWheelTextColor = text
        prefs.customCenterButtonColor = center
        soundAndHaptics.performClickHaptic()
    }

    fun setFontType(fontType: IpodFontType) {
        _uiState.value = _uiState.value.copy(fontType = fontType)
        prefs.fontType = fontType
        soundAndHaptics.performClickHaptic()
    }

    fun setFontSizeScale(scale: IpodFontSizeScale) {
        _uiState.value = _uiState.value.copy(fontSizeScale = scale)
        prefs.fontSizeScale = scale
        soundAndHaptics.performClickHaptic()
    }

    fun setFontBold(isBold: Boolean) {
        _uiState.value = _uiState.value.copy(isFontBold = isBold)
        prefs.isFontBold = isBold
        soundAndHaptics.performClickHaptic()
    }

    fun toggleFontBold() {
        val next = !_uiState.value.isFontBold
        setFontBold(next)
    }

    fun setAutoPlayOnLaunch(autoPlay: Boolean) {
        _uiState.value = _uiState.value.copy(autoPlayOnLaunch = autoPlay)
        prefs.isAutoPlayOnLaunch = autoPlay
        soundAndHaptics.performClickHaptic()
    }

    fun cycleWheelPreset() {
        val presets = IpodWheelPreset.values()
        val next = (uiState.value.wheelPreset.ordinal + 1) % presets.size
        setWheelPreset(presets[next])
    }

    fun cycleFontType() {
        val types = IpodFontType.values()
        val next = (uiState.value.fontType.ordinal + 1) % types.size
        setFontType(types[next])
    }

    fun cycleFontSizeScale() {
        val scales = IpodFontSizeScale.values()
        val next = (uiState.value.fontSizeScale.ordinal + 1) % scales.size
        setFontSizeScale(scales[next])
    }

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
        soundAndHaptics.performHeavyHaptic()
    }

    fun navigateTo(screen: IpodScreenDestination) {
        if (_uiState.value.isHoldLocked) return
        if (screen != _uiState.value.currentScreen) {
            navigationHistory.addLast(_uiState.value.currentScreen)
            _uiState.value = _uiState.value.copy(
                currentScreen = screen,
                selectedIndex = 0
            )
            soundAndHaptics.performClickHaptic()
        }
    }

    fun navigateBack() {
        if (_uiState.value.isHoldLocked) return
        if (navigationHistory.isNotEmpty()) {
            val prev = navigationHistory.removeLast()
            _uiState.value = _uiState.value.copy(
                currentScreen = prev,
                selectedIndex = 0
            )
            soundAndHaptics.performClickHaptic()
        } else if (_uiState.value.currentScreen != IpodScreenDestination.MAIN_MENU) {
            _uiState.value = _uiState.value.copy(
                currentScreen = IpodScreenDestination.MAIN_MENU,
                selectedIndex = 0
            )
            soundAndHaptics.performClickHaptic()
        }
    }

    fun onRotaryScroll(stepDelta: Int) {
        if (_uiState.value.isHoldLocked) return
        if (stepDelta == 0) return

        // In NOW PLAYING screen (Radio or MP3), rotating the Click Wheel changes the volume!
        if (_uiState.value.currentScreen == IpodScreenDestination.NOW_PLAYING_RDS ||
            _uiState.value.currentScreen == IpodScreenDestination.MP3_NOW_PLAYING) {
            adjustVolume(stepDelta * 0.04f)
            soundAndHaptics.performClickHaptic()
            return
        }

        // In VIDEO PLAYER screen, rotating the Click Wheel seeks forward or backward
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER) {
            if (stepDelta > 0) videoPlayerManager.forward10s() else videoPlayerManager.rewind10s()
            soundAndHaptics.performClickHaptic()
            return
        }

        // In BRICK GAME screen, rotating the Click Wheel moves the paddle raquete!
        if (_uiState.value.currentScreen == IpodScreenDestination.GAME_BRICK) {
            val delta = stepDelta * 0.05f
            val newPos = (_uiState.value.gamePaddlePosition + delta).coerceIn(0.12f, 0.88f)
            _uiState.value = _uiState.value.copy(gamePaddlePosition = newPos)
            soundAndHaptics.performClickHaptic()
            return
        }

        val count = getItemCountForCurrentScreen()
        if (count <= 0) return

        val current = _uiState.value.selectedIndex
        val next = (current + stepDelta).coerceIn(0, (count - 1).coerceAtLeast(0))
        if (next != current) {
            _uiState.value = _uiState.value.copy(selectedIndex = next)
            soundAndHaptics.performClickHaptic()
        }
    }

    fun setPaddlePosition(position: Float) {
        _uiState.value = _uiState.value.copy(gamePaddlePosition = position.coerceIn(0.12f, 0.88f))
    }

    fun onCenterButtonPress() {
        if (_uiState.value.isHoldLocked) return
        val currentScreen = _uiState.value.currentScreen
        val index = _uiState.value.selectedIndex

        soundAndHaptics.performHeavyHaptic()

        when (currentScreen) {
            IpodScreenDestination.MAIN_MENU -> {
                when (index) {
                    0 -> navigateTo(IpodScreenDestination.RADIO_MENU)
                    1 -> {
                        loadAudioFolders()
                        navigateTo(IpodScreenDestination.MP3_FOLDERS)
                    }
                    2 -> {
                        loadVideoFolders()
                        navigateTo(IpodScreenDestination.VIDEO_FOLDERS)
                    }
                    3 -> navigateTo(IpodScreenDestination.EQUALIZER)
                    4 -> navigateTo(IpodScreenDestination.GAME_BRICK)
                    5 -> toggleDisplayMode()
                    6 -> navigateTo(IpodScreenDestination.SETTINGS_THEMES)
                    7 -> navigateTo(IpodScreenDestination.ABOUT)
                }
            }
            IpodScreenDestination.RADIO_MENU -> {
                when (index) {
                    0 -> navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                    1 -> navigateTo(IpodScreenDestination.FAVORITES)
                    2 -> {
                        loadRecents()
                        navigateTo(IpodScreenDestination.RECENTS)
                    }
                    3 -> {
                        loadTopStations()
                        navigateTo(IpodScreenDestination.TOP_WORLD)
                    }
                    4 -> navigateTo(IpodScreenDestination.GENRES_LIST)
                    5 -> navigateTo(IpodScreenDestination.COUNTRIES_LIST)
                    6 -> {
                        executeSearch()
                        navigateTo(IpodScreenDestination.SEARCH)
                    }
                }
            }
            IpodScreenDestination.MP3_FOLDERS -> {
                if (index == 0) {
                    loadAllAudioTracks()
                    navigateTo(IpodScreenDestination.MP3_TRACKS_LIST)
                } else {
                    val folderIndex = index - 1
                    val folders = _uiState.value.localAudioFolders
                    if (folderIndex in folders.indices) {
                        selectAudioFolder(folders[folderIndex])
                        navigateTo(IpodScreenDestination.MP3_TRACKS_LIST)
                    }
                }
            }
            IpodScreenDestination.MP3_TRACKS_LIST -> {
                val tracks = _uiState.value.localAudioTracks
                if (index in tracks.indices) {
                    playLocalAudio(tracks[index], tracks)
                    navigateTo(IpodScreenDestination.MP3_NOW_PLAYING)
                }
            }
            IpodScreenDestination.MP3_NOW_PLAYING -> {
                playerManager.togglePlayPause()
            }
            IpodScreenDestination.VIDEO_FOLDERS -> {
                if (index == 0) {
                    loadAllVideoTracks()
                    navigateTo(IpodScreenDestination.VIDEO_LIST)
                } else {
                    val folderIndex = index - 1
                    val folders = _uiState.value.localVideoFolders
                    if (folderIndex in folders.indices) {
                        selectVideoFolder(folders[folderIndex])
                        navigateTo(IpodScreenDestination.VIDEO_LIST)
                    }
                }
            }
            IpodScreenDestination.VIDEO_LIST -> {
                val videos = _uiState.value.localVideoTracks
                if (index in videos.indices) {
                    playLocalVideo(videos[index])
                    navigateTo(IpodScreenDestination.VIDEO_PLAYER)
                }
            }
            IpodScreenDestination.VIDEO_PLAYER -> {
                videoPlayerManager.togglePlayPause()
            }
            IpodScreenDestination.FAVORITES -> {
                val list = favorites.value
                if (index in list.indices) {
                    playbackQueue = list
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Favoritos")
                    playStation(list[index])
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.RECENTS -> {
                val list = _uiState.value.recentsList
                if (index in list.indices) {
                    playbackQueue = list
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Recentes")
                    playStation(list[index])
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.TOP_WORLD -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    playbackQueue = list
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Top Mundial")
                    playStation(list[index])
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.STATIONS_BY_GENRE -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    playbackQueue = list
                    _uiState.value = _uiState.value.copy(activeCategoryName = _uiState.value.activeGenre?.name ?: "Gênero")
                    playStation(list[index])
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.STATIONS_BY_COUNTRY -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    playbackQueue = list
                    _uiState.value = _uiState.value.copy(activeCategoryName = _uiState.value.activeCountry?.name ?: "País")
                    playStation(list[index])
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.SEARCH -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    playbackQueue = list
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Busca")
                    playStation(list[index])
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.GENRES_LIST -> {
                if (index in CuratedData.GENRES.indices) {
                    selectGenre(CuratedData.GENRES[index])
                }
            }
            IpodScreenDestination.COUNTRIES_LIST -> {
                if (index in CuratedData.COUNTRIES.indices) {
                    selectCountry(CuratedData.COUNTRIES[index])
                }
            }
            IpodScreenDestination.NOW_PLAYING_RDS -> {
                // Center click on now playing toggles favorite
                val current = currentStation.value
                if (current != null) {
                    toggleFavorite(current)
                }
            }
            IpodScreenDestination.SETTINGS_THEMES -> {
                when (index) {
                    0 -> cycleChassisTheme()
                    1 -> cycleBacklight()
                    2 -> cycleWheelPreset()
                    3 -> cycleFontType()
                    4 -> cycleFontSizeScale()
                    5 -> toggleFontBold()
                    6 -> setAutoPlayOnLaunch(!_uiState.value.autoPlayOnLaunch)
                    7 -> toggleSound()
                    8 -> toggleHaptics()
                    9 -> cycleSleepTimer()
                }
            }
            IpodScreenDestination.EQUALIZER -> {
                val presets = playerManager.EQUALIZER_PRESETS.keys.toList()
                val currentP = equalizerPreset.value
                val nextIdx = (presets.indexOf(currentP) + 1) % presets.size
                setEqualizerPreset(presets[nextIdx])
            }
            IpodScreenDestination.GAME_BRICK -> {
                // Center button in game releases ball or restarts
            }
            IpodScreenDestination.ABOUT -> {
                // Return to main menu on center button press
                navigateTo(IpodScreenDestination.MAIN_MENU)
            }
        }
    }

    fun onPlayPausePress() {
        if (_uiState.value.isHoldLocked) return
        soundAndHaptics.performHeavyHaptic()
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER) {
            videoPlayerManager.togglePlayPause()
        } else {
            playerManager.togglePlayPause()
        }
    }

    fun onNextTrackPress() {
        if (_uiState.value.isHoldLocked) return
        soundAndHaptics.performClickHaptic()
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER || videoPlayerManager.currentVideo.value != null) {
            nextVideoWithFolderWrap()
            return
        }
        if (_uiState.value.currentScreen == IpodScreenDestination.MP3_NOW_PLAYING || currentLocalAudio.value != null) {
            nextLocalTrackWithFolderWrap()
            return
        }
        val list = getActiveStationList()
        if (list.isNotEmpty()) {
            val current = currentStation.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % list.size else 0
            playStation(list[nextIndex])
        }
    }

    fun onPrevTrackPress() {
        if (_uiState.value.isHoldLocked) return
        soundAndHaptics.performClickHaptic()
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER || videoPlayerManager.currentVideo.value != null) {
            prevVideoWithFolderWrap()
            return
        }
        if (_uiState.value.currentScreen == IpodScreenDestination.MP3_NOW_PLAYING || currentLocalAudio.value != null) {
            prevLocalTrackWithFolderWrap()
            return
        }
        val list = getActiveStationList()
        if (list.isNotEmpty()) {
            val current = currentStation.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
            playStation(list[prevIndex])
        }
    }

    fun nextLocalTrackWithFolderWrap() {
        viewModelScope.launch {
            val current = playerManager.currentLocalAudio.value ?: return@launch
            val queue = _uiState.value.localAudioTracks
            val currentIndex = queue.indexOfFirst { it.id == current.id }
            if (currentIndex in 0 until queue.size - 1) {
                playLocalAudio(queue[currentIndex + 1], queue)
            } else {
                val folders = _uiState.value.localAudioFolders
                if (folders.isNotEmpty()) {
                    val currentFolderIndex = folders.indexOfFirst { it.name.equals(current.folderName, true) }
                    val nextFolderIndex = if (currentFolderIndex >= 0) (currentFolderIndex + 1) % folders.size else 0
                    val nextFolder = folders[nextFolderIndex]
                    val tracks = localMediaRepo.getTracksByFolder(nextFolder.name)
                    if (tracks.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(currentAudioFolder = nextFolder, localAudioTracks = tracks)
                        playLocalAudio(tracks.first(), tracks)
                    }
                } else if (queue.isNotEmpty()) {
                    playLocalAudio(queue.first(), queue)
                }
            }
        }
    }

    fun prevLocalTrackWithFolderWrap() {
        viewModelScope.launch {
            val current = playerManager.currentLocalAudio.value ?: return@launch
            val queue = _uiState.value.localAudioTracks
            val currentIndex = queue.indexOfFirst { it.id == current.id }
            if (currentIndex > 0) {
                playLocalAudio(queue[currentIndex - 1], queue)
            } else {
                val folders = _uiState.value.localAudioFolders
                if (folders.isNotEmpty()) {
                    val currentFolderIndex = folders.indexOfFirst { it.name.equals(current.folderName, true) }
                    val prevFolderIndex = if (currentFolderIndex > 0) currentFolderIndex - 1 else folders.size - 1
                    val prevFolder = folders[prevFolderIndex]
                    val tracks = localMediaRepo.getTracksByFolder(prevFolder.name)
                    if (tracks.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(currentAudioFolder = prevFolder, localAudioTracks = tracks)
                        playLocalAudio(tracks.last(), tracks)
                    }
                } else if (queue.isNotEmpty()) {
                    playLocalAudio(queue.last(), queue)
                }
            }
        }
    }

    fun nextVideoWithFolderWrap() {
        viewModelScope.launch {
            val current = videoPlayerManager.currentVideo.value ?: return@launch
            val queue = _uiState.value.localVideoTracks
            val currentIndex = queue.indexOfFirst { it.id == current.id }
            if (currentIndex in 0 until queue.size - 1) {
                playLocalVideo(queue[currentIndex + 1])
            } else {
                val folders = _uiState.value.localVideoFolders
                if (folders.isNotEmpty()) {
                    val currentFolderIndex = folders.indexOfFirst { it.name.equals(current.folderName, true) }
                    val nextFolderIndex = if (currentFolderIndex >= 0) (currentFolderIndex + 1) % folders.size else 0
                    val nextFolder = folders[nextFolderIndex]
                    val videos = localMediaRepo.getVideosByFolder(nextFolder.name)
                    if (videos.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(currentVideoFolder = nextFolder, localVideoTracks = videos)
                        playLocalVideo(videos.first())
                    }
                } else if (queue.isNotEmpty()) {
                    playLocalVideo(queue.first())
                }
            }
        }
    }

    fun prevVideoWithFolderWrap() {
        viewModelScope.launch {
            val current = videoPlayerManager.currentVideo.value ?: return@launch
            val queue = _uiState.value.localVideoTracks
            val currentIndex = queue.indexOfFirst { it.id == current.id }
            if (currentIndex > 0) {
                playLocalVideo(queue[currentIndex - 1])
            } else {
                val folders = _uiState.value.localVideoFolders
                if (folders.isNotEmpty()) {
                    val currentFolderIndex = folders.indexOfFirst { it.name.equals(current.folderName, true) }
                    val prevFolderIndex = if (currentFolderIndex > 0) currentFolderIndex - 1 else folders.size - 1
                    val prevFolder = folders[prevFolderIndex]
                    val videos = localMediaRepo.getVideosByFolder(prevFolder.name)
                    if (videos.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(currentVideoFolder = prevFolder, localVideoTracks = videos)
                        playLocalVideo(videos.last())
                    }
                } else if (queue.isNotEmpty()) {
                    playLocalVideo(queue.last())
                }
            }
        }
    }

    fun playStation(station: RadioStation) {
        // Persist last played station for startup
        prefs.saveLastPlayedStation(station)
        prefs.addRecentStation(station)
        _uiState.value = _uiState.value.copy(
            recentsList = prefs.getRecentStations()
        )

        // If station was played directly, ensure it's in the queue
        if (playbackQueue.none { it.id == station.id }) {
            playbackQueue = if (_uiState.value.stationsList.any { it.id == station.id }) {
                _uiState.value.stationsList
            } else if (favorites.value.any { it.id == station.id }) {
                favorites.value
            } else if (_uiState.value.recentsList.any { it.id == station.id }) {
                _uiState.value.recentsList
            } else {
                listOf(station) + CuratedData.CURATED_GLOBAL_STATIONS
            }
        }
        playerManager.updatePlaylist(playbackQueue)
        playerManager.playStation(station)
    }

    fun loadRecents() {
        val recents = prefs.getRecentStations()
        _uiState.value = _uiState.value.copy(
            recentsList = recents,
            stationsList = recents,
            activeCategoryName = "Recentes"
        )
        playbackQueue = recents
    }

    fun toggleFavorite(station: RadioStation) {
        viewModelScope.launch {
            repository.toggleFavorite(station)
            soundAndHaptics.performHeavyHaptic()
        }
    }

    fun deleteFavorite(stationId: String) {
        viewModelScope.launch {
            repository.removeFavorite(stationId)
            soundAndHaptics.performClickHaptic()
        }
    }

    fun selectGenre(genre: GenreCategory) {
        _uiState.value = _uiState.value.copy(
            activeGenre = genre,
            activeCategoryName = genre.name,
            isLoadingList = true,
            stationsList = emptyList()
        )
        navigateTo(IpodScreenDestination.STATIONS_BY_GENRE)
        viewModelScope.launch {
            val stations = repository.getStationsByGenre(genre.tag)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = stations
            )
            playbackQueue = stations
        }
    }

    fun selectCountry(country: CountryCategory) {
        _uiState.value = _uiState.value.copy(
            activeCountry = country,
            activeCategoryName = country.name,
            isLoadingList = true,
            stationsList = emptyList()
        )
        navigateTo(IpodScreenDestination.STATIONS_BY_COUNTRY)
        viewModelScope.launch {
            val stations = repository.getStationsByCountry(country.code)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = stations
            )
            playbackQueue = stations
        }
    }

    fun loadTopStations() {
        _uiState.value = _uiState.value.copy(
            isLoadingList = true,
            activeCategoryName = "Top Mundial"
        )
        viewModelScope.launch {
            val stations = repository.getTopStations(80)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = stations
            )
            playbackQueue = stations
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        executeSearch()
    }

    fun onSearchCountryChanged(countryCode: String) {
        _uiState.value = _uiState.value.copy(
            searchCountryCode = countryCode,
            searchCity = "ALL" // Reset city when country changes
        )
        executeSearch()
    }

    fun onSearchGenreChanged(genreTag: String) {
        _uiState.value = _uiState.value.copy(searchGenreTag = genreTag)
        executeSearch()
    }

    fun onSearchStateChanged(stateCode: String) {
        _uiState.value = _uiState.value.copy(
            searchStateCode = stateCode,
            searchCity = "ALL" // Reset city when state changes
        )
        executeSearch()
    }

    fun onSearchCityChanged(city: String) {
        _uiState.value = _uiState.value.copy(searchCity = city)
        executeSearch()
    }

    private fun executeSearch() {
        val query = _uiState.value.searchQuery
        val country = _uiState.value.searchCountryCode
        val genre = _uiState.value.searchGenreTag
        val state = _uiState.value.searchStateCode
        val city = _uiState.value.searchCity

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingList = true)
            val results = repository.searchStations(
                query = query,
                countryCode = country,
                genreTag = genre,
                stateCode = state,
                city = city
            )
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = results,
                activeCategoryName = "Busca"
            )
            playbackQueue = results
        }
    }

    fun adjustVolume(delta: Float) {
        playerManager.adjustVolumeDelta(delta)
        prefs.volumeLevel = playerManager.volume.value
    }

    fun setVolume(vol: Float) {
        playerManager.setVolumeLevel(vol)
        prefs.volumeLevel = vol
    }

    fun stepVolumeUp() {
        adjustVolume(0.05f)
        soundAndHaptics.performClickHaptic()
    }

    fun stepVolumeDown() {
        adjustVolume(-0.05f)
        soundAndHaptics.performClickHaptic()
    }

    fun cycleChassisTheme() {
        val themes = IpodChassisTheme.values()
        val next = (uiState.value.chassisTheme.ordinal + 1) % themes.size
        setChassisTheme(themes[next])
    }

    fun cycleBacklight() {
        val backlights = LcdBacklight.values()
        val next = (uiState.value.backlight.ordinal + 1) % backlights.size
        setBacklight(backlights[next])
    }

    fun toggleSound() {
        soundAndHaptics.isSoundEnabled = !soundAndHaptics.isSoundEnabled
        prefs.isSoundEnabled = soundAndHaptics.isSoundEnabled
        soundAndHaptics.performClickHaptic()
    }

    fun toggleHaptics() {
        soundAndHaptics.isHapticsEnabled = !soundAndHaptics.isHapticsEnabled
        prefs.isHapticsEnabled = soundAndHaptics.isHapticsEnabled
        soundAndHaptics.performClickHaptic()
    }

    fun cycleSleepTimer() {
        val current = sleepTimerMinutes.value
        val next = when (current) {
            0 -> 15
            15 -> 30
            30 -> 45
            45 -> 60
            else -> 0
        }
        setSleepTimer(next)
    }

    fun loadAudioFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingList = true)
            val folders = localMediaRepo.getAudioFolders()
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localAudioFolders = folders
            )
        }
    }

    fun selectAudioFolder(folder: com.example.data.model.MediaFolder) {
        _uiState.value = _uiState.value.copy(
            currentAudioFolder = folder,
            isLoadingList = true,
            localAudioTracks = emptyList()
        )
        viewModelScope.launch {
            val tracks = localMediaRepo.getTracksByFolder(folder.name)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localAudioTracks = tracks
            )
        }
    }

    fun loadAllAudioTracks() {
        _uiState.value = _uiState.value.copy(
            currentAudioFolder = null,
            isLoadingList = true,
            localAudioTracks = emptyList()
        )
        viewModelScope.launch {
            val tracks = localMediaRepo.getAllAudioTracks()
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localAudioTracks = tracks
            )
        }
    }

    fun playLocalAudio(track: com.example.data.model.LocalAudioTrack, queue: List<com.example.data.model.LocalAudioTrack> = emptyList()) {
        videoPlayerManager.pause()
        playerManager.playLocalAudio(track, queue)
        soundAndHaptics.performHeavyHaptic()

        // Background online artwork search fallback if local artwork is missing
        viewModelScope.launch {
            val onlineArt = localMediaRepo.fetchOnlineAlbumArt(track.artist, track.title, track.album)
            if (!onlineArt.isNullOrBlank()) {
                playerManager.updateArtworkForCurrentTrack(onlineArt)
            }
        }
    }

    fun loadVideoFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingList = true)
            val folders = localMediaRepo.getVideoFolders()
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localVideoFolders = folders
            )
        }
    }

    fun selectVideoFolder(folder: com.example.data.model.MediaFolder) {
        _uiState.value = _uiState.value.copy(
            currentVideoFolder = folder,
            isLoadingList = true,
            localVideoTracks = emptyList()
        )
        viewModelScope.launch {
            val videos = localMediaRepo.getVideosByFolder(folder.name)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localVideoTracks = videos
            )
        }
    }

    fun loadAllVideoTracks() {
        _uiState.value = _uiState.value.copy(
            currentVideoFolder = null,
            isLoadingList = true,
            localVideoTracks = emptyList()
        )
        viewModelScope.launch {
            val videos = localMediaRepo.getAllVideoTracks()
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localVideoTracks = videos
            )
        }
    }

    fun playLocalVideo(video: com.example.data.model.LocalVideoTrack) {
        playerManager.pause()
        videoPlayerManager.playVideo(video)
        soundAndHaptics.performHeavyHaptic()
    }

    fun toggleVideoFullscreen() {
        _uiState.value = _uiState.value.copy(isVideoFullscreen = !_uiState.value.isVideoFullscreen)
    }

    fun setCarModeSource(source: CarModeSource) {
        _uiState.value = _uiState.value.copy(carModeSource = source)
        if (source == CarModeSource.MP3 && _uiState.value.localAudioTracks.isEmpty()) {
            loadAllAudioTracks()
        }
        soundAndHaptics.performClickHaptic()
    }

    fun toggleCarModeSource() {
        val next = if (_uiState.value.carModeSource == CarModeSource.RADIO) CarModeSource.MP3 else CarModeSource.RADIO
        setCarModeSource(next)
    }

    private fun getItemCountForCurrentScreen(): Int {
        return when (_uiState.value.currentScreen) {
            IpodScreenDestination.MAIN_MENU -> 8
            IpodScreenDestination.RADIO_MENU -> 7
            IpodScreenDestination.MP3_FOLDERS -> _uiState.value.localAudioFolders.size + 1
            IpodScreenDestination.MP3_TRACKS_LIST -> _uiState.value.localAudioTracks.size
            IpodScreenDestination.MP3_NOW_PLAYING -> 0
            IpodScreenDestination.VIDEO_FOLDERS -> _uiState.value.localVideoFolders.size + 1
            IpodScreenDestination.VIDEO_LIST -> _uiState.value.localVideoTracks.size
            IpodScreenDestination.VIDEO_PLAYER -> 0
            IpodScreenDestination.FAVORITES -> favorites.value.size
            IpodScreenDestination.RECENTS -> _uiState.value.recentsList.size
            IpodScreenDestination.TOP_WORLD,
            IpodScreenDestination.STATIONS_BY_GENRE,
            IpodScreenDestination.STATIONS_BY_COUNTRY,
            IpodScreenDestination.SEARCH -> _uiState.value.stationsList.size
            IpodScreenDestination.GENRES_LIST -> CuratedData.GENRES.size
            IpodScreenDestination.COUNTRIES_LIST -> CuratedData.COUNTRIES.size
            IpodScreenDestination.SETTINGS_THEMES -> 10
            IpodScreenDestination.EQUALIZER,
            IpodScreenDestination.GAME_BRICK,
            IpodScreenDestination.NOW_PLAYING_RDS,
            IpodScreenDestination.ABOUT -> 0
        }
    }

    private fun getActiveStationList(): List<RadioStation> {
        // If we are actively viewing a screen, prefer that screen's list
        val state = _uiState.value
        return when (state.currentScreen) {
            IpodScreenDestination.RECENTS -> state.recentsList.ifEmpty { playbackQueue }
            IpodScreenDestination.FAVORITES -> favorites.value.ifEmpty { playbackQueue }
            IpodScreenDestination.TOP_WORLD,
            IpodScreenDestination.STATIONS_BY_GENRE,
            IpodScreenDestination.STATIONS_BY_COUNTRY,
            IpodScreenDestination.SEARCH -> state.stationsList.ifEmpty { playbackQueue }
            else -> {
                // If playbackQueue has items, navigate inside the current playlist category!
                if (playbackQueue.isNotEmpty()) playbackQueue
                else if (favorites.value.isNotEmpty()) favorites.value
                else CuratedData.CURATED_GLOBAL_STATIONS
            }
        }
    }
}
