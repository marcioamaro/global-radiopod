package com.example.player

import android.content.Context
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
import kotlinx.coroutines.launch

enum class AudioDeviceType {
    THIS_DEVICE,
    BLUETOOTH,
    CAST_REMOTE,
    OTHER
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
    private val mediaRouter: MediaRouter = MediaRouter.getInstance(context)

    private val _availableDevices = MutableStateFlow<List<AudioRouteDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioRouteDevice>> = _availableDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<AudioRouteDevice?>(null)
    val selectedDevice: StateFlow<AudioRouteDevice?> = _selectedDevice.asStateFlow()

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

    private val castSessionListener = object : com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> {
        override fun onSessionStarting(session: com.google.android.gms.cast.framework.CastSession) {}
        override fun onSessionStarted(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {
            castSession = session
            isCastingActive = true
            session.remoteMediaClient?.registerCallback(remoteClientCallback)
            transferPlaybackToCast(session)
            updateRoutes()
        }
        override fun onSessionStartFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            castSession = null
            isCastingActive = false
            updateRoutes()
        }
        override fun onSessionEnding(session: com.google.android.gms.cast.framework.CastSession) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
        }
        override fun onSessionEnded(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            castSession = null
            if (isCastingActive) {
                isCastingActive = false
                transferPlaybackToLocal()
            }
            updateRoutes()
        }
        override fun onSessionResuming(session: com.google.android.gms.cast.framework.CastSession, sessionId: String) {}
        override fun onSessionResumed(session: com.google.android.gms.cast.framework.CastSession, wasSuspended: Boolean) {
            castSession = session
            isCastingActive = true
            session.remoteMediaClient?.registerCallback(remoteClientCallback)
            updateRoutes()
        }
        override fun onSessionResumeFailed(session: com.google.android.gms.cast.framework.CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteClientCallback)
            castSession = null
            isCastingActive = false
            updateRoutes()
        }
        override fun onSessionSuspended(session: com.google.android.gms.cast.framework.CastSession, reason: Int) {}
    }

    private val remoteClientCallback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val rmc = castSession?.remoteMediaClient ?: return
            val playerManager = RadioPlayerManager.getInstance(context)
            if (rmc.isPlaying) {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.PLAYING)
            } else if (rmc.isPaused) {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.PAUSED)
            } else if (rmc.isBuffering) {
                playerManager.setPlaybackStatusDirect(RadioPlaybackStatus.BUFFERING)
            }
            try {
                val castVol = castSession?.volume?.toFloat()
                if (castVol != null && castVol in 0f..1f) {
                    playerManager.setVolumeLevel(castVol)
                }
            } catch (_: Exception) {}
        }
    }

    init {
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

    fun play() {
        try {
            castSession?.remoteMediaClient?.play()
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            castSession?.remoteMediaClient?.pause()
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        val rmc = castSession?.remoteMediaClient ?: return
        if (rmc.isPlaying) {
            rmc.pause()
        } else {
            rmc.play()
        }
    }

    fun setVolume(volume: Float) {
        try {
            castSession?.setVolume(volume.toDouble().coerceIn(0.0, 1.0))
        } catch (_: Exception) {}
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
        val playerManager = RadioPlayerManager.getInstance(context)
        playerManager.playNext()
    }

    fun playPrevious() {
        val playerManager = RadioPlayerManager.getInstance(context)
        playerManager.playPrevious()
    }

    fun updateCastMedia() {
        val session = castSession ?: return
        if (session.isConnected) {
            transferPlaybackToCast(session)
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
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, station.name)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, station.country.ifBlank { "Brasil" })
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, "${station.city} • ${station.primaryGenre}".trim())
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Rádio")
                    if (station.favicon.isNotBlank()) {
                        try {
                            addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(station.favicon)))
                        } catch (_: Exception) {}
                    }
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(streamUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                    .setContentType("audio/mpeg")
                    .setMetadata(castMeta)
                    .build()

                remoteMediaClient.load(mediaInfo, true)
                playerManager.pauseLocalOnly()
            } else if (podcast != null) {
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, podcast.title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, podcast.showTitle)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, podcast.publishDate.ifBlank { "Podcast" })
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Podcast")
                    if (podcast.artworkUrl.isNotBlank()) {
                        try {
                            addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(podcast.artworkUrl)))
                        } catch (_: Exception) {}
                    }
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(podcast.audioUrl)
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("audio/mpeg")
                    .setMetadata(castMeta)
                    .build()

                remoteMediaClient.load(mediaInfo, true, playerManager.audioPositionMs.value)
                playerManager.pauseLocalOnly()
            } else if (localAudio != null) {
                val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, localAudio.title)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, localAudio.artist)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, localAudio.album)
                    putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, "MediaPod • Músicas")
                    if (!localAudio.albumArtUrl.isNullOrBlank()) {
                        try {
                            addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(localAudio.albumArtUrl)))
                        } catch (_: Exception) {}
                    }
                }
                val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(localAudio.contentUri.toString())
                    .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("audio/mpeg")
                    .setMetadata(castMeta)
                    .build()

                remoteMediaClient.load(mediaInfo, true, playerManager.audioPositionMs.value)
                playerManager.pauseLocalOnly()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRouteManager", "Error transferring media to Cast session", e)
        }
    }

    private fun transferPlaybackToLocal() {
        try {
            val playerManager = RadioPlayerManager.getInstance(context)
            playerManager.resume()
        } catch (e: Exception) {
            android.util.Log.e("AudioRouteManager", "Error restoring local playback from Cast", e)
        }
    }

    fun startDiscovery() {
        try {
            mediaRouter.addCallback(
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
            mediaRouter.removeCallback(routerCallback)
        } catch (e: Exception) {
            android.util.Log.w("AudioRouteManager", "Failed to stop MediaRouter discovery", e)
        }
    }

    fun updateRoutes() {
        scope.launch(Dispatchers.Main) {
            try {
                val routes = mediaRouter.routes
                val deviceList = mutableListOf<AudioRouteDevice>()

                for (route in routes) {
                    // Ignora rotas não utilizáveis
                    if (!route.matchesSelector(routeSelector) && !route.isDefault && route.playbackType != MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL) {
                        continue
                    }

                    val isDefault = route.isDefault
                    val isSelected = route.isSelected

                    val type = when {
                        isDefault -> AudioDeviceType.THIS_DEVICE
                        route.deviceType == 3 ||
                                route.name.contains("Bluetooth", ignoreCase = true) -> AudioDeviceType.BLUETOOTH
                        route.deviceType == 1 ||
                                route.deviceType == 2 ||
                                route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE -> AudioDeviceType.CAST_REMOTE
                        else -> if (route.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL) AudioDeviceType.THIS_DEVICE else AudioDeviceType.OTHER
                    }

                    val displayName = when {
                        isDefault || type == AudioDeviceType.THIS_DEVICE -> {
                            "Este Dispositivo (Alto-falante)"
                        }
                        else -> route.name
                    }

                    val desc = route.description ?: when (type) {
                        AudioDeviceType.THIS_DEVICE -> "Alto-falante embutido"
                        AudioDeviceType.BLUETOOTH -> "Dispositivo Bluetooth"
                        AudioDeviceType.CAST_REMOTE -> "Rede Local / Google Cast"
                        AudioDeviceType.OTHER -> "Dispositivo de áudio"
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
                        isSelected = _selectedDevice.value == null,
                        isDefault = true,
                        routeInfo = mediaRouter.defaultRoute
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
        val route = device.routeInfo ?: mediaRouter.routes.firstOrNull { it.id == device.id }
        if (route != null) {
            mediaRouter.selectRoute(route)
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
