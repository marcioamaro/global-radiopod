package com.example.player

import android.content.Context
import android.content.Intent
import com.example.R
import android.media.AudioManager
import android.media.audiofx.Equalizer
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
import com.example.data.model.RadioStation
import com.example.service.RadioMediaService
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
import kotlin.random.Random

enum class RadioPlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
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

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _rdsInfo = MutableStateFlow(RdsInfo())
    val rdsInfo: StateFlow<RdsInfo> = _rdsInfo.asStateFlow()

    private val _visualizerAmplitudes = MutableStateFlow(List(16) { 0.1f })
    val visualizerAmplitudes: StateFlow<List<Float>> = _visualizerAmplitudes.asStateFlow()

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    // Local Audio Playback Support (MP3 Player)
    private val _currentLocalAudio = MutableStateFlow<com.example.data.model.LocalAudioTrack?>(null)
    val currentLocalAudio: StateFlow<com.example.data.model.LocalAudioTrack?> = _currentLocalAudio.asStateFlow()

    private val _audioPositionMs = MutableStateFlow(0L)
    val audioPositionMs: StateFlow<Long> = _audioPositionMs.asStateFlow()

    private val _audioDurationMs = MutableStateFlow(0L)
    val audioDurationMs: StateFlow<Long> = _audioDurationMs.asStateFlow()

    // Global Equalizer Engine (Rádio ao Vivo e MP3 Local)
    private var equalizer: Equalizer? = null

    val EQUALIZER_PRESETS = mapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(4.5f, 2.5f, -1.0f, 2.5f, 5.0f),
        "Pop" to listOf(-1.5f, 2.0f, 4.0f, 1.5f, -1.0f),
        "Blues" to listOf(3.0f, 1.5f, 0.0f, 2.0f, 3.5f),
        "Jazz" to listOf(3.5f, 2.0f, -1.5f, 2.0f, 4.0f),
        "Clássica" to listOf(4.0f, 2.5f, -1.0f, 2.5f, 3.5f),
        "Bass Boost" to listOf(6.0f, 4.0f, 1.0f, 0.0f, -1.0f),
        "Eletrônica" to listOf(5.0f, 3.0f, -1.5f, 2.0f, 4.5f),
        "Vocal" to listOf(-2.0f, 1.0f, 5.0f, 3.0f, 0.0f),
        "Personalizado" to listOf(0f, 0f, 0f, 0f, 0f)
    )

    private val eqPrefs = context.getSharedPreferences("radiopod_equalizer", Context.MODE_PRIVATE)

    private val _isEqualizerEnabled = MutableStateFlow(eqPrefs.getBoolean("eq_enabled", true))
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(eqPrefs.getString("eq_preset", "Rock") ?: "Rock")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()

    private fun loadSavedEqualizerBands(): List<Float> {
        val preset = eqPrefs.getString("eq_preset", "Rock") ?: "Rock"
        val defaultBands = EQUALIZER_PRESETS[preset] ?: EQUALIZER_PRESETS["Rock"]!!
        return List(5) { i ->
            eqPrefs.getFloat("eq_band_$i", defaultBands[i])
        }
    }

    private val _equalizerBands = MutableStateFlow<List<Float>>(loadSavedEqualizerBands())
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    var onFolderWrapNext: (() -> Unit)? = null
    var onFolderWrapPrev: (() -> Unit)? = null

    private var localAudioQueue: List<com.example.data.model.LocalAudioTrack> = emptyList()
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
    private var stabilityValidationJob: Job? = null
    private var isStreamStable = false
    private var userInitiatedPause = false

    init {
        try {
            audioManager?.let { am ->
                val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) {
                    _volume.value = (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                }
            }
        } catch (_: Exception) {}
        initLocks()
        initPlayer()
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

    private fun initPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Configure buffer duration for smooth and stable live radio streaming
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // minBufferMs (15 seconds buffer)
                60000,  // maxBufferMs (60 seconds)
                2500,   // bufferForPlaybackMs (2.5 seconds to start quickly)
                5000    // bufferForPlaybackAfterRebufferMs (5 seconds)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10000, false)
            .build()

        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .build().apply {
                volume = _volume.value
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                _playbackStatus.value = RadioPlaybackStatus.BUFFERING
                                acquireLocks()
                                stopVisualizer()
                            }
                            Player.STATE_READY -> {
                                if (playWhenReady) {
                                    _playbackStatus.value = RadioPlaybackStatus.PLAYING
                                    acquireLocks()
                                    startVisualizer()
                                    val station = _currentStation.value
                                    if (station != null && !userInitiatedPause) {
                                        startStabilityValidation(station)
                                    }
                                } else if (userInitiatedPause) {
                                    _playbackStatus.value = RadioPlaybackStatus.PAUSED
                                    stabilityValidationJob?.cancel()
                                    streamingTimeoutJob?.cancel()
                                    releaseLocks()
                                    stopVisualizer()
                                }
                                _errorMessage.value = null
                            }
                            Player.STATE_ENDED -> {
                                if (_currentLocalAudio.value != null) {
                                    nextLocalTrack()
                                } else {
                                    val station = _currentStation.value
                                    if (station != null && !userInitiatedPause) {
                                        android.util.Log.w(
                                            "RadioPlayerManager",
                                            "Fim de fluxo recebido para '${station.name}'. Alternando para próxima fonte ou reconectando..."
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
                            _playbackStatus.value = RadioPlaybackStatus.PLAYING
                            acquireLocks()
                            startVisualizer()
                            val station = _currentStation.value
                            if (station != null && !userInitiatedPause) {
                                startStabilityValidation(station)
                            }
                        } else if (userInitiatedPause) {
                            _playbackStatus.value = RadioPlaybackStatus.PAUSED
                            stabilityValidationJob?.cancel()
                            releaseLocks()
                            stopVisualizer()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val station = _currentStation.value
                        if (station != null) {
                            handleStreamFailureOrTimeout(station, "Erro no streaming: ${error.errorCodeName}")
                        } else {
                            stopVisualizer()
                            releaseLocks()
                            streamingTimeoutJob?.cancel()
                            _playbackStatus.value = RadioPlaybackStatus.ERROR
                            _errorMessage.value = "Erro na transmissão: ${error.localizedMessage ?: "Stream indisponível"}"
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

                    override fun onMetadata(metadata: Metadata) {
                        for (i in 0 until metadata.length()) {
                            val entry = metadata.get(i)
                            if (entry is IcyInfo) {
                                val streamTitle = entry.title
                                if (!streamTitle.isNullOrBlank()) {
                                    processDetectedRdsTitle(streamTitle)
                                }
                            }
                        }
                    }

                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                            initEqualizer(audioSessionId)
                        }
                    }
                })
                val sid = audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET) {
                    initEqualizer(sid)
                }
            }
    }

    private var playlist: List<RadioStation> = emptyList()

    fun updatePlaylist(stations: List<RadioStation>) {
        if (stations.isNotEmpty()) {
            playlist = stations
        }
    }

    fun playNextStation() {
        val list = if (playlist.isNotEmpty()) playlist else com.example.data.repository.CuratedData.CURATED_GLOBAL_STATIONS
        if (list.isNotEmpty()) {
            val current = _currentStation.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % list.size else 0
            playStation(list[nextIndex])
        }
    }

    fun playPreviousStation() {
        val list = if (playlist.isNotEmpty()) playlist else com.example.data.repository.CuratedData.CURATED_GLOBAL_STATIONS
        if (list.isNotEmpty()) {
            val current = _currentStation.value
            val currentIndex = list.indexOfFirst { it.id == current?.id }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
            playStation(list[prevIndex])
        }
    }

    fun playNext() {
        if (_currentLocalAudio.value != null) {
            nextLocalTrack()
        } else {
            playNextStation()
        }
    }

    fun playPrevious() {
        if (_currentLocalAudio.value != null) {
            prevLocalTrack()
        } else {
            playPreviousStation()
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) initPlayer()
        return exoPlayer!!
    }

    fun playStation(station: RadioStation) {
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (_: Exception) {}
        _currentLocalAudio.value = null
        audioProgressJob?.cancel()
        _currentStation.value = station
        com.example.data.preferences.IpodPreferencesManager.getInstance(context).addRecentStation(station)
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
        isStreamStable = false
        stabilityValidationJob?.cancel()
        reconnectJob?.cancel()

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
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(mapOf(
                "Icy-MetaData" to "1",
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            ))
    }

    private fun startStabilityValidation(station: RadioStation) {
        stabilityValidationJob?.cancel()
        stabilityValidationJob = scope.launch {
            delay(5000L) // Validação de 5 segundos de streaming contínuo sem interrupções
            if (exoPlayer?.isPlaying == true && !userInitiatedPause) {
                isStreamStable = true
                retryCount = 0
                totalAttemptCount = 0
                streamingTimeoutJob?.cancel()
                _errorMessage.value = null
                android.util.Log.i(
                    "RadioPlayerManager",
                    "Streaming da rádio '${station.name}' validado e estabilizado após 5s. Mantendo ativo continuamente."
                )
            }
        }
    }

    private fun startStreamingTimeoutWatcher(station: RadioStation) {
        streamingTimeoutJob?.cancel()
        streamingTimeoutJob = scope.launch {
            delay(10000L) // Limite de 10 segundos para iniciar reprodução
            if (_playbackStatus.value == RadioPlaybackStatus.BUFFERING && !userInitiatedPause) {
                android.util.Log.w(
                    "RadioPlayerManager",
                    "Streaming timeout de 10s na emissora ${station.name}. Alternando para próxima fonte/agente..."
                )
                handleStreamFailureOrTimeout(station, "Tempo limite de 10s esgotado")
            }
        }
    }

    private fun handleStreamFailureOrTimeout(station: RadioStation, reason: String) {
        if (userInitiatedPause) return

        stopVisualizer()
        streamingTimeoutJob?.cancel()
        stabilityValidationJob?.cancel()

        val candidates = station.getAllStreamCandidates()

        // Se o streaming já estava estável e sofreu uma oscilação na rede, reinicia contagem para manter o streaming ativo
        if (isStreamStable) {
            isStreamStable = false
            totalAttemptCount = 0
            android.util.Log.w(
                "RadioPlayerManager",
                "Oscilação em stream estável de '${station.name}' ($reason). Reconectando para manter ativo..."
            )
        }

        // Tenta próximas fontes com agentes distintos
        if (totalAttemptCount < 5) {
            totalAttemptCount++
            currentUserAgentIndex = (currentUserAgentIndex + 1) % USER_AGENTS.size
            if (candidates.size > 1) {
                currentCandidateIndex = (currentCandidateIndex + 1) % candidates.size
            }
            val nextUrl = candidates[currentCandidateIndex]

            val agentLabel = when (currentUserAgentIndex) {
                0 -> "Android Chrome"
                1 -> "ExoPlayer"
                2 -> "VLC"
                3 -> "Winamp"
                else -> "Apple CoreMedia"
            }
            android.util.Log.w(
                "RadioPlayerManager",
                "Tentativa $totalAttemptCount/5 para '${station.name}' usando agente '$agentLabel' e fonte: $nextUrl ($reason)"
            )
            _playbackStatus.value = RadioPlaybackStatus.BUFFERING
            _errorMessage.value = "Conectando fonte alternativa ($totalAttemptCount/5)..."

            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(300L)
                playStreamUrl(station, nextUrl)
            }
            return
        }

        // 5 tentativas expiradas sem sucesso
        android.util.Log.e("RadioPlayerManager", "Todas as 5 tentativas esgotadas para a rádio ${station.name}")
        releaseLocks()
        _playbackStatus.value = RadioPlaybackStatus.ERROR
        _errorMessage.value = "Não foi possível conectar a uma fonte válida para esta rádio"
    }

    private fun playStreamUrl(station: RadioStation, streamUrl: String) {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(station.primaryGenre)
            .setAlbumTitle(station.country)
            .setArtworkUri(Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}"))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(streamUrl)
            .setMediaMetadata(mediaMetadata)
            .build()

        val player = getPlayer()
        try {
            player.stop()
            player.clearMediaItems()

            val mediaSource = DefaultMediaSourceFactory(getHttpDataSourceFactory())
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource)

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

    fun playLocalAudio(track: com.example.data.model.LocalAudioTrack, queue: List<com.example.data.model.LocalAudioTrack> = emptyList()) {
        try {
            LocalVideoPlayerManager.getInstance(context).pause()
        } catch (_: Exception) {}
        _currentStation.value = null
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
            .setArtworkUri(track.albumArtUrl?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.contentUri)
            .setMediaMetadata(mediaMetadata)
            .build()

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

        val player = getPlayer()
        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            startMediaService()
            startAudioProgressTracker()
            startVisualizer()
        } catch (e: Exception) {
            _playbackStatus.value = RadioPlaybackStatus.ERROR
            _errorMessage.value = "Falha ao reproduzir: ${e.message}"
        }
    }

    private fun startAudioProgressTracker() {
        audioProgressJob?.cancel()
        audioProgressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { p ->
                    _audioPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                    val dur = p.duration
                    if (dur > 0) {
                        _audioDurationMs.value = dur
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

    private fun applyEqualizerToHardware() {
        val eq = equalizer ?: return
        try {
            eq.enabled = _isEqualizerEnabled.value
            if (!eq.enabled) return

            val numBands = eq.numberOfBands.toInt()
            val currentLevels = _equalizerBands.value
            for (i in 0 until minOf(numBands, currentLevels.size)) {
                val mB = (currentLevels[i] * 100).toInt().coerceIn(-1500, 1500).toShort()
                eq.setBandLevel(i.toShort(), mB)
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioPlayerManager", "Failed to apply Equalizer levels", e)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        eqPrefs.edit().putBoolean("eq_enabled", enabled).apply()
        applyEqualizerToHardware()
    }

    fun setEqualizerPreset(presetName: String) {
        _equalizerPreset.value = presetName
        eqPrefs.edit().putString("eq_preset", presetName).apply()
        if (presetName != "Personalizado") {
            val presetBands = EQUALIZER_PRESETS[presetName] ?: return
            _equalizerBands.value = presetBands
            eqPrefs.edit().apply {
                presetBands.forEachIndexed { idx, v -> putFloat("eq_band_$idx", v) }
                apply()
            }
        }
        applyEqualizerToHardware()
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelDb: Float) {
        if (bandIndex !in 0..4) return
        val current = _equalizerBands.value.toMutableList()
        current[bandIndex] = levelDb.coerceIn(-12f, 12f)
        _equalizerBands.value = current
        _equalizerPreset.value = "Personalizado"
        eqPrefs.edit().apply {
            putString("eq_preset", "Personalizado")
            putFloat("eq_band_$bandIndex", current[bandIndex])
            apply()
        }
        applyEqualizerToHardware()
    }

    fun seekLocalAudioTo(posMs: Long) {
        val target = posMs.coerceIn(0L, _audioDurationMs.value)
        exoPlayer?.seekTo(target)
        _audioPositionMs.value = target
    }

    fun updateArtworkForCurrentTrack(artworkUrl: String) {
        val current = _currentLocalAudio.value ?: return
        _currentLocalAudio.value = current.copy(albumArtUrl = artworkUrl)
    }


    fun togglePlayPause() {
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
        userInitiatedPause = true
        stabilityValidationJob?.cancel()
        streamingTimeoutJob?.cancel()
        reconnectJob?.cancel()
        exoPlayer?.pause()
        _playbackStatus.value = RadioPlaybackStatus.PAUSED
        releaseLocks()
        stopVisualizer()
    }

    fun resume() {
        userInitiatedPause = false
        isStreamStable = false
        acquireLocks()
        exoPlayer?.play()
        _playbackStatus.value = RadioPlaybackStatus.PLAYING
        startVisualizer()
        if (_currentLocalAudio.value != null) {
            startAudioProgressTracker()
        }
        startMediaService()
        val station = _currentStation.value
        if (station != null) {
            startStabilityValidation(station)
        }
    }

    fun stop() {
        userInitiatedPause = true
        stabilityValidationJob?.cancel()
        streamingTimeoutJob?.cancel()
        reconnectJob?.cancel()
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
        val clamped = newVolume.coerceIn(0f, 1f)
        _volume.value = clamped
        _isMuted.value = (clamped <= 0.01f)
        exoPlayer?.volume = clamped
        try {
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (clamped * maxVol).toInt().coerceIn(0, maxVol)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        } catch (_: Exception) {}
    }

    fun adjustVolumeDelta(delta: Float) {
        setVolumeLevel(_volume.value + delta)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            sleepTimerJob = scope.launch {
                var remaining = minutes
                while (remaining > 0 && isActive) {
                    delay(60_000L)
                    remaining -= 1
                    _sleepTimerMinutes.value = remaining
                }
                if (isActive) {
                    pause()
                    _sleepTimerMinutes.value = 0
                }
            }
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

    fun updateNotificationAndSessionMetadata(title: String, artist: String, album: String, artworkUri: Uri?) {
        scope.launch(Dispatchers.Main) {
            try {
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri)
                    .build()
                exoPlayer?.playlistMetadata = metadata
            } catch (e: Exception) {
                android.util.Log.w("RadioPlayerManager", "Failed to set playlist metadata on main thread", e)
            }
        }
    }

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
            if (isCommercialOrStationPromo(clean, station.name)) {
                // It's a commercial or slogan: do NOT erase last detected song!
                val displayText = if (!lastRealSongTitle.isNullOrBlank()) {
                    "COMERCIAL • ANTERIOR: $lastRealSongTitle"
                } else {
                    "SINTONIZANDO RDS... [BUSCANDO FAIXA]"
                }
                _rdsInfo.value = _rdsInfo.value.copy(
                    radioText = displayText,
                    hasRealRds = true
                )
            } else {
                // Real song identified!
                lastRealSongTitle = clean
                _rdsInfo.value = _rdsInfo.value.copy(
                    radioText = clean,
                    hasRealRds = true
                )
                updateNotificationAndSessionMetadata(
                    title = clean,
                    artist = station.name,
                    album = "${station.country} • ${station.displayFrequency}",
                    artworkUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
                )
            }
        }
    }

    private fun pollIcyMetadata(streamUrl: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(streamUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Icy-MetaData", "1")
            connection.setRequestProperty("User-Agent", "WinampMPEG/5.0")
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.instanceFollowRedirects = true
            connection.connect()

            val metaIntHeader = connection.getHeaderField("icy-metaint")
            if (metaIntHeader != null) {
                val metaInt = metaIntHeader.toIntOrNull() ?: 0
                if (metaInt > 0) {
                    val inputStream = connection.inputStream
                    var skipped = 0L
                    while (skipped < metaInt) {
                        val s = inputStream.skip(metaInt - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    val lengthByte = inputStream.read()
                    val metaLength = lengthByte * 16
                    if (metaLength in 1..4096) {
                        val buffer = ByteArray(metaLength)
                        var read = 0
                        while (read < metaLength) {
                            val r = inputStream.read(buffer, read, metaLength - read)
                            if (r <= 0) break
                            read += r
                        }
                        val metaString = String(buffer, 0, read, Charsets.UTF_8)
                        val match = Regex("StreamTitle='(.*?)';", RegexOption.IGNORE_CASE).find(metaString)
                        return match?.groupValues?.getOrNull(1)?.trim()
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun startRdsMetadataSimulation(station: RadioStation) {
        rdsSimulationJob?.cancel()
        lastRealSongTitle = null
        lastRawStreamTitle = null

        // Initial background ICY probe on station connect (single shot)
        scope.launch(Dispatchers.IO) {
            try {
                val initialTitle = pollIcyMetadata(station.streamUrl)
                if (!initialTitle.isNullOrBlank()) {
                    processDetectedRdsTitle(initialTitle)
                }
            } catch (_: Exception) {}
        }

        // Lightweight smooth ticker rotation (zero network sockets, zero modem heating)
        rdsSimulationJob = scope.launch(Dispatchers.Default) {
            val sampleTracks = getSampleTrackListForGenre(station.tags + " " + station.primaryGenre)
            var sampleIndex = 0
            var tickerPhase = 0
            var intervalsWithoutSong = 0

            while (isActive) {
                if (_playbackStatus.value == RadioPlaybackStatus.PLAYING) {
                    // If ExoPlayer in-stream ICY hasn't provided a title after 30 seconds (10 intervals of 3s), try a single probe
                    if (lastRealSongTitle.isNullOrBlank()) {
                        intervalsWithoutSong++
                        if (intervalsWithoutSong >= 10) {
                            intervalsWithoutSong = 0
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val probeResult = pollIcyMetadata(station.streamUrl)
                                    if (!probeResult.isNullOrBlank()) {
                                        processDetectedRdsTitle(probeResult)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        intervalsWithoutSong = 0
                    }

                    // Smooth RDS ticker rotation
                    val currentSong = lastRealSongTitle
                    scope.launch(Dispatchers.Main) {
                        if (!currentSong.isNullOrBlank()) {
                            val displayText = when (tickerPhase % 4) {
                                0, 1 -> currentSong
                                2 -> "MÚSICA: $currentSong"
                                else -> "${station.name.uppercase()} • ${station.displayFrequency}"
                            }
                            _rdsInfo.value = _rdsInfo.value.copy(
                                radioText = displayText,
                                programService = station.name.take(12).uppercase()
                            )
                        } else {
                            val currentSample = sampleTracks[sampleIndex % sampleTracks.size]
                            val displayText = when (tickerPhase % 3) {
                                0 -> currentSample
                                1 -> "${station.name.uppercase()} • ${station.displayFrequency}"
                                else -> "MÚSICA: $currentSample"
                            }
                            _rdsInfo.value = _rdsInfo.value.copy(
                                radioText = displayText,
                                programService = station.name.take(12).uppercase()
                            )
                            if (tickerPhase % 3 == 0) sampleIndex++
                        }
                    }
                    tickerPhase++
                }
                delay(3500L) // Lightweight, calm rotation interval
            }
        }
    }

    private fun getSampleTrackListForGenre(tags: String): List<String> {
        val lower = tags.lowercase()
        return when {
            lower.contains("rock") -> listOf(
                "QUEEN - BOHEMIAN RHAPSODY",
                "PINK FLOYD - COMFORTABLY NUMB",
                "AC/DC - HIGHWAY TO HELL",
                "LED ZEPPELIN - STAIRWAY TO HEAVEN",
                "THE ROLLING STONES - PAINT IT BLACK",
                "FOO FIGHTERS - EVERLONG"
            )
            lower.contains("jazz") -> listOf(
                "MILES DAVIS - SO WHAT",
                "JOHN COLTRANE - GIANT STEPS",
                "DAVE BRUBECK - TAKE FIVE",
                "BILL EVANS - AUTUMN LEAVES",
                "CHET BAKER - MY FUNNY VALENTINE"
            )
            lower.contains("sertanejo") -> listOf(
                "CHITÃOZINHO & XORORÓ - EVIDÊNCIAS",
                "JORGE & MATEUS - AMO NOITE E DIA",
                "HENRIQUE & JULIANO - LIBERDADE PROVISÓRIA",
                "ZEZÉ DI CAMARGO & LUCIANO - É O AMOR"
            )
            lower.contains("mpb") -> listOf(
                "TOM JOBIM - GAROTA DE IPANEMA",
                "TIM MAIA - NÃO QUERO DINHEIRO",
                "DJAVAN - OCEANO",
                "CAETANO VELOSO - VOCÊ É LINDA",
                "ELIS REGINA - COMO NOSSOS PAIS"
            )
            lower.contains("dance") || lower.contains("electronic") -> listOf(
                "DAFT PUNK - ONE MORE TIME",
                "AVICII - LEVELS",
                "CALVIN HARRIS - SUMMER",
                "DAVID GUETTA - TITANIUM",
                "SWEDISH HOUSE MAFIA - DON'T YOU WORRY CHILD"
            )
            lower.contains("classical") -> listOf(
                "L. V. BEETHOVEN - SYMPHONY NO. 5 IN C MINOR",
                "W. A. MOZART - EINE KLEINE NACHTMUSIK",
                "A. VIVALDI - LE QUATTRO STAGIONI (SPRING)",
                "J. S. BACH - CELLO SUITE NO. 1 IN G MAJOR"
            )
            lower.contains("news") -> listOf(
                "GIRO DE NOTÍCIAS • ECONOMIA, MUNDO E POLÍTICA",
                "BOLETIM DO TRÂNSITO E TEMPO EM TEMPO REAL",
                "DEBATE AO VIVO • JORNALISMO 24 HORAS"
            )
            else -> listOf(
                "DUA LIPA - LEVITATING",
                "THE WEEKND - BLINDING LIGHTS",
                "COLDPLAY - VIVA LA VIDA",
                "MICHAEL JACKSON - BILLIE JEAN",
                "ADELE - ROLLING IN THE DEEP",
                "BRUNO MARS - 24K MAGIC"
            )
        }
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

    fun release() {
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
