package com.marcioamaro.mediapod.player

import com.marcioamaro.mediapod.data.repository.libraryKey
import android.content.Context
import android.content.Intent
import com.marcioamaro.mediapod.R
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.PlaybackParameters
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.ChapterTocFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.marcioamaro.mediapod.data.model.PodcastChapter
import com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.player.coordinator.toRadioStation
import com.marcioamaro.mediapod.service.RadioMediaService
import com.marcioamaro.mediapod.util.NetworkConnectivityValidator
import com.marcioamaro.mediapod.util.NetworkStatus
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class RadioPlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR,
    NO_INTERNET
}

data class RdsInfo(
    val programService: String = "IPOD RDS",
    val radioText: String = "",
    val hasRealRds: Boolean = false,
    val programType: String = "POP MUSIC",
    val signalStrengthBars: Int = 5,
    val isStereo: Boolean = true,
    val hasTrafficProgram: Boolean = true,
    val bitrateInfo: String = "128 kbps AAC",
    val frequencyMhz: String = "104.5 MHz"
)

class RadioPlayerManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var wifiLock: WifiManager.WifiLock? = null

    private val _playbackStatus = MutableStateFlow(RadioPlaybackStatus.IDLE)
    val playbackStatus: StateFlow<RadioPlaybackStatus> = _playbackStatus.asStateFlow()

    fun setPlaybackStatusDirect(status: RadioPlaybackStatus) {
        _playbackStatus.value = status
    }

    fun setAudioPositionDirect(positionMs: Long) {
        if (_activeMediaType.value == ActiveMediaType.LIVE_RADIO || _currentStation.value != null) {
            return
        }
        _audioPositionMs.value = positionMs
        updateCurrentChapter(positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: _audioPositionMs.value

    fun pauseLocalOnly() {
        try {
            exoPlayer?.pause()
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Falha ao pausar ExoPlayer local: ${e.message}")
        }
        releaseLocks()
        stopVisualizer()
    }

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _rdsInfo = MutableStateFlow(RdsInfo())
    val rdsInfo: StateFlow<RdsInfo> = _rdsInfo.asStateFlow()

    private val _nowPlaying = MutableStateFlow(
        NowPlayingMetadata(
            title = "Rádio Pod",
            artist = "[sem informações]",
            album = "Ao Vivo",
            artworkUri = null,
            isLiveStream = true,
            hasTrackInfo = false
        )
    )
    val nowPlaying: StateFlow<NowPlayingMetadata> = _nowPlaying.asStateFlow()

    private val _visualizerAmplitudes = MutableStateFlow(List(16) { 0.1f })
    val visualizerAmplitudes: StateFlow<List<Float>> = _visualizerAmplitudes.asStateFlow()

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerSecondsRemaining = MutableStateFlow(0L)
    val sleepTimerSecondsRemaining: StateFlow<Long> = _sleepTimerSecondsRemaining.asStateFlow()

    // Active Media Type (Isolamento exclusivo de fila de reprodução)
    private val _activeMediaType = MutableStateFlow<ActiveMediaType>(ActiveMediaType.LIVE_RADIO)
    val activeMediaType: StateFlow<ActiveMediaType> = _activeMediaType.asStateFlow()

    fun setActiveMediaType(type: ActiveMediaType) {
        _activeMediaType.value = type
    }

    // Local Audio Playback Support (MP3 Player)
    private val _currentLocalAudio = MutableStateFlow<com.marcioamaro.mediapod.data.model.LocalAudioTrack?>(null)
    val currentLocalAudio: StateFlow<com.marcioamaro.mediapod.data.model.LocalAudioTrack?> = _currentLocalAudio.asStateFlow()

    // Podcast Playback Support
    private val _currentPodcastEpisode = MutableStateFlow<com.marcioamaro.mediapod.data.model.PodcastEpisode?>(null)
    val currentPodcastEpisode: StateFlow<com.marcioamaro.mediapod.data.model.PodcastEpisode?> = _currentPodcastEpisode.asStateFlow()
    private val _currentPodcastShow = MutableStateFlow<com.marcioamaro.mediapod.data.model.PodcastShow?>(null)
    val currentPodcastShow: StateFlow<com.marcioamaro.mediapod.data.model.PodcastShow?> = _currentPodcastShow.asStateFlow()
    private var podcastQueue: List<com.marcioamaro.mediapod.data.model.PodcastEpisode> = emptyList()

    // Live Radio Continuous Session Duration Timer
    private val _liveSessionDurationSeconds = MutableStateFlow(0L)
    val liveSessionDurationSeconds: StateFlow<Long> = _liveSessionDurationSeconds.asStateFlow()
    private var liveSessionJob: Job? = null

    // Auto-reconnect loop on network loss (runs every 10s)
    private var autoReconnectJob: Job? = null

    private val _audioPositionMs = MutableStateFlow(0L)
    val audioPositionMs: StateFlow<Long> = _audioPositionMs.asStateFlow()

    private val _audioDurationMs = MutableStateFlow(0L)
    val audioDurationMs: StateFlow<Long> = _audioDurationMs.asStateFlow()

    // Global Equalizer & DSP Loudness Engine (Rádio ao Vivo e MP3 Local)
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    val EQUALIZER_PRESETS: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
        "Rock" to listOf(5.0f, 3.0f, -1.5f, 3.0f, 5.0f),
        "Pop" to listOf(-1.0f, 2.0f, 4.0f, 2.5f, 1.0f),
        "Bass Booster" to listOf(7.0f, 4.5f, 1.0f, 0.0f, -1.5f),
        "Voz / Podcast" to listOf(-4.0f, -1.5f, 4.5f, 4.0f, 1.5f),
        "Jazz" to listOf(3.5f, 2.0f, 0.0f, 2.0f, 3.5f),
        "Clássica" to listOf(4.0f, 2.0f, -0.5f, 2.5f, 4.0f),
        "Eletrônica" to listOf(6.0f, 4.0f, -1.0f, 3.0f, 5.5f),
        "Blues" to listOf(3.0f, 2.0f, 1.0f, 2.5f, 3.0f),
        "Loudness" to listOf(6.0f, 2.5f, 0.0f, 2.0f, 5.0f),
        "Personalizado" to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
    )

    private val eqPrefs = context.getSharedPreferences("radiopod_equalizer", Context.MODE_PRIVATE)

    private val _isEqualizerEnabled = MutableStateFlow(eqPrefs.getBoolean("eq_enabled", true))
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _isLoudnessEnabled = MutableStateFlow(eqPrefs.getBoolean("loudness_enabled", false))
    val isLoudnessEnabled: StateFlow<Boolean> = _isLoudnessEnabled.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(eqPrefs.getInt("loudness_gain", 400)) // +4.0 dB
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(eqPrefs.getString("eq_preset", "Rock") ?: "Rock")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private fun loadSavedEqualizerBands(): List<Float> {
        val preset = eqPrefs.getString("eq_preset", "Rock") ?: "Rock"
        val defaultBands = EQUALIZER_PRESETS[preset] ?: EQUALIZER_PRESETS["Rock"] ?: listOf(0f, 0f, 0f, 0f, 0f)
        return List(5) { i ->
            eqPrefs.getFloat("eq_band_$i", defaultBands[i])
        }
    }

    private val _equalizerBands = MutableStateFlow<List<Float>>(loadSavedEqualizerBands())
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    val ipodPrefs = IpodPreferencesManager.getInstance(context)

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float, isPodcast: Boolean) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = safeSpeed
        // PlaybackParameters(speed, 1.0f) mantém a correção de pitch via Sonic, evitando distorção de voz
        exoPlayer?.playbackParameters = PlaybackParameters(safeSpeed, 1.0f)
        if (isPodcast) {
            ipodPrefs.podcastPlaybackSpeed = safeSpeed
        } else {
            ipodPrefs.localMediaPlaybackSpeed = safeSpeed
        }
    }

    private val _currentPodcastChapters = MutableStateFlow<List<PodcastChapter>>(emptyList())
    val currentPodcastChapters: StateFlow<List<PodcastChapter>> = _currentPodcastChapters.asStateFlow()

    private val _currentChapter = MutableStateFlow<PodcastChapter?>(null)
    val currentChapter: StateFlow<PodcastChapter?> = _currentChapter.asStateFlow()

    fun updateCurrentChapter(posMs: Long) {
        val chapters = _currentPodcastChapters.value
        if (chapters.isEmpty()) {
            _currentChapter.value = null
            return
        }
        val ch = chapters.lastOrNull { it.startTimeMs <= posMs }
        if (_currentChapter.value != ch) {
            _currentChapter.value = ch
        }
    }

    fun addEmbeddedChapter(chapter: PodcastChapter) {
        val current = _currentPodcastChapters.value.toMutableList()
        if (current.none { it.startTimeMs == chapter.startTimeMs || it.title.equals(chapter.title, ignoreCase = true) }) {
            current.add(chapter)
            val sorted = current.sortedBy { it.startTimeMs }
            _currentPodcastChapters.value = sorted
        }
    }

    fun skipToNextChapter() {
        val chapters = _currentPodcastChapters.value
        if (chapters.isEmpty()) return
        val currentPos = _audioPositionMs.value
        val nextChapter = chapters.firstOrNull { it.startTimeMs > currentPos + 2000L }
        if (nextChapter != null) {
            seekToPosition(nextChapter.startTimeMs)
        }
    }

    fun skipToPreviousChapter() {
        val chapters = _currentPodcastChapters.value
        if (chapters.isEmpty()) return
        val currentPos = _audioPositionMs.value
        val currentChapter = _currentChapter.value
        if (currentChapter != null && currentPos > currentChapter.startTimeMs + 3000L) {
            seekToPosition(currentChapter.startTimeMs)
            return
        }
        val prevChapter = chapters.lastOrNull { it.startTimeMs < (currentChapter?.startTimeMs ?: currentPos) }
        if (prevChapter != null) {
            seekToPosition(prevChapter.startTimeMs)
        } else {
            seekToPosition(0L)
        }
    }

    fun seekToPosition(posMs: Long) {
        android.util.Log.d("AUDIO_DEBUG", "🔴 seekToPosition() chamado com posMs=$posMs (mídia: ${_activeMediaType.value})")
        if (_activeMediaType.value == ActiveMediaType.LIVE_RADIO || _currentStation.value != null) {
            android.util.Log.d("AUDIO_DEBUG", "seekToPosition ignorado: rádio ao vivo não permite seek")
            return
        }
        val routeManager = AudioRouteManager.getInstance(context)
        if (routeManager.isCastingActive()) {
            routeManager.seekTo(posMs)
            _audioPositionMs.value = posMs
            updateCurrentChapter(posMs)
            _currentPodcastEpisode.value?.let { ep ->
                com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context).savePlaybackPosition(ep.id, posMs)
            }
            return
        }
        val player = exoPlayer ?: return
        val duration = if (_audioDurationMs.value > 0) _audioDurationMs.value else player.duration
        val target = posMs.coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
        _audioPositionMs.value = target
        updateCurrentChapter(target)
        _currentPodcastEpisode.value?.let { ep ->
            com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context).savePlaybackPosition(ep.id, target)
        }
    }

    var onFolderWrapNext: (() -> Unit)? = null
    var onFolderWrapPrev: (() -> Unit)? = null
    var onNextVideo: (() -> Unit)? = null
    var onPrevVideo: (() -> Unit)? = null

    private var localAudioQueue: List<com.marcioamaro.mediapod.data.model.LocalAudioTrack> = emptyList()
    private var audioProgressJob: Job? = null

    private var visualizerJob: Job? = null
    private var rdsSimulationJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var reconnectJob: Job? = null
    private var retryCount = 0
    private var currentCandidateIndex = 0
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
        "ExoPlayerLib/2.19.1 (Linux; Android 14)",
        "VLC/3.5.4 (Linux; Android 14)",
        "WinampMPEG/5.0",
        "AppleCoreMedia/1.0.0 (iPhone; iOS 17.3)"
    )
    private var currentUserAgentIndex = 0
    private var totalAttemptCount = 0
    private var streamingTimeoutJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    // Exposta como val somente-leitura para que o RadioMediaService possa verificar
    // antes de chamar autoPlayLastMediaIfIdle() e evitar tocar sem pedido do usuário
    // @Volatile garante visibilidade entre threads (auditoria P1 — 24/09/2026)
    @Volatile
    var userInitiatedPause = false
        private set

    init {
        try {
            audioManager?.let { am ->
                val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) {
                    _volume.value = (current.toFloat() / max.toFloat()).coerceIn(0f, 1.0f)
                }
            }
        } catch (_: Exception) {
            val savedVol = com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager.getInstance(context).volumeLevel
            _volume.value = savedVol.coerceIn(0.05f, 1.0f)
        }
        initVolumeListeners()
        initLocks()
        initPlayer()

        // Monitoramento não-bloqueante de conectividade de rede com NetworkCallback
        NetworkConnectivityValidator.startMonitoring(context)
        scope.launch {
            NetworkConnectivityValidator.networkStatus.collect { status ->
                when (status) {
                    NetworkStatus.OFFLINE -> handleNetworkLoss()
                    NetworkStatus.ONLINE -> handleNetworkRestored()
                }
            }
        }
    }

    private fun initLocks() {
        try {
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "iClassicPodRadio::BackgroundStreamingLock"
            )?.apply {
                setReferenceCounted(false)
            }

            @Suppress("DEPRECATION")
            val wifiMode = WifiManager.WIFI_MODE_FULL
            wifiLock = wifiManager?.createWifiLock(wifiMode, "iClassicPodRadio::WifiStreamingLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Could not initialize wake/wifi locks", e)
        }
    }

    private fun acquireLocks() {
        try {
            // Safe, standard power management. ExoPlayer's WAKE_MODE_NETWORK handles primary streaming
            wakeLock?.let {
                if (!it.isHeld) it.acquire(2 * 60 * 60 * 1000L) // 2 hours max safety timeout
            }
            wifiLock?.let {
                if (!it.isHeld) it.acquire()
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to acquire locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to release locks", e)
        }
    }

    fun requestAudioFocus(): Boolean {
        // O ExoPlayer já gerencia nativamente o AudioFocus via setAudioAttributes(audioAttributes, true)
        // e setHandleAudioBecomingNoisy(true). Registrar um listener manual concorrente no AudioManager
        // faz com que o Android despache AUDIOFOCUS_LOSS (-1) para o listener manual assim que o ExoPlayer
        // inicia o playback, causando o cancelamento/pausa imediata do áudio após ~600ms.
        return true
    }

    private fun initPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Configure buffer for continuous, resilient live radio streaming (zero backBuffer to save RAM)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // minBufferMs (15 segundos para estabilidade contínua sem engasgo)
                50000,  // maxBufferMs (50 segundos)
                5000,   // bufferForPlaybackMs (5 segundos para iniciar reprodução sem rebuffering)
                8000    // bufferForPlaybackAfterRebufferMs (8 segundos para rebuffer seguro)
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(0, false) // Sem retenção de buffer passado para economizar memória RAM
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 1.0f // Ganho unitário: delega o volume estritamente ao AudioManager.STREAM_MUSIC
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                _playbackStatus.value = RadioPlaybackStatus.BUFFERING
                                acquireLocks()
                                stopVisualizer()
                                val station = _currentStation.value
                                if (station != null && !userInitiatedPause) {
                                    startBufferingWatchdog(station)
                                }
                            }
                            Player.STATE_READY -> {
                                cancelBufferingWatchdog()
                                streamingTimeoutJob?.cancel()
                                reconnectJob?.cancel()
                                val sid = audioSessionId
                                if (sid != C.AUDIO_SESSION_ID_UNSET && sid != 0 && sid != currentAudioSessionId) {
                                    attachAudioEffects(sid)
                                }
                                if (playWhenReady) {
                                    _playbackStatus.value = RadioPlaybackStatus.PLAYING
                                    acquireLocks()
                                    startVisualizer()
                                    retryCount = 0
                                    totalAttemptCount = 0
                                    if (_activeMediaType.value == ActiveMediaType.LIVE_RADIO) {
                                        startLiveSessionTimer(resume = true)
                                    }
                                } else if (userInitiatedPause) {
                                    _playbackStatus.value = RadioPlaybackStatus.PAUSED
                                    releaseLocks()
                                    stopVisualizer()
                                }
                                _errorMessage.value = null
                            }
                            Player.STATE_ENDED -> {
                                cancelBufferingWatchdog()
                                _currentPodcastEpisode.value?.let { episode ->
                                    com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context)
                                        .markEpisodePlayed(episode.id)
                                }
                                if (stopAtEndOfEpisodeOrTrack) {
                                    triggerSleepTimerStop()
                                } else if (_currentLocalAudio.value != null) {
                                    nextLocalTrack()
                                } else if (_currentPodcastEpisode.value != null) {
                                    if (podcastQueue.size > 1) {
                                        nextPodcastEpisode()
                                    } else {
                                        _playbackStatus.value = RadioPlaybackStatus.IDLE
                                        releaseLocks()
                                        stopVisualizer()
                                    }
                                } else {
                                    val station = _currentStation.value
                                    if (station != null && !userInitiatedPause) {
                                        android.util.Log.w(
                                            "RadioPlayerManager",
                                            "Fim de fluxo recebido para '${station.name}'. Alternando para próxima fonte..."
                                        )
                                        handleStreamFailureOrTimeout(station, "Fim prematuro do fluxo (EOF)")
                                    } else {
                                        _playbackStatus.value = RadioPlaybackStatus.IDLE
                                        releaseLocks()
                                        stopVisualizer()
                                    }
                                }
                            }
                            Player.STATE_IDLE -> {
                                if (_playbackStatus.value != RadioPlaybackStatus.ERROR &&
                                    _playbackStatus.value != RadioPlaybackStatus.BUFFERING &&
                                    userInitiatedPause) {
                                    _playbackStatus.value = RadioPlaybackStatus.IDLE
                                    releaseLocks()
                                    stopVisualizer()
                                }
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            cancelBufferingWatchdog()
                            streamingTimeoutJob?.cancel()
                            reconnectJob?.cancel()
                            _playbackStatus.value = RadioPlaybackStatus.PLAYING
                            acquireLocks()
                            startVisualizer()
                        } else if (userInitiatedPause) {
                            cancelBufferingWatchdog()
                            _playbackStatus.value = RadioPlaybackStatus.PAUSED
                            releaseLocks()
                            stopVisualizer()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        com.marcioamaro.mediapod.util.Diagnostics.record(context, com.marcioamaro.mediapod.util.Diagnostics.Event.PLAYBACK_FAILED)
                        cancelBufferingWatchdog()
                        if (userInitiatedPause) return

                        val station = _currentStation.value
                        val podcast = _currentPodcastEpisode.value

                        if (isNetworkError(error)) {
                            android.util.Log.w("RadioPlayerManager", "Falha de I/O de rede no ExoPlayer (${error.errorCodeName}). Tratando com resiliência sem crash.")
                            if (station != null) {
                                reportNoInternetState(station)
                            } else if (podcast != null) {
                                reportNoInternetStateForPodcast(podcast)
                            } else {
                                _playbackStatus.value = RadioPlaybackStatus.NO_INTERNET
                                _errorMessage.value = "VERIFIQUE A CONEXÃO COM A INTERNET"
                            }
                            return
                        }

                        if (station != null) {
                            handleStreamFailureOrTimeout(station, "Erro no streaming: ${error.errorCodeName}")
                        } else {
                            stopVisualizer()
                            releaseLocks()
                            streamingTimeoutJob?.cancel()
                            _playbackStatus.value = RadioPlaybackStatus.ERROR
                            _errorMessage.value = "Não foi possível reproduzir. Verifique a conexão e tente novamente ou escolha outra fonte."
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        val title = mediaMetadata.title?.toString()
                        val artist = mediaMetadata.artist?.toString()
                        if (!title.isNullOrBlank()) {
                            val rdsText = if (!artist.isNullOrBlank() && !title.contains(artist, ignoreCase = true)) "$artist - $title" else title
                            processDetectedRdsTitle(rdsText)
                        }
                    }

                    @androidx.media3.common.util.UnstableApi
                    override fun onMetadata(metadata: Metadata) {
                        for (i in 0 until metadata.length()) {
                            val entry = metadata.get(i)
                            if (entry is IcyInfo) {
                                val streamTitle = entry.title
                                if (!streamTitle.isNullOrBlank()) {
                                    processDetectedRdsTitle(streamTitle)
                                }
                            } else if (entry is ChapterFrame) {
                                var chTitle = entry.chapterId
                                val count = entry.subFrameCount
                                for (subIdx in 0 until count) {
                                    val sub = entry.getSubFrame(subIdx)
                                    if (sub is TextInformationFrame) {
                                        val text = sub.values.firstOrNull() ?: sub.value
                                        if (!text.isNullOrBlank()) {
                                            chTitle = text
                                            break
                                        }
                                    }
                                }
                                val startMs = entry.startTimeMs.toLong()
                                val endMs = entry.endTimeMs.toLong()
                                addEmbeddedChapter(PodcastChapter(chTitle, startMs, endMs))
                            }
                        }
                    }

                    @androidx.media3.common.util.UnstableApi
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                            attachAudioEffects(audioSessionId)
                        }
                    }
                })
                val sid = audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET) {
                    attachAudioEffects(sid)
                }
            }
    }

    private var playlist: List<RadioStation> = emptyList()

    fun updatePlaylist(stations: List<RadioStation>) {
        if (stations.isNotEmpty()) {
            playlist = stations
        }
    }

    fun getCurrentPlaylist(): List<RadioStation> = playlist

    fun playNextStation() {
        val coordinator = (context.applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
        if (coordinator != null && coordinator.navigationContext.value.items.isNotEmpty()) {
            coordinator.skipToNext()
            return
        }
        val coordinatorContextItems = coordinator?.navigationContext?.value?.items
        val list = if (!coordinatorContextItems.isNullOrEmpty()) {
            coordinatorContextItems.map { it.toRadioStation() }
        } else if (playlist.isNotEmpty()) {
            playlist
        } else {
            com.marcioamaro.mediapod.data.repository.CuratedData.CURATED_GLOBAL_STATIONS
        }
        if (list.isNotEmpty()) {
            val current = _currentStation.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % list.size else 0
            playStation(list[nextIndex])
        }
    }

    fun playPreviousStation() {
        val coordinator = (context.applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
        if (coordinator != null && coordinator.navigationContext.value.items.isNotEmpty()) {
            coordinator.skipToPrevious()
            return
        }
        val coordinatorContextItems = coordinator?.navigationContext?.value?.items
        val list = if (!coordinatorContextItems.isNullOrEmpty()) {
            coordinatorContextItems.map { it.toRadioStation() }
        } else if (playlist.isNotEmpty()) {
            playlist
        } else {
            com.marcioamaro.mediapod.data.repository.CuratedData.CURATED_GLOBAL_STATIONS
        }
        if (list.isNotEmpty()) {
            val current = _currentStation.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
            playStation(list[prevIndex])
        }
    }

    fun playNext() {
        when (_activeMediaType.value) {
            ActiveMediaType.LOCAL_VIDEO -> onNextVideo?.invoke()
            ActiveMediaType.LOCAL_AUDIO -> nextLocalTrack()
            ActiveMediaType.PODCAST_EPISODE -> nextPodcastEpisode()
            ActiveMediaType.LIVE_RADIO -> playNextStation()
            else -> playNextStation()
        }
    }

    fun playPrevious() {
        when (_activeMediaType.value) {
            ActiveMediaType.LOCAL_VIDEO -> {
                val videoManager = LocalVideoPlayerManager.getInstance(context)
                if (videoManager.currentPositionMs.value > 3000L) {
                    videoManager.seekTo(0L)
                } else {
                    onPrevVideo?.invoke()
                }
            }
            ActiveMediaType.LOCAL_AUDIO -> {
                if (_audioPositionMs.value > 3000L) {
                    seekToPosition(0L)
                } else {
                    prevLocalTrack()
                }
            }
            ActiveMediaType.PODCAST_EPISODE -> {
                if (_audioPositionMs.value > 3000L) {
                    seekToPosition(0L)
                } else {
                    prevPodcastEpisode()
                }
            }
            ActiveMediaType.LIVE_RADIO -> playPreviousStation()
            else -> playPreviousStation()
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) initPlayer()
        val bitrate = com.marcioamaro.mediapod.util.DataUsagePolicy(context).preferredBitrate
        exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters.buildUpon()
            .setMaxAudioBitrate(if (bitrate == 0) Int.MAX_VALUE else bitrate).build()
        return exoPlayer!!
    }

    fun playStation(station: RadioStation) {
        requestAudioFocus()
        clearPlayerMetadata()
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (_: Exception) {}
        _activeMediaType.value = ActiveMediaType.LIVE_RADIO
        _currentLocalAudio.value = null
        _currentPodcastEpisode.value = null
        localAudioQueue = emptyList()
        podcastQueue = emptyList()
        _currentPodcastChapters.value = emptyList()
        _currentChapter.value = null
        _playbackSpeed.value = 1.0f
        exoPlayer?.playbackParameters = PlaybackParameters(1.0f, 1.0f)
        audioProgressJob?.cancel()
        val isDifferentStation = _currentStation.value?.id != station.id
        _currentStation.value = station
        if (isDifferentStation) {
            _liveSessionDurationSeconds.value = 0L
            startLiveSessionTimer(resume = false)
        }
        ipodPrefs.addRecentStation(station)
        ipodPrefs.saveLastPlayedStation(station)
        if (playlist.none { it.id == station.id }) {
            playlist = listOf(station) + playlist
        }
        currentCandidateIndex = 0
        currentUserAgentIndex = 0
        totalAttemptCount = 1
        retryCount = 0
        _errorMessage.value = null
        _playbackStatus.value = RadioPlaybackStatus.BUFFERING
        rdsSimulationJob?.cancel()
        userInitiatedPause = false
        reconnectJob?.cancel()

        _nowPlaying.value = NowPlayingMetadata(
            title = station.name,
            artist = "Ao Vivo",
            album = "MediaPod • Rádio",
            artworkUri = null,
            isLiveStream = true,
            hasTrackInfo = false
        )
        updateNotificationAndSessionMetadata(
            title = station.name,
            artist = "Ao Vivo",
            album = "MediaPod • Rádio",
            artworkUri = null
        )

        // Set initial RDS info with Station Name as default when RDS is absent
        _rdsInfo.value = RdsInfo(
            programService = station.name.take(12).uppercase(),
            radioText = station.name.uppercase(),
            hasRealRds = false,
            programType = "[${station.primaryGenre.uppercase()}]",
            signalStrengthBars = 5,
            isStereo = true,
            hasTrafficProgram = true,
            bitrateInfo = "${station.bitrate} kbps ${station.codec}",
            frequencyMhz = station.displayFrequency
        )

        val candidates = station.getAllStreamCandidates()
        val initialUrl = if (candidates.isNotEmpty()) candidates[0] else station.streamUrl
        playStreamUrl(station, initialUrl)
    }

    private fun getHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        val currentUa = USER_AGENTS[currentUserAgentIndex % USER_AGENTS.size]
        return DefaultHttpDataSource.Factory()
            .setUserAgent(currentUa)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(mapOf(
                "Icy-MetaData" to "1",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache",
                "Accept" to "*/*"
            ))
    }

    private fun startBufferingWatchdog(station: RadioStation) {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = scope.launch {
            delay(9000L) // Watchdog: 9 segundos congelado em buffering
            val player = exoPlayer
            if (player?.playbackState == Player.STATE_BUFFERING && !userInitiatedPause) {
                android.util.Log.w(
                    "RadioPlayerManager",
                    "Watchdog de Buffering acionado: congelamento por > 9s em '${station.name}'. Penalizando fonte e alternando..."
                )
                handleStreamFailureOrTimeout(station, "Watchdog de Buffering (travamento > 9s)")
            }
        }
    }

    private fun cancelBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
    }

    private fun startStreamingTimeoutWatcher(station: RadioStation) {
        streamingTimeoutJob?.cancel()
        streamingTimeoutJob = scope.launch {
            delay(12000L) // 12 segundos apenas para detecção de falha de conexão inicial
            val player = exoPlayer
            val isPlaying = player?.isPlaying == true
            val isReady = player?.playbackState == Player.STATE_READY
            if (!isPlaying && !isReady && !userInitiatedPause) {
                android.util.Log.w(
                    "RadioPlayerManager",
                    "Falha ao conectar emissora ${station.name} em 12s. Penalizando e tentando próxima fonte..."
                )
                handleStreamFailureOrTimeout(station, "Falha de conexão inicial")
            }
        }
    }

    private fun handleStreamFailureOrTimeout(station: RadioStation, reason: String) {
        if (userInitiatedPause) return

        cancelBufferingWatchdog()
        streamingTimeoutJob?.cancel()

        scope.launch {
            // Valida conectividade com pelo menos 3 servidores confiáveis (ex: example.com, google, cloudflare)
            val hasInternet = com.marcioamaro.mediapod.util.NetworkConnectivityValidator.checkInternetAccess(context)
            if (!hasInternet) {
                android.util.Log.w(
                    "RadioPlayerManager",
                    "Sem acesso à internet confirmado após consultar servidores de validação. Notificando UI."
                )
                reportNoInternetState(station)
                return@launch
            }

            proceedStreamFallback(station, reason)
        }
    }

    private fun proceedStreamFallback(station: RadioStation, reason: String) {
        if (userInitiatedPause) return

        // 1. Penalização Dinâmica: move a URL que falhou para o final da lista daquela estação
        val candidates = station.getAllStreamCandidates()
        val failedUrl = candidates.getOrNull(currentCandidateIndex) ?: station.streamUrl
        val penalizedStation = station.penalizeStreamUrl(failedUrl)
        _currentStation.value = penalizedStation
        try {
            com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager.getInstance(context)
                .addRecentStation(penalizedStation)
        } catch (_: Exception) {}

        val newCandidates = penalizedStation.getAllStreamCandidates()

        // 2. Sequenciamento de fallback inteligente
        if (totalAttemptCount < minOf(newCandidates.size + 1, 6)) {
            totalAttemptCount++
            currentUserAgentIndex = (currentUserAgentIndex + 1) % USER_AGENTS.size
            currentCandidateIndex = (currentCandidateIndex + 1) % newCandidates.size
            val nextUrl = newCandidates[currentCandidateIndex]

            val agentLabel = when (currentUserAgentIndex) {
                0 -> "Android Chrome"
                1 -> "ExoPlayer"
                2 -> "VLC"
                3 -> "Winamp"
                else -> "Apple CoreMedia"
            }
            android.util.Log.w(
                "RadioPlayerManager",
                "Penalizada URL ($failedUrl). Tentativa $totalAttemptCount com fonte reserva: $nextUrl (Agente: $agentLabel) por motivo: $reason"
            )
            _playbackStatus.value = RadioPlaybackStatus.BUFFERING
            _errorMessage.value = "Alternando para fonte reserva..."

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(250L)
                playStreamUrl(penalizedStation, nextUrl)
            }
            return
        }

        // 3. Todas as fontes falharam: Falha remota notificada na UI e Android Auto
        android.util.Log.e("RadioPlayerManager", "Todas as fontes falharam para a rádio ${station.name}")
        reportRemotePlaybackFailure(penalizedStation)
    }

    private fun isNetworkError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        ) {
            return true
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.net.UnknownHostException ||
                cause is java.net.SocketTimeoutException ||
                cause is java.net.ConnectException ||
                cause is java.net.NoRouteToHostException ||
                cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    fun handleNetworkLoss() {
        if (userInitiatedPause) return
        val station = _currentStation.value
        val podcast = _currentPodcastEpisode.value

        // Se estiver reproduzindo áudio local MP3, perda de internet não afeta a reprodução offline
        if (_currentLocalAudio.value != null) return

        if (station != null) {
            reportNoInternetState(station)
        } else if (podcast != null) {
            reportNoInternetStateForPodcast(podcast)
        }
    }

    fun handleNetworkRestored() {
        if (userInitiatedPause) return
        if (_playbackStatus.value != RadioPlaybackStatus.NO_INTERNET) return

        val station = _currentStation.value
        val podcast = _currentPodcastEpisode.value

        if (station != null) {
            android.util.Log.i("RadioPlayerManager", "Internet restabelecida. Retomando transmissão da rádio ${station.name} automaticamente.")
            autoReconnectJob?.cancel()
            _playbackStatus.value = RadioPlaybackStatus.BUFFERING
            _errorMessage.value = null
            playStation(station)
            startLiveSessionTimer(resume = true)
        } else if (podcast != null) {
            android.util.Log.i("RadioPlayerManager", "Internet restabelecida. Retomando episódio de podcast ${podcast.title} automaticamente.")
            autoReconnectJob?.cancel()
            _playbackStatus.value = RadioPlaybackStatus.BUFFERING
            _errorMessage.value = null
            playPodcastEpisode(podcast, _currentPodcastShow.value, podcastQueue)
        }
    }

    private fun reportNoInternetState(station: RadioStation) {
        try {
            exoPlayer?.pause()
        } catch (_: Exception) {}

        releaseLocks()
        stopVisualizer()
        pauseLiveSessionTimer()
        _playbackStatus.value = RadioPlaybackStatus.NO_INTERNET
        _errorMessage.value = "VERIFIQUE A CONEXÃO COM A INTERNET"

        // Atualiza MediaMetadata no Android Auto e central de notificações
        try {
            val appLogoUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
            val offlineMetadata = MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist("VERIFIQUE A CONEXÃO COM A INTERNET")
                .setSubtitle("VERIFIQUE A CONEXÃO COM A INTERNET")
                .setAlbumTitle("Sem conexão")
                .setArtworkUri(appLogoUri)
                .build()
            exoPlayer?.playlistMetadata = offlineMetadata
        } catch (_: Exception) {}

        startAutoReconnectLoop()
    }

    private fun reportNoInternetStateForPodcast(episode: com.marcioamaro.mediapod.data.model.PodcastEpisode) {
        try {
            val currentPos = exoPlayer?.currentPosition ?: _audioPositionMs.value
            _audioPositionMs.value = currentPos
            com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context).savePlaybackPosition(episode.id, currentPos)
            exoPlayer?.pause()
        } catch (_: Exception) {}

        releaseLocks()
        stopVisualizer()
        _playbackStatus.value = RadioPlaybackStatus.NO_INTERNET
        _errorMessage.value = "VERIFIQUE A CONEXÃO COM A INTERNET"

        // Atualiza MediaMetadata no Android Auto e notificações
        try {
            val appLogoUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
            val offlineMetadata = MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist("VERIFIQUE A CONEXÃO COM A INTERNET")
                .setSubtitle("VERIFIQUE A CONEXÃO COM A INTERNET")
                .setAlbumTitle(episode.showTitle)
                .setArtworkUri(appLogoUri)
                .build()
            exoPlayer?.playlistMetadata = offlineMetadata
        } catch (_: Exception) {}

        startAutoReconnectLoop()
    }

    private fun startAutoReconnectLoop() {
        autoReconnectJob?.cancel()
        autoReconnectJob = scope.launch(Dispatchers.IO) {
            android.util.Log.i("RadioPlayerManager", "Iniciando loop de tentativa de reconexão a cada 10s...")
            while (isActive && _playbackStatus.value == RadioPlaybackStatus.NO_INTERNET && !userInitiatedPause) {
                delay(10000L)
                if (userInitiatedPause) break
                val hasNet = NetworkConnectivityValidator.checkInternetAccess(context)
                if (hasNet && !userInitiatedPause) {
                    android.util.Log.i("RadioPlayerManager", "Internet detectada no retry de 10s! Retomando transmissão...")
                    withContext(Dispatchers.Main) {
                        handleNetworkRestored()
                    }
                    break
                }
            }
        }
    }

    fun startLiveSessionTimer(resume: Boolean = false) {
        liveSessionJob?.cancel()
        if (!resume) {
            _liveSessionDurationSeconds.value = 0L
        }
        liveSessionJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (_playbackStatus.value == RadioPlaybackStatus.PLAYING && _currentStation.value != null) {
                    _liveSessionDurationSeconds.value += 1L
                }
            }
        }
    }

    fun pauseLiveSessionTimer() {
        liveSessionJob?.cancel()
        liveSessionJob = null
    }

    fun stopLiveSessionTimer() {
        liveSessionJob?.cancel()
        liveSessionJob = null
        _liveSessionDurationSeconds.value = 0L
    }

    fun retryPlayback() {
        autoReconnectJob?.cancel()
        val station = _currentStation.value ?: return
        _errorMessage.value = null
        totalAttemptCount = 0
        currentCandidateIndex = 0
        playStation(station)
    }

    private fun reportRemotePlaybackFailure(station: RadioStation) {
        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        } catch (_: Exception) {}

        releaseLocks()
        stopVisualizer()
        _playbackStatus.value = RadioPlaybackStatus.ERROR
        val errorNotice = "Impossível reproduzir no momento (falha remota)"
        _errorMessage.value = errorNotice

        // Notificar Android Auto e MediaSession para exibição limpa no painel veicular
        try {
            val appLogoUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
            val errorMetadata = MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(errorNotice)
                .setSubtitle(errorNotice)
                .setAlbumTitle("Falha remota")
                .setArtworkUri(appLogoUri)
                .build()
            exoPlayer?.setPlaylistMetadata(errorMetadata)
        } catch (_: Exception) {}
    }

    private fun playStreamUrl(station: RadioStation, streamUrl: String) {
        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).updateCastMedia()
            pauseLocalOnly()
            startMediaService()
            startRdsMetadataSimulation(station)
            return
        }

        val streamTitle = _rdsInfo.value.radioText.ifBlank {
            lastRealSongTitle?.ifBlank { null } ?: lastRawStreamTitle?.ifBlank { null } ?: "Ao Vivo"
        }
        val radioLogoUri = if (station.hasValidFavicon) {
            Uri.parse(station.effectiveFavicon)
        } else {
            Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_radio_generic}")
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(streamTitle)
            .setSubtitle(streamTitle)
            .setAlbumTitle("Ao Vivo")
            .setArtworkUri(radioLogoUri)
            .setIsPlayable(true)
            .build()

        // LiveConfiguration: trava clock a 1.0f para eliminar micro-acelerações de buffer em streams Icecast/Triton
        val liveConfiguration = MediaItem.LiveConfiguration.Builder()
            .setMinPlaybackSpeed(1.0f)
            .setMaxPlaybackSpeed(1.0f)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(streamUrl)
            .setLiveConfiguration(liveConfiguration)
            .setMediaMetadata(mediaMetadata)
            .build()

        val player = getPlayer()
        try {
            val mediaSource = DefaultMediaSourceFactory(getHttpDataSourceFactory())
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource, /* resetPosition = */ true)
            player.playlistMetadata = mediaMetadata

            player.prepare()
            player.playWhenReady = true
            startMediaService()
            startRdsMetadataSimulation(station)

            startStreamingTimeoutWatcher(station)
        } catch (e: Exception) {
            handleStreamFailureOrTimeout(station, "Falha ao conectar fluxo: ${e.message}")
        }
    }

    @Volatile
    private var isMediaServiceRunning = false

    private fun startMediaService() {
        if (isMediaServiceRunning) return
        try {
            val serviceIntent = Intent(context, RadioMediaService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            isMediaServiceRunning = true
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to start media service", e)
        }
    }

    fun playLocalAudio(track: com.marcioamaro.mediapod.data.model.LocalAudioTrack, queue: List<com.marcioamaro.mediapod.data.model.LocalAudioTrack> = emptyList()) {
        com.marcioamaro.mediapod.data.repository.MediaLibraryRepository.getInstance(context).recordRecent(track.libraryKey())
        requestAudioFocus()
        clearPlayerMetadata()
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (_: Exception) {}
        _activeMediaType.value = ActiveMediaType.LOCAL_AUDIO
        _currentStation.value = null
        _currentPodcastEpisode.value = null
        _currentPodcastShow.value = null
        podcastQueue = emptyList()
        _currentLocalAudio.value = track
        if (queue.isNotEmpty()) {
            localAudioQueue = queue
        } else if (localAudioQueue.none { it.id == track.id }) {
            localAudioQueue = listOf(track)
        }
        _audioDurationMs.value = track.durationMs
        _audioPositionMs.value = 0L

        _errorMessage.value = null
        _playbackStatus.value = RadioPlaybackStatus.BUFFERING
        rdsSimulationJob?.cancel()

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUrl?.let { Uri.parse(it) } ?: Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}"))
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.contentUri)
            .setMediaMetadata(mediaMetadata)
            .build()

        _nowPlaying.value = NowPlayingMetadata(
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUri = track.albumArtUrl?.let { Uri.parse(it) },
            isLiveStream = false,
            hasTrackInfo = true
        )

        _rdsInfo.value = RdsInfo(
            programService = track.title.take(12).uppercase(),
            radioText = "${track.artist} - ${track.title}".uppercase(),
            hasRealRds = true,
            programType = "[MP3]",
            signalStrengthBars = 5,
            isStereo = true,
            hasTrafficProgram = false,
            bitrateInfo = "MP3 / Áudio Local",
            frequencyMhz = track.displayDuration
        )

        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).updateCastMedia()
            pauseLocalOnly()
            startMediaService()
            startAudioProgressTracker()
            return
        }

        val player = getPlayer()
        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.playlistMetadata = mediaMetadata
            val speed = ipodPrefs.localMediaPlaybackSpeed
            player.seekTo(com.marcioamaro.mediapod.data.repository.MediaLibraryRepository.getInstance(context).position(track.libraryKey()))
            _playbackSpeed.value = speed
            player.playbackParameters = PlaybackParameters(speed, 1.0f)
            _currentPodcastChapters.value = emptyList()
            _currentChapter.value = null
            player.prepare()
            player.playWhenReady = true
            startMediaService()
            startAudioProgressTracker()
            startVisualizer()
            if (AudioRouteManager.getInstance(context).isCastingActive()) {
                AudioRouteManager.getInstance(context).updateCastMedia()
            }
        } catch (e: Exception) {
            _playbackStatus.value = RadioPlaybackStatus.ERROR
            _errorMessage.value = "Falha ao reproduzir: ${e.message}"
        }
    }

    fun playPodcastEpisode(
        episode: com.marcioamaro.mediapod.data.model.PodcastEpisode,
        show: com.marcioamaro.mediapod.data.model.PodcastShow? = null,
        queue: List<com.marcioamaro.mediapod.data.model.PodcastEpisode> = emptyList()
    ) {
        requestAudioFocus()
        clearPlayerMetadata()
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (_: Exception) {}
        _activeMediaType.value = ActiveMediaType.PODCAST_EPISODE
        _currentStation.value = null
        _currentLocalAudio.value = null
        localAudioQueue = emptyList()
        _currentPodcastEpisode.value = episode
        _currentPodcastShow.value = show
        podcastQueue = if (queue.isNotEmpty()) queue else listOf(episode)

        userInitiatedPause = false
        autoReconnectJob?.cancel()
        stopLiveSessionTimer()

        val repo = com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context)
        repo.addRecentEpisode(episode)
        val effectiveShow = show ?: _currentPodcastShow.value ?: com.marcioamaro.mediapod.data.model.PodcastShow(
            id = episode.showId,
            title = episode.showTitle,
            author = "",
            description = "",
            artworkUrl = episode.artworkUrl,
            feedUrl = ""
        )
        ipodPrefs.saveLastPlayedPodcast(episode, effectiveShow)
        val savedPos = repo.getSavedPlaybackPosition(episode.id)

        _audioDurationMs.value = episode.durationMs
        _audioPositionMs.value = savedPos
        _errorMessage.value = null
        _playbackStatus.value = RadioPlaybackStatus.BUFFERING
        rdsSimulationJob?.cancel()

        // Carrega capítulos locais ou busca via Podcasting 2.0 JSON / fallback assincronamente
        _currentPodcastChapters.value = episode.chapters
        _currentChapter.value = null
        scope.launch {
            val chapters = com.marcioamaro.mediapod.util.PodcastChaptersExtractor.getOrFetchChapters(episode)
            if (chapters.isNotEmpty()) {
                _currentPodcastChapters.value = chapters
                updateCurrentChapter(_audioPositionMs.value)
            }
        }

        val appLogoUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.showTitle)
            .setAlbumTitle(episode.publishDate.ifBlank { "Podcast" })
            .setArtworkUri(appLogoUri)
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(com.marcioamaro.mediapod.data.download.PodcastDownloadManager.getInstance(context).getLocalFilePath(episode.id)
                ?.let { Uri.fromFile(java.io.File(it)) } ?: Uri.parse(episode.audioUrl))
            .setMediaMetadata(mediaMetadata)
            .build()

        _nowPlaying.value = NowPlayingMetadata(
            title = episode.title,
            artist = episode.showTitle,
            album = episode.publishDate,
            artworkUri = null,
            isLiveStream = false,
            hasTrackInfo = true
        )

        _rdsInfo.value = RdsInfo(
            programService = episode.showTitle.take(12).uppercase(),
            radioText = "${episode.showTitle} - ${episode.title}".uppercase(),
            hasRealRds = true,
            programType = "[PODCAST]",
            signalStrengthBars = 5,
            isStereo = true,
            hasTrafficProgram = false,
            bitrateInfo = "Podcast Áudio Digital",
            frequencyMhz = ""
        )

        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).updateCastMedia()
            pauseLocalOnly()
            startMediaService()
            startAudioProgressTracker()
            return
        }

        val player = getPlayer()
        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.playlistMetadata = mediaMetadata
            val speed = ipodPrefs.podcastPlaybackSpeed
            _playbackSpeed.value = speed
            player.playbackParameters = PlaybackParameters(speed, 1.0f)
            player.prepare()
            if (savedPos > 0) {
                player.seekTo(savedPos)
            }
            player.playWhenReady = true
            startMediaService()
            startAudioProgressTracker()
            startVisualizer()
            if (AudioRouteManager.getInstance(context).isCastingActive()) {
                AudioRouteManager.getInstance(context).updateCastMedia()
            }
        } catch (e: Exception) {
            _playbackStatus.value = RadioPlaybackStatus.ERROR
            _errorMessage.value = "Falha ao reproduzir podcast: ${e.message}"
        }
    }

    fun seekRelative(offsetMs: Long) {
        if (_activeMediaType.value == ActiveMediaType.LIVE_RADIO || _currentStation.value != null) {
            android.util.Log.d("AUDIO_DEBUG", "seekRelative ignorado: rádio ao vivo não permite seek")
            return
        }
        val player = exoPlayer ?: return
        val current = player.currentPosition
        val target = (current + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        _audioPositionMs.value = target
        updateCurrentChapter(target)
        _currentPodcastEpisode.value?.let { ep ->
            com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context).savePlaybackPosition(ep.id, target)
        }
    }

    fun nextPodcastEpisode() {
        if (podcastQueue.isEmpty()) return
        val current = _currentPodcastEpisode.value ?: return
        val idx = podcastQueue.indexOfFirst { it.id == current.id }
        val nextIdx = if (idx >= 0) (idx + 1) % podcastQueue.size else 0
        playPodcastEpisode(podcastQueue[nextIdx], _currentPodcastShow.value, podcastQueue)
    }

    fun prevPodcastEpisode() {
        if (podcastQueue.isEmpty()) return
        val current = _currentPodcastEpisode.value ?: return
        val idx = podcastQueue.indexOfFirst { it.id == current.id }
        val prevIdx = if (idx > 0) idx - 1 else podcastQueue.size - 1
        playPodcastEpisode(podcastQueue[prevIdx], _currentPodcastShow.value, podcastQueue)
    }

    private fun startAudioProgressTracker() {
        audioProgressJob?.cancel()
        audioProgressJob = scope.launch {
            while (isActive) {
                val routeManager = AudioRouteManager.getInstance(context)
                if (routeManager.isCastingActive()) {
                    val rmc = routeManager.getRemoteMediaClient()
                    if (rmc != null) {
                        val pos = rmc.approximateStreamPosition.coerceAtLeast(0L)
                        if (pos > 0) {
                            _audioPositionMs.value = pos
                            updateCurrentChapter(pos)
                        }
                        val streamDur = rmc.streamDuration
                        if (streamDur > 0) {
                            _audioDurationMs.value = streamDur
                        }
                        _currentPodcastEpisode.value?.let { ep ->
                            if (pos > 0) {
                                com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context).savePlaybackPosition(ep.id, pos)
                            }
                        }
                    }
                } else {
                    exoPlayer?.let { p ->
                        val pos = p.currentPosition.coerceAtLeast(0L)
                        _audioPositionMs.value = pos
                        _currentLocalAudio.value?.let { track ->
                            com.marcioamaro.mediapod.data.repository.MediaLibraryRepository.getInstance(context)
                                .savePosition(track.libraryKey(), pos, track.durationMs)
                        }
                        updateCurrentChapter(pos)
                        val dur = p.duration
                        if (dur > 0) {
                            _audioDurationMs.value = dur
                        }
                        _currentPodcastEpisode.value?.let { ep ->
                            if (pos > 0) {
                                com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(context).savePlaybackPosition(ep.id, pos)
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun nextLocalTrack() {
        if (localAudioQueue.isEmpty()) {
            onFolderWrapNext?.invoke()
            return
        }
        val current = _currentLocalAudio.value
        val currentIndex = localAudioQueue.indexOfFirst { it.id == current?.id }
        if (currentIndex in 0 until localAudioQueue.size - 1) {
            playLocalAudio(localAudioQueue[currentIndex + 1], localAudioQueue)
        } else {
            if (onFolderWrapNext != null) {
                onFolderWrapNext?.invoke()
            } else {
                playLocalAudio(localAudioQueue.first(), localAudioQueue)
            }
        }
    }

    fun prevLocalTrack() {
        if (localAudioQueue.isEmpty()) {
            onFolderWrapPrev?.invoke()
            return
        }
        val current = _currentLocalAudio.value
        val currentIndex = localAudioQueue.indexOfFirst { it.id == current?.id }
        if (currentIndex > 0) {
            playLocalAudio(localAudioQueue[currentIndex - 1], localAudioQueue)
        } else {
            if (onFolderWrapPrev != null) {
                onFolderWrapPrev?.invoke()
            } else {
                playLocalAudio(localAudioQueue.last(), localAudioQueue)
            }
        }
    }

    fun attachAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        currentAudioSessionId = audioSessionId
        initEqualizer(audioSessionId)
        initLoudness(audioSessionId)
    }

    private fun initEqualizer(audioSessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEqualizerEnabled.value
            }
            applyEqualizerToHardware()
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Could not attach hardware Equalizer", e)
        }
    }

    private fun initLoudness(audioSessionId: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(_loudnessGainMb.value)
                enabled = _isLoudnessEnabled.value
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Could not attach hardware LoudnessEnhancer", e)
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        _isLoudnessEnabled.value = enabled
        eqPrefs.edit().putBoolean("loudness_enabled", enabled).commit()
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to toggle LoudnessEnhancer", e)
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        val safeGain = gainMb.coerceIn(0, 1200) // 0 a +12 dB
        _loudnessGainMb.value = safeGain
        eqPrefs.edit().putInt("loudness_gain", safeGain).commit()
        try {
            loudnessEnhancer?.setTargetGain(safeGain)
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to set LoudnessEnhancer gain", e)
        }
    }

    private fun applyEqualizerToHardware() {
        val eq = equalizer ?: return
        try {
            eq.enabled = _isEqualizerEnabled.value
            if (!eq.enabled) return

            val range = try { eq.bandLevelRange } catch (e: Exception) {
                android.util.Log.d("RadioPlayerManager", "Using default bandLevelRange: ${e.message}")
                shortArrayOf(-1200, 1200)
            }
            val minMb = if (range.isNotEmpty()) range[0].toInt() else -1200
            val maxMb = if (range.size > 1) range[1].toInt() else 1200

            val numBands = eq.numberOfBands.toInt()
            val currentLevels = _equalizerBands.value
            for (i in 0 until minOf(numBands, currentLevels.size)) {
                val targetMb = (currentLevels[i] * 100).toInt().coerceIn(minMb, maxMb).toShort()
                eq.setBandLevel(i.toShort(), targetMb)
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to apply Equalizer levels", e)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        eqPrefs.edit().putBoolean("eq_enabled", enabled).commit()
        applyEqualizerToHardware()
    }

    fun setEqualizerPreset(presetName: String) {
        val canonicalName = when {
            presetName.equals("Flat", ignoreCase = true) -> "Flat"
            presetName.equals("Rock", ignoreCase = true) -> "Rock"
            presetName.equals("Pop", ignoreCase = true) -> "Pop"
            presetName.contains("Bass", ignoreCase = true) -> "Bass Booster"
            presetName.contains("Voz", ignoreCase = true) || presetName.contains("Podcast", ignoreCase = true) || presetName.contains("Vocal", ignoreCase = true) -> "Voz / Podcast"
            presetName.equals("Jazz", ignoreCase = true) -> "Jazz"
            presetName.contains("Clássica", ignoreCase = true) || presetName.contains("Classica", ignoreCase = true) -> "Clássica"
            presetName.contains("Eletr", ignoreCase = true) || presetName.contains("Dance", ignoreCase = true) -> "Eletrônica"
            presetName.contains("Blues", ignoreCase = true) -> "Blues"
            presetName.contains("Loud", ignoreCase = true) -> "Loudness"
            else -> "Personalizado"
        }

        _equalizerPreset.value = canonicalName
        eqPrefs.edit().putString("eq_preset", canonicalName).commit()

        if (canonicalName == "Loudness") {
            setLoudnessEnabled(true)
        }

        if (canonicalName != "Personalizado") {
            val presetBands = EQUALIZER_PRESETS[canonicalName] ?: listOf(0f, 0f, 0f, 0f, 0f)
            _equalizerBands.value = presetBands
            eqPrefs.edit().apply {
                presetBands.forEachIndexed { idx, v -> putFloat("eq_band_$idx", v) }
                commit()
            }
        }
        applyEqualizerToHardware()
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelDb: Float) {
        if (bandIndex !in 0..4) return
        val current = _equalizerBands.value.toMutableList()
        current[bandIndex] = levelDb.coerceIn(-12f, 12f)
        val updated = current.toList()
        _equalizerBands.value = updated
        _equalizerPreset.value = "Personalizado"
        eqPrefs.edit().apply {
            putString("eq_preset", "Personalizado")
            updated.forEachIndexed { idx, v -> putFloat("eq_band_$idx", v) }
            commit()
        }
        applyEqualizerToHardware()
    }

    fun seekLocalAudioTo(posMs: Long) {
        val target = posMs.coerceIn(0L, _audioDurationMs.value)
        val routeManager = AudioRouteManager.getInstance(context)
        if (routeManager.isCastingActive()) {
            routeManager.seekTo(target)
            _audioPositionMs.value = target
            return
        }
        exoPlayer?.seekTo(target)
        _audioPositionMs.value = target
    }

    fun updateArtworkForCurrentTrack(artworkUrl: String) {
        val current = _currentLocalAudio.value ?: return
        _currentLocalAudio.value = current.copy(albumArtUrl = artworkUrl)
    }


    fun togglePlayPause() {
        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).togglePlayPause()
            return
        }
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            val localAudio = _currentLocalAudio.value
            val station = _currentStation.value
            if (localAudio != null && player.playbackState == Player.STATE_IDLE) {
                playLocalAudio(localAudio, localAudioQueue)
            } else if (station != null && player.playbackState == Player.STATE_IDLE) {
                playStation(station)
            } else {
                resume()
            }
        }
    }

    fun pause() {
        android.util.Log.d("AUDIO_DEBUG", "🔴 pause() chamado")
        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).pause()
        }
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Falha ao pausar LocalVideoPlayerManager: ${e.message}")
        }
        userInitiatedPause = true
        cancelBufferingWatchdog()
        streamingTimeoutJob?.cancel()
        reconnectJob?.cancel()
        autoReconnectJob?.cancel()
        pauseLiveSessionTimer()
        exoPlayer?.pause()
        _playbackStatus.value = RadioPlaybackStatus.PAUSED
        releaseLocks()
        stopVisualizer()
    }

    fun resume() {
        android.util.Log.d("AUDIO_DEBUG", "🔴 resume()/play chamado - estado: ${exoPlayer?.playbackState}")
        requestAudioFocus()
        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).play()
            return
        }
        userInitiatedPause = false
        acquireLocks()
        exoPlayer?.volume = 1.0f
        exoPlayer?.play()
        _playbackStatus.value = RadioPlaybackStatus.PLAYING
        startVisualizer()
        if (_currentLocalAudio.value != null) {
            startAudioProgressTracker()
        } else if (_currentStation.value != null) {
            startLiveSessionTimer(resume = true)
        }
        startMediaService()
    }

    fun stop() {
        android.util.Log.d("AUDIO_DEBUG", "🔴 stop() chamado")
        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).pause()
        }
        userInitiatedPause = true
        cancelBufferingWatchdog()
        streamingTimeoutJob?.cancel()
        reconnectJob?.cancel()
        autoReconnectJob?.cancel()
        stopLiveSessionTimer()
        exoPlayer?.stop()
        _playbackStatus.value = RadioPlaybackStatus.IDLE
        releaseLocks()
        stopVisualizer()
        isMediaServiceRunning = false
    }

    private var preMuteVolume: Float = 0.8f
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    fun toggleMute() {
        if (_isMuted.value) {
            val restoreVol = if (preMuteVolume > 0.05f) preMuteVolume else 0.8f
            setVolumeLevel(restoreVol)
            _isMuted.value = false
        } else {
            if (_volume.value > 0.05f) {
                preMuteVolume = _volume.value
            }
            setVolumeLevel(0f)
            _isMuted.value = true
        }
    }

    fun setVolumeLevel(newVolume: Float) {
        android.util.Log.d("AUDIO_DEBUG", "🔵 setVolumeLevel() chamado: $newVolume")
        val clamped = newVolume.coerceIn(0f, 1f)
        _volume.value = clamped
        _isMuted.value = (clamped <= 0.01f)
        exoPlayer?.volume = 1.0f
        if (AudioRouteManager.getInstance(context).isCastingActive()) {
            AudioRouteManager.getInstance(context).setVolume(clamped)
            return
        }
        try {
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = kotlin.math.round(clamped * maxVol).toInt().coerceIn(0, maxVol)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Falha ao ajustar stream volume do sistema: ${e.message}")
        }

        // Salvar persistentemente na preferência para preservar a consistência de volume entre execuções
        try {
            if (clamped > 0.01f) {
                com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager.getInstance(context).volumeLevel = clamped
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Falha ao salvar volume persistentemente: ${e.message}")
        }
    }

    fun adjustVolumeDelta(delta: Float) {
        setVolumeLevel(_volume.value + delta)
    }

    private var stopAtEndOfEpisodeOrTrack = false

    private fun triggerSleepTimerStop() {
        pause()
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Falha ao pausar vídeo no sleep timer: ${e.message}")
        }
        try {
            val pauseIntent = Intent(context, RadioMediaService::class.java).apply {
                action = RadioMediaService.ACTION_PAUSE
            }
            context.startService(pauseIntent)
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Falha ao enviar pauseIntent no sleep timer: ${e.message}")
        }
        exoPlayer?.volume = 1.0f
        _sleepTimerSecondsRemaining.value = 0L
        _sleepTimerMinutes.value = 0
        stopAtEndOfEpisodeOrTrack = false
    }

    fun setSleepTimer(minutes: Int, stopAtEndOfEpisode: Boolean = false) {
        sleepTimerJob?.cancel()
        stopAtEndOfEpisodeOrTrack = stopAtEndOfEpisode || minutes == -1

        if (stopAtEndOfEpisodeOrTrack) {
            _sleepTimerMinutes.value = -1
            _sleepTimerSecondsRemaining.value = -1L
            sleepTimerJob = scope.launch(Dispatchers.Main) {
                while (isActive && stopAtEndOfEpisodeOrTrack) {
                    val duration = _audioDurationMs.value
                    val position = _audioPositionMs.value
                    if (duration > 0L && position >= duration - 1500L) {
                        triggerSleepTimerStop()
                        break
                    }
                    delay(500L)
                }
            }
            return
        }

        val totalSeconds = minutes * 60L
        _sleepTimerSecondsRemaining.value = totalSeconds
        _sleepTimerMinutes.value = minutes
        if (totalSeconds > 0L) {
            val targetEndMs = System.currentTimeMillis() + totalSeconds * 1000L
            val fadeDurationSec = 60L.coerceAtMost(totalSeconds / 3L).coerceAtLeast(10L)
            sleepTimerJob = scope.launch(Dispatchers.Main) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val remainingMs = targetEndMs - now
                    if (remainingMs <= 0L) {
                        break
                    }
                    val remainingSec = (remainingMs + 999L) / 1000L
                    _sleepTimerSecondsRemaining.value = remainingSec
                    _sleepTimerMinutes.value = ((remainingSec + 59L) / 60L).toInt()

                    // Suave Fade-out de volume nos últimos instantes antes de desligar
                    if (remainingSec <= fadeDurationSec) {
                        val fadeFactor = (remainingSec.toFloat() / fadeDurationSec.toFloat()).coerceIn(0.0f, 1.0f)
                        exoPlayer?.volume = fadeFactor
                    } else {
                        if (exoPlayer?.volume != 1.0f) {
                            exoPlayer?.volume = 1.0f
                        }
                    }

                    delay(1000L.coerceAtMost(remainingMs))
                }
                if (isActive) {
                    triggerSleepTimerStop()
                }
            }
        } else {
            exoPlayer?.volume = 1.0f
        }
    }

    private var lastRealSongTitle: String? = null
    private var lastRawStreamTitle: String? = null

    private fun isCommercialOrStationPromo(text: String, stationName: String): Boolean {
        val upper = text.uppercase()
        val stUpper = stationName.uppercase()
        if (upper == stUpper) return true
        val commercialKeywords = listOf(
            "COMERCIAL", "INTERVALO", "PROPAGANDA", "VINHETA", "SPOT", "BLOCO",
            "HORA CERTA", "A MELHOR", "AO VIVO", "SINTONIA", "RADIO", "RÁDIO", "FM", "WEB", "TUDORADIO"
        )
        if (!upper.contains(" - ") && !upper.contains(" – ")) {
            if (commercialKeywords.any { upper.contains(it) } || upper.length < 4) {
                return true
            }
        }
        return false
    }

    fun clearPlayerMetadata() {
        lastRealSongTitle = null
        lastRawStreamTitle = null
        _nowPlaying.value = NowPlayingMetadata(
            title = "Preparando reprodução",
            artist = "Conectando...",
            album = "Ao Vivo",
            artworkUri = null,
            isLiveStream = true,
            hasTrackInfo = false
        )
        scope.launch(Dispatchers.Main) {
            try {
                val appLogoUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
                val emptyMetadata = MediaMetadata.Builder()
                    .setTitle("MediaPod • Rádio")
                    .setDisplayTitle("MediaPod • Rádio")
                    .setArtist("Ao Vivo")
                    .setSubtitle("Ao Vivo")
                    .setAlbumTitle("MediaPod • Rádio")
                    .setArtworkUri(appLogoUri)
                    .setIsPlayable(true)
                    .build()
                exoPlayer?.playlistMetadata = emptyMetadata
            } catch (_: Exception) {}
        }
    }

    fun updateNotificationAndSessionMetadata(title: String, artist: String, album: String, artworkUri: Uri?) {
        scope.launch(Dispatchers.Main) {
            try {
                val logoUri = artworkUri
                    ?: Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_radio_generic}")
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .setArtist(artist)
                    .setSubtitle(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(logoUri)
                    .setIsPlayable(true)
                    .build()
                exoPlayer?.playlistMetadata = metadata
            } catch (e: Exception) {
                android.util.Log.w("RadioPlayerManager", "Failed to set playlist metadata on main thread", e)
            }
        }
    }

    fun getLastRealSongTitle(): String? = lastRealSongTitle

    private fun processDetectedRdsTitle(rawTitle: String) {
        val clean = rawTitle.replace("/RDS", "", ignoreCase = true)
            .replace("/ RDS", "", ignoreCase = true)
            .replace("StreamTitle=", "", ignoreCase = true)
            .replace("'", "")
            .replace(";", "")
            .trim()
            .uppercase()
        if (clean.isBlank()) return
        lastRawStreamTitle = clean

        val station = _currentStation.value ?: return

        scope.launch(Dispatchers.Main) {
            val (metadata, newValidTitle) = MetadataParser.parseIcyForRadio(
                rawStreamTitle = clean,
                stationName = station.name,
                stationUrl = station.streamUrl,
                lastValidTitle = lastRealSongTitle
            )
            _nowPlaying.value = metadata

            if (isCommercialOrStationPromo(clean, station.name)) {
                // Intervalo/vinheta: mantém a última música real detectada (sem mensagem de busca)
                val displayText = if (!lastRealSongTitle.isNullOrBlank()) {
                    lastRealSongTitle!!
                } else {
                    "Ao Vivo"
                }
                _rdsInfo.value = _rdsInfo.value.copy(
                    radioText = displayText,
                    hasRealRds = !lastRealSongTitle.isNullOrBlank()
                )
            } else {
                // Música real identificada diretamente pelo stream do ExoPlayer
                val validSong = newValidTitle ?: clean
                lastRealSongTitle = validSong
                _rdsInfo.value = _rdsInfo.value.copy(
                    radioText = validSong,
                    hasRealRds = true
                )
                updateNotificationAndSessionMetadata(
                    title = station.name,
                    artist = validSong,
                    album = "MediaPod • Rádio",
                    artworkUri = LocalArtworkGenerator.getDefaultRadioArtwork(context)
                )
                if (AudioRouteManager.getInstance(context).isCastingActive()) {
                    AudioRouteManager.getInstance(context).updateCastMedia()
                }
            }
        }
    }

    private fun startRdsMetadataSimulation(station: RadioStation) {
        rdsSimulationJob?.cancel()
        lastRealSongTitle = null
        lastRawStreamTitle = null

        // Inicializa dados de exibição diretamente sem abrir conexões concorrentes que interfiram no streaming de áudio
        _rdsInfo.value = RdsInfo(
            programService = station.name.take(12).uppercase(),
            radioText = "${station.name.uppercase()} • ${station.displayFrequency}",
            hasRealRds = false,
            programType = "[${station.primaryGenre.uppercase()}]",
            signalStrengthBars = 5,
            isStereo = true,
            hasTrafficProgram = true,
            bitrateInfo = "${station.bitrate} kbps ${station.codec}",
            frequencyMhz = station.displayFrequency
        )
    }


    private var isUiActive = true

    fun setUiActive(active: Boolean) {
        isUiActive = active
        if (!active) {
            stopVisualizer()
        } else if (_playbackStatus.value == RadioPlaybackStatus.PLAYING) {
            startVisualizer()
        }
    }

    private fun startVisualizer() {
        if (!isUiActive) return
        if (visualizerJob?.isActive == true) return
        visualizerJob = scope.launch(Dispatchers.Default) {
            while (isActive && isUiActive) {
                val newAmps = List(16) { i ->
                    val base = 0.25f + (sinWave(i) * 0.4f)
                    val jitter = Random.nextFloat() * 0.35f
                    (base + jitter).coerceIn(0.08f, 1.0f)
                }
                _visualizerAmplitudes.value = newAmps
                delay(220) // Smooth, low CPU (4.5 fps instead of aggressive 12.5 fps)
            }
        }
    }

    private fun sinWave(index: Int): Float {
        val t = System.currentTimeMillis() / 250.0
        return ((kotlin.math.sin(t + index * 0.4) + 1.0) / 2.0).toFloat()
    }

    private fun stopVisualizer() {
        visualizerJob?.cancel()
        visualizerJob = null
        _visualizerAmplitudes.value = List(16) { 0.08f }
    }

    private var isVolumeListenerRegistered = false

    private val volumeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                syncVolumeFromNativeStream()
            }
        }
    }

    private val volumeObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            syncVolumeFromNativeStream()
        }
    }

    private fun initVolumeListeners() {
        if (!isVolumeListenerRegistered) {
            try {
                val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    volumeReceiver,
                    filter,
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                )
                context.contentResolver.registerContentObserver(
                    android.provider.Settings.System.CONTENT_URI,
                    true,
                    volumeObserver
                )
                isVolumeListenerRegistered = true
                syncVolumeFromNativeStream()
            } catch (_: Exception) {}
        }
    }

    fun syncVolumeFromNativeStream() {
        try {
            audioManager?.let { am ->
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) {
                    val ratio = (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    if (kotlin.math.abs(_volume.value - ratio) > 0.005f) {
                        _volume.value = ratio
                        _isMuted.value = (ratio <= 0.01f)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        if (isVolumeListenerRegistered) {
            try {
                context.unregisterReceiver(volumeReceiver)
                context.contentResolver.unregisterContentObserver(volumeObserver)
                isVolumeListenerRegistered = false
            } catch (_: Exception) {}
        }
        visualizerJob?.cancel()
        rdsSimulationJob?.cancel()
        sleepTimerJob?.cancel()
        reconnectJob?.cancel()
        releaseLocks()
        exoPlayer?.release()
        exoPlayer = null
    }

    companion object {
        @Volatile
        private var INSTANCE: RadioPlayerManager? = null

        fun getInstance(context: Context): RadioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = RadioPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
