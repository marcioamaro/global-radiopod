package com.marcioamaro.mediapod.player

import android.content.Context
import com.marcioamaro.mediapod.R
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AudioDeviceType {
    THIS_DEVICE,
    BLUETOOTH,
    CAST_REMOTE,
    OTHER
}

/**
 * Máquina de estados formal para sessões do Google Cast (Chromecast/Nest).
 */
enum class CastSessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    SUSPENDED,
    ENDING,
    ERROR
}

data class AudioRouteDevice(
    val id: String,
    val name: String,
    val description: String = "",
    val deviceType: AudioDeviceType,
    val isSelected: Boolean,
    val isDefault: Boolean,
    val routeInfo: MediaRouter.RouteInfo? = null
)

class AudioRouteManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mediaRouter: MediaRouter? by lazy {
        try {
            MediaRouter.getInstance(context)
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "MediaRouter indisponível neste ambiente: ${e.message}")
            null
        }
    }

    private val _availableDevices = MutableStateFlow<List<AudioRouteDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioRouteDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AudioRouteDevice?>(null)
    val selectedDevice: StateFlow<AudioRouteDevice?> = _selectedDevice.asStateFlow()

    private val _castSessionState = MutableStateFlow(CastSessionState.DISCONNECTED)
    val castSessionState: StateFlow<CastSessionState> = _castSessionState.asStateFlow()

    @androidx.annotation.VisibleForTesting
    fun setCastSessionStateForTesting(state: CastSessionState) {
        _castSessionState.value = state
    }

    private val routeSelector: MediaRouteSelector by lazy {
        val builder = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)

        try {
            builder.addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
        } catch (_: Exception) {}

        builder.build()
    }

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
        }

        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            updateRoutes()
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            updateRoutes()
        }
    }

    private var castSession: com.google.android.gms.cast.framework.CastSession? = null
    private var isCastingActive = false
    private var lastRemoteIsPlaying: Boolean = true
    private var lastRemoteStreamPositionMs: Long = 0L

    private val castVolumeListener = object : com.google.android.gms.cast.Cast.Listener() {
        override fun onVolumeChanged() {
            val session = castSession ?: return
            try {
                val reportedVol = session.volume
                android.util.Log.d("CAST_VOL", "onVolumeChanged reportado pelo CastSession: $reportedVol")
                com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context).updateCastVolumeFromStatus(reportedVol)
            } catch (e: Exception) {
                android.util.Log.w("CAST_VOL", "Erro ao ler volume em onVolumeChanged: ${e.message}")
            }
        }
    }

    private val castSessionListener = object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
        override fun onSessionStarting(session: com.google.android.gms.cast.framework.CastSession) {
            _castSessionState.value = CastSessionState.CONNECTING
        }
        override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {
            castSession = session
            isCastingActive = true
            _castSessionState.value = CastSessionState.CONNECTED
            com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).startServer()
            session.remoteMediaClient?.registerCallback(remoteClientCallback)
            try {
                session.addCastListener(castVolumeListener)
            } catch (e: Exception) {
                android.util.Log.w("CAST_VOL", "Falha ao registrar castVolumeListener: ${e.message}")
            }
            val volumeManager = com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context)
            val castVol = try { session.volume.toFloat() } catch (_: Exception) { 1.0f }
            volumeManager.switchToCast(castVol)
            transferPlaybackToCast(session)
            updateRoutes()
        }
        override fun onSessionStartFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            try {
                session.removeCastListener(castVolumeListener)
            } catch (_: Exception) {}
            castSession = null
            isCastingActive = false
            _castSessionState.value = CastSessionState.ERROR
            com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context).switchToLocal()
            com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).stopServer()
            updateRoutes()
        }
        override fun onSessionEnding(session: com.google.android.gms.cast.framework.CastSession) {
            _castSessionState.value = CastSessionState.ENDING
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            try {
                session.removeCastListener(castVolumeListener)
            } catch (_: Exception) {}
        }
        override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            try {
                session.removeCastListener(castVolumeListener)
            } catch (_: Exception) {}
            castSession = null
            com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).stopServer()
            _castSessionState.value = CastSessionState.DISCONNECTED
            com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context).switchToLocal()
            if (isCastingActive) {
                isCastingActive = false
                transferPlaybackToLocal()
            }
            updateRoutes()
        }
        override fun onSessionResuming(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {
            _castSessionState.value = CastSessionState.CONNECTING
        }
        override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) {
            castSession = session
            isCastingActive = true
            _castSessionState.value = CastSessionState.CONNECTED
            com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).startServer()
            session.remoteMediaClient?.registerCallback(remoteClientCallback)
            try {
                session.addCastListener(castVolumeListener)
            } catch (e: Exception) {
                android.util.Log.w("CAST_VOL", "Falha ao registrar castVolumeListener: ${e.message}")
            }
            val volumeManager = com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context)
            val castVol = try { session.volume.toFloat() } catch (_: Exception) { 1.0f }
            volumeManager.switchToCast(castVol)
            updateRoutes()
        }
        override fun onSessionResumeFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            try {
                session.removeCastListener(castVolumeListener)
            } catch (_: Exception) {}
            castSession = null
            isCastingActive = false
            _castSessionState.value = CastSessionState.ERROR
            com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).stopServer()
            transferPlaybackToLocal()
            updateRoutes()
        }
        override fun onSessionSuspended(session: com.google.android.gms.cast.framework.CastSession, reason: Int) {
            _castSessionState.value = CastSessionState.SUSPENDED
        }
    }

    private val remoteClientCallback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val rmc = castSession?.remoteMediaClient ?: return
            val playerManager = RadioPlayerManager.getInstance(context)
            val mediaStatus = rmc.mediaStatus
            android.util.Log.d(
                "AudioRouteManager",
                "Cast onStatusUpdated: isPlaying=${rmc.isPlaying}, isPaused=${rmc.isPaused}, isBuffering=${rmc.isBuffering}, " +
                    "playerState=${mediaStatus?.playerState}, idleReason=${mediaStatus?.idleReason}, " +
                    "streamPosition=${rmc.approximateStreamPosition}"
            )
            lastRemoteIsPlaying = rmc.isPlaying
            val pos = rmc.approximateStreamPosition
            if (pos > 0L && isCastingActive()) {
                lastRemoteStreamPositionMs = pos
                val activeType = playerManager.activeMediaType.value
                if (activeType == ActiveMediaType.LOCAL_VIDEO) {
                    com.marcioamaro.mediapod.player.LocalVideoPlayerManager.getInstance(context)
                        .setCastPositionDirect(pos, rmc.isPlaying)
                } else if (activeType != ActiveMediaType.LIVE_RADIO) {
                    playerManager.setAudioPositionDirect(pos)
                }
            }
            try {
                val castVol = castSession?.volume?.toFloat() ?: 1.0f
                com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context).updateCastVolume(castVol)
            } catch (_: Exception) {}
            if (mediaStatus != null) {
                if (mediaStatus.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE) {
                    when (mediaStatus.idleReason) {
                        com.google.android.gms.cast.MediaStatus.IDLE_REASON_FINISHED -> {
                            android.util.Log.d("AudioRouteManager", "Cast item finalizou -> avançando próximo")
                            scope.launch(Dispatchers.Main) {
                                playNext()
                            }
                            return
                        }
                        com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR -> {
                            android.util.Log.e("AudioRouteManager", "Cast ERRO DE REPRODUÇÃO (IDLE_REASON_ERROR) no dispositivo receptor!")
                            playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.ERROR)
                            return
                        }
                        com.google.android.gms.cast.MediaStatus.IDLE_REASON_CANCELED -> {
                            android.util.Log.w("AudioRouteManager", "Cast reprodução cancelada (IDLE_REASON_CANCELED)")
                        }
                    }
                }
            }
            if (rmc.isPlaying) {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.PLAYING)
            } else if (rmc.isPaused) {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.PAUSED)
            } else if (rmc.isBuffering) {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.BUFFERING)
            }
        }
    }

    init {
        try {
            com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context).onCastVolumeChange = { vol ->
                setCastVolume(vol)
            }
        } catch (_: Exception) {}
        try {
            // Inicialização segura do CastContext em Main thread conforme exigido pelo SDK
            scope.launch(Dispatchers.Main) {
                try {
                    val castContext = CastContext.getSharedInstance(context)
                    castContext.sessionManager.addSessionManagerListener(
                        castSessionListener,
                        com.google.android.gms.cast.framework.CastSession::class.java
                    )
                } catch (e: Exception) {
                    android.util.Log.d("AudioRouteManager", "CastContext init info: ${e.message}")
                }
            }
        } catch (_: Exception) {}

        startDiscovery()
    }

    fun isCastingActive(): Boolean {
        return isCastingActive && castSession?.isConnected == true
    }

    fun getActiveCastDeviceName(): String? {
        return if (isCastingActive()) castSession?.castDevice?.friendlyName else null
    }

    fun getRemoteMediaClient(): com.google.android.gms.cast.framework.media.RemoteMediaClient? {
        return castSession?.remoteMediaClient
    }

    fun play() {
        try {
            castSession?.remoteMediaClient?.play()
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Erro ao executar play no Cast: ${e.message}")
        }
    }

    fun pause() {
        try {
            castSession?.remoteMediaClient?.pause()
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Erro ao executar pause no Cast: ${e.message}")
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            castSession?.remoteMediaClient?.seek(positionMs.coerceAtLeast(0L))
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Erro ao executar seekTo no Cast: ${e.message}")
        }
    }

    fun togglePlayPause() {
        val rmc = castSession?.remoteMediaClient ?: return
        if (rmc.isPlaying) {
            rmc.pause()
        } else {
            rmc.play()
        }
    }

    private var lastSeekTime = 0L

    fun setAudioPositionDirect(positionMs: Long) {
        if (!isCastingActive()) {
            android.util.Log.w("AUDIO_DEBUG", "Ignorando setAudioPositionDirect - Cast não conectado")
            return
        }
        val playerManager = RadioPlayerManager.getInstance(context)
        if (playerManager.activeMediaType.value == ActiveMediaType.LIVE_RADIO) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSeekTime < 500L) {
            android.util.Log.w("AUDIO_DEBUG", "Seek ignorado por debounce")
            return
        }
        lastSeekTime = now
        val currentPos = playerManager.getCurrentPosition()
        if (kotlin.math.abs(positionMs - currentPos) < 2000L) {
            return
        }
        playerManager.seekToPosition(positionMs)
    }

    fun setCastVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        try {
            val session = castSession ?: try {
                com.google.android.gms.cast.framework.CastContext.getSharedInstance(context).sessionManager.currentCastSession
            } catch (_: Exception) { null }

            session?.let {
                it.setVolume(clamped.toDouble())
                android.util.Log.d("CAST_VOL", "CastSession.setVolume($clamped) enviado com sucesso")
            }
        } catch (e: Exception) {
            android.util.Log.e("CAST_VOL", "Falha ao definir volume no Cast", e)
        }
    }

    fun setVolume(volume: Float) {
        setCastVolume(volume)
    }

    fun getCastVolume(): Float {
        return try {
            castSession?.volume?.toFloat() ?: 0.5f
        } catch (_: Exception) {
            0.5f
        }
    }

    fun adjustVolumeDelta(delta: Float) {
        val current = getCastVolume()
        val target = (current + delta).coerceIn(0f, 1f)
        setVolume(target)
        val playerManager = RadioPlayerManager.getInstance(context)
        playerManager.setVolumeLevel(target)
    }

    fun playNext() {
        val coordinator = (context.applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
        if (coordinator != null && coordinator.state.value.queue.isNotEmpty()) {
            coordinator.skipToNext()
        } else {
            val playerManager = RadioPlayerManager.getInstance(context)
            playerManager.playNext()
        }
    }

    fun playPrevious() {
        val coordinator = (context.applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
        if (coordinator != null && coordinator.state.value.queue.isNotEmpty()) {
            coordinator.skipToPrevious()
        } else {
            val playerManager = RadioPlayerManager.getInstance(context)
            playerManager.playPrevious()
        }
    }

    fun updateCastMedia() {
        val session = castSession ?: return
        if (session.isConnected) {
            transferPlaybackToCast(session)
        }
    }

    /**
     * Atualiza apenas os metadados exibidos no Google Cast (Chromecast/Google Home)
     * SEM reiniciar o stream de áudio. Chamada quando ICY/RDS detecta nova faixa.
     *
     * Usa MediaQueue.setQueueItemMetadata quando disponível, ou força um
     * load leve apenas se não houver forma de atualizar in-place.
     */
    fun updateCastMetadataOnly() {
        val session = castSession ?: return
        if (!session.isConnected) return
        val remoteMediaClient = session.remoteMediaClient ?: return
        val playerManager = RadioPlayerManager.getInstance(context)
        val station = playerManager.currentStation.value ?: return
        val nowPlaying = playerManager.nowPlaying.value
        val streamTitle = if (nowPlaying.hasTrackInfo && !nowPlaying.artist.equals("[sem informações]", ignoreCase = true)) nowPlaying.artist else "[sem informações]"

        try {
            // Construir metadata atualizada do Cast
            val castMeta = com.google.android.gms.cast.MediaMetadata(
                com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
            ).apply {
                putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, station.name)
                putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, streamTitle)
                putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, streamTitle)
                putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Rádio")
                val iconUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).getAppIconUrl()
                if (iconUrl != null) {
                    try {
                        addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(iconUrl), 512, 512))
                    } catch (_: Exception) {}
                }
            }

            // Verificar se há um item na fila do Cast e atualizar seus metadados
            val mediaStatus = remoteMediaClient.mediaStatus
            val currentItem = mediaStatus?.getQueueItemById(mediaStatus.currentItemId)

            if (currentItem != null) {
                // Atualiza metadata do item na fila sem reload do stream
                val updatedMediaInfo = com.google.android.gms.cast.MediaInfo.Builder(
                    currentItem.media?.contentId ?: station.streamUrl
                )
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                    .setContentType(currentItem.media?.contentType ?: "audio/mpeg")
                    .setMetadata(castMeta)
                    .build()

                val updatedItem = com.google.android.gms.cast.MediaQueueItem.Builder(updatedMediaInfo)
                    .setItemId(currentItem.itemId)
                    .build()

                remoteMediaClient.queueUpdateItems(
                    arrayOf(updatedItem),
                    null // sem callback customizado
                )

                android.util.Log.d("AudioRouteManager", "Cast metadata atualizada in-place: $streamTitle")
            } else {
                // Fallback: se não conseguir atualizar in-place, faz load completo
                android.util.Log.d("AudioRouteManager", "Cast: sem item ativo na fila, fazendo load completo")
                transferPlaybackToCast(session)
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Falha ao atualizar Cast metadata in-place", e)
        }
    }

    private fun detectContentType(url: String): String {
        val u = url.lowercase(java.util.Locale.ROOT)
        return when {
            u.contains(".m3u8") || u.contains("/hls") || u.contains("m3u8") -> "application/vnd.apple.mpegurl"
            u.contains(".aac") || u.contains("aac") -> "audio/aac"
            u.contains(".ogg") || u.contains(".opus") -> "audio/ogg"
            u.contains(".m4a") || u.contains(".mp4") -> "audio/mp4"
            else -> "audio/mpeg"
        }
    }

    private fun transferPlaybackToCast(session: com.google.android.gms.cast.framework.CastSession) {
        val remoteMediaClient = session.remoteMediaClient ?: return
        val playerManager = RadioPlayerManager.getInstance(context)
        val videoPlayerManager = com.marcioamaro.mediapod.player.LocalVideoPlayerManager.getInstance(context)
        val station = playerManager.currentStation.value
        val podcast = playerManager.currentPodcastEpisode.value
        val localAudio = playerManager.currentLocalAudio.value
        val localVideo = videoPlayerManager.currentVideo.value
        val activeType = playerManager.activeMediaType.value

        val currentMediaId: String = when (activeType) {
            ActiveMediaType.LOCAL_VIDEO -> "video_${localVideo?.id}"
            ActiveMediaType.LOCAL_AUDIO -> "audio_${localAudio?.id}"
            ActiveMediaType.PODCAST_EPISODE -> "podcast_${podcast?.id}"
            else -> "station_${station?.id}"
        }

        val now = System.currentTimeMillis()
        if (now - lastTransferTime < 600L && currentMediaId == lastTransferMediaId) {
            android.util.Log.d("AudioRouteManager", "transferPlaybackToCast: ignorando chamada duplicada por debounce (< 600ms)")
            return
        }
        lastTransferTime = now
        lastTransferMediaId = currentMediaId

        try {
            if (activeType == ActiveMediaType.LOCAL_VIDEO && localVideo != null) {
                val proxyUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context)
                    .getLocalMediaProxyUrl(localVideo.contentUri, "video/mp4")
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, localVideo.title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, "MediaPod • Vídeo")
                }
                val iconUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).getAppIconUrl()
                if (iconUrl != null) {
                    try {
                        castMeta.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(iconUrl), 512, 512))
                    } catch (_: Exception) {}
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(proxyUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("video/mp4")
                    .setStreamDuration(localVideo.durationMs)
                    .setMetadata(castMeta)
                    .build()

                val request = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(videoPlayerManager.currentPositionMs.value)
                    .build()

                val pendingResult = remoteMediaClient.load(request)
                pendingResult.setResultCallback { result ->
                    val status = result.status
                    android.util.Log.d(
                        "AudioRouteManager",
                        "Cast video load ResultCallback: isSuccess=${status.isSuccess}, " +
                            "statusCode=${status.statusCode}, statusMessage=${status.statusMessage}"
                    )
                }
                videoPlayerManager.pause()
            } else if (activeType == ActiveMediaType.LOCAL_AUDIO && localAudio != null) {
                val proxyUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context)
                    .getLocalMediaProxyUrl(localAudio.contentUri, "audio/mpeg")
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, localAudio.title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, localAudio.artist)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, localAudio.artist)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, localAudio.album)
                }
                if (localAudio.albumArtUrl != null) {
                    try {
                        castMeta.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(localAudio.albumArtUrl)))
                    } catch (_: Exception) {}
                }
                val iconUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).getAppIconUrl()
                if (iconUrl != null) {
                    try {
                        castMeta.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(iconUrl), 512, 512))
                    } catch (_: Exception) {}
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(proxyUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("audio/mpeg")
                    .setStreamDuration(localAudio.durationMs)
                    .setMetadata(castMeta)
                    .build()

                val request = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(playerManager.audioPositionMs.value)
                    .build()

                val pendingResult = remoteMediaClient.load(request)
                pendingResult.setResultCallback { result ->
                    val status = result.status
                    android.util.Log.d(
                        "AudioRouteManager",
                        "Cast local audio load ResultCallback: isSuccess=${status.isSuccess}, " +
                            "statusCode=${status.statusCode}, statusMessage=${status.statusMessage}"
                    )
                }
                playerManager.pauseLocalOnly()
            } else if (station != null) {
                val streamUrl = station.streamUrl
                val nowPlaying = playerManager.nowPlaying.value
                val streamTitle = if (nowPlaying.hasTrackInfo && !nowPlaying.artist.equals("[sem informações]", ignoreCase = true)) nowPlaying.artist else "[sem informações]"
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, station.name)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, streamTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, streamTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Rádio")
                }
                if (station.favicon.isNotBlank()) {
                    try {
                        castMeta.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(station.favicon)))
                    } catch (_: Exception) {}
                }
                val iconUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).getAppIconUrl()
                if (iconUrl != null) {
                    try {
                        castMeta.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(iconUrl), 512, 512))
                    } catch (_: Exception) {}
                }

                val isHls = streamUrl.contains(".m3u8", ignoreCase = true) || streamUrl.contains("/hls", ignoreCase = true)
                val finalContentType = when {
                    isHls -> "application/vnd.apple.mpegurl"
                    streamUrl.contains(".aac", ignoreCase = true) -> "audio/aac"
                    streamUrl.contains(".ogg", ignoreCase = true) || streamUrl.contains(".opus", ignoreCase = true) -> "audio/ogg"
                    else -> "audio/mpeg"
                }
                val finalUrl = if (isHls) {
                    streamUrl
                } else {
                    com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).getProxyStreamUrl(streamUrl)
                }
                android.util.Log.d("AudioRouteManager", "transferPlaybackToCast: rádio='${station.name}', urlFinal='$finalUrl', isHls=$isHls, contentType='$finalContentType'")

                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(finalUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                    .setContentType(finalContentType)
                    .setMetadata(castMeta)
                    .build()

                val request = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()

                val pendingResult = remoteMediaClient.load(request)
                pendingResult.setResultCallback { result ->
                    val status = result.status
                    android.util.Log.d(
                        "AudioRouteManager",
                        "Cast remoteMediaClient.load ResultCallback: isSuccess=${status.isSuccess}, " +
                            "statusCode=${status.statusCode}, statusMessage=${status.statusMessage}"
                    )
                    if (!status.isSuccess) {
                        android.util.Log.e(
                            "AudioRouteManager",
                            "FALHA ao carregar rádio no Cast: code=${status.statusCode}, msg=${status.statusMessage}"
                        )
                    }
                }
                playerManager.pauseLocalOnly()
            } else if (podcast != null) {
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, podcast.title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, podcast.showTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, podcast.publishDate.ifBlank { "Podcast" })
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Podcast")
                }
                val iconUrl = com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).getAppIconUrl()
                if (iconUrl != null) {
                    try {
                        castMeta.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(iconUrl), 512, 512))
                    } catch (_: Exception) {}
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(podcast.audioUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(detectContentType(podcast.audioUrl))
                    .setMetadata(castMeta)
                    .build()

                val request = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(playerManager.audioPositionMs.value)
                    .build()

                val pendingResult = remoteMediaClient.load(request)
                pendingResult.setResultCallback { result ->
                    val status = result.status
                    android.util.Log.d(
                        "AudioRouteManager",
                        "Cast podcast load ResultCallback: isSuccess=${status.isSuccess}, " +
                            "statusCode=${status.statusCode}, statusMessage=${status.statusMessage}"
                    )
                }
                playerManager.pauseLocalOnly()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRouteManager", "Error transferring media to Cast session", e)
        }
    }

    private var lastTransferTime = 0L
    private var lastTransferMediaId: String? = null

    fun transferPlaybackToLocal() {
        try {
            val playerManager = RadioPlayerManager.getInstance(context)
            val videoPlayerManager = com.marcioamaro.mediapod.player.LocalVideoPlayerManager.getInstance(context)
            val activeType = playerManager.activeMediaType.value

            if (activeType == ActiveMediaType.LOCAL_VIDEO) {
                if (lastRemoteStreamPositionMs > 0L) {
                    videoPlayerManager.seekTo(lastRemoteStreamPositionMs)
                }
                if (lastRemoteIsPlaying) {
                    videoPlayerManager.resume()
                } else {
                    videoPlayerManager.pause()
                }
                return
            }

            if (activeType != ActiveMediaType.LIVE_RADIO && lastRemoteStreamPositionMs > 0L) {
                playerManager.seekToPosition(lastRemoteStreamPositionMs)
            }
            if (lastRemoteIsPlaying) {
                playerManager.resume()
            } else {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.PAUSED)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRouteManager", "Error restoring local playback from Cast", e)
        }
    }

    fun startDiscovery() {
        try {
            mediaRouter?.addCallback(
                routeSelector,
                routerCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            updateRoutes()
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Failed to start MediaRouter discovery", e)
        }
    }

    fun stopDiscovery() {
        try {
            mediaRouter?.removeCallback(routerCallback)
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Failed to stop MediaRouter discovery", e)
        }
    }

    fun updateRoutes() {
        scope.launch(Dispatchers.Main) {
            try {
                val routes = mediaRouter?.routes ?: emptyList()
                val deviceList = mutableListOf<AudioRouteDevice>()

                // Determina o nome do dispositivo Cast ativo (se houver)
                val activeCastDeviceName = if (isCastingActive) {
                    castSession?.castDevice?.friendlyName
                } else null

                for (route in routes) {
                    // Ignora rotas não utilizáveis
                    if (!route.matchesSelector(routeSelector) && !route.isDefault && route.playbackType != MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL) {
                        continue
                    }

                    val isDefault = route.isDefault

                    val type = when {
                        isDefault -> AudioDeviceType.THIS_DEVICE
                        route.deviceType == 3 ||
                                route.name.contains("Bluetooth", ignoreCase = true) -> AudioDeviceType.BLUETOOTH
                        route.deviceType == 1 ||
                                route.deviceType == 2 ||
                                route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE -> AudioDeviceType.CAST_REMOTE
                        else -> if (route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL) AudioDeviceType.THIS_DEVICE else AudioDeviceType.OTHER
                    }

                    // Determina isSelected com base no estado REAL de reprodução:
                    // - Se Cast ativo: apenas o dispositivo Cast correspondente é "selected"
                    // - Caso contrário: usa o estado reportado pelo MediaRouter
                    val isSelected = if (isCastingActive) {
                        when {
                            type == AudioDeviceType.CAST_REMOTE &&
                                    activeCastDeviceName != null &&
                                    route.name.equals(activeCastDeviceName, ignoreCase = true) -> true
                            type == AudioDeviceType.CAST_REMOTE && route.isSelected -> true
                            isDefault -> false // Dispositivo local NÃO está ativo durante Cast
                            else -> false
                        }
                    } else {
                        route.isSelected
                    }

                    val displayName = when {
                        isDefault || type == AudioDeviceType.THIS_DEVICE -> {
                            "Este Dispositivo (Alto-falante)"
                        }
                        else -> route.name
                    }

                    val desc = when {
                        // Se este Cast está ativo, mostra info de streaming
                        isCastingActive && isSelected && type == AudioDeviceType.CAST_REMOTE -> {
                            val mediaInfo = castSession?.remoteMediaClient?.mediaInfo
                            val title = mediaInfo?.metadata?.getString(
                                com.google.android.gms.cast.MediaMetadata.KEY_TITLE
                            )
                            if (!title.isNullOrBlank()) "Casting: $title" else route.description ?: "Rede Local / Google Cast"
                        }
                        else -> route.description ?: when (type) {
                            AudioDeviceType.THIS_DEVICE -> "Alto-falante embutido"
                            AudioDeviceType.BLUETOOTH -> "Dispositivo Bluetooth"
                            AudioDeviceType.CAST_REMOTE -> "Rede Local / Google Cast"
                            AudioDeviceType.OTHER -> "Dispositivo de áudio"
                        }
                    }

                    val dev = AudioRouteDevice(
                        id = route.id,
                        name = displayName,
                        description = desc,
                        deviceType = type,
                        isSelected = isSelected,
                        isDefault = isDefault,
                        routeInfo = route
                    )

                    // Evita duplicatas do dispositivo padrão
                    if (isDefault) {
                        deviceList.add(0, dev)
                    } else if (deviceList.none { it.id == dev.id }) {
                        deviceList.add(dev)
                    }

                    if (isSelected) {
                        _selectedDevice.value = dev
                    }
                }

                if (deviceList.none { it.isDefault }) {
                    // Fallback garantido para o alto-falante local caso MediaRouter ainda não tenha carregado
                    val defaultDev = AudioRouteDevice(
                        id = "default_speaker",
                        name = "Este Dispositivo (Alto-falante)",
                        description = "Alto-falante embutido",
                        deviceType = AudioDeviceType.THIS_DEVICE,
                        isSelected = !isCastingActive && _selectedDevice.value == null,
                        isDefault = true,
                        routeInfo = mediaRouter?.defaultRoute
                    )
                    deviceList.add(0, defaultDev)
                }

                _availableDevices.value = deviceList

                if (_selectedDevice.value == null) {
                    _selectedDevice.value = deviceList.firstOrNull { it.isSelected } ?: deviceList.firstOrNull()
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioRouteManager", "Error updating media routes", e)
            }
        }
    }

    fun selectDevice(device: AudioRouteDevice) {
        val route = device.routeInfo ?: mediaRouter?.routes?.firstOrNull { it.id == device.id }
        if (route != null) {
            mediaRouter?.selectRoute(route)
            _selectedDevice.value = device.copy(isSelected = true)
            if (device.isDefault || device.deviceType == AudioDeviceType.THIS_DEVICE) {
                if (isCastingActive) {
                    try {
                        val castCtx = CastContext.getSharedInstance(context)
                        castCtx.sessionManager.endCurrentSession(true)
                    } catch (_: Exception) {}
                }
            }
            updateRoutes()
        }
    }

    fun showNativeChooserDialog(context: Context) {
        try {
            val dialog = androidx.mediarouter.app.MediaRouteChooserDialog(context)
            dialog.routeSelector = routeSelector
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Could not show native MediaRouteChooserDialog", e)
        }
    }

    /**
     * Limpa listeners, callbacks e encerra servidores locais ao encerrar o serviço ou aplicativo.
     */
    fun cleanup() {
        stopDiscovery()
        com.marcioamaro.mediapod.cast.CastStreamProxy.getInstance(context).stopServer()
        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.removeSessionManagerListener(
                castSessionListener,
                com.google.android.gms.cast.framework.CastSession::class.java
            )
        } catch (_: Exception) {}
        castSession?.remoteMediaClient?.unregisterCallback(remoteClientCallback)
        castSession = null
        isCastingActive = false
        _castSessionState.value = CastSessionState.DISCONNECTED
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioRouteManager? = null

        fun getInstance(context: Context): AudioRouteManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AudioRouteManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
