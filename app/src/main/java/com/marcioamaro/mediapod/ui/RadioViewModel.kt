package com.marcioamaro.mediapod.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marcioamaro.mediapod.RadioApp
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.data.preferences.IpodFontSizeScale
import com.marcioamaro.mediapod.data.preferences.IpodFontType
import com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager
import com.marcioamaro.mediapod.data.preferences.IpodWheelPreset
import com.marcioamaro.mediapod.data.repository.CountryCategory
import com.marcioamaro.mediapod.data.repository.CuratedData
import com.marcioamaro.mediapod.data.repository.GenreCategory
import com.marcioamaro.mediapod.data.repository.libraryKey
import com.marcioamaro.mediapod.data.repository.LibraryKind
import com.marcioamaro.mediapod.data.repository.PodcastRankingRepository
import com.marcioamaro.mediapod.data.repository.RadioRankingRepository
import com.marcioamaro.mediapod.data.repository.RadioRepository
import com.marcioamaro.mediapod.player.ActiveMediaType
import com.marcioamaro.mediapod.player.RadioPlaybackStatus
import com.marcioamaro.mediapod.player.RadioPlayerManager
import com.marcioamaro.mediapod.player.RdsInfo
import com.marcioamaro.mediapod.player.NowPlayingMetadata
import com.marcioamaro.mediapod.player.context.NavigationContext
import com.marcioamaro.mediapod.player.context.QueueSource
import com.marcioamaro.mediapod.player.coordinator.toPlaybackQueueItem
import com.marcioamaro.mediapod.data.model.IpodAppearanceSettings
import com.marcioamaro.mediapod.data.model.IpodPalette
import com.marcioamaro.mediapod.ui.theme.IpodColorContrastUtil
import com.marcioamaro.mediapod.ui.theme.SafeIpodColorGenerator
import com.marcioamaro.mediapod.util.IpodSoundAndHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class IpodScreenDestination {
    MAIN_MENU,
    PERSONAL_LIBRARY,
    RADIO_MENU,
    NOW_PLAYING_RDS,
    FAVORITES,
    RECENTS,
    TOP_BRAZIL,
    TOP_WORLD,
    GENRES_LIST,
    STATIONS_BY_GENRE,
    COUNTRIES_LIST,
    STATIONS_BY_COUNTRY,
    SEARCH,
    RADIO_CUSTOM_LIST,
    ADD_CUSTOM_RADIO,
    PODCASTS_MENU,
    PODCASTS_FAVORITES,
    PODCASTS_RECENTS,
    PODCASTS_TOP_BRAZIL,
    PODCASTS_TOP_WORLD,
    PODCASTS_CATEGORIES,
    PODCASTS_BY_CATEGORY,
    PODCASTS_COUNTRIES,
    PODCASTS_BY_COUNTRY,
    PODCASTS_SEARCH,
    PODCASTS_CUSTOM_LIST,
    ADD_CUSTOM_PODCAST,
    PODCAST_EPISODES_LIST,
    PODCAST_NOW_PLAYING,
    PODCAST_CHAPTERS,
    MP3_FOLDERS,
    MP3_TRACKS_LIST,
    MP3_NOW_PLAYING,
    VIDEO_FOLDERS,
    VIDEO_LIST,
    VIDEO_PLAYER,
    YOUTUBE_VIDEOS_LIST,
    ADD_CUSTOM_YOUTUBE,
    YOUTUBE_PLAYER,
    EQUALIZER,
    AUDIO_OUTPUT_MENU,
    GAME_BRICK,
    SETTINGS_THEMES,
    ABOUT
}

enum class DisplayMode {
    IPOD_CLASSIC,
    CAR_FULLSCREEN_RDS,
    DOCK_STANDBY
}

enum class DockColorTheme(val displayName: String, val primaryColor: Long, val secondaryColor: Long) {
    ELECTRIC_BLUE("Azul Elétrico", 0xFF2979FF, 0xFF82B1FF),
    NIGHT_AMBER("Âmbar Noturno", 0xFFFF9100, 0xFFFFD180),
    SOFT_WHITE("Branco Suave", 0xFFE0E0E0, 0xFF9E9E9E)
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
    EMERALD_GREEN("Retro Emerald", 0xFF062817, 0xFF86EFAC, 0xFF4ADE80, 0xFF16A34A),
    U2_RED_BLACK("U2 Ruby Red LCD", 0xFFFF8A8A, 0xFF111111, 0xFF222222, 0xFFDC2626)
}

data class UiState(
    val currentScreen: IpodScreenDestination = IpodScreenDestination.MAIN_MENU,
    val displayMode: DisplayMode = DisplayMode.IPOD_CLASSIC,
    val dockColorTheme: DockColorTheme = DockColorTheme.ELECTRIC_BLUE,
    val carModeSource: CarModeSource = CarModeSource.RADIO,
    val chassisTheme: IpodChassisTheme = IpodChassisTheme.CLASSIC_SILVER,
    val appearanceSettings: IpodAppearanceSettings = IpodAppearanceSettings(),
    val customBodyColor: Long = 0xFFF1F5F9,
    val backlight: LcdBacklight = LcdBacklight.RETRO_IPOD_LCD,
    val wheelPreset: IpodWheelPreset = IpodWheelPreset.CLASSIC_GREY,
    val customWheelColor: Long = 0xFFE2E4E8,
    val customWheelTextColor: Long = 0xFF475569,
    val customCenterButtonColor: Long = 0xFFFFFFFF,
    val fontType: IpodFontType = IpodFontType.MONOSPACE,
    val fontSizeScale: IpodFontSizeScale = IpodFontSizeScale.SCALE_100,
    val isFontBold: Boolean = true,
    val is24HourClock: Boolean = true,
    val autoPlayOnLaunch: Boolean = true,
    val dockClockScale: com.marcioamaro.mediapod.data.preferences.DockClockScale = com.marcioamaro.mediapod.data.preferences.DockClockScale.SCALE_100,
    val dockShowSeconds: Boolean = false,
    val selectedIndex: Int = 0,
    val isHoldLocked: Boolean = false,
    val isLoadingList: Boolean = false,
    val searchQuery: String = "",
    val searchCountryCode: String = "ALL",
    val searchGenreTag: String = "ALL",
    val searchStateCode: String = "ALL",
    val searchCity: String = "ALL",
    val availableCities: List<String> = emptyList(),
    val isLoadingCities: Boolean = false,
    val activeGenre: GenreCategory? = null,
    val activeCountry: CountryCategory? = null,
    val stationsList: List<RadioStation> = emptyList(),
    val recentsList: List<RadioStation> = emptyList(),
    val activeCategoryName: String = "Top Mundial",
    val isSearchActive: Boolean = false,
    val gamePaddlePosition: Float = 0.5f,
    val gameWheelTicks: Long = 0L,
    // Custom Stations
    val customStations: List<RadioStation> = emptyList(),
    // Podcasts
    val podcastShows: List<com.marcioamaro.mediapod.data.model.PodcastShow> = emptyList(),
    val currentPodcastShow: com.marcioamaro.mediapod.data.model.PodcastShow? = null,
    val podcastEpisodes: List<com.marcioamaro.mediapod.data.model.PodcastEpisode> = emptyList(),
    val currentPodcastEpisode: com.marcioamaro.mediapod.data.model.PodcastEpisode? = null,
    val podcastCategories: List<com.marcioamaro.mediapod.data.model.PodcastCategory> = emptyList(),
    val podcastCountries: List<com.marcioamaro.mediapod.data.model.PodcastCountry> = emptyList(),
    val customPodcasts: List<com.marcioamaro.mediapod.data.model.PodcastShow> = emptyList(),
    val podcastSearchQuery: String = "",
    val podcastSearchResults: List<com.marcioamaro.mediapod.data.model.PodcastShow> = emptyList(),
    val isPodcastSearchLoading: Boolean = false,
    val isPodcastLoading: Boolean = false,
    // MP3 Player Local Media
    val localAudioFolders: List<com.marcioamaro.mediapod.data.model.MediaFolder> = emptyList(),
    val localAudioTracks: List<com.marcioamaro.mediapod.data.model.LocalAudioTrack> = emptyList(),
    val currentAudioFolder: com.marcioamaro.mediapod.data.model.MediaFolder? = null,
    // Video Player Local Media
    val localVideoFolders: List<com.marcioamaro.mediapod.data.model.MediaFolder> = emptyList(),
    val localVideoTracks: List<com.marcioamaro.mediapod.data.model.LocalVideoTrack> = emptyList(),
    val currentVideoFolder: com.marcioamaro.mediapod.data.model.MediaFolder? = null,
    val isVideoFullscreen: Boolean = false,
    // YouTube
    val customYouTubeVideos: List<com.marcioamaro.mediapod.data.model.YouTubeVideo> = emptyList(),
    val currentYouTubeVideo: com.marcioamaro.mediapod.data.model.YouTubeVideo? = null
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val radioApp = application as RadioApp
    private val repository: RadioRepository = radioApp.repository
    private val playerManager: RadioPlayerManager = radioApp.playerManager
    val soundAndHaptics: IpodSoundAndHaptics = radioApp.soundAndHaptics
    private val prefs: IpodPreferencesManager = IpodPreferencesManager.getInstance(application)
    private val localMediaRepo: com.marcioamaro.mediapod.data.repository.LocalMediaRepository = radioApp.localMediaRepository
    val videoPlayerManager: com.marcioamaro.mediapod.player.LocalVideoPlayerManager = com.marcioamaro.mediapod.player.LocalVideoPlayerManager.getInstance(application)

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

    private val _isChassisBackAnimationEnabled = MutableStateFlow(
        prefs.isChassisBackAnimationEnabled()
    )
    val isChassisBackAnimationEnabled: StateFlow<Boolean> = _isChassisBackAnimationEnabled.asStateFlow()

    fun setChassisBackAnimationEnabled(enabled: Boolean) {
        _isChassisBackAnimationEnabled.value = enabled
        prefs.setChassisBackAnimationEnabled(enabled)
    }

    fun toggleChassisBackAnimationEnabled() {
        setChassisBackAnimationEnabled(!_isChassisBackAnimationEnabled.value)
    }

    private val _isPureAudioModeEnabled = MutableStateFlow(
        prefs.isPureAudioModeEnabled()
    )
    val isPureAudioModeEnabled: StateFlow<Boolean> = _isPureAudioModeEnabled.asStateFlow()

    fun setPureAudioModeEnabled(enabled: Boolean) {
        _isPureAudioModeEnabled.value = enabled
        prefs.setPureAudioModeEnabled(enabled)
        playerManager.setPureAudioUserPreference(enabled)
        soundAndHaptics.performClickHaptic()
    }

    fun togglePureAudioMode() {
        setPureAudioModeEnabled(!_isPureAudioModeEnabled.value)
    }

    val favorites: StateFlow<List<RadioStation>> = repository.favoritesFlow
        .map { list -> list.sortedBy { it.name.trim().lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackStatus: StateFlow<RadioPlaybackStatus> = playerManager.playbackStatus
    val currentStation: StateFlow<RadioStation?> = playerManager.currentStation
    val rdsInfo: StateFlow<RdsInfo> = playerManager.rdsInfo
    val nowPlaying: StateFlow<NowPlayingMetadata> = playerManager.nowPlaying
    val visualizerAmplitudes: StateFlow<List<Float>> = playerManager.visualizerAmplitudes
    val volume: StateFlow<Float> = radioApp.playbackCoordinator.activeVolume
    val sleepTimerMinutes: StateFlow<Int> = playerManager.sleepTimerMinutes
    val sleepTimerSecondsRemaining: StateFlow<Long> = playerManager.sleepTimerSecondsRemaining
    val errorMessage: StateFlow<String?> = playerManager.errorMessage

    private val _brickGameCenterAction = MutableStateFlow(0L)
    val brickGameCenterAction: StateFlow<Long> = _brickGameCenterAction.asStateFlow()

    val clickWheelPreferences: StateFlow<com.marcioamaro.mediapod.data.prefs.ClickWheelPreferences> = radioApp.clickWheelRepository.clickWheelPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.marcioamaro.mediapod.data.prefs.ClickWheelPreferences())

    fun setClickWheelMode(mode: com.marcioamaro.mediapod.data.prefs.ClickWheelMode) {
        viewModelScope.launch {
            radioApp.clickWheelRepository.updateClickWheelMode(mode)
        }
        soundAndHaptics.performClickHaptic()
    }

    fun setFixedSpeed(speed: com.marcioamaro.mediapod.data.prefs.FixedSpeed) {
        viewModelScope.launch {
            radioApp.clickWheelRepository.updateFixedSpeed(speed)
        }
        soundAndHaptics.performClickHaptic()
    }

    // Audio Output Switcher & MediaRouter
    val audioRouteManager = com.marcioamaro.mediapod.player.AudioRouteManager.getInstance(application)
    val availableAudioDevices = audioRouteManager.availableDevices
    val selectedAudioDevice = audioRouteManager.selectedDevice

    val podcastRepo: com.marcioamaro.mediapod.data.repository.PodcastRepository = radioApp.podcastRepository

    val currentPodcastEpisode = playerManager.currentPodcastEpisode
    val currentPodcastShow = playerManager.currentPodcastShow
    val liveSessionDurationSeconds = playerManager.liveSessionDurationSeconds

    val podcastFavorites: StateFlow<List<com.marcioamaro.mediapod.data.model.PodcastShow>> = podcastRepo.favoriteShowsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val podcastRecents: StateFlow<List<com.marcioamaro.mediapod.data.model.PodcastShow>> = podcastRepo.recentShowsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectAudioDevice(device: com.marcioamaro.mediapod.player.AudioRouteDevice) {
        audioRouteManager.selectDevice(device)
        soundAndHaptics.performClickHaptic()
    }

    fun showNativeAudioChooserDialog(context: android.content.Context) {
        audioRouteManager.showNativeChooserDialog(context)
    }

    fun loadCustomStations() {
        val list = prefs.getCustomStations()
        _uiState.value = _uiState.value.copy(customStations = list)
    }

    fun addCustomStation(name: String, url: String) {
        val station = RadioStation(
            id = "custom_" + java.util.UUID.randomUUID().toString().take(8),
            name = name,
            streamUrl = url,
            country = "Personalizada",
            countryCode = "BR",
            city = "Minhas Rádios"
        )
        prefs.addCustomStation(station)
        loadCustomStations()
        soundAndHaptics.performHeavyHaptic()
    }

    fun removeCustomStation(id: String) {
        prefs.removeCustomStation(id)
        loadCustomStations()
        soundAndHaptics.performHeavyHaptic()
    }

    fun loadCustomPodcasts() {
        val list = podcastRepo.getCustomPodcasts()
        _uiState.value = _uiState.value.copy(customPodcasts = list)
    }

    fun addCustomPodcast(name: String, feedUrl: String) {
        viewModelScope.launch {
            podcastRepo.addCustomPodcast(name, feedUrl)
            loadCustomPodcasts()
            soundAndHaptics.performHeavyHaptic()
        }
    }

    fun removeCustomPodcast(id: String) {
        viewModelScope.launch {
            podcastRepo.removeCustomPodcast(id)
            loadCustomPodcasts()
            soundAndHaptics.performHeavyHaptic()
        }
    }

    // --- YouTube Video Methods ---
    fun loadYouTubeVideos() {
        val list = prefs.getYouTubeVideos()
        _uiState.value = _uiState.value.copy(customYouTubeVideos = list)
    }

    fun addYouTubeVideo(title: String, url: String): Boolean {
        val validation = com.marcioamaro.mediapod.util.YouTubeUrlValidator.validateUrl(url)
        return if (validation is com.marcioamaro.mediapod.util.YouTubeValidationResult.Success) {
            val video = com.marcioamaro.mediapod.data.model.YouTubeVideo(
                id = validation.videoId,
                title = title.ifBlank { "Vídeo YouTube" },
                url = validation.cleanUrl
            )
            prefs.addYouTubeVideo(video)
            loadYouTubeVideos()
            soundAndHaptics.performHeavyHaptic()
            true
        } else {
            false
        }
    }

    fun removeYouTubeVideo(videoId: String) {
        prefs.removeYouTubeVideo(videoId)
        loadYouTubeVideos()
        soundAndHaptics.performHeavyHaptic()
    }

    fun playYouTubeVideo(video: com.marcioamaro.mediapod.data.model.YouTubeVideo) {
        playerManager.pause()
        playerManager.setActiveMediaType(ActiveMediaType.YOUTUBE_STREAM)
        try {
            val pauseIntent = android.content.Intent(getApplication(), com.marcioamaro.mediapod.service.RadioMediaService::class.java).apply {
                action = com.marcioamaro.mediapod.service.RadioMediaService.ACTION_PAUSE
            }
            getApplication<android.app.Application>().startService(pauseIntent)
        } catch (_: Exception) {}
        try {
            videoPlayerManager.pause()
        } catch (_: Exception) {}
        if (_uiState.value.currentYouTubeVideo?.id != video.id) {
            youTubePlaybackPositionSeconds = 0
        }
        _uiState.value = _uiState.value.copy(currentYouTubeVideo = video)
        navigateTo(IpodScreenDestination.YOUTUBE_PLAYER)
        soundAndHaptics.performHeavyHaptic()
    }

    var youTubePlaybackPositionSeconds: Int = 0
        private set

    fun updateYouTubePlaybackPosition(seconds: Int) {
        if (seconds >= 0) {
            youTubePlaybackPositionSeconds = seconds
        }
    }

    fun nextYouTubeVideo() {
        val list = _uiState.value.customYouTubeVideos
        val current = _uiState.value.currentYouTubeVideo ?: return
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx in 0 until list.size - 1) {
            playYouTubeVideo(list[idx + 1])
        } else if (list.isNotEmpty()) {
            playYouTubeVideo(list.first())
        }
    }

    fun prevYouTubeVideo() {
        val list = _uiState.value.customYouTubeVideos
        val current = _uiState.value.currentYouTubeVideo ?: return
        val idx = list.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            playYouTubeVideo(list[idx - 1])
        } else if (list.isNotEmpty()) {
            playYouTubeVideo(list.last())
        }
    }

    fun loadPodcastTopBrazil() {
        _uiState.value = _uiState.value.copy(isPodcastLoading = true)
        viewModelScope.launch {
            val shows = PodcastRankingRepository.getInstance(radioApp).getTopPodcastsBrazil(20)
            _uiState.value = _uiState.value.copy(
                podcastShows = shows,
                activeCategoryName = "Top Podcasts Brasil",
                isPodcastLoading = false
            )
        }
    }

    fun loadPodcastTopWorld() {
        _uiState.value = _uiState.value.copy(isPodcastLoading = true)
        viewModelScope.launch {
            val shows = PodcastRankingRepository.getInstance(radioApp).getTopPodcastsWorld(20)
            _uiState.value = _uiState.value.copy(
                podcastShows = shows,
                activeCategoryName = "Top Podcasts Mundial",
                isPodcastLoading = false
            )
        }
    }

    fun loadPodcastCategories() {
        val categories = podcastRepo.categories
        _uiState.value = _uiState.value.copy(podcastCategories = categories)
    }

    fun loadPodcastsByCategory(category: String) {
        _uiState.value = _uiState.value.copy(isPodcastLoading = true)
        viewModelScope.launch {
            val shows = podcastRepo.getPodcastsByCategory(category)
            _uiState.value = _uiState.value.copy(
                podcastShows = shows,
                activeCategoryName = category,
                isPodcastLoading = false
            )
        }
    }

    fun loadPodcastCountries() {
        val countries = podcastRepo.countries
        _uiState.value = _uiState.value.copy(podcastCountries = countries)
    }

    fun loadPodcastsByCountry(countryCode: String, countryName: String) {
        _uiState.value = _uiState.value.copy(isPodcastLoading = true)
        viewModelScope.launch {
            val shows = podcastRepo.getTopPodcasts(countryCode, 500)
            _uiState.value = _uiState.value.copy(
                podcastShows = shows,
                activeCategoryName = countryName,
                isPodcastLoading = false
            )
        }
    }

    private var podcastSearchJob: kotlinx.coroutines.Job? = null

    fun searchPodcasts(query: String) {
        podcastSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(podcastSearchQuery = query,
            podcastSearchResults = emptyList(), isPodcastSearchLoading = true, selectedIndex = 0)
        podcastSearchJob = viewModelScope.launch {
            val local = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                podcastRepo.searchCatalog(query)
            }
            _uiState.value = _uiState.value.copy(podcastSearchResults = local,
                isPodcastSearchLoading = query.isNotBlank())
            if (query.isBlank()) return@launch
            kotlinx.coroutines.delay(300)
            val shows = podcastRepo.searchPodcasts(query)
            if (_uiState.value.podcastSearchQuery != query) return@launch
            _uiState.value = _uiState.value.copy(
                podcastSearchResults = shows,
                isPodcastSearchLoading = false
            )
        }
    }

    fun selectPodcastShow(show: com.marcioamaro.mediapod.data.model.PodcastShow): Boolean {
        if (show.feedUrl.isBlank()) {
            return false
        }
        _uiState.value = _uiState.value.copy(
            currentPodcastShow = show,
            podcastEpisodes = emptyList(),
            isPodcastLoading = true
        )
        viewModelScope.launch {
            val episodes = podcastRepo.getEpisodes(show)
            _uiState.value = _uiState.value.copy(
                podcastEpisodes = episodes.map { it.copy(isPlayed = podcastRepo.isEpisodePlayed(it.id)) },
                isPodcastLoading = false
            )
        }
        return true
    }

    fun togglePodcastPlayed(episode: com.marcioamaro.mediapod.data.model.PodcastEpisode) {
        podcastRepo.markEpisodePlayed(episode.id, !podcastRepo.isEpisodePlayed(episode.id))
    }

    fun playPodcastEpisode(
        episode: com.marcioamaro.mediapod.data.model.PodcastEpisode,
        explicitShow: com.marcioamaro.mediapod.data.model.PodcastShow? = null,
        queue: List<com.marcioamaro.mediapod.data.model.PodcastEpisode> = emptyList()
    ) {
        val show = explicitShow ?: _uiState.value.currentPodcastShow
        playerManager.playPodcastEpisode(episode, show, queue)
        if (show != null) {
            viewModelScope.launch {
                podcastRepo.addToRecents(show)
            }
        }
        _uiState.value = _uiState.value.copy(
            currentPodcastEpisode = episode,
            currentPodcastShow = show ?: _uiState.value.currentPodcastShow,
            currentYouTubeVideo = null
        )
        soundAndHaptics.performHeavyHaptic()
    }

    fun togglePodcastFavorite(show: com.marcioamaro.mediapod.data.model.PodcastShow) {
        viewModelScope.launch {
            podcastRepo.toggleFavorite(show)
            soundAndHaptics.performClickHaptic()
        }
    }

    fun seekRelative(deltaMs: Long) {
        playerManager.seekRelative(deltaMs)
        soundAndHaptics.performClickHaptic()
    }

    val currentPodcastChapters = playerManager.currentPodcastChapters
    val currentChapter = playerManager.currentChapter
    val playbackSpeed = playerManager.playbackSpeed
    val videoPlaybackSpeed = videoPlayerManager.playbackSpeed

    fun setPlaybackSpeed(speed: Float, isPodcast: Boolean) {
        playerManager.setPlaybackSpeed(speed, isPodcast)
    }

    fun cyclePlaybackSpeed(isPodcast: Boolean) {
        val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val current = if (isPodcast) playerManager.playbackSpeed.value else videoPlayerManager.playbackSpeed.value
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val next = if (idx in 0 until speeds.size - 1) speeds[idx + 1] else speeds[0]
        if (isPodcast) {
            playerManager.setPlaybackSpeed(next, true)
        } else {
            videoPlayerManager.setPlaybackSpeed(next)
            playerManager.setPlaybackSpeed(next, false)
        }
        soundAndHaptics.performClickHaptic()
    }

    fun seekToPosition(positionMs: Long) {
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER) {
            videoPlayerManager.seekToPosition(positionMs)
        } else {
            playerManager.seekToPosition(positionMs)
        }
    }

    fun selectPodcastChapter(chapter: com.marcioamaro.mediapod.data.model.PodcastChapter) {
        playerManager.seekToPosition(chapter.startTimeMs)
        navigateTo(IpodScreenDestination.PODCAST_NOW_PLAYING)
    }

    private fun createInitialAppearanceSettings(): IpodAppearanceSettings {
        val manualPalette = IpodPalette(
            bodyColor = prefs.customBodyColor,
            wheelColor = prefs.customWheelColor,
            wheelTextColor = prefs.customWheelTextColor,
            centerButtonColor = prefs.customCenterButtonColor
        )
        val isRandom = prefs.randomHardwareColorsEnabled
        return if (isRandom) {
            val sessionPalette = SafeIpodColorGenerator.generateSafePalette()
            IpodAppearanceSettings(
                randomHardwareColorsEnabled = true,
                manualPalette = manualPalette,
                activePalette = sessionPalette,
                lastValidPalette = sessionPalette,
                lastGenerationId = System.currentTimeMillis()
            )
        } else {
            IpodAppearanceSettings(
                randomHardwareColorsEnabled = false,
                manualPalette = manualPalette,
                activePalette = manualPalette,
                lastValidPalette = manualPalette,
                lastGenerationId = 0L
            )
        }
    }

    private val initialAppearanceSettings by lazy { createInitialAppearanceSettings() }

    private val _uiState = MutableStateFlow(
        run {
            val appearance = createInitialAppearanceSettings()
            UiState(
                chassisTheme = prefs.chassisTheme,
                appearanceSettings = appearance,
                customBodyColor = appearance.activePalette.bodyColor,
                backlight = prefs.lcdBacklight,
                wheelPreset = prefs.wheelPreset,
                customWheelColor = appearance.activePalette.wheelColor,
                customWheelTextColor = appearance.activePalette.wheelTextColor,
                customCenterButtonColor = appearance.activePalette.centerButtonColor,
                fontType = prefs.fontType,
                fontSizeScale = prefs.fontSizeScale,
                isFontBold = prefs.isFontBold,
                is24HourClock = prefs.is24HourClock,
                autoPlayOnLaunch = prefs.isAutoPlayOnLaunch,
                dockClockScale = prefs.dockClockScale,
                dockShowSeconds = prefs.dockShowSeconds,
                recentsList = prefs.getRecentStations()
            )
        }
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Holds the playback queue corresponding to the last list user played from
    private var playbackQueue: List<RadioStation> = emptyList()

    val mediaLibrary = com.marcioamaro.mediapod.data.repository.MediaLibraryRepository.getInstance(radioApp)
    private var audioPlaybackQueue = emptyList<com.marcioamaro.mediapod.data.model.LocalAudioTrack>()
    private var videoPlaybackQueue = emptyList<com.marcioamaro.mediapod.data.model.LocalVideoTrack>()

    private val navigationHistory = ArrayDeque<IpodScreenDestination>()

    init {
        viewModelScope.launch {
            mediaLibrary.state.collect {
                when (_uiState.value.currentScreen) {
                    IpodScreenDestination.MP3_FOLDERS -> loadAudioFolders()
                    IpodScreenDestination.VIDEO_FOLDERS -> loadVideoFolders()
                    IpodScreenDestination.MP3_TRACKS_LIST -> _uiState.value.currentAudioFolder?.let { selectAudioFolder(it, false) }
                    IpodScreenDestination.VIDEO_LIST -> _uiState.value.currentVideoFolder?.let { selectVideoFolder(it, false) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            podcastRepo.playedEpisodeIds.collect { ids ->
                _uiState.value = _uiState.value.copy(
                    podcastEpisodes = _uiState.value.podcastEpisodes.map { it.copy(isPlayed = it.id in ids) }
                )
            }
        }
        // Restore sound and haptics preferences
        soundAndHaptics.isSoundEnabled = prefs.isSoundEnabled
        soundAndHaptics.isHapticsEnabled = prefs.isHapticsEnabled

        // Volume nativo Android: sincroniza sem forçar nem aumentar o volume do STREAM_MUSIC no boot
        playerManager.syncVolumeFromNativeStream()

        // Restaura modo de exibição persistido (iPod, Car Mode RDS, Dock Standby)
        val savedModeStr = prefs.getDisplayMode()
        val initialDisplayMode = try {
            DisplayMode.valueOf(savedModeStr)
        } catch (_: Exception) {
            DisplayMode.IPOD_CLASSIC
        }
        if (initialDisplayMode != _uiState.value.displayMode) {
            _uiState.value = _uiState.value.copy(displayMode = initialDisplayMode)
        }

        // Wire folder-spanning navigation callbacks
        playerManager.onFolderWrapNext = { nextLocalTrackWithFolderWrap() }
        playerManager.onFolderWrapPrev = { prevLocalTrackWithFolderWrap() }
        videoPlayerManager.onVideoEnded = { nextVideoWithFolderWrap() }

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

        // Auto-play on launch: Retoma última rádio ou podcast executado após carregamento na memória
        val lastMediaType = prefs.getLastMediaType()
        val lastStation = prefs.getLastPlayedStation()
        val lastPodcast = prefs.getLastPlayedPodcast()

        viewModelScope.launch {
            kotlinx.coroutines.delay(400) // tempo para inicialização de serviços e memória
            if (lastMediaType == "PODCAST" && lastPodcast != null) {
                playPodcastEpisode(lastPodcast.first, lastPodcast.second)
            } else if (lastStation != null) {
                playStation(lastStation)
            } else if (lastPodcast != null) {
                playPodcastEpisode(lastPodcast.first, lastPodcast.second)
            }
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        prefs.saveDisplayMode(mode.name)
        soundAndHaptics.performHeavyHaptic()
    }

    fun enterDockMode() {
        setDisplayMode(DisplayMode.DOCK_STANDBY)
    }

    fun cycleDockColorTheme() {
        val themes = DockColorTheme.values()
        val currentIdx = themes.indexOf(_uiState.value.dockColorTheme)
        val nextTheme = themes[(currentIdx + 1) % themes.size]
        _uiState.value = _uiState.value.copy(dockColorTheme = nextTheme)
        soundAndHaptics.performClickHaptic()
    }

    fun setDockClockScale(scale: com.marcioamaro.mediapod.data.preferences.DockClockScale) {
        prefs.dockClockScale = scale
        _uiState.value = _uiState.value.copy(dockClockScale = scale)
        soundAndHaptics.performClickHaptic()
    }

    fun setDockShowSeconds(show: Boolean) {
        prefs.dockShowSeconds = show
        _uiState.value = _uiState.value.copy(dockShowSeconds = show)
        soundAndHaptics.performClickHaptic()
    }

    fun cycleDockClockScale() {
        val scales = com.marcioamaro.mediapod.data.preferences.DockClockScale.values()
        val currentIdx = scales.indexOf(_uiState.value.dockClockScale)
        val nextScale = scales[(currentIdx + 1) % scales.size]
        setDockClockScale(nextScale)
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

    fun setRandomHardwareColorsEnabled(enabled: Boolean) {
        prefs.randomHardwareColorsEnabled = enabled
        val currentSettings = _uiState.value.appearanceSettings

        val newSettings = if (enabled) {
            val newPalette = SafeIpodColorGenerator.generateSafePalette()
            currentSettings.copy(
                randomHardwareColorsEnabled = true,
                activePalette = newPalette,
                lastValidPalette = newPalette,
                lastGenerationId = System.currentTimeMillis()
            )
        } else {
            val manual = currentSettings.manualPalette
            currentSettings.copy(
                randomHardwareColorsEnabled = false,
                activePalette = manual
            )
        }

        _uiState.value = _uiState.value.copy(
            appearanceSettings = newSettings,
            customBodyColor = newSettings.activePalette.bodyColor,
            customWheelColor = newSettings.activePalette.wheelColor,
            customWheelTextColor = newSettings.activePalette.wheelTextColor,
            customCenterButtonColor = newSettings.activePalette.centerButtonColor
        )
        soundAndHaptics.performClickHaptic()
    }

    fun setChassisTheme(theme: IpodChassisTheme) {
        if (!_uiState.value.appearanceSettings.isManualColorEditingEnabled) return
        val currentSettings = _uiState.value.appearanceSettings
        val updatedManual = currentSettings.manualPalette.copy(bodyColor = theme.bodyColor)
        val updatedSettings = currentSettings.copy(
            manualPalette = updatedManual,
            activePalette = updatedManual,
            lastValidPalette = updatedManual
        )
        _uiState.value = _uiState.value.copy(
            chassisTheme = theme,
            customBodyColor = theme.bodyColor,
            appearanceSettings = updatedSettings
        )
        prefs.chassisTheme = theme
        prefs.customBodyColor = theme.bodyColor
        soundAndHaptics.performClickHaptic()
    }

    fun setCustomBodyColor(color: Long) {
        if (!_uiState.value.appearanceSettings.isManualColorEditingEnabled) return
        val currentSettings = _uiState.value.appearanceSettings
        val updatedManual = currentSettings.manualPalette.copy(bodyColor = color)
        val updatedSettings = currentSettings.copy(
            manualPalette = updatedManual,
            activePalette = updatedManual,
            lastValidPalette = updatedManual
        )
        _uiState.value = _uiState.value.copy(
            customBodyColor = color,
            appearanceSettings = updatedSettings
        )
        prefs.customBodyColor = color
        soundAndHaptics.performClickHaptic()
    }

    fun setBacklight(backlight: LcdBacklight) {
        _uiState.value = _uiState.value.copy(backlight = backlight)
        prefs.lcdBacklight = backlight
        soundAndHaptics.performClickHaptic()
    }

    fun setWheelPreset(preset: IpodWheelPreset) {
        if (!_uiState.value.appearanceSettings.isManualColorEditingEnabled) return
        val currentSettings = _uiState.value.appearanceSettings
        val updatedManual = currentSettings.manualPalette.copy(
            wheelColor = preset.wheelColor,
            wheelTextColor = preset.textColor,
            centerButtonColor = preset.centerButtonColor
        )
        val updatedSettings = currentSettings.copy(
            manualPalette = updatedManual,
            activePalette = updatedManual,
            lastValidPalette = updatedManual
        )
        _uiState.value = _uiState.value.copy(
            wheelPreset = preset,
            customWheelColor = preset.wheelColor,
            customWheelTextColor = preset.textColor,
            customCenterButtonColor = preset.centerButtonColor,
            appearanceSettings = updatedSettings
        )
        prefs.wheelPreset = preset
        prefs.customWheelColor = preset.wheelColor
        prefs.customWheelTextColor = preset.textColor
        prefs.customCenterButtonColor = preset.centerButtonColor
        soundAndHaptics.performClickHaptic()
    }

    fun setCustomWheelColors(wheel: Long, text: Long, center: Long) {
        if (!_uiState.value.appearanceSettings.isManualColorEditingEnabled) return
        val currentSettings = _uiState.value.appearanceSettings
        val updatedManual = currentSettings.manualPalette.copy(
            wheelColor = wheel,
            wheelTextColor = text,
            centerButtonColor = center
        )
        val updatedSettings = currentSettings.copy(
            manualPalette = updatedManual,
            activePalette = updatedManual,
            lastValidPalette = updatedManual
        )
        _uiState.value = _uiState.value.copy(
            wheelPreset = IpodWheelPreset.CUSTOM,
            customWheelColor = wheel,
            customWheelTextColor = text,
            customCenterButtonColor = center,
            appearanceSettings = updatedSettings
        )
        prefs.wheelPreset = IpodWheelPreset.CUSTOM
        prefs.customWheelColor = wheel
        prefs.customWheelTextColor = text
        prefs.customCenterButtonColor = center
        soundAndHaptics.performClickHaptic()
    }

    fun setCustomWheelColor(wheelColor: Long) {
        if (!_uiState.value.appearanceSettings.isManualColorEditingEnabled) return
        val optimalText = IpodColorContrastUtil.getOptimalWheelTextColor(wheelColor)
        val currentCenter = _uiState.value.appearanceSettings.manualPalette.centerButtonColor
        setCustomWheelColors(wheel = wheelColor, text = optimalText, center = currentCenter)
    }

    fun setCustomCenterButtonColor(centerButtonColor: Long) {
        if (!_uiState.value.appearanceSettings.isManualColorEditingEnabled) return
        val currentWheel = _uiState.value.appearanceSettings.manualPalette.wheelColor
        val currentText = _uiState.value.appearanceSettings.manualPalette.wheelTextColor
        setCustomWheelColors(wheel = currentWheel, text = currentText, center = centerButtonColor)
    }

    fun generateNewRandomHardwarePalette() {
        val currentSettings = _uiState.value.appearanceSettings
        if (!currentSettings.randomHardwareColorsEnabled) return

        val newPalette = SafeIpodColorGenerator.generateSafePalette()
        val newSettings = currentSettings.copy(
            activePalette = newPalette,
            lastValidPalette = newPalette,
            lastGenerationId = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(
            appearanceSettings = newSettings,
            customBodyColor = newPalette.bodyColor,
            customWheelColor = newPalette.wheelColor,
            customWheelTextColor = newPalette.wheelTextColor,
            customCenterButtonColor = newPalette.centerButtonColor
        )
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

    fun reloadPreferencesFromStorage() {
        val manualPalette = IpodPalette(
            bodyColor = prefs.customBodyColor,
            wheelColor = prefs.customWheelColor,
            wheelTextColor = prefs.customWheelTextColor,
            centerButtonColor = prefs.customCenterButtonColor
        )
        val updatedAppearance = _uiState.value.appearanceSettings.copy(
            manualPalette = manualPalette,
            activePalette = manualPalette,
            lastValidPalette = manualPalette
        )
        _uiState.value = _uiState.value.copy(
            chassisTheme = prefs.chassisTheme,
            backlight = prefs.lcdBacklight,
            wheelPreset = prefs.wheelPreset,
            appearanceSettings = updatedAppearance,
            customBodyColor = prefs.customBodyColor,
            customWheelColor = prefs.customWheelColor,
            customWheelTextColor = prefs.customWheelTextColor,
            customCenterButtonColor = prefs.customCenterButtonColor,
            fontSizeScale = prefs.fontSizeScale,
            isFontBold = prefs.isFontBold,
            customYouTubeVideos = prefs.getYouTubeVideos(),
            customPodcasts = podcastRepo.getCustomPodcasts(),
            customStations = prefs.getCustomStations(),
            recentsList = prefs.getRecentStations(),
            is24HourClock = prefs.is24HourClock
        )
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

    fun skipToNextChapter() {
        playerManager.skipToNextChapter()
        soundAndHaptics.performClickHaptic()
    }

    fun skipToPreviousChapter() {
        playerManager.skipToPreviousChapter()
        soundAndHaptics.performClickHaptic()
    }

    private fun refreshVisibleLibrary() {
        when (_uiState.value.currentScreen) {
            IpodScreenDestination.MP3_FOLDERS -> loadAudioFolders()
            IpodScreenDestination.VIDEO_FOLDERS -> loadVideoFolders()
            IpodScreenDestination.MP3_TRACKS_LIST -> _uiState.value.currentAudioFolder?.let { selectAudioFolder(it, false) }
            IpodScreenDestination.VIDEO_LIST -> _uiState.value.currentVideoFolder?.let { selectVideoFolder(it, false) }
            else -> Unit
        }
    }

    fun navigateTo(screen: IpodScreenDestination) {
        if (_uiState.value.isHoldLocked) return
        if (screen != _uiState.value.currentScreen) {
            navigationHistory.addLast(_uiState.value.currentScreen)
            _uiState.value = _uiState.value.copy(
                currentScreen = screen,
                selectedIndex = 0
            )
            refreshVisibleLibrary()
            if (screen == IpodScreenDestination.PODCASTS_SEARCH) searchPodcasts(_uiState.value.podcastSearchQuery)
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
            refreshVisibleLibrary()
            soundAndHaptics.performClickHaptic()
        } else if (_uiState.value.currentScreen != IpodScreenDestination.MAIN_MENU) {
            _uiState.value = _uiState.value.copy(
                currentScreen = IpodScreenDestination.MAIN_MENU,
                selectedIndex = 0
            )
            refreshVisibleLibrary()
            soundAndHaptics.performClickHaptic()
        }
    }

    fun onRotaryScroll(stepDelta: Int) {
        if (_uiState.value.isHoldLocked) return
        if (stepDelta == 0) return

        // In NOW PLAYING screen (Radio, MP3 or Podcast), rotating the Click Wheel changes the volume!
        if (_uiState.value.currentScreen == IpodScreenDestination.NOW_PLAYING_RDS ||
            _uiState.value.currentScreen == IpodScreenDestination.MP3_NOW_PLAYING ||
            _uiState.value.currentScreen == IpodScreenDestination.PODCAST_NOW_PLAYING) {
            // Bug fix #2: No modo FIXED o stepDelta pode ser > 1 dependendo do threshold acumulado.
            // Para o volume, normalizamos para ±1 (sinal), garantindo sempre um passo suave
            // e consistente independente do modo ou velocidade configurada na roda.
            val normalizedVolumeDelta = Integer.signum(stepDelta)
            adjustVolume(normalizedVolumeDelta * 0.04f)
            soundAndHaptics.performClickHaptic()
            return
        }

        // In VIDEO PLAYER screen, rotating the Click Wheel seeks forward or backward
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER) {
            if (stepDelta > 0) videoPlayerManager.forward10s() else videoPlayerManager.rewind10s()
            soundAndHaptics.performClickHaptic()
            return
        }

        // In BRICK GAME screen, rotating the Click Wheel moves the paddle raquete or rotates initials!
        if (_uiState.value.currentScreen == IpodScreenDestination.GAME_BRICK) {
            val delta = stepDelta * 0.05f
            val newPos = (_uiState.value.gamePaddlePosition + delta).coerceIn(0.12f, 0.88f)
            _uiState.value = _uiState.value.copy(
                gamePaddlePosition = newPos,
                gameWheelTicks = _uiState.value.gameWheelTicks + stepDelta
            )
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

    fun selectMenuItemDirect(index: Int) {
        if (_uiState.value.isHoldLocked) return
        _uiState.value = _uiState.value.copy(selectedIndex = index)
        onCenterButtonPress()
    }

    fun onCenterButtonPress() {
        if (_uiState.value.isHoldLocked) return
        val currentScreen = _uiState.value.currentScreen
        val index = _uiState.value.selectedIndex

        soundAndHaptics.performHeavyHaptic()

        when (currentScreen) {
            IpodScreenDestination.PERSONAL_LIBRARY -> Unit
            IpodScreenDestination.MAIN_MENU -> {
                when (index) {
                    0 -> navigateTo(IpodScreenDestination.RADIO_MENU)
                    1 -> navigateTo(IpodScreenDestination.PODCASTS_MENU)
                    2 -> {
                        loadAudioFolders()
                        navigateTo(IpodScreenDestination.MP3_FOLDERS)
                    }
                    3 -> {
                        loadVideoFolders()
                        navigateTo(IpodScreenDestination.VIDEO_FOLDERS)
                    }
                    4 -> {
                        loadYouTubeVideos()
                        navigateTo(IpodScreenDestination.YOUTUBE_VIDEOS_LIST)
                    }
                    5 -> navigateTo(IpodScreenDestination.EQUALIZER)
                    6 -> navigateTo(IpodScreenDestination.AUDIO_OUTPUT_MENU)
                    7 -> navigateTo(IpodScreenDestination.GAME_BRICK)
                    8 -> toggleDisplayMode()
                    9 -> enterDockMode()
                    10 -> navigateTo(IpodScreenDestination.SETTINGS_THEMES)
                    11 -> navigateTo(IpodScreenDestination.ABOUT)
                    12 -> navigateTo(IpodScreenDestination.PERSONAL_LIBRARY)
                    13 -> exitApplication()
                }
            }
            IpodScreenDestination.AUDIO_OUTPUT_MENU -> {
                val devices = audioRouteManager.availableDevices.value
                if (index in devices.indices) {
                    selectAudioDevice(devices[index])
                } else if (index == devices.size) {
                    showNativeAudioChooserDialog(getApplication())
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
                        loadTopBrazilStations()
                        navigateTo(IpodScreenDestination.TOP_BRAZIL)
                    }
                    4 -> {
                        loadTopStations()
                        navigateTo(IpodScreenDestination.TOP_WORLD)
                    }
                    5 -> navigateTo(IpodScreenDestination.GENRES_LIST)
                    6 -> {
                        resetSearchFiltersToDefault()
                        executeSearch()
                        navigateTo(IpodScreenDestination.SEARCH)
                    }
                    7 -> {
                        loadCustomStations()
                        navigateTo(IpodScreenDestination.RADIO_CUSTOM_LIST)
                    }
                }
            }
            IpodScreenDestination.PODCASTS_MENU -> {
                when (index) {
                    0 -> navigateTo(IpodScreenDestination.PODCAST_NOW_PLAYING)
                    1 -> navigateTo(IpodScreenDestination.PODCASTS_FAVORITES)
                    2 -> navigateTo(IpodScreenDestination.PODCASTS_RECENTS)
                    3 -> {
                        loadPodcastTopBrazil()
                        navigateTo(IpodScreenDestination.PODCASTS_TOP_BRAZIL)
                    }
                    4 -> {
                        loadPodcastTopWorld()
                        navigateTo(IpodScreenDestination.PODCASTS_TOP_WORLD)
                    }
                    5 -> {
                        loadPodcastCountries()
                        navigateTo(IpodScreenDestination.PODCASTS_COUNTRIES)
                    }
                    6 -> navigateTo(IpodScreenDestination.PODCASTS_SEARCH)
                    7 -> {
                        loadCustomPodcasts()
                        navigateTo(IpodScreenDestination.PODCASTS_CUSTOM_LIST)
                    }
                }
            }
            IpodScreenDestination.RADIO_CUSTOM_LIST -> {
                if (index == 0) {
                    navigateTo(IpodScreenDestination.ADD_CUSTOM_RADIO)
                } else {
                    val customIdx = index - 1
                    val list = _uiState.value.customStations
                    if (customIdx in list.indices) {
                        onRadioClicked(list[customIdx], QueueSource.GLOBAL, list)
                        navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                    }
                }
            }
            IpodScreenDestination.PODCASTS_CUSTOM_LIST -> {
                if (index == 0) {
                    navigateTo(IpodScreenDestination.ADD_CUSTOM_PODCAST)
                } else {
                    val customIdx = index - 1
                    val list = _uiState.value.customPodcasts
                    if (customIdx in list.indices) {
                        if (selectPodcastShow(list[customIdx])) navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST)
                    }
                }
            }
            IpodScreenDestination.PODCASTS_TOP_BRAZIL,
            IpodScreenDestination.PODCASTS_TOP_WORLD,
            IpodScreenDestination.PODCASTS_BY_CATEGORY,
            IpodScreenDestination.PODCASTS_BY_COUNTRY -> {
                val list = _uiState.value.podcastShows
                if (index in list.indices) {
                    if (selectPodcastShow(list[index])) navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST)
                }
            }
            IpodScreenDestination.PODCASTS_SEARCH -> {
                val list = _uiState.value.podcastSearchResults
                if (index in list.indices && selectPodcastShow(list[index])) {
                    navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST)
                }
            }
            IpodScreenDestination.PODCASTS_FAVORITES -> {
                val list = podcastFavorites.value
                if (index in list.indices) {
                    if (selectPodcastShow(list[index])) navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST)
                }
            }
            IpodScreenDestination.PODCASTS_RECENTS -> {
                val list = podcastRecents.value
                if (index in list.indices) {
                    if (selectPodcastShow(list[index])) navigateTo(IpodScreenDestination.PODCAST_EPISODES_LIST)
                }
            }
            IpodScreenDestination.PODCASTS_CATEGORIES -> {
                val list = _uiState.value.podcastCategories
                if (index in list.indices) {
                    loadPodcastsByCategory(list[index].name)
                    navigateTo(IpodScreenDestination.PODCASTS_BY_CATEGORY)
                }
            }
            IpodScreenDestination.PODCASTS_COUNTRIES -> {
                val list = _uiState.value.podcastCountries
                if (index in list.indices) {
                    loadPodcastsByCountry(list[index].code, list[index].name)
                    navigateTo(IpodScreenDestination.PODCASTS_BY_COUNTRY)
                }
            }
            IpodScreenDestination.PODCAST_EPISODES_LIST -> {
                val list = _uiState.value.podcastEpisodes
                if (index in list.indices) {
                    playPodcastEpisode(list[index])
                    navigateTo(IpodScreenDestination.PODCAST_NOW_PLAYING)
                }
            }
            IpodScreenDestination.PODCAST_NOW_PLAYING -> {
                playerManager.togglePlayPause()
            }
            IpodScreenDestination.PODCAST_CHAPTERS -> {
                val chapters = playerManager.currentPodcastChapters.value
                if (index in chapters.indices) {
                    selectPodcastChapter(chapters[index])
                }
            }
            IpodScreenDestination.MP3_FOLDERS -> {
                val folders = _uiState.value.localAudioFolders
                if (index in folders.indices) {
                    selectAudioFolder(folders[index])
                    navigateTo(IpodScreenDestination.MP3_TRACKS_LIST)
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
                val folders = _uiState.value.localVideoFolders
                if (index in folders.indices) {
                    selectVideoFolder(folders[index])
                    navigateTo(IpodScreenDestination.VIDEO_LIST)
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
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Favoritos")
                    onRadioClicked(list[index], QueueSource.FAVORITES, list)
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.RECENTS -> {
                val list = _uiState.value.recentsList
                if (index in list.indices) {
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Recentes")
                    onRadioClicked(list[index], QueueSource.RECENTS, list)
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.TOP_BRAZIL -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Top Brasil")
                    onRadioClicked(list[index], QueueSource.RANKING, list)
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.ADD_CUSTOM_RADIO,
            IpodScreenDestination.ADD_CUSTOM_PODCAST,
            IpodScreenDestination.ADD_CUSTOM_YOUTUBE -> {
                // Handled by custom URL input screen
            }
            IpodScreenDestination.YOUTUBE_VIDEOS_LIST -> {
                if (index == 0) {
                    navigateTo(IpodScreenDestination.ADD_CUSTOM_YOUTUBE)
                } else {
                    val videoIndex = index - 1
                    val list = _uiState.value.customYouTubeVideos
                    if (videoIndex in list.indices) {
                        playYouTubeVideo(list[videoIndex])
                    }
                }
            }
            IpodScreenDestination.YOUTUBE_PLAYER -> {
                // Handled in player screen
            }
            IpodScreenDestination.TOP_WORLD -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Top Mundial")
                    onRadioClicked(list[index], QueueSource.RANKING, list)
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.STATIONS_BY_GENRE -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    _uiState.value = _uiState.value.copy(activeCategoryName = _uiState.value.activeGenre?.name ?: "Gênero")
                    onRadioClicked(list[index], QueueSource.CATEGORY, list)
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.STATIONS_BY_COUNTRY -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    _uiState.value = _uiState.value.copy(activeCategoryName = _uiState.value.activeCountry?.name ?: "País")
                    onRadioClicked(list[index], QueueSource.CATEGORY, list)
                    navigateTo(IpodScreenDestination.NOW_PLAYING_RDS)
                }
            }
            IpodScreenDestination.SEARCH -> {
                val list = _uiState.value.stationsList
                if (index in list.indices) {
                    _uiState.value = _uiState.value.copy(activeCategoryName = "Busca")
                    onRadioClicked(list[index], QueueSource.SEARCH, list)
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
                _brickGameCenterAction.value = System.currentTimeMillis()
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
        if (_uiState.value.currentScreen == IpodScreenDestination.GAME_BRICK) {
            _brickGameCenterAction.value = System.currentTimeMillis()
            return
        }
        if (_uiState.value.currentScreen == IpodScreenDestination.VIDEO_PLAYER) {
            videoPlayerManager.togglePlayPause()
        } else {
            playerManager.togglePlayPause()
        }
    }

    fun onNextTrackPress() {
        if (_uiState.value.isHoldLocked) return
        soundAndHaptics.performClickHaptic()

        val currentScreen = _uiState.value.currentScreen
        val activeMedia = when {
            currentScreen == IpodScreenDestination.YOUTUBE_PLAYER -> ActiveMediaType.YOUTUBE_STREAM
            currentScreen == IpodScreenDestination.VIDEO_PLAYER -> ActiveMediaType.LOCAL_VIDEO
            currentScreen == IpodScreenDestination.PODCAST_NOW_PLAYING || currentScreen == IpodScreenDestination.PODCAST_CHAPTERS -> ActiveMediaType.PODCAST_EPISODE
            currentScreen == IpodScreenDestination.MP3_NOW_PLAYING -> ActiveMediaType.LOCAL_AUDIO
            currentScreen == IpodScreenDestination.NOW_PLAYING_RDS -> ActiveMediaType.LIVE_RADIO
            else -> playerManager.activeMediaType.value
        }

        when (activeMedia) {
            ActiveMediaType.YOUTUBE_STREAM -> {
                nextYouTubeVideo()
            }
            ActiveMediaType.LOCAL_VIDEO -> {
                nextVideoWithFolderWrap()
            }
            ActiveMediaType.PODCAST_EPISODE -> {
                if (currentScreen == IpodScreenDestination.PODCAST_CHAPTERS && playerManager.currentPodcastChapters.value.isNotEmpty()) {
                    playerManager.skipToNextChapter()
                } else {
                    val episodes = _uiState.value.podcastEpisodes
                    val current = currentPodcastEpisode.value
                    val idx = episodes.indexOfFirst { it.id == current?.id }
                    if (idx in 0 until episodes.size - 1) {
                        playPodcastEpisode(episodes[idx + 1])
                    } else {
                        playerManager.nextPodcastEpisode()
                    }
                }
            }
            ActiveMediaType.LOCAL_AUDIO -> {
                nextLocalTrackWithFolderWrap()
            }
            ActiveMediaType.LIVE_RADIO -> {
                radioApp.playbackCoordinator.skipToNext()
            }
        }
    }

    fun onPrevTrackPress() {
        if (_uiState.value.isHoldLocked) return
        soundAndHaptics.performClickHaptic()

        val currentScreen = _uiState.value.currentScreen
        val activeMedia = when {
            currentScreen == IpodScreenDestination.YOUTUBE_PLAYER -> ActiveMediaType.YOUTUBE_STREAM
            currentScreen == IpodScreenDestination.VIDEO_PLAYER -> ActiveMediaType.LOCAL_VIDEO
            currentScreen == IpodScreenDestination.PODCAST_NOW_PLAYING || currentScreen == IpodScreenDestination.PODCAST_CHAPTERS -> ActiveMediaType.PODCAST_EPISODE
            currentScreen == IpodScreenDestination.MP3_NOW_PLAYING -> ActiveMediaType.LOCAL_AUDIO
            currentScreen == IpodScreenDestination.NOW_PLAYING_RDS -> ActiveMediaType.LIVE_RADIO
            else -> playerManager.activeMediaType.value
        }

        when (activeMedia) {
            ActiveMediaType.YOUTUBE_STREAM -> {
                prevYouTubeVideo()
            }
            ActiveMediaType.LOCAL_VIDEO -> {
                prevVideoWithFolderWrap()
            }
            ActiveMediaType.PODCAST_EPISODE -> {
                if (currentScreen == IpodScreenDestination.PODCAST_CHAPTERS && playerManager.currentPodcastChapters.value.isNotEmpty()) {
                    playerManager.skipToPreviousChapter()
                } else if (audioPositionMs.value > 3000L) {
                    playerManager.seekToPosition(0L)
                } else {
                    val episodes = _uiState.value.podcastEpisodes
                    val current = currentPodcastEpisode.value
                    val idx = episodes.indexOfFirst { it.id == current?.id }
                    if (idx > 0) {
                        playPodcastEpisode(episodes[idx - 1])
                    } else {
                        playerManager.prevPodcastEpisode()
                    }
                }
            }
            ActiveMediaType.LOCAL_AUDIO -> {
                if (audioPositionMs.value > 3000L) {
                    playerManager.seekToPosition(0L)
                } else {
                    prevLocalTrackWithFolderWrap()
                }
            }
            ActiveMediaType.LIVE_RADIO -> {
                radioApp.playbackCoordinator.skipToPrevious()
            }
        }
    }

    fun nextLocalTrackWithFolderWrap() = stepAudioQueue(1)
    fun prevLocalTrackWithFolderWrap() = stepAudioQueue(-1)
    private fun stepAudioQueue(delta: Int) {
        val queue = audioPlaybackQueue
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { it.id == playerManager.currentLocalAudio.value?.id }
        playLocalAudio(queue[Math.floorMod(index + delta, queue.size)], queue)
    }
    fun nextVideoWithFolderWrap() = stepVideoQueue(1)
    fun prevVideoWithFolderWrap() = stepVideoQueue(-1)
    private fun stepVideoQueue(delta: Int) {
        val queue = videoPlaybackQueue
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { it.id == videoPlayerManager.currentVideo.value?.id }
        playLocalVideo(queue[Math.floorMod(index + delta, queue.size)])
    }

    fun onRadioClicked(radio: RadioStation, source: QueueSource, fullList: List<RadioStation>) {
        // 1. Atualiza o contexto ANTES de iniciar a reprodução
        val context = NavigationContext(
            source = source,
            queryId = when (source) {
                QueueSource.SEARCH -> _uiState.value.searchQuery
                QueueSource.CATEGORY -> _uiState.value.activeGenre?.name ?: _uiState.value.activeCountry?.name
                QueueSource.RANKING -> _uiState.value.activeCategoryName
                else -> source.name
            },
            items = fullList.map { it.toPlaybackQueueItem() }
        )
        radioApp.playbackCoordinator.setNavigationContext(context)
        playbackQueue = fullList
        playerManager.updatePlaylist(fullList)

        prefs.saveLastPlayedStation(radio)
        prefs.addRecentStation(radio)
        _uiState.value = _uiState.value.copy(
            recentsList = prefs.getRecentStations(),
            currentYouTubeVideo = null
        )

        // 2. Inicia a reprodução
        radioApp.playbackCoordinator.play(radio.toPlaybackQueueItem())
    }

    fun playStation(station: RadioStation) {
        val fullList = if (playbackQueue.any { it.id == station.id }) {
            playbackQueue
        } else if (_uiState.value.stationsList.any { it.id == station.id }) {
            _uiState.value.stationsList
        } else if (favorites.value.any { it.id == station.id }) {
            favorites.value
        } else if (_uiState.value.recentsList.any { it.id == station.id }) {
            _uiState.value.recentsList
        } else {
            listOf(station) + CuratedData.CURATED_GLOBAL_STATIONS
        }

        val source = when {
            favorites.value.any { it.id == station.id } && _uiState.value.stationsList == favorites.value -> QueueSource.FAVORITES
            _uiState.value.recentsList.any { it.id == station.id } && _uiState.value.stationsList == _uiState.value.recentsList -> QueueSource.RECENTS
            _uiState.value.searchQuery.isNotBlank() -> QueueSource.SEARCH
            _uiState.value.activeCategoryName.contains("Top", ignoreCase = true) -> QueueSource.RANKING
            _uiState.value.activeGenre != null || _uiState.value.activeCountry != null -> QueueSource.CATEGORY
            else -> QueueSource.GLOBAL
        }

        onRadioClicked(station, source, fullList)
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

    fun clearRecentStations() {
        prefs.clearRecentStations()
        _uiState.value = _uiState.value.copy(
            recentsList = emptyList(),
            stationsList = if (_uiState.value.activeCategoryName == "Recentes") emptyList() else _uiState.value.stationsList
        )
        if (_uiState.value.activeCategoryName == "Recentes") {
            playbackQueue = emptyList()
        }
        soundAndHaptics.performHeavyHaptic()
    }

    fun clearRecentPodcasts() {
        podcastRepo.clearRecents()
        soundAndHaptics.performHeavyHaptic()
    }

    fun clearAllRecents() {
        clearRecentStations()
        clearRecentPodcasts()
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
            searchGenreTag = genre.tag,
            searchQuery = "",
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
        val isBrazil = country.code.equals("BR", ignoreCase = true) || country.name.contains("Brasil", ignoreCase = true)
        val defaultState = if (isBrazil) "SP" else "ALL"
        val defaultCity = if (isBrazil) "São Paulo" else "ALL"
        _uiState.value = _uiState.value.copy(
            activeCountry = country,
            activeCategoryName = country.name,
            searchCountryCode = country.code,
            searchStateCode = defaultState,
            searchCity = defaultCity,
            availableCities = emptyList(),
            isLoadingCities = isBrazil,
            searchQuery = "",
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
            if (isBrazil) {
                onSearchStateChanged("SP", defaultCity = "São Paulo")
            }
        }
    }

    fun loadTopStations() {
        _uiState.value = _uiState.value.copy(
            isLoadingList = true,
            activeCategoryName = "Top Mundial"
        )
        viewModelScope.launch {
            val stations = RadioRankingRepository.getInstance().getTopWorld(limit = 20)
            val favIds = repository.getFavoriteIdsSet()
            val mapped = stations.map { it.copy(isFavorite = favIds.contains(it.id)) }
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = mapped
            )
            playbackQueue = mapped
        }
    }

    fun resetSearchFiltersToDefault() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchCountryCode = "ALL",
            searchGenreTag = "ALL",
            searchStateCode = "ALL",
            searchCity = "ALL",
            availableCities = emptyList(),
            isLoadingCities = false
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        executeSearch(debounce = true)
    }

    fun onSearchCountryChanged(countryCode: String) {
        val isBrazil = countryCode.equals("BR", ignoreCase = true) || countryCode.equals("Brasil", ignoreCase = true)
        val defaultState = if (isBrazil) "SP" else "ALL"
        val defaultCity = if (isBrazil) "São Paulo" else "ALL"
        _uiState.value = _uiState.value.copy(
            searchCountryCode = countryCode,
            searchStateCode = defaultState,
            searchCity = defaultCity,
            availableCities = emptyList(),
            isLoadingCities = isBrazil
        )
        if (isBrazil) {
            onSearchStateChanged("SP", defaultCity = "São Paulo")
        } else {
            executeSearch()
        }
    }

    fun onSearchGenreChanged(genreTag: String) {
        val cleanGenre = if (genreTag.isBlank()) "ALL" else genreTag
        _uiState.value = _uiState.value.copy(searchGenreTag = cleanGenre)
        executeSearch()
    }

    fun onSearchStateChanged(stateCode: String, defaultCity: String? = null) {
        val cleanState = if (stateCode.isBlank()) "ALL" else stateCode
        val isBrazil = _uiState.value.searchCountryCode.equals("BR", ignoreCase = true) ||
                       _uiState.value.searchCountryCode.equals("ALL", ignoreCase = true) ||
                       _uiState.value.searchCountryCode.isBlank()
        val isSp = isBrazil && cleanState.equals("SP", ignoreCase = true)
        val chosenCity = defaultCity ?: if (isSp) "São Paulo" else "ALL"
        
        _uiState.value = _uiState.value.copy(
            searchStateCode = cleanState,
            searchCity = chosenCity,
            availableCities = emptyList(),
            isLoadingCities = isBrazil && cleanState != "ALL"
        )

        if (isBrazil && cleanState != "ALL") {
            viewModelScope.launch {
                val rawCities = com.marcioamaro.mediapod.data.remote.IbgeLocationService.getCitiesForState(cleanState)
                // Garante que "São Paulo" apareça em primeiro na lista de cidades
                val sortedCities = if (isSp) {
                    val spIndex = rawCities.indexOfFirst { it.equals("São Paulo", ignoreCase = true) }
                    if (spIndex > 0) {
                        val list = rawCities.toMutableList()
                        val sp = list.removeAt(spIndex)
                        listOf(sp) + list
                    } else rawCities
                } else rawCities

                _uiState.value = _uiState.value.copy(
                    availableCities = sortedCities,
                    isLoadingCities = false
                )
            }
        }

        executeSearch()
    }

    fun onSearchCityChanged(city: String) {
        _uiState.value = _uiState.value.copy(searchCity = city)
        executeSearch()
    }

    fun loadTopBrazilStations() {
        val brazil = CuratedData.COUNTRIES.firstOrNull { it.code.equals("BR", ignoreCase = true) }
            ?: CountryCategory("BR", "Brasil", "🇧🇷", "América do Sul")
        _uiState.value = _uiState.value.copy(
            activeCountry = brazil,
            activeCategoryName = "Top 20 Brasil",
            isLoadingList = true,
            stationsList = emptyList()
        )
        viewModelScope.launch {
            val stations = RadioRankingRepository.getInstance().getTopBrazil(limit = 20)
            val favIds = repository.getFavoriteIdsSet()
            val mapped = stations.map { it.copy(isFavorite = favIds.contains(it.id)) }
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = mapped
            )
            playbackQueue = mapped
        }
    }

    private var radioSearchJob: kotlinx.coroutines.Job? = null

    fun executeSearch(debounce: Boolean = false) {
        radioSearchJob?.cancel()
        val query = _uiState.value.searchQuery.trim()
        val currentScreen = _uiState.value.currentScreen

        val effectiveCountry = when (currentScreen) {
            IpodScreenDestination.STATIONS_BY_COUNTRY -> _uiState.value.activeCountry?.code ?: _uiState.value.searchCountryCode
            IpodScreenDestination.TOP_BRAZIL -> "BR"
            else -> _uiState.value.searchCountryCode
        }

        val effectiveGenre = when (currentScreen) {
            IpodScreenDestination.STATIONS_BY_GENRE -> _uiState.value.activeGenre?.tag ?: _uiState.value.searchGenreTag
            else -> _uiState.value.searchGenreTag
        }

        val state = _uiState.value.searchStateCode
        val city = _uiState.value.searchCity

        radioSearchJob = viewModelScope.launch {
            if (debounce) kotlinx.coroutines.delay(250)
            _uiState.value = _uiState.value.copy(isLoadingList = true)
            val results = if (query.isBlank() && currentScreen == IpodScreenDestination.STATIONS_BY_COUNTRY && _uiState.value.activeCountry != null) {
                repository.getStationsByCountry(_uiState.value.activeCountry!!.code)
            } else if (query.isBlank() && currentScreen == IpodScreenDestination.STATIONS_BY_GENRE && _uiState.value.activeGenre != null) {
                repository.getStationsByGenre(_uiState.value.activeGenre!!.tag)
            } else if (currentScreen == IpodScreenDestination.TOP_BRAZIL) {
                val stations = RadioRankingRepository.getInstance().getTopBrazil(limit = 20)
                    .filter { com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query) }
                val favIds = repository.getFavoriteIdsSet()
                stations.map { it.copy(isFavorite = favIds.contains(it.id)) }
            } else if (currentScreen == IpodScreenDestination.TOP_WORLD) {
                val stations = RadioRankingRepository.getInstance().getTopWorld(limit = 20)
                    .filter { com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query) }
                val favIds = repository.getFavoriteIdsSet()
                stations.map { it.copy(isFavorite = favIds.contains(it.id)) }
            } else {
                repository.searchStations(
                    query = query,
                    countryCode = effectiveCountry,
                    genreTag = effectiveGenre,
                    stateCode = state,
                    city = city
                )
            }

            val categoryName = when (currentScreen) {
                IpodScreenDestination.STATIONS_BY_COUNTRY -> _uiState.value.activeCountry?.name ?: "País"
                IpodScreenDestination.STATIONS_BY_GENRE -> _uiState.value.activeGenre?.name ?: "Gênero"
                IpodScreenDestination.TOP_BRAZIL -> "Top 20 Brasil"
                IpodScreenDestination.TOP_WORLD -> "Top 20 Mundial"
                else -> if (query.isNotBlank()) "Busca: $query" else "Busca"
            }

            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                stationsList = results,
                activeCategoryName = categoryName
            )
            playbackQueue = results
        }
    }

    fun adjustVolume(delta: Float) {
        val current = radioApp.playbackCoordinator.activeVolume.value
        val target = (current + delta).coerceIn(0f, 1f)
        radioApp.playbackCoordinator.setActiveVolume(target)
        prefs.volumeLevel = target
    }

    fun setVolume(vol: Float) {
        val target = vol.coerceIn(0f, 1f)
        radioApp.playbackCoordinator.setActiveVolume(target)
        prefs.volumeLevel = target
    }

    fun syncVolumeFromSystem() {
        val vm = com.marcioamaro.mediapod.audio.VolumeManager.getInstance(radioApp)
        vm.setSystemVolume(vm.getSystemVolume())
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
        val isTrackOrEpisode = playerManager.activeMediaType.value != ActiveMediaType.LIVE_RADIO
        val next = when (current) {
            0 -> 15
            15 -> 30
            30 -> 45
            45 -> 60
            60 -> if (isTrackOrEpisode) -1 else 0
            -1 -> 0
            else -> 0
        }
        setSleepTimer(next)
    }

    fun loadAudioFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingList = true)
            val allTracks = localMediaRepo.getAllAudioTracks()
            val folders = mediaLibrary.folders(LibraryKind.AUDIO, localMediaRepo.getAudioFolders(), allTracks.map { it.libraryKey() }.toSet())
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localAudioFolders = folders
            )
        }
    }

    fun selectAudioFolder(folder: com.marcioamaro.mediapod.data.model.MediaFolder, resetSelection: Boolean = true) {
        val selectedFolder = if (folder.path.startsWith("playlist:")) {
            val playlist = mediaLibrary.state.value.playlists.firstOrNull { "playlist:${it.id}" == folder.path }
            if (playlist == null) { navigateBack(); return }
            folder.copy(name = "Playlist · ${playlist.name}")
        } else folder
        _uiState.value = _uiState.value.copy(
            currentAudioFolder = selectedFolder,
            selectedIndex = if (resetSelection) 0 else _uiState.value.selectedIndex,
            isLoadingList = true,
            localAudioTracks = if (resetSelection) emptyList() else _uiState.value.localAudioTracks
        )
        viewModelScope.launch {
            val tracks = if (folder.path.startsWith("library:") || folder.path.startsWith("playlist:")) {
                mediaLibrary.select(folder.path, localMediaRepo.getAllAudioTracks()) { it.libraryKey() }
            } else localMediaRepo.getTracksByFolder(folder.name, folder.path)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localAudioTracks = tracks,
                selectedIndex = _uiState.value.selectedIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
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
                localAudioTracks = tracks,
                selectedIndex = _uiState.value.selectedIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun playLocalAudio(track: com.marcioamaro.mediapod.data.model.LocalAudioTrack, queue: List<com.marcioamaro.mediapod.data.model.LocalAudioTrack> = emptyList()) {
        videoPlayerManager.pause()
        _uiState.value = _uiState.value.copy(currentYouTubeVideo = null)
        audioPlaybackQueue = queue.ifEmpty { _uiState.value.localAudioTracks }.ifEmpty { listOf(track) }
        playerManager.playLocalAudio(track, audioPlaybackQueue)
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
            val allVideos = localMediaRepo.getAllVideoTracks()
            val folders = mediaLibrary.folders(LibraryKind.VIDEO, localMediaRepo.getVideoFolders(), allVideos.map { it.libraryKey() }.toSet())
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localVideoFolders = folders
            )
        }
    }

    fun selectVideoFolder(folder: com.marcioamaro.mediapod.data.model.MediaFolder, resetSelection: Boolean = true) {
        val selectedFolder = if (folder.path.startsWith("playlist:")) {
            val playlist = mediaLibrary.state.value.playlists.firstOrNull { "playlist:${it.id}" == folder.path }
            if (playlist == null) { navigateBack(); return }
            folder.copy(name = "Playlist · ${playlist.name}")
        } else folder
        _uiState.value = _uiState.value.copy(
            currentVideoFolder = selectedFolder,
            selectedIndex = if (resetSelection) 0 else _uiState.value.selectedIndex,
            isLoadingList = true,
            localVideoTracks = if (resetSelection) emptyList() else _uiState.value.localVideoTracks
        )
        viewModelScope.launch {
            val videos = if (folder.path.startsWith("library:") || folder.path.startsWith("playlist:")) {
                mediaLibrary.select(folder.path, localMediaRepo.getAllVideoTracks()) { it.libraryKey() }
            } else localMediaRepo.getVideosByFolder(folder.name, folder.path)
            _uiState.value = _uiState.value.copy(
                isLoadingList = false,
                localVideoTracks = videos,
                selectedIndex = _uiState.value.selectedIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
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
                localVideoTracks = videos,
                selectedIndex = _uiState.value.selectedIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun playLocalVideo(video: com.marcioamaro.mediapod.data.model.LocalVideoTrack) {
        playerManager.pause()
        playerManager.setActiveMediaType(ActiveMediaType.LOCAL_VIDEO)
        if (videoPlaybackQueue.none { it.id == video.id } || _uiState.value.currentScreen == IpodScreenDestination.VIDEO_LIST) {
            videoPlaybackQueue = _uiState.value.localVideoTracks.ifEmpty { listOf(video) }
        }
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

    fun exitApplication() {
        com.marcioamaro.mediapod.util.AppRestartHelper.exitApp(getApplication())
    }

    private fun getItemCountForCurrentScreen(): Int {
        return when (_uiState.value.currentScreen) {
            IpodScreenDestination.PERSONAL_LIBRARY -> 0
            IpodScreenDestination.MAIN_MENU -> 14
            IpodScreenDestination.AUDIO_OUTPUT_MENU -> audioRouteManager.availableDevices.value.size
            IpodScreenDestination.RADIO_MENU -> 9
            IpodScreenDestination.PODCASTS_MENU -> 9
            IpodScreenDestination.RADIO_CUSTOM_LIST -> _uiState.value.customStations.size + 1
            IpodScreenDestination.ADD_CUSTOM_RADIO -> 0
            IpodScreenDestination.PODCASTS_CUSTOM_LIST -> _uiState.value.customPodcasts.size + 1
            IpodScreenDestination.ADD_CUSTOM_PODCAST -> 0
            IpodScreenDestination.YOUTUBE_VIDEOS_LIST -> _uiState.value.customYouTubeVideos.size + 1
            IpodScreenDestination.ADD_CUSTOM_YOUTUBE -> 0
            IpodScreenDestination.YOUTUBE_PLAYER -> 0
            IpodScreenDestination.PODCASTS_FAVORITES -> podcastFavorites.value.size
            IpodScreenDestination.PODCASTS_RECENTS -> podcastRecents.value.size
            IpodScreenDestination.PODCASTS_TOP_BRAZIL,
            IpodScreenDestination.PODCASTS_TOP_WORLD,
            IpodScreenDestination.PODCASTS_BY_CATEGORY,
            IpodScreenDestination.PODCASTS_BY_COUNTRY -> _uiState.value.podcastShows.size
            IpodScreenDestination.PODCASTS_SEARCH -> _uiState.value.podcastSearchResults.size
            IpodScreenDestination.PODCASTS_CATEGORIES -> _uiState.value.podcastCategories.size
            IpodScreenDestination.PODCASTS_COUNTRIES -> _uiState.value.podcastCountries.size
            IpodScreenDestination.PODCAST_EPISODES_LIST -> _uiState.value.podcastEpisodes.size
            IpodScreenDestination.PODCAST_NOW_PLAYING -> 0
            IpodScreenDestination.PODCAST_CHAPTERS -> playerManager.currentPodcastChapters.value.size
            IpodScreenDestination.MP3_FOLDERS -> _uiState.value.localAudioFolders.size
            IpodScreenDestination.MP3_TRACKS_LIST -> _uiState.value.localAudioTracks.size
            IpodScreenDestination.MP3_NOW_PLAYING -> 0
            IpodScreenDestination.VIDEO_FOLDERS -> _uiState.value.localVideoFolders.size
            IpodScreenDestination.VIDEO_LIST -> _uiState.value.localVideoTracks.size
            IpodScreenDestination.VIDEO_PLAYER -> 0
            IpodScreenDestination.FAVORITES -> favorites.value.size
            IpodScreenDestination.RECENTS -> _uiState.value.recentsList.size
            IpodScreenDestination.TOP_BRAZIL,
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

    override fun onCleared() {
        videoPlayerManager.onVideoEnded = null
        playerManager.onFolderWrapNext = null
        playerManager.onFolderWrapPrev = null
        super.onCleared()
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
