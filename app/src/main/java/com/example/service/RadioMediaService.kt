package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
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
import com.example.MainActivity
import com.example.R
import com.example.data.db.RadioDatabase
import com.example.data.model.RadioStation
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.repository.CuratedData
import com.example.data.repository.RadioRepository
import com.example.player.RadioPlaybackStatus
import com.example.player.RadioPlayerManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class RadioMediaService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var playerManager: RadioPlayerManager
    private lateinit var repository: RadioRepository
    private var isForegroundActive = false

    companion object {
        const val CHANNEL_ID = "radio_playback_channel"
        const val NOTIFICATION_ID = 1001

        // Notification Playback & Mute Actions
        const val ACTION_PREVIOUS = "com.example.ACTION_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.example.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_TOGGLE_MUTE = "com.example.ACTION_TOGGLE_MUTE"

        // Android Auto Media Tree Navigation Roots
        const val ROOT_ID = "root"
        const val ROOT_FAVORITES = "root_favorites"
        const val ROOT_RECENT = "root_recent"
        const val ROOT_ALL = "root_all"
        const val SUB_BRAZIL = "sub_brazil"
        const val SUB_WORLD = "sub_world"
        const val SUB_GENRES = "sub_genres"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            playerManager = RadioPlayerManager.getInstance(applicationContext)
            val db = RadioDatabase.getDatabase(applicationContext)
            repository = RadioRepository(db.favoriteStationDao())

            // ETAPA 1: Garantir registro imediato do Foreground Service para evitar
            // ForegroundServiceDidNotStartInTimeException
            startImmediateForeground()

            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val forwardingPlayer = RadioForwardingPlayer(
                playerManager.getPlayer(),
                playerManager
            )

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

            val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.media_notification_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
            notificationProvider.setSmallIcon(R.drawable.ic_stat_radio)
            setMediaNotificationProvider(notificationProvider)

            // Monitorar mudanças em tempo real para atualizar o Android Auto e notificação
            observeAppStateForCarAndNotification()

        } catch (e: Exception) {
            android.util.Log.e("RadioMediaService", "Error during service onCreate", e)
        }
    }

    private fun startImmediateForeground() {
        try {
            val notification = buildMediaNotification(
                title = "IPod Class + Radio",
                content = "Sintonizando rádio..."
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } catch (e: Exception) {
            android.util.Log.e("RadioMediaService", "Failed to start immediate foreground", e)
        }
    }

    private fun buildMediaNotification(title: String, content: String): Notification {
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RadioMediaService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, RadioMediaService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, RadioMediaService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val muteIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, RadioMediaService::class.java).setAction(ACTION_TOGGLE_MUTE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = playerManager.playbackStatus.value == RadioPlaybackStatus.PLAYING
        val isMuted = playerManager.isMuted.value

        val playPauseIcon = if (isPlaying) R.drawable.ic_action_pause else R.drawable.ic_action_play
        val playPauseTitle = if (isPlaying) "Pausar" else "Reproduzir"

        val muteIcon = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        val muteTitle = if (isMuted) "Ativar Som" else "Mudo"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(sessionActivityPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_action_previous, "Anterior", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(R.drawable.ic_action_next, "Próxima", nextIntent)
            .addAction(muteIcon, muteTitle, muteIntent)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun updatePlaybackNotification(
        customTitle: String? = null,
        customContent: String? = null
    ) {
        try {
            val localAudio = playerManager.currentLocalAudio.value
            val station = playerManager.currentStation.value
            val rds = playerManager.rdsInfo.value

            val title = customTitle ?: when {
                localAudio != null -> localAudio.title
                rds.radioText.isNotBlank() -> rds.radioText
                station != null -> station.name
                else -> "IPod Class + Radio"
            }

            val content = customContent ?: when {
                localAudio != null -> localAudio.artist
                rds.radioText.isNotBlank() && station != null -> station.name
                station != null -> "${station.city} ${station.country} • ${station.primaryGenre}".trim()
                else -> "Sintonizando rádio..."
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notification = buildMediaNotification(title, content)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            android.util.Log.w("RadioMediaService", "Failed to update playback notification", e)
        }
    }

    private fun observeAppStateForCarAndNotification() {
        // 1. Sincronização em tempo real da pasta FAVORITOS no Android Auto
        serviceScope.launch {
            repository.favoritesFlow.collect {
                try {
                    mediaLibrarySession?.notifyChildrenChanged(ROOT_FAVORITES, 0, null)
                } catch (_: Exception) {}
            }
        }

        // 2. Sincronização em tempo real da pasta RECENTES e notificação
        serviceScope.launch {
            playerManager.currentStation.collect { station ->
                if (station != null) {
                    try {
                        mediaLibrarySession?.notifyChildrenChanged(ROOT_RECENT, 0, null)
                    } catch (_: Exception) {}
                    updatePlaybackNotification()
                }
            }
        }

        // 3. Sincronização em tempo real de MP3 local
        serviceScope.launch {
            playerManager.currentLocalAudio.collect {
                updatePlaybackNotification()
            }
        }

        // 4. Atualização de RDS na notificação
        serviceScope.launch {
            playerManager.rdsInfo.collect {
                updatePlaybackNotification()
            }
        }

        // 5. Atualização de status de reprodução (Play/Pause) na notificação
        serviceScope.launch {
            playerManager.playbackStatus.collect {
                updatePlaybackNotification()
            }
        }

        // 6. Atualização de status de Mudo na notificação
        serviceScope.launch {
            playerManager.isMuted.collect {
                updatePlaybackNotification()
            }
        }
    }

    private class RadioForwardingPlayer(
        player: Player,
        private val playerManager: RadioPlayerManager
    ) : ForwardingPlayer(player) {

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
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true

        override fun play() {
            playerManager.resume()
        }

        override fun pause() {
            playerManager.pause()
        }

        override fun stop() {
            playerManager.stop()
        }

        override fun seekToNext() {
            if (playerManager.currentLocalAudio.value != null) {
                playerManager.nextLocalTrack()
            } else {
                playerManager.playNextStation()
            }
        }

        override fun seekToNextMediaItem() {
            seekToNext()
        }

        override fun seekToPrevious() {
            if (playerManager.currentLocalAudio.value != null) {
                playerManager.prevLocalTrack()
            } else {
                playerManager.playPreviousStation()
            }
        }

        override fun seekToPreviousMediaItem() {
            seekToPrevious()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ETAPA 1: Garantir que startForeground esteja ativo logo no início do onStartCommand
        if (!isForegroundActive) {
            startImmediateForeground()
        }

        when (intent?.action) {
            ACTION_PREVIOUS -> {
                playerManager.playPrevious()
                updatePlaybackNotification()
            }
            ACTION_PLAY_PAUSE -> {
                playerManager.togglePlayPause()
                updatePlaybackNotification()
            }
            ACTION_NEXT -> {
                playerManager.playNext()
                updatePlaybackNotification()
            }
            ACTION_TOGGLE_MUTE -> {
                playerManager.toggleMute()
                updatePlaybackNotification()
            }
        }

        try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            android.util.Log.e("RadioMediaService", "Error in onStartCommand super", e)
        }
        return START_STICKY
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
        serviceScope.launch {
            mediaLibrarySession?.run {
                player.release()
                release()
                mediaLibrarySession = null
            }
        }
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

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(defaultResult.availablePlayerCommands)
                .setCustomLayout(ImmutableList.of(favButton, muteButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                "ACTION_TOGGLE_FAVORITE" -> {
                    val current = playerManager.currentStation.value
                    if (current != null) {
                        serviceScope.launch(Dispatchers.IO) {
                            repository.toggleFavorite(current)
                        }
                    }
                }
                ACTION_TOGGLE_MUTE -> {
                    playerManager.toggleMute()
                    updatePlaybackNotification()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = Bundle().apply {
                putBoolean("android.media.browse.SEARCH_SUPPORTED", false)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // LIST/TABS
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)  // LIST
                putInt("android.media.browse.CONTENT_STYLE_SUPPORTED", 1)
            }
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("IPod Class + Radio")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setExtras(rootExtras)
                        .build()
                )
                .build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(rootItem, LibraryParams.Builder().setExtras(rootExtras).build())
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

            when (parentId) {
                ROOT_ID -> {
                    // Árvore principal exclusiva do Android Auto com as 3 pastas exigidas:
                    val children = listOf(
                        createFolderItem(ROOT_FAVORITES, "⭐ Favoritos", "Emissoras salvas"),
                        createFolderItem(ROOT_RECENT, "🕒 Recentes", "Últimas ouvidas"),
                        createFolderItem(ROOT_ALL, "📻 Todas as Rádios", "Categorias e catálogo")
                    )
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                    )
                }

                ROOT_FAVORITES -> {
                    // 1. Favoritos do banco assíncrono direto e rápido
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val favs = repository.getFavoritesDirect()
                            val items = favs.take(40).map { createStationItem(it) }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            android.util.Log.e("RadioMediaService", "Error loading favorites for AA", e)
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                ROOT_RECENT -> {
                    // 2. Histórico cronológico de recentes
                    val prefs = IpodPreferencesManager.getInstance(applicationContext)
                    val recents = prefs.getRecentStations().take(20)
                    val items = recents.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                ROOT_ALL -> {
                    // 3. Todas as Rádios / Categorias
                    val categories = listOf(
                        createFolderItem(SUB_BRAZIL, "🇧🇷 Rádios do Brasil", "Emissoras nacionais"),
                        createFolderItem(SUB_WORLD, "🌍 Top Mundial", "Mais ouvidas do mundo"),
                        createFolderItem(SUB_GENRES, "🎵 Gêneros Musicais", "Estilos e ritmos")
                    )
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(categories), params)
                    )
                }

                SUB_BRAZIL -> {
                    val list = CuratedData.CURATED_GLOBAL_STATIONS
                        .filter { it.countryCode == "BR" }
                        .take(30)
                    val items = list.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                SUB_WORLD -> {
                    val list = CuratedData.CURATED_GLOBAL_STATIONS.take(30)
                    val items = list.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                SUB_GENRES -> {
                    val items = CuratedData.GENRES.take(15).map { genre ->
                        createFolderItem("genre_${genre.tag}", "${genre.iconEmoji} ${genre.name}", genre.description)
                    }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                else -> {
                    if (parentId.startsWith("genre_")) {
                        val tag = parentId.removePrefix("genre_")
                        val list = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                            it.tags.contains(tag, ignoreCase = true) || it.primaryGenre.contains(tag, ignoreCase = true)
                        }.take(25)
                        val items = list.map { createStationItem(it) }
                        return Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                        )
                    }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.of(), params)
                    )
                }
            }
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
                val streamUrl = target.requestMetadata.mediaUri?.toString()
                    ?: target.mediaId

                val match = CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull { it.id == target.mediaId }
                    ?: IpodPreferencesManager.getInstance(applicationContext).getRecentStations().firstOrNull { it.id == target.mediaId }
                    ?: RadioStation(
                        id = target.mediaId,
                        name = target.mediaMetadata.title?.toString() ?: "Rádio",
                        streamUrl = streamUrl,
                        favicon = target.mediaMetadata.artworkUri?.toString() ?: "",
                        country = target.mediaMetadata.albumTitle?.toString() ?: "Mundial",
                        countryCode = "BR",
                        state = "",
                        city = target.mediaMetadata.artist?.toString() ?: "",
                        tags = "radio",
                        bitrate = 128,
                        codec = "MP3",
                        votes = 100
                    )
                playerManager.playStation(match)
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val current = playerManager.currentStation.value
                ?: IpodPreferencesManager.getInstance(applicationContext).getLastPlayedStation()
                ?: CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull()

            if (current != null) {
                playerManager.playStation(current)
                val item = createStationItem(current)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0)
                )
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            )
        }

        private fun createFolderItem(id: String, title: String, subtitle: String): MediaItem {
            val extras = Bundle().apply {
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        private fun createStationItem(station: RadioStation): MediaItem {
            val subtitle = buildString {
                if (station.primaryGenre.isNotBlank()) append(station.primaryGenre)
                if (station.country.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(station.country)
                }
                if (station.city.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(station.city)
                }
            }

            // Ícone fixo do aplicativo em alta resolução para o painel do Android Auto
            val appIconUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.mipmap.ic_launcher}")

            return MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrl)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(Uri.parse(station.streamUrl))
                        .build()
                )
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist(subtitle)
                        .setAlbumTitle(station.country)
                        .setArtworkUri(appIconUri)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .build()
                )
                .build()
        }
    }
}
