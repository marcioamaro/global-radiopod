package com.marcioamaro.mediapod.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.marcioamaro.mediapod.MainActivity
import com.marcioamaro.mediapod.R
import com.marcioamaro.mediapod.data.db.RadioDatabase
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager
import com.marcioamaro.mediapod.data.repository.CuratedData
import com.marcioamaro.mediapod.data.repository.RadioRepository
import com.marcioamaro.mediapod.player.AudioRouteManager
import com.marcioamaro.mediapod.player.LocalArtworkGenerator
import com.marcioamaro.mediapod.player.RadioPlaybackStatus
import com.marcioamaro.mediapod.player.RadioPlayerManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class RadioMediaService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var forwardingPlayerInstance: RadioForwardingPlayer? = null
    @Volatile private var currentArtworkBitmap: android.graphics.Bitmap? = null
    private lateinit var playerManager: RadioPlayerManager
    private lateinit var repository: RadioRepository

    private val appLogoUri: Uri by lazy {
        Uri.parse("android.resource://${applicationContext.packageName}/${R.mipmap.ic_launcher}")
    }

    private val radioDefaultIconUri: Uri by lazy {
        Uri.parse("android.resource://${applicationContext.packageName}/${R.drawable.ic_radio_generic}")
    }

    private val podcastDefaultIconUri: Uri by lazy {
        Uri.parse("android.resource://${applicationContext.packageName}/${R.drawable.ic_podcast_generic}")
    }

    private val radioDefaultIconBytes: ByteArray by lazy {
        try {
            resources.openRawResource(R.drawable.ic_radio_generic).use { it.readBytes() }
        } catch (_: Exception) {
            try {
                val drawable = androidx.core.content.ContextCompat.getDrawable(applicationContext, R.drawable.ic_radio_generic)
                if (drawable != null) {
                    val size = 512
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    bitmap.recycle()
                    stream.toByteArray()
                } else ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }
    }

    private val podcastDefaultIconBytes: ByteArray by lazy {
        try {
            resources.openRawResource(R.drawable.ic_podcast_generic).use { it.readBytes() }
        } catch (_: Exception) {
            try {
                val drawable = androidx.core.content.ContextCompat.getDrawable(applicationContext, R.drawable.ic_podcast_generic)
                if (drawable != null) {
                    val size = 512
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    bitmap.recycle()
                    stream.toByteArray()
                } else ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "radio_playback_channel"
        const val NOTIFICATION_ID = 1001

        // Ações de mídia para controle em segundo plano e tela de bloqueio
        const val ACTION_PLAY = "com.marcioamaro.mediapod.ACTION_PLAY"
        const val ACTION_PAUSE = "com.marcioamaro.mediapod.ACTION_PAUSE"
        const val ACTION_STOP = "com.marcioamaro.mediapod.ACTION_STOP"
        const val ACTION_NEXT = "com.marcioamaro.mediapod.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.marcioamaro.mediapod.ACTION_PREVIOUS"

        // Custom Commands for Android Auto
        const val ACTION_TOGGLE_MUTE = "com.marcioamaro.mediapod.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_FAVORITE = "ACTION_TOGGLE_FAVORITE"

        // Android Auto Media Tree Navigation Roots (4 Abas Obrigatórias)
        const val ROOT_MEDIA_ID = "ROOT_MEDIA_ID"
        const val ROOT_ID = "ROOT_MEDIA_ID" // Alias canônico para compatibilidade
        const val FAVORITE_RADIOS = "FAVORITE_RADIOS"
        const val RECENT_RADIOS = "RECENT_RADIOS"
        const val FAVORITE_PODCASTS = "FAVORITE_PODCASTS"
        const val RECENT_PODCASTS = "RECENT_PODCASTS"
    }

    private lateinit var podcastRepository: com.marcioamaro.mediapod.data.repository.PodcastRepository
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var serviceWifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            playerManager = RadioPlayerManager.getInstance(applicationContext)
            LocalArtworkGenerator.clearCache(applicationContext)
            val db = RadioDatabase.getDatabase(applicationContext)
            repository = RadioRepository(db.favoriteStationDao())
            podcastRepository = com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(applicationContext)

            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val forwardingPlayer = RadioForwardingPlayer(
                playerManager.getPlayer(),
                playerManager,
                repository,
                podcastRepository,
                serviceScope
            )
            forwardingPlayerInstance = forwardingPlayer

            val toggleFavCommand = SessionCommand("ACTION_TOGGLE_FAVORITE", Bundle.EMPTY)
            val favButton = androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Favoritar")
                .setIconResId(android.R.drawable.btn_star)
                .setSessionCommand(toggleFavCommand)
                .build()

            val toggleMuteCommand = SessionCommand(ACTION_TOGGLE_MUTE, Bundle.EMPTY)
            val muteButton = androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Mudo")
                .setIconResId(R.drawable.ic_volume_off)
                .setSessionCommand(toggleMuteCommand)
                .build()

            mediaLibrarySession = MediaLibrarySession.Builder(
                this,
                forwardingPlayer,
                AutoMediaLibraryCallback()
            )
                .setSessionActivity(sessionActivityPendingIntent)
                .setId("IpodRadioMediaSession")
                .setCustomLayout(ImmutableList.of(favButton, muteButton))
                .build()

            val initialNotification = buildMediaNotification()
            // Inicia Foreground Service de forma segura apenas se houver playback ativo (resiliente para Android 14)
            if (playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING) {
                startForegroundSafely(initialNotification)
            }

            // Sincronização em tempo real do estado de reprodução e do Android Auto
            observeAppState()

        } catch (e: Exception) {
            android.util.Log.e("RadioMediaService", "Error during service onCreate", e)
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Could not startForeground safely: ${e.message}")
        }
    }

    private fun getActiveSongOrLiveText(): String {
        val realSong = playerManager.getLastRealSongTitle()
        if (!realSong.isNullOrBlank() && !realSong.equals("[sem informações]", ignoreCase = true) && !realSong.contains("BUSCANDO", ignoreCase = true)) {
            return realSong
        }
        val nowPlaying = playerManager.nowPlaying.value
        if (nowPlaying.hasTrackInfo && nowPlaying.artist.isNotBlank() && !nowPlaying.artist.equals("[sem informações]", ignoreCase = true) && !nowPlaying.artist.contains("BUSCANDO", ignoreCase = true)) {
            return nowPlaying.artist
        }
        val rds = playerManager.rdsInfo.value
        if (rds.hasRealRds && rds.radioText.isNotBlank() && !rds.radioText.equals("[sem informações]", ignoreCase = true) && !rds.radioText.contains("BUSCANDO", ignoreCase = true)) {
            return rds.radioText
        }
        return "Ao Vivo"
    }

    private fun observeAppState() {
        // 1. Sincronização em tempo real das abas do Android Auto
        serviceScope.launch {
            repository.favoritesFlow.collect {
                try {
                    mediaLibrarySession?.notifyChildrenChanged(FAVORITE_RADIOS, 0, null)
                } catch (e: Exception) {
                    android.util.Log.w("RadioMediaService", "Falha ao notificar mudança em FAVORITE_RADIOS: ${e.message}")
                }
            }
        }

        serviceScope.launch {
            playerManager.currentStation.collect { station ->
                if (station != null) {
                    try {
                        mediaLibrarySession?.notifyChildrenChanged(RECENT_RADIOS, 0, null)
                    } catch (e: Exception) {
                        android.util.Log.w("RadioMediaService", "Falha ao notificar mudança em RECENT_RADIOS: ${e.message}")
                    }
                }
                updateNotification()
            }
        }

        serviceScope.launch {
            podcastRepository.favoritesFlow.collect {
                try {
                    mediaLibrarySession?.notifyChildrenChanged(FAVORITE_PODCASTS, 0, null)
                } catch (e: Exception) {
                    android.util.Log.w("RadioMediaService", "Falha ao notificar mudança em FAVORITE_PODCASTS: ${e.message}")
                }
            }
        }

        serviceScope.launch {
            podcastRepository.recentEpisodesFlow.collect {
                try {
                    mediaLibrarySession?.notifyChildrenChanged(RECENT_PODCASTS, 0, null)
                } catch (e: Exception) {
                    android.util.Log.w("RadioMediaService", "Falha ao notificar mudança em RECENT_PODCASTS: ${e.message}")
                }
            }
        }

        // 3. Monitoramento de WAKE_LOCK, WIFI_LOCK e atualização de status
        serviceScope.launch {
            playerManager.playbackStatus.collect { status ->
                when (status) {
                    RadioPlaybackStatus.PLAYING -> {
                        acquireServiceLocks()
                        startForegroundSafely(buildMediaNotification())
                    }
                    RadioPlaybackStatus.PAUSED,
                    RadioPlaybackStatus.IDLE,
                    RadioPlaybackStatus.ERROR,
                    RadioPlaybackStatus.NO_INTERNET -> {
                        releaseServiceLocks()
                    }
                    else -> {}
                }
                updateNotification()
            }
        }

        // 4. Sincronização dinâmica da SSOT (nowPlaying) com a MediaSession do Android Auto
        serviceScope.launch {
            playerManager.nowPlaying.collect { nowPlaying ->
                updateNotification()
                try {
                    val podcast = playerManager.currentPodcastEpisode.value
                    if (podcast != null) {
                        val hasPodcastArt = !podcast.artworkUrl.isNullOrBlank()
                        val artworkUri = if (hasPodcastArt) Uri.parse(podcast.artworkUrl) else podcastDefaultIconUri
                        val builder = MediaMetadata.Builder()
                            .setTitle(podcast.title)
                            .setDisplayTitle(podcast.title)
                            .setArtist(podcast.showTitle)
                            .setSubtitle(podcast.showTitle)
                            .setAlbumTitle("MediaPod • Podcast")
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(true)
                        if (!hasPodcastArt && podcastDefaultIconBytes.isNotEmpty()) {
                            builder.setArtworkData(podcastDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                        val metadata = builder.build()
                        playerManager.getPlayer().playlistMetadata = metadata
                        forwardingPlayerInstance?.notifyMetadataChanged(metadata)
                        return@collect
                    }
                    val station = playerManager.currentStation.value
                    val stationName = station?.name ?: nowPlaying.title
                    val songTitle = getActiveSongOrLiveText()
                    val hasFavicon = station?.hasValidFavicon == true
                    val artworkUri = if (hasFavicon) Uri.parse(station!!.effectiveFavicon) else radioDefaultIconUri

                    val builder = MediaMetadata.Builder()
                        .setTitle(stationName)
                        .setDisplayTitle(stationName)
                        .setArtist(songTitle)
                        .setSubtitle(songTitle)
                        .setAlbumTitle("MediaPod • Rádio")
                        .setArtworkUri(artworkUri)
                        .setIsPlayable(true)
                    if (!hasFavicon && radioDefaultIconBytes.isNotEmpty()) {
                        builder.setArtworkData(radioDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                    val metadata = builder.build()
                    playerManager.getPlayer().playlistMetadata = metadata
                    forwardingPlayerInstance?.notifyMetadataChanged(metadata)
                } catch (e: Exception) {
                    android.util.Log.w("RadioMediaService", "Failed to sync MediaSession playlistMetadata", e)
                }
            }
        }

        serviceScope.launch {
            playerManager.rdsInfo.collect {
                updateNotification()
                try {
                    val station = playerManager.currentStation.value
                    if (station != null) {
                        val stationName = station.name
                        val songTitle = getActiveSongOrLiveText()
                        val hasFavicon = station.hasValidFavicon
                        val artworkUri = if (hasFavicon) Uri.parse(station.effectiveFavicon) else radioDefaultIconUri

                        val builder = MediaMetadata.Builder()
                            .setTitle(stationName)
                            .setDisplayTitle(stationName)
                            .setArtist(songTitle)
                            .setSubtitle(songTitle)
                            .setAlbumTitle("MediaPod • Rádio")
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(true)
                        if (!hasFavicon && radioDefaultIconBytes.isNotEmpty()) {
                            builder.setArtworkData(radioDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                        val metadata = builder.build()
                        playerManager.getPlayer().playlistMetadata = metadata
                        forwardingPlayerInstance?.notifyMetadataChanged(metadata)
                    }
                } catch (_: Exception) {}
            }
        }

        // 5. Atualização para faixas MP3 locais
        serviceScope.launch {
            playerManager.currentLocalAudio.collect {
                updateNotification()
            }
        }

        // 6. Atualização para episódios de podcasts e cache de arte
        serviceScope.launch {
            playerManager.currentPodcastEpisode.collect { episode ->
                val artwork = episode?.artworkUrl?.trim().orEmpty()
                if (artwork.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val req = coil.request.ImageRequest.Builder(applicationContext)
                                .data(artwork)
                                .size(512, 512)
                                .allowHardware(false)
                                .build()
                            val result = coil.Coil.imageLoader(applicationContext).execute(req)
                            currentArtworkBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        } catch (_: Exception) {
                            currentArtworkBitmap = null
                        }
                    }
                } else {
                    currentArtworkBitmap = null
                }
                updateNotification()
                if (episode != null) {
                    try {
                        val hasPodcastArt = episode.artworkUrl.isNotBlank()
                        val artworkUri = if (hasPodcastArt) Uri.parse(episode.artworkUrl) else podcastDefaultIconUri
                        val builder = MediaMetadata.Builder()
                            .setTitle(episode.title)
                            .setDisplayTitle(episode.title)
                            .setArtist(episode.showTitle)
                            .setSubtitle(episode.showTitle)
                            .setAlbumTitle("MediaPod • Podcast")
                            .setArtworkUri(artworkUri)
                            .setIsPlayable(true)
                        if (!hasPodcastArt && podcastDefaultIconBytes.isNotEmpty()) {
                            builder.setArtworkData(podcastDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                        val metadata = builder.build()
                        playerManager.getPlayer().playlistMetadata = metadata
                        forwardingPlayerInstance?.notifyMetadataChanged(metadata)
                    } catch (_: Exception) {}
                }
            }
        }

        // 7. Atualização do logotipo remoto colorido de rádio
        serviceScope.launch {
            playerManager.currentStation.collect { station ->
                val favicon = station?.effectiveFavicon.orEmpty()
                if (favicon.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val req = coil.request.ImageRequest.Builder(applicationContext)
                                .data(favicon)
                                .size(512, 512)
                                .allowHardware(false)
                                .build()
                            val result = coil.Coil.imageLoader(applicationContext).execute(req)
                            currentArtworkBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        } catch (_: Exception) {
                            currentArtworkBitmap = null
                        }
                    }
                } else {
                    currentArtworkBitmap = null
                }
                updateNotification()
            }
        }

    }

    private inner class RadioForwardingPlayer(
        player: Player,
        private val playerManager: RadioPlayerManager,
        private val repository: RadioRepository,
        private val podcastRepository: com.marcioamaro.mediapod.data.repository.PodcastRepository,
        private val serviceScope: CoroutineScope
    ) : ForwardingPlayer(player) {

        private val playerListeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            super.addListener(listener)
            playerListeners.add(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            super.removeListener(listener)
            playerListeners.remove(listener)
        }

        fun notifyMetadataChanged(metadata: MediaMetadata) {
            for (listener in playerListeners) {
                try {
                    listener.onMediaMetadataChanged(metadata)
                    listener.onPlaylistMetadataChanged(metadata)
                } catch (_: Exception) {}
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            return Player.Commands.Builder()
                .addAll(super.getAvailableCommands())
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SET_DEVICE_VOLUME)
                .add(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)
                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME)
                .add(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                .add(Player.COMMAND_GET_DEVICE_VOLUME)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_GET_DEVICE_VOLUME -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true

        override fun getMediaMetadata(): MediaMetadata {
            val podcast = playerManager.currentPodcastEpisode.value
            if (podcast != null) {
                val hasPodcastArt = !podcast.artworkUrl.isNullOrBlank()
                val artworkUri = if (hasPodcastArt) Uri.parse(podcast.artworkUrl) else podcastDefaultIconUri
                val builder = MediaMetadata.Builder()
                    .setTitle(podcast.title)
                    .setDisplayTitle(podcast.title)
                    .setArtist(podcast.showTitle)
                    .setSubtitle(podcast.showTitle)
                    .setAlbumTitle("MediaPod • Podcast")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                if (!hasPodcastArt && podcastDefaultIconBytes.isNotEmpty()) {
                    builder.setArtworkData(podcastDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                return builder.build()
            }
            val station = playerManager.currentStation.value
            if (station != null) {
                val songTitle = getActiveSongOrLiveText()
                val hasFavicon = station.hasValidFavicon
                val artworkUri = if (hasFavicon) Uri.parse(station.effectiveFavicon) else radioDefaultIconUri
                val builder = MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setDisplayTitle(station.name)
                    .setArtist(songTitle)
                    .setSubtitle(songTitle)
                    .setAlbumTitle("MediaPod • Rádio")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                if (!hasFavicon && radioDefaultIconBytes.isNotEmpty()) {
                    builder.setArtworkData(radioDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                return builder.build()
            }
            return super.getMediaMetadata()
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val podcast = playerManager.currentPodcastEpisode.value
            if (podcast != null) {
                val hasPodcastArt = !podcast.artworkUrl.isNullOrBlank()
                val artworkUri = if (hasPodcastArt) Uri.parse(podcast.artworkUrl) else podcastDefaultIconUri
                val builder = MediaMetadata.Builder()
                    .setTitle(podcast.title)
                    .setDisplayTitle(podcast.title)
                    .setArtist(podcast.showTitle)
                    .setSubtitle(podcast.showTitle)
                    .setAlbumTitle("MediaPod • Podcast")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                if (!hasPodcastArt && podcastDefaultIconBytes.isNotEmpty()) {
                    builder.setArtworkData(podcastDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                val metadata = builder.build()
                return MediaItem.Builder()
                    .setMediaId("podcast_${podcast.id}")
                    .setUri(podcast.audioUrl)
                    .setMediaMetadata(metadata)
                    .build()
            }
            val station = playerManager.currentStation.value
            if (station != null) {
                val songTitle = getActiveSongOrLiveText()
                val hasFavicon = station.hasValidFavicon
                val artworkUri = if (hasFavicon) Uri.parse(station.effectiveFavicon) else radioDefaultIconUri
                val builder = MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setDisplayTitle(station.name)
                    .setArtist(songTitle)
                    .setSubtitle(songTitle)
                    .setAlbumTitle("MediaPod • Rádio")
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                if (!hasFavicon && radioDefaultIconBytes.isNotEmpty()) {
                    builder.setArtworkData(radioDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                val metadata = builder.build()
                return MediaItem.Builder()
                    .setMediaId("radio_${station.id}")
                    .setUri(station.streamUrl)
                    .setMediaMetadata(metadata)
                    .build()
            }
            return super.getCurrentMediaItem()
        }

        override fun play() {
            playerManager.resume()
        }

        override fun pause() {
            playerManager.pause()
        }

        override fun stop() {
            playerManager.stop()
        }

        private var lastSkipTimeMs = 0L

        override fun seekToNext() {
            val now = System.currentTimeMillis()
            if (now - lastSkipTimeMs > 400L) {
                lastSkipTimeMs = now
                if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                    android.util.Log.d("MEDIA_BTN", "onSkipToNext/seekToNext recebido do sistema — roteando via NavigationContext")
                }
                val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                if (coordinator != null) {
                    coordinator.skipToNext()
                } else {
                    playerManager.playNext()
                }
            }
        }

        override fun seekToNextMediaItem() {
            seekToNext()
        }

        override fun seekToPrevious() {
            val now = System.currentTimeMillis()
            if (now - lastSkipTimeMs > 400L) {
                lastSkipTimeMs = now
                if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                    android.util.Log.d("MEDIA_BTN", "onSkipToPrevious/seekToPrevious recebido do sistema")
                }
                val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                if (coordinator != null) {
                    coordinator.skipToPrevious()
                } else {
                    playerManager.playPrevious()
                }
            }
        }

        override fun seekToPreviousMediaItem() {
            seekToPrevious()
        }

        override fun seekBack() {
            playerManager.seekRelative(-15000L)
        }

        override fun seekForward() {
            playerManager.seekRelative(30000L)
        }

        override fun getDeviceInfo(): androidx.media3.common.DeviceInfo {
            val isCast = AudioRouteManager.getInstance(applicationContext).isCastingActive()
            val playbackType = if (isCast) {
                androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_REMOTE
            } else {
                androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_LOCAL
            }
            return androidx.media3.common.DeviceInfo.Builder(playbackType)
                .setMinVolume(0)
                .setMaxVolume(100)
                .build()
        }

        override fun getDeviceVolume(): Int {
            val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
            return ((coordinator?.activeVolume?.value ?: 0.8f) * 100).toInt()
        }

        override fun setDeviceVolume(volume: Int) {
            val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
            coordinator?.setActiveVolume(volume.toFloat() / 100f)
        }

        override fun setDeviceVolume(volume: Int, flags: Int) {
            val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
            coordinator?.setActiveVolume(volume.toFloat() / 100f)
        }

        override fun increaseDeviceVolume(flags: Int) {
            val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
            val current = coordinator?.activeVolume?.value ?: 0.5f
            coordinator?.setActiveVolume((current + 0.05f).coerceIn(0f, 1f))
        }

        override fun decreaseDeviceVolume(flags: Int) {
            val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
            val current = coordinator?.activeVolume?.value ?: 0.5f
            coordinator?.setActiveVolume((current - 0.05f).coerceIn(0f, 1f))
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            super.setMediaItem(mediaItem)
            handleItemPlayback(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            super.setMediaItem(mediaItem, startPositionMs)
            handleItemPlayback(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            super.setMediaItem(mediaItem, resetPosition)
            handleItemPlayback(mediaItem)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
            super.setMediaItems(mediaItems)
            mediaItems.firstOrNull()?.let { handleItemPlayback(it) }
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            super.setMediaItems(mediaItems, resetPosition)
            mediaItems.firstOrNull()?.let { handleItemPlayback(it) }
        }

        override fun setMediaItems(
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ) {
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
            mediaItems.getOrNull(startIndex)?.let { handleItemPlayback(it) }
        }

        private fun handleItemPlayback(mediaItem: MediaItem) {
            val id = mediaItem.mediaId
            if (id.startsWith("radio_")) {
                val realId = id.removePrefix("radio_")
                serviceScope.launch(Dispatchers.IO) {
                    val favs = repository.getFavoritesDirect()
                    val station = favs.find { it.id == realId }
                        ?: CuratedData.CURATED_GLOBAL_STATIONS.find { it.id == realId }
                    if (station != null) {
                        withContext(Dispatchers.Main) {
                            if (favs.isNotEmpty() && favs.any { it.id == station.id }) {
                                playerManager.updatePlaylist(favs)
                            }
                            playerManager.playStation(station)
                        }
                    }
                }
            } else if (id.startsWith("podelem_")) {
                val epId = id.removePrefix("podelem_")
                serviceScope.launch(Dispatchers.IO) {
                    val show = podcastRepository.favoritesFlow.value.find { s ->
                        podcastRepository.getEpisodesForShow(s).any { it.id == epId }
                    }
                    val episodes = show?.let { podcastRepository.getEpisodesForShow(it) } ?: emptyList()
                    val ep = episodes.find { e -> e.id == epId }
                    if (ep != null) {
                        withContext(Dispatchers.Main) {
                            playerManager.playPodcastEpisode(ep, show, episodes)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                playerManager.requestAudioFocus()
                playerManager.resume()
                acquireServiceLocks()
                updateNotification()
            }
            ACTION_PAUSE -> {
                playerManager.pause()
                releaseServiceLocks()
                updateNotification()
            }
            ACTION_NEXT -> {
                if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                    android.util.Log.d("MEDIA_BTN", "ACTION_NEXT recebido — roteando via PlaybackCoordinator")
                }
                val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                if (coordinator != null) {
                    coordinator.skipToNext()
                } else {
                    playerManager.playNext()
                }
                updateNotification()
            }
            ACTION_PREVIOUS -> {
                if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                    android.util.Log.d("MEDIA_BTN", "ACTION_PREVIOUS recebido — roteando via PlaybackCoordinator")
                }
                val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                if (coordinator != null) {
                    coordinator.skipToPrevious()
                } else {
                    playerManager.playPrevious()
                }
                updateNotification()
            }
            ACTION_STOP -> {
                playerManager.stop()
                releaseServiceLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun buildMediaNotification(): Notification {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RadioMediaService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RadioMediaService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, RadioMediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, RadioMediaService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val station = playerManager.currentStation.value
        val localAudio = playerManager.currentLocalAudio.value
        val podcast = playerManager.currentPodcastEpisode.value
        val rds = playerManager.rdsInfo.value

        val title = when {
            localAudio != null -> localAudio.title
            podcast != null -> podcast.title
            station != null -> station.name
            else -> "MediaPod • Rádio & Podcasts"
        }

        val subtitle = when {
            localAudio != null -> localAudio.artist
            podcast != null -> podcast.showTitle
            station != null -> getActiveSongOrLiveText()
            else -> "Streaming de Áudio Digital"
        }

        val subText = when {
            localAudio != null -> "Música"
            podcast != null -> "Podcast"
            station != null -> "Rádio Ao Vivo"
            else -> null
        }

        val platformToken = mediaLibrarySession?.platformToken
        val compatToken = try {
            if (platformToken != null) {
                android.support.v4.media.session.MediaSessionCompat.Token.fromToken(platformToken)
            } else null
        } catch (_: Exception) {
            null
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2) // Previous, Play/Pause, Next
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopIntent)

        if (compatToken != null) {
            mediaStyle.setMediaSession(compatToken)
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_action_pause else R.drawable.ic_action_play
        val playPauseTitle = if (isPlaying) "Pausar" else "Tocar"

        val largeIcon = currentArtworkBitmap ?: try {
            val iconRes = if (podcast != null) R.drawable.ic_podcast_generic else R.drawable.ic_radio_generic
            android.graphics.BitmapFactory.decodeResource(resources, iconRes) ?: run {
                val drawable = androidx.core.content.ContextCompat.getDrawable(this, iconRes)
                drawable?.let {
                    val size = 512
                    val b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(b)
                    it.setBounds(0, 0, size, size)
                    it.draw(c)
                    b
                }
            }
        } catch (_: Exception) { null }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(subText)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(stopIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(R.drawable.ic_action_previous, "Anterior", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(R.drawable.ic_action_next, "Próxima", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", stopIntent)

        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        return notificationBuilder.build()
    }

    private fun updateNotification() {
        try {
            val notification = buildMediaNotification()
            val isPlaying = playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING
            if (isPlaying) {
                startForegroundSafely(notification)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Failed to update media notification", e)
        }
    }

    private fun acquireServiceLocks() {
        try {
            if (serviceWakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                serviceWakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "RadioMediaService::StreamingLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            serviceWakeLock?.let {
                // CORREÇÃO P1 (auditoria item 6 — 24/09/2026):
                // O ExoPlayer já gerencia WAKE_MODE_NETWORK internamente (RadioPlayerManager L449).
                // Este lock do service é backup para o ForegroundService em si (não para streaming).
                // Timeout aumentado de 4h para 8h para cobrir sessões longas; renovado a cada PLAYING.
                if (!it.isHeld) it.acquire(8 * 60 * 60 * 1000L)
            }

            if (serviceWifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                serviceWifiLock = wm?.createWifiLock(wifiMode, "RadioMediaService::WifiStreamingLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            serviceWifiLock?.let {
                if (!it.isHeld) it.acquire()
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Falha ao adquirir locks do serviço", e)
        }
    }

    private fun releaseServiceLocks() {
        try {
            serviceWakeLock?.let { if (it.isHeld) it.release() }
            serviceWifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Falha ao liberar locks do serviço", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de mídia de rádio para tela de bloqueio e segundo plano"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        releaseServiceLocks()
        try {
            AudioRouteManager.getInstance(applicationContext).cleanup()
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Falha ao limpar AudioRouteManager: ${e.message}")
        }
        // CORREÇÃO P1 (auditoria 24/09): mediaLibrarySession liberada sincronamente antes de cancelar
        // o serviceScope. A versão anterior fazia serviceScope.launch{} seguido de cancel() imediato,
        // causando vazamento de MediaLibrarySession e ExoPlayer se a coroutine fosse cancelada antes
        // de executar.
        try {
            mediaLibrarySession?.run {
                player.release()
                release()
            }
            mediaLibrarySession = null
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Erro ao liberar MediaLibrarySession: ${e.message}")
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * ETAPA 2: Callback do MediaLibrarySession isolado para o painel do Android Auto.
     * Árvore de navegação visual:
     * - ROOT_ID ("root")
     *   ├── ROOT_FAVORITES ("Favoritos")
     *   ├── ROOT_RECENT ("Recentes")
     *   └── ROOT_ALL ("Todas as Rádios")
     *       ├── SUB_BRAZIL ("Brasil")
     *       ├── SUB_WORLD ("Top Mundial")
     *       └── SUB_GENRES ("Gêneros")
     *           └── genre_{tag}
     */
    private inner class AutoMediaLibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val toggleFavCommand = SessionCommand("ACTION_TOGGLE_FAVORITE", Bundle.EMPTY)
            val favButton = androidx.media3.session.CommandButton.Builder()
                .setDisplayName("Favoritar")
                .setIconResId(android.R.drawable.btn_star)
                .setSessionCommand(toggleFavCommand)
                .build()

            val toggleMuteCommand = SessionCommand(ACTION_TOGGLE_MUTE, Bundle.EMPTY)
            val isMuted = playerManager.isMuted.value
            val muteButton = androidx.media3.session.CommandButton.Builder()
                .setDisplayName(if (isMuted) "Ativar Som" else "Mudo")
                .setIconResId(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
                .setSessionCommand(toggleMuteCommand)
                .build()

            // Super onConnect automatically enables all session AND library commands for MediaLibrarySession
            val defaultResult = super.onConnect(session, controller)
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(toggleFavCommand)
                .add(toggleMuteCommand)
                .build()

            val playerCommands = defaultResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            if (isAndroidAutoController(session, controller)) {
                autoPlayForAndroidAutoIfAllowed()
            }

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(ImmutableList.of(favButton, muteButton))
                .build()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_BACK -> {
                    playerManager.seekRelative(-15000L)
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_FORWARD -> {
                    playerManager.seekRelative(30000L)
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                        android.util.Log.d("MEDIA_BTN", "COMMAND_SEEK_TO_NEXT recebido do sistema — roteando via NavigationContext")
                    }
                    val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                    if (coordinator != null) {
                        coordinator.skipToNext()
                    } else {
                        playerManager.playNext()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                        android.util.Log.d("MEDIA_BTN", "COMMAND_SEEK_TO_PREVIOUS recebido do sistema")
                    }
                    val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                    if (coordinator != null) {
                        coordinator.skipToPrevious()
                    } else {
                        playerManager.playPrevious()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_PLAY_PAUSE -> {
                    if (playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING) {
                        playerManager.pause()
                    } else {
                        playerManager.resume()
                    }
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_STOP -> {
                    playerManager.stop()
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                android.util.Log.d("MEDIA_BTN", "MediaButton recebido: ${intent.action}")
            }
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        playerManager.resume()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        playerManager.pause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                        if (playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING) {
                            playerManager.pause()
                        } else {
                            playerManager.resume()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                            android.util.Log.d("MEDIA_BTN", "onSkipToNext (KEYCODE_MEDIA_NEXT) recebido do sistema — roteando via NavigationContext")
                        }
                        val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                        if (coordinator != null) {
                            coordinator.skipToNext()
                        } else {
                            playerManager.playNext()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (com.marcioamaro.mediapod.BuildConfig.DEBUG) {
                            android.util.Log.d("MEDIA_BTN", "onSkipToPrevious (KEYCODE_MEDIA_PREVIOUS) recebido do sistema")
                        }
                        val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                        if (coordinator != null) {
                            coordinator.skipToPrevious()
                        } else {
                            playerManager.playPrevious()
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        playerManager.stop()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        playerManager.seekRelative(30000L)
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        playerManager.seekRelative(-15000L)
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    val current = playerManager.currentStation.value
                    if (current != null) {
                        serviceScope.launch(Dispatchers.IO) {
                            repository.toggleFavorite(current)
                            withContext(Dispatchers.Main) {
                                mediaLibrarySession?.notifyChildrenChanged(FAVORITE_RADIOS, 0, null)
                                updateNotification()
                            }
                        }
                    }
                }
                ACTION_TOGGLE_MUTE -> {
                    playerManager.toggleMute()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun isAndroidAutoController(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean {
            val pkg = controller.packageName.lowercase()
            val isAutoPkg = pkg.contains("gearhead") ||
                    pkg.contains("car") ||
                    pkg.contains("auto") ||
                    pkg.contains("bluetooth")
            val isAutoCompanion = try {
                session.isAutoCompanionController(controller)
            } catch (_: Throwable) {
                false
            }
            return isAutoPkg || isAutoCompanion
        }

        private fun restoreNavigationContextForStation(station: RadioStation) {
            val ipodPrefs = IpodPreferencesManager.getInstance(applicationContext)
            val sourceStr = ipodPrefs.getLastQueueSource()
            val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator

            serviceScope.launch(Dispatchers.IO) {
                val favs = repository.getFavoritesDirect()
                val recents = ipodPrefs.getRecentStations()
                val (queueSource, activeList) = when (sourceStr) {
                    "FAVORITES" -> {
                        if (favs.isNotEmpty()) Pair(com.marcioamaro.mediapod.player.context.QueueSource.FAVORITES, favs)
                        else Pair(com.marcioamaro.mediapod.player.context.QueueSource.GLOBAL, CuratedData.CURATED_GLOBAL_STATIONS)
                    }
                    "RECENTS" -> {
                        if (recents.isNotEmpty()) Pair(com.marcioamaro.mediapod.player.context.QueueSource.RECENTS, recents)
                        else Pair(com.marcioamaro.mediapod.player.context.QueueSource.GLOBAL, CuratedData.CURATED_GLOBAL_STATIONS)
                    }
                    else -> {
                        if (favs.any { it.id == station.id }) Pair(com.marcioamaro.mediapod.player.context.QueueSource.FAVORITES, favs)
                        else if (recents.any { it.id == station.id }) Pair(com.marcioamaro.mediapod.player.context.QueueSource.RECENTS, recents)
                        else Pair(com.marcioamaro.mediapod.player.context.QueueSource.GLOBAL, CuratedData.CURATED_GLOBAL_STATIONS)
                    }
                }
                withContext(Dispatchers.Main) {
                    playerManager.updatePlaylist(activeList)
                    coordinator?.setNavigationContext(
                        com.marcioamaro.mediapod.player.context.NavigationContext(
                            source = queueSource,
                            items = activeList.map { stationToQueueItem(it) }
                        )
                    )
                }
            }
        }

        private fun autoPlayForAndroidAutoIfAllowed() {
            val ipodPrefs = IpodPreferencesManager.getInstance(applicationContext)
            // Reproduz automaticamente no Android Auto apenas se a preferência do usuário estiver ativa
            if (!ipodPrefs.isAutoPlayOnLaunch) return

            val currentStation = playerManager.currentStation.value
            val currentPodcast = playerManager.currentPodcastEpisode.value

            if (playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING) {
                updateNotification()
                return
            }

            if (currentStation != null) {
                playerManager.playStation(currentStation)
                return
            }

            if (currentPodcast != null) {
                val show = ipodPrefs.getLastPlayedPodcast()?.second
                playerManager.playPodcastEpisode(currentPodcast, show)
                return
            }

            val lastMediaType = ipodPrefs.getLastMediaType()
            val lastStation = ipodPrefs.getLastPlayedStation()
            val lastPodcast = ipodPrefs.getLastPlayedPodcast()

            if (lastMediaType == "PODCAST" && lastPodcast != null) {
                playerManager.playPodcastEpisode(lastPodcast.first, lastPodcast.second)
            } else if (lastStation != null) {
                restoreNavigationContextForStation(lastStation)
                playerManager.playStation(lastStation)
            } else if (lastPodcast != null) {
                playerManager.playPodcastEpisode(lastPodcast.first, lastPodcast.second)
            }
        }

        private fun autoPlayLastMediaIfIdle() {
            autoPlayForAndroidAutoIfAllowed()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Reproduz apenas se a conexão for proveniente do Android Auto e a preferência de autoplay estiver ativa
            if (isAndroidAutoController(session, browser)) {
                autoPlayForAndroidAutoIfAllowed()
            }
            val listExtras = createContentStyleExtras(isGrid = false)
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_MEDIA_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("MediaPod + Radio / Podcast")
                        .setSubtitle("Rádios & Podcasts")
                        .setArtworkUri(radioDefaultIconUri)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setExtras(listExtras)
                        .build()
                )
                .build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(rootItem, LibraryParams.Builder().setExtras(listExtras).build())
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val listExtras = createContentStyleExtras(isGrid = false)
            val gridExtras = createContentStyleExtras(isGrid = true)

            when (parentId) {
                ROOT_MEDIA_ID, ROOT_ID, "root", "" -> {
                    val rootCategories = listOf(
                        createCategoryFolderItem(
                            id = FAVORITE_RADIOS,
                            title = "Rádios Favoritas",
                            subtitle = "Estações salvas",
                            iconUri = radioDefaultIconUri,
                            extras = gridExtras
                        ),
                        createCategoryFolderItem(
                            id = RECENT_RADIOS,
                            title = "Rádios Recentes",
                            subtitle = "Últimas estações ouvidas",
                            iconUri = radioDefaultIconUri,
                            extras = gridExtras
                        ),
                        createCategoryFolderItem(
                            id = FAVORITE_PODCASTS,
                            title = "Podcasts Favoritos",
                            subtitle = "Programas salvos",
                            iconUri = podcastDefaultIconUri,
                            extras = gridExtras
                        ),
                        createCategoryFolderItem(
                            id = RECENT_PODCASTS,
                            title = "Podcasts Recentes",
                            subtitle = "Últimos episódios ouvidos",
                            iconUri = podcastDefaultIconUri,
                            extras = gridExtras
                        )
                    )
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(rootCategories), params)
                    )
                }

                FAVORITE_RADIOS, "radio_favorites", "root_favorites", "root_radio" -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val favorites = repository.getFavoritesDirect()
                            val items = if (favorites.isNotEmpty()) {
                                favorites.map { station ->
                                    createStationCardItem(station, gridExtras, "radio_fav_${station.id}")
                                }
                            } else {
                                listOf(
                                    createInfoCardItem(
                                        id = "empty_radios",
                                        title = "Nenhuma rádio favorita",
                                        subtitle = "Favorite rádios no celular para vê-las aqui",
                                        iconUri = radioDefaultIconUri
                                    )
                                )
                            }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            android.util.Log.e("RadioMediaService", "Erro ao carregar rádios favoritas", e)
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                RECENT_RADIOS, "radio_recents", "root_recents" -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val recents = IpodPreferencesManager.getInstance(applicationContext).getRecentStations()
                            val items = if (recents.isNotEmpty()) {
                                recents.map { station ->
                                    createStationCardItem(station, gridExtras, "radio_rec_${station.id}")
                                }
                            } else {
                                listOf(
                                    createInfoCardItem(
                                        id = "empty_recent_radios",
                                        title = "Nenhuma rádio recente",
                                        subtitle = "Sintonize rádios para ver seu histórico aqui",
                                        iconUri = radioDefaultIconUri
                                    )
                                )
                            }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            android.util.Log.e("RadioMediaService", "Erro ao carregar rádios recentes", e)
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                FAVORITE_PODCASTS, "podcast_favorites", "root_podcasts" -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val favShows = podcastRepository.favoritesFlow.value
                            val items = if (favShows.isNotEmpty()) {
                                favShows.map { show ->
                                    createPodcastShowCardItem(show, gridExtras)
                                }
                            } else {
                                listOf(
                                    createInfoCardItem(
                                        id = "empty_podcasts",
                                        title = "Nenhum podcast favorito",
                                        subtitle = "Favorite podcasts no celular para vê-los aqui",
                                        iconUri = podcastDefaultIconUri
                                    )
                                )
                            }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            android.util.Log.e("RadioMediaService", "Erro ao carregar podcasts favoritos", e)
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                RECENT_PODCASTS, "podcast_recents" -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val recentEps = podcastRepository.recentEpisodesFlow.value
                            val items = if (recentEps.isNotEmpty()) {
                                recentEps.map { ep ->
                                    createPodcastEpisodeCardItem(ep, gridExtras, "podrec_${ep.id}")
                                }
                            } else {
                                listOf(
                                    createInfoCardItem(
                                        id = "empty_recent_podcasts",
                                        title = "Nenhum podcast recente",
                                        subtitle = "Ouça episódios de podcasts para ter histórico aqui",
                                        iconUri = podcastDefaultIconUri
                                    )
                                )
                            }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            android.util.Log.e("RadioMediaService", "Erro ao carregar podcasts recentes", e)
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                else -> {
                    if (parentId.startsWith("podshow_")) {
                        val showId = parentId.removePrefix("podshow_")
                        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val show = podcastRepository.favoritesFlow.value.find { it.id == showId }
                                val episodes = if (show != null) {
                                    podcastRepository.getEpisodesForShow(show)
                                } else emptyList()

                                val items = if (episodes.isNotEmpty()) {
                                    episodes.map { ep ->
                                        createPodcastEpisodeCardItem(ep, gridExtras, "podelem_${ep.id}")
                                    }
                                } else {
                                    listOf(
                                        createInfoCardItem(
                                            id = "empty_episodes_$showId",
                                            title = "Nenhum episódio encontrado",
                                            subtitle = "Feed indisponível no momento",
                                            iconUri = podcastDefaultIconUri
                                        )
                                    )
                                }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            } catch (e: Exception) {
                                android.util.Log.e("RadioMediaService", "Erro ao carregar episódios", e)
                                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                            }
                        }
                        return future
                    }

                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.of(), params)
                    )
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val listExtras = createContentStyleExtras(isGrid = false)
            val gridExtras = createContentStyleExtras(isGrid = true)
            when {
                mediaId == ROOT_MEDIA_ID || mediaId == ROOT_ID || mediaId == "root" -> {
                    return onGetLibraryRoot(session, browser, null)
                }
                mediaId == FAVORITE_RADIOS -> {
                    val item = createCategoryFolderItem(
                        FAVORITE_RADIOS, "Rádios Favoritas", "Estações salvas", radioDefaultIconUri, gridExtras
                    )
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null))
                }
                mediaId == RECENT_RADIOS -> {
                    val item = createCategoryFolderItem(
                        RECENT_RADIOS, "Rádios Recentes", "Últimas estações ouvidas", radioDefaultIconUri, gridExtras
                    )
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null))
                }
                mediaId == FAVORITE_PODCASTS -> {
                    val item = createCategoryFolderItem(
                        FAVORITE_PODCASTS, "Podcasts Favoritos", "Programas salvos", podcastDefaultIconUri, gridExtras
                    )
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null))
                }
                mediaId == RECENT_PODCASTS -> {
                    val item = createCategoryFolderItem(
                        RECENT_PODCASTS, "Podcasts Recentes", "Últimos episódios ouvidos", podcastDefaultIconUri, gridExtras
                    )
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null))
                }
                mediaId.startsWith("radio_fav_") || mediaId.startsWith("radio_rec_") || mediaId.startsWith("radio_") -> {
                    val realId = mediaId.removePrefix("radio_fav_").removePrefix("radio_rec_").removePrefix("radio_")
                    val future = SettableFuture.create<LibraryResult<MediaItem>>()
                    serviceScope.launch(Dispatchers.IO) {
                        val station = repository.getFavoritesDirect().find { it.id == realId }
                            ?: IpodPreferencesManager.getInstance(applicationContext).getRecentStations().find { it.id == realId }
                            ?: CuratedData.CURATED_GLOBAL_STATIONS.find { it.id == realId }
                        if (station != null) {
                            future.set(LibraryResult.ofItem(createStationCardItem(station, listExtras, mediaId), null))
                        } else {
                            future.set(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
                        }
                    }
                    return future
                }
                mediaId.startsWith("podshow_") -> {
                    val showId = mediaId.removePrefix("podshow_")
                    val show = podcastRepository.favoritesFlow.value.find { it.id == showId }
                    if (show != null) {
                        return Futures.immediateFuture(
                            LibraryResult.ofItem(createPodcastShowCardItem(show, listExtras), null)
                        )
                    }
                }
                mediaId.startsWith("podrec_") || mediaId.startsWith("podelem_") -> {
                    val epId = mediaId.removePrefix("podrec_").removePrefix("podelem_")
                    val future = SettableFuture.create<LibraryResult<MediaItem>>()
                    serviceScope.launch(Dispatchers.IO) {
                        val ep = podcastRepository.recentEpisodesFlow.value.find { it.id == epId }
                            ?: podcastRepository.favoritesFlow.value.flatMap { podcastRepository.getEpisodesForShow(it) }.find { it.id == epId }
                        if (ep != null) {
                            future.set(LibraryResult.ofItem(createPodcastEpisodeCardItem(ep, listExtras, mediaId), null))
                        } else {
                            future.set(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
                        }
                    }
                    return future
                }
            }
            return Futures.immediateFuture(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val target = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            if (target != null) {
                val mediaId = target.mediaId
                val gridExtras = createContentStyleExtras(isGrid = true)
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()

                serviceScope.launch(Dispatchers.IO) {
                    when {
                        mediaId.startsWith("radio_fav_") -> {
                            val realId = mediaId.removePrefix("radio_fav_")
                            val favs = repository.getFavoritesDirect()
                            val matchIndex = favs.indexOfFirst { it.id == realId }.coerceAtLeast(0)
                            val station = favs.getOrNull(matchIndex) ?: CuratedData.CURATED_GLOBAL_STATIONS.find { it.id == realId }
                            if (station != null) {
                                withContext(Dispatchers.Main) {
                                    if (favs.isNotEmpty()) playerManager.updatePlaylist(favs)
                                    playerManager.playStation(station)
                                    val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                                    coordinator?.setNavigationContext(
                                        com.marcioamaro.mediapod.player.context.NavigationContext(
                                            source = com.marcioamaro.mediapod.player.context.QueueSource.FAVORITES,
                                            items = favs.map { stationToQueueItem(it) }
                                        )
                                    )
                                    IpodPreferencesManager.getInstance(applicationContext).saveLastQueueSource("FAVORITES")
                                }
                                val fullQueue = if (favs.isNotEmpty()) {
                                    favs.map { createStationCardItem(it, gridExtras, "radio_fav_${it.id}") }
                                } else {
                                    listOf(createStationCardItem(station, gridExtras, "radio_fav_${station.id}"))
                                }
                                future.set(MediaSession.MediaItemsWithStartPosition(fullQueue, matchIndex, 0))
                                return@launch
                            }
                        }
                        mediaId.startsWith("radio_rec_") -> {
                            val realId = mediaId.removePrefix("radio_rec_")
                            val recents = IpodPreferencesManager.getInstance(applicationContext).getRecentStations()
                            val matchIndex = recents.indexOfFirst { it.id == realId }.coerceAtLeast(0)
                            val station = recents.getOrNull(matchIndex) ?: repository.getFavoritesDirect().find { it.id == realId }
                            if (station != null) {
                                withContext(Dispatchers.Main) {
                                    if (recents.isNotEmpty()) playerManager.updatePlaylist(recents)
                                    playerManager.playStation(station)
                                    val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                                    coordinator?.setNavigationContext(
                                        com.marcioamaro.mediapod.player.context.NavigationContext(
                                            source = com.marcioamaro.mediapod.player.context.QueueSource.RECENTS,
                                            items = recents.map { stationToQueueItem(it) }
                                        )
                                    )
                                    IpodPreferencesManager.getInstance(applicationContext).saveLastQueueSource("RECENTS")
                                }
                                val fullQueue = if (recents.isNotEmpty()) {
                                    recents.map { createStationCardItem(it, gridExtras, "radio_rec_${it.id}") }
                                } else {
                                    listOf(createStationCardItem(station, gridExtras, "radio_rec_${station.id}"))
                                }
                                future.set(MediaSession.MediaItemsWithStartPosition(fullQueue, matchIndex, 0))
                                return@launch
                            }
                        }
                        mediaId.startsWith("radio_") -> {
                            val realId = mediaId.removePrefix("radio_")
                            val favs = repository.getFavoritesDirect()
                            val recents = IpodPreferencesManager.getInstance(applicationContext).getRecentStations()
                            val station = favs.find { it.id == realId } ?: recents.find { it.id == realId } ?: CuratedData.CURATED_GLOBAL_STATIONS.find { it.id == realId }
                            if (station != null) {
                                val currentPlaylist = playerManager.getCurrentPlaylist()
                                val activeList = if (currentPlaylist.any { it.id == station.id }) currentPlaylist
                                    else if (favs.any { it.id == station.id }) favs
                                    else if (recents.any { it.id == station.id }) recents
                                    else listOf(station)
                                val matchIndex = activeList.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
                                withContext(Dispatchers.Main) {
                                    playerManager.updatePlaylist(activeList)
                                    playerManager.playStation(station)
                                    val coordinator = (applicationContext as? com.marcioamaro.mediapod.RadioApp)?.playbackCoordinator
                                    coordinator?.setNavigationContext(
                                        com.marcioamaro.mediapod.player.context.NavigationContext(
                                            source = com.marcioamaro.mediapod.player.context.QueueSource.GLOBAL,
                                            items = activeList.map { stationToQueueItem(it) }
                                        )
                                    )
                                    IpodPreferencesManager.getInstance(applicationContext).saveLastQueueSource("GLOBAL")
                                }
                                val fullQueue = activeList.map { createStationCardItem(it, gridExtras, "radio_${it.id}") }
                                future.set(MediaSession.MediaItemsWithStartPosition(fullQueue, matchIndex, 0))
                                return@launch
                            }
                        }
                        mediaId.startsWith("podrec_") -> {
                            val epId = mediaId.removePrefix("podrec_")
                            val recentEps = podcastRepository.recentEpisodesFlow.value
                            val matchIndex = recentEps.indexOfFirst { it.id == epId }.coerceAtLeast(0)
                            val ep = recentEps.getOrNull(matchIndex)
                            if (ep != null) {
                                val show = podcastRepository.favoritesFlow.value.find { it.id == ep.showId }
                                withContext(Dispatchers.Main) {
                                    playerManager.playPodcastEpisode(ep, show, recentEps)
                                }
                                val fullQueue = recentEps.map { createPodcastEpisodeCardItem(it, gridExtras, "podrec_${it.id}") }
                                future.set(MediaSession.MediaItemsWithStartPosition(fullQueue, matchIndex, 0))
                                return@launch
                            }
                        }
                        mediaId.startsWith("podelem_") -> {
                            val epId = mediaId.removePrefix("podelem_")
                            val show = podcastRepository.favoritesFlow.value.find { s ->
                                podcastRepository.getEpisodesForShow(s).any { it.id == epId }
                            }
                            val episodes = show?.let { podcastRepository.getEpisodesForShow(it) } ?: emptyList()
                            val matchIndex = episodes.indexOfFirst { it.id == epId }.coerceAtLeast(0)
                            val episode = episodes.getOrNull(matchIndex)
                            if (episode != null) {
                                withContext(Dispatchers.Main) {
                                    playerManager.playPodcastEpisode(episode, show, episodes)
                                }
                                val fullQueue = episodes.map { createPodcastEpisodeCardItem(it, gridExtras, "podelem_${it.id}") }
                                future.set(MediaSession.MediaItemsWithStartPosition(fullQueue, matchIndex, 0))
                                return@launch
                            }
                        }
                    }
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
                }
                return future
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            autoPlayLastMediaIfIdle()
            val ipodPrefs = IpodPreferencesManager.getInstance(applicationContext)
            val currentStation = playerManager.currentStation.value ?: ipodPrefs.getLastPlayedStation()
            val currentPodcast = playerManager.currentPodcastEpisode.value ?: ipodPrefs.getLastPlayedPodcast()?.first
            val gridExtras = createContentStyleExtras(isGrid = true)
            if (currentStation != null) {
                val item = createStationCardItem(currentStation, gridExtras, "radio_${currentStation.id}")
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0)
                )
            } else if (currentPodcast != null) {
                val item = createPodcastEpisodeCardItem(currentPodcast, gridExtras, "podelem_${currentPodcast.id}")
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0)
                )
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch(Dispatchers.Default) {
                val results = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                    com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query)
                }
                session.notifySearchResultChanged(browser, query, results.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val gridExtras = createContentStyleExtras(isGrid = true)
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                val resolvedList = mediaItems.map { item ->
                    val id = item.mediaId
                    val query = item.requestMetadata.searchQuery
                    if (!query.isNullOrBlank()) {
                        val match = repository.getFavoritesDirect().find {
                            com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query)
                        } ?: CuratedData.CURATED_GLOBAL_STATIONS.find {
                            com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query)
                        }
                        if (match != null) {
                            withContext(Dispatchers.Main) {
                                playerManager.playStation(match)
                            }
                            createStationCardItem(match, gridExtras)
                        } else {
                            item
                        }
                    } else if (id.startsWith("radio_")) {
                        val realId = id.removePrefix("radio_")
                        val station = repository.getFavoritesDirect().find { it.id == realId }
                            ?: CuratedData.CURATED_GLOBAL_STATIONS.find { it.id == realId }
                        if (station != null) {
                            withContext(Dispatchers.Main) {
                                playerManager.playStation(station)
                            }
                            createStationCardItem(station, gridExtras)
                        } else {
                            item
                        }
                    } else if (id.startsWith("podelem_")) {
                        withContext(Dispatchers.Main) {
                            playerManager.resume()
                        }
                        item
                    } else {
                        item
                    }
                }.toMutableList()
                future.set(resolvedList)
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val gridExtras = createContentStyleExtras(isGrid = true)
            val list = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query)
            }.take(30)
            val items = list.map { createStationCardItem(it, gridExtras) }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            )
        }

        private fun createCategoryFolderItem(
            id: String,
            title: String,
            subtitle: String,
            iconUri: Uri,
            extras: Bundle
        ): MediaItem {
            val builder = MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(iconUri)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(
                    if (id == FAVORITE_PODCASTS || id == RECENT_PODCASTS) MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                    else MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
                )
                .setExtras(extras)
            val bytes = if (id == FAVORITE_PODCASTS || id == RECENT_PODCASTS) podcastDefaultIconBytes else radioDefaultIconBytes
            if (bytes.isNotEmpty()) {
                builder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(builder.build())
                .build()
        }

        private fun createInfoCardItem(
            id: String,
            title: String,
            subtitle: String,
            iconUri: Uri
        ): MediaItem {
            val builder = MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(iconUri)
                .setIsBrowsable(false)
                .setIsPlayable(false)
            val bytes = if (iconUri == podcastDefaultIconUri) podcastDefaultIconBytes else radioDefaultIconBytes
            if (bytes.isNotEmpty()) {
                builder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(builder.build())
                .build()
        }

        private fun createStationCardItem(
            station: RadioStation,
            extras: Bundle,
            customMediaId: String? = null
        ): MediaItem {
            val isCurrent = playerManager.currentStation.value?.id == station.id
            val subtitle = if (isCurrent) getActiveSongOrLiveText() else "Ao Vivo"
            val hasFavicon = station.hasValidFavicon
            val artworkUri = if (hasFavicon) Uri.parse(station.effectiveFavicon) else radioDefaultIconUri
            val itemMediaId = customMediaId ?: "radio_${station.id}"
            val builder = MediaMetadata.Builder()
                .setTitle(station.name.take(40))
                .setDisplayTitle(station.name.take(40))
                .setArtist(subtitle)
                .setSubtitle(subtitle)
                .setAlbumTitle("MediaPod • Rádio")
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setExtras(extras)
            if (!hasFavicon && radioDefaultIconBytes.isNotEmpty()) {
                builder.setArtworkData(radioDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            val metadata = builder.build()

            return MediaItem.Builder()
                .setMediaId(itemMediaId)
                .setUri(station.streamUrl)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(Uri.parse(station.streamUrl))
                        .build()
                )
                .setMediaMetadata(metadata)
                .build()
        }

        private fun createPodcastShowCardItem(
            show: com.marcioamaro.mediapod.data.model.PodcastShow,
            extras: Bundle
        ): MediaItem {
            val hasShowArt = show.artworkUrl.isNotBlank()
            val artworkUri = if (hasShowArt) Uri.parse(show.artworkUrl) else podcastDefaultIconUri
            val builder = MediaMetadata.Builder()
                .setTitle(show.title)
                .setSubtitle(show.author.ifBlank { "Podcast" })
                .setArtworkUri(artworkUri)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                .setExtras(extras)
            if (!hasShowArt && podcastDefaultIconBytes.isNotEmpty()) {
                builder.setArtworkData(podcastDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            return MediaItem.Builder()
                .setMediaId("podshow_${show.id}")
                .setMediaMetadata(builder.build())
                .build()
        }

        private fun createPodcastEpisodeCardItem(
            episode: com.marcioamaro.mediapod.data.model.PodcastEpisode,
            extras: Bundle,
            customMediaId: String? = null
        ): MediaItem {
            val itemMediaId = customMediaId ?: "podelem_${episode.id}"
            val hasEpArt = episode.artworkUrl.isNotBlank()
            val artworkUri = if (hasEpArt) Uri.parse(episode.artworkUrl) else podcastDefaultIconUri
            val builder = MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(episode.showTitle)
                .setSubtitle(episode.showTitle)
                .setAlbumTitle(episode.publishDate)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setExtras(extras)
            if (!hasEpArt && podcastDefaultIconBytes.isNotEmpty()) {
                builder.setArtworkData(podcastDefaultIconBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            return MediaItem.Builder()
                .setMediaId(itemMediaId)
                .setUri(episode.audioUrl)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(Uri.parse(episode.audioUrl))
                        .build()
                )
                .setMediaMetadata(builder.build())
                .build()
        }

        private fun createContentStyleExtras(isGrid: Boolean = true): Bundle {
            return Bundle().apply {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                putInt(
                    "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT",
                    if (isGrid) 2 else 1
                )
                putInt(
                    "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT",
                    if (isGrid) 2 else 1
                )
                putInt(
                    "androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE",
                    if (isGrid) 2 else 1
                )
                putInt(
                    "androidx.media.utils.MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE",
                    if (isGrid) 2 else 1
                )
                // CLIP_CHILDREN removido para permitir exibição completa e sem bloqueio no Android Auto durante a condução
            }
        }

        private fun stationToQueueItem(station: RadioStation): com.marcioamaro.mediapod.player.coordinator.PlaybackQueueItem {
            val subtitle = if (station.city.isNotBlank()) "${station.city} • ${station.country}" else station.country
            return com.marcioamaro.mediapod.player.coordinator.PlaybackQueueItem(
                id = station.id,
                mediaUri = station.streamUrl,
                title = station.name,
                subtitle = subtitle,
                artworkUri = station.effectiveFavicon.ifBlank { null },
                mediaType = com.marcioamaro.mediapod.player.ActiveMediaType.LIVE_RADIO,
                durationMs = null,
                isLiveStream = true
            )
        }
    }
}
