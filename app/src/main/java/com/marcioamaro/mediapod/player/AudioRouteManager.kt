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
            startLocalIconServer()
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
            stopLocalIconServer()
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
            stopLocalIconServer()
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
            startLocalIconServer()
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
            stopLocalIconServer()
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
            lastRemoteIsPlaying = rmc.isPlaying
            val pos = rmc.approximateStreamPosition
            if (pos > 0L && isCastingActive() && playerManager.activeMediaType.value != ActiveMediaType.LIVE_RADIO) {
                lastRemoteStreamPositionMs = pos
                playerManager.setAudioPositionDirect(pos)
            }
            try {
                val castVol = castSession?.volume?.toFloat() ?: 1.0f
                com.marcioamaro.mediapod.audio.VolumeManager.getInstance(context).updateCastVolume(castVol)
            } catch (_: Exception) {}
            val mediaStatus = rmc.mediaStatus
            if (mediaStatus != null) {
                if (mediaStatus.playerState == com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE &&
                    mediaStatus.idleReason == com.google.android.gms.cast.MediaStatus.IDLE_REASON_FINISHED) {
                    // Item finalizou no Chromecast -> Avançar automaticamente para o próximo da fila
                    scope.launch(Dispatchers.Main) {
                        playNext()
                    }
                    return
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
                applyAppIconToCast(this)
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
        val station = playerManager.currentStation.value
        val podcast = playerManager.currentPodcastEpisode.value
        val localAudio = playerManager.currentLocalAudio.value

        try {
            if (station != null) {
                val streamUrl = station.streamUrl
                val nowPlaying = playerManager.nowPlaying.value
                val streamTitle = if (nowPlaying.hasTrackInfo && !nowPlaying.artist.equals("[sem informações]", ignoreCase = true)) nowPlaying.artist else "[sem informações]"
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, station.name)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, streamTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, streamTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Rádio")
                    applyAppIconToCast(this)
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(streamUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                    .setContentType(detectContentType(streamUrl))
                    .setMetadata(castMeta)
                    .build()

                val request = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()

                remoteMediaClient.load(request)
                playerManager.pauseLocalOnly()
            } else if (podcast != null) {
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, podcast.title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, podcast.showTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, podcast.publishDate.ifBlank { "Podcast" })
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Podcast")
                    applyAppIconToCast(this)
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

                remoteMediaClient.load(request)
                playerManager.pauseLocalOnly()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRouteManager", "Error transferring media to Cast session", e)
        }
    }

    private var localIconServer: java.net.ServerSocket? = null
    private var localIconServerPort: Int = 8992
    private var localIconJob: kotlinx.coroutines.Job? = null
    private var appIconPngCached: ByteArray? = null

    private fun getAppIconPngBytes(): ByteArray {
        appIconPngCached?.let { return it }
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.playstore_icon)
                ?: androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val b = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(b)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    b
                }
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                appIconPngCached = bytes
                return bytes
            }
        } catch (_: Exception) {}
        return ByteArray(0)
    }

    private fun getLocalWifiIp(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun startLocalIconServer() {
        if (localIconServer != null && !localIconServer!!.isClosed) return
        localIconJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                localIconServer = java.net.ServerSocket(0)
                localIconServerPort = localIconServer!!.localPort
                while (isActive && !localIconServer!!.isClosed) {
                    val socket = localIconServer!!.accept()
                    launch {
                        try {
                            val inStream = socket.getInputStream()
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inStream))
                            val line = reader.readLine()
                            if (line != null && line.startsWith("GET /app_icon.png")) {
                                val bytes = getAppIconPngBytes()
                                val out = socket.getOutputStream()
                                val header = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
                                out.write(header.toByteArray())
                                out.write(bytes)
                                out.flush()
                            }
                            socket.close()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopLocalIconServer() {
        try {
            localIconJob?.cancel()
            localIconServer?.close()
        } catch (_: Exception) {}
        localIconServer = null
    }

    private fun applyAppIconToCast(castMeta: com.google.android.gms.cast.MediaMetadata) {
        try {
            startLocalIconServer()
            val wifiIp = getLocalWifiIp()
            if (wifiIp != null && localIconServer != null && !localIconServer!!.isClosed) {
                val httpIconUri = android.net.Uri.parse("http://$wifiIp:$localIconServerPort/app_icon.png")
                castMeta.addImage(com.google.android.gms.common.images.WebImage(httpIconUri, 512, 512))
            }
        } catch (_: Exception) {}
    }

    fun transferPlaybackToLocal() {
        try {
            val playerManager = RadioPlayerManager.getInstance(context)
            if (playerManager.activeMediaType.value != ActiveMediaType.LIVE_RADIO && lastRemoteStreamPositionMs > 0L) {
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
        stopLocalIconServer()
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
