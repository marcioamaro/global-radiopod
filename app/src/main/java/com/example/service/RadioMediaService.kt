package com.example.service

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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class RadioMediaService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var playerManager: RadioPlayerManager
    private lateinit var repository: RadioRepository

    companion object {
        const val CHANNEL_ID = "radio_playback_channel"
        const val NOTIFICATION_ID = 1001

        // Ações de mídia para controle em segundo plano e tela de bloqueio
        const val ACTION_PLAY = "com.example.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.ACTION_PREVIOUS"

        // Custom Mute Action for Android Auto
        const val ACTION_TOGGLE_MUTE = "com.example.ACTION_TOGGLE_MUTE"

        // Android Auto Media Tree Navigation Roots
        const val ROOT_ID = "root"
        const val ROOT_RADIO = "root_radio"
        const val ROOT_PODCASTS = "root_podcasts"
        const val ROOT_FAVORITES = "root_favorites"
        const val ROOT_RECENT = "root_recent"
        const val ROOT_ALL = "root_all"
        const val SUB_BRAZIL = "sub_brazil"
        const val SUB_WORLD = "sub_world"
        const val SUB_GENRES = "sub_genres"

        // Symmetrical Sub-branches
        const val RADIO_FAVORITES = "radio_favorites"
        const val RADIO_RECENTS = "radio_recents"
        const val RADIO_TOP_BRAZIL = "radio_top_brazil"
        const val RADIO_STATES = "radio_states"
        const val RADIO_TOP_WORLD = "radio_top_world"
        const val RADIO_GENRES = "radio_genres"
        const val RADIO_CUSTOM = "radio_custom"

        const val PODCAST_FAVORITES = "podcast_favorites"
        const val PODCAST_RECENTS = "podcast_recents"
        const val PODCAST_TOP_BRAZIL = "podcast_top_brazil"
        const val PODCAST_TOP_WORLD = "podcast_top_world"
        const val PODCAST_CATEGORIES = "podcast_categories"
        const val PODCAST_CUSTOM = "podcast_custom"
    }

    private lateinit var podcastRepository: com.example.data.repository.PodcastRepository
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var serviceWifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            playerManager = RadioPlayerManager.getInstance(applicationContext)
            val db = RadioDatabase.getDatabase(applicationContext)
            repository = RadioRepository(db.favoriteStationDao())
            podcastRepository = com.example.data.repository.PodcastRepository.getInstance(applicationContext)

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

            val initialNotification = buildMediaNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }

            // Sincronização em tempo real do estado de reprodução e do Android Auto
            observeAppState()

        } catch (e: Exception) {
            android.util.Log.e("RadioMediaService", "Error during service onCreate", e)
        }
    }

    private fun observeAppState() {
        // 1. Sincronização em tempo real da pasta FAVORITOS no Android Auto
        serviceScope.launch {
            repository.favoritesFlow.collect {
                try {
                    mediaLibrarySession?.notifyChildrenChanged(ROOT_FAVORITES, 0, null)
                } catch (_: Exception) {}
            }
        }

        // 2. Sincronização em tempo real da pasta RECENTES no Android Auto e notificação
        serviceScope.launch {
            playerManager.currentStation.collect { station ->
                if (station != null) {
                    try {
                        mediaLibrarySession?.notifyChildrenChanged(ROOT_RECENT, 0, null)
                    } catch (_: Exception) {}
                }
                updateNotification()
            }
        }

        // 3. Monitoramento de WAKE_LOCK, WIFI_LOCK e atualização de status
        serviceScope.launch {
            playerManager.playbackStatus.collect { status ->
                when (status) {
                    RadioPlaybackStatus.PLAYING -> {
                        acquireServiceLocks()
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

        // 4. Atualização dinâmica com metadados de RDS (música e artista ao vivo)
        serviceScope.launch {
            playerManager.rdsInfo.collect {
                updateNotification()
            }
        }

        // 5. Atualização para faixas MP3 locais
        serviceScope.launch {
            playerManager.currentLocalAudio.collect {
                updateNotification()
            }
        }

        // 6. Atualização para episódios de podcasts
        serviceScope.launch {
            playerManager.currentPodcastEpisode.collect {
                updateNotification()
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
            playerManager.playNext()
        }

        override fun seekToNextMediaItem() {
            playerManager.playNext()
        }

        override fun seekToPrevious() {
            playerManager.playPrevious()
        }

        override fun seekToPreviousMediaItem() {
            playerManager.playPrevious()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
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
                playerManager.playNext()
                updateNotification()
            }
            ACTION_PREVIOUS -> {
                playerManager.playPrevious()
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
            station != null -> {
                if (rds.radioText.isNotBlank()) rds.radioText
                else listOfNotNull(
                    station.displayFrequency.ifBlank { null },
                    station.city.ifBlank { null },
                    station.primaryGenre.ifBlank { null }
                ).joinToString(" • ").ifBlank { "Ao Vivo • Streaming HD" }
            }
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
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
            .build()
    }

    private fun updateNotification() {
        try {
            val notification = buildMediaNotification()
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
                if (!it.isHeld) it.acquire(4 * 60 * 60 * 1000L) // 4 horas para streaming contínuo sem cortes
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
        serviceScope.launch {
            mediaLibrarySession?.run {
                player.release()
                release()
                mediaLibrarySession = null
            }
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
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()

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
                Player.COMMAND_SEEK_TO_NEXT -> {
                    playerManager.playNext()
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS -> {
                    playerManager.playPrevious()
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
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
                putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // LIST/TABS
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)  // LIST
                putInt("android.media.browse.CONTENT_STYLE_SUPPORTED", 1)
            }
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("MediaPod + Radio / Podcast")
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
                    val children = listOf(
                        createFolderItem(ROOT_RADIO, "📻 Rádios", "Emissoras ao vivo e gêneros"),
                        createFolderItem(ROOT_PODCASTS, "🎙️ Podcasts", "Programas, notícias e episódios"),
                        createFolderItem(ROOT_FAVORITES, "⭐ Favoritos Rápidos", "Acesso direto"),
                        createFolderItem(ROOT_RECENT, "🕒 Recentes Rápidos", "Últimas ouvidas")
                    )
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                    )
                }

                ROOT_RADIO, ROOT_ALL -> {
                    val children = listOf(
                        createFolderItem(RADIO_FAVORITES, "⭐ Rádios Favoritas", "Estações salvas"),
                        createFolderItem(RADIO_RECENTS, "🕒 Recentes", "Últimas emissoras ouvidas"),
                        createFolderItem(RADIO_TOP_BRAZIL, "🇧🇷 Top Brasil", "Emissoras mais ouvidas do Brasil"),
                        createFolderItem(RADIO_STATES, "🏛️ Estados (UFs do Brasil)", "Navegação por estado brasileiro"),
                        createFolderItem(RADIO_TOP_WORLD, "🌍 Top Mundial", "Emissoras mais ouvidas do mundo"),
                        createFolderItem(RADIO_GENRES, "🎵 Gêneros Musicais", "39 estilos, notícias e ritmos"),
                        createFolderItem(RADIO_CUSTOM, "📻 Minhas Rádios", "Emissoras personalizadas")
                    )
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                    )
                }

                RADIO_STATES -> {
                    val items = mutableListOf<MediaItem>()
                    items.add(createFolderItem("state_ALL", "🇧🇷 Todas as UFs", "1.321 emissoras de todo o Brasil"))
                    CuratedData.BRAZILIAN_STATES.filter { it.first.isNotBlank() }.forEach { (uf, label) ->
                        items.add(createFolderItem("state_$uf", "📍 $label", "Emissoras de $uf"))
                    }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                ROOT_PODCASTS -> {
                    val children = listOf(
                        createFolderItem(PODCAST_FAVORITES, "⭐ Podcasts Favoritos", "Seus programas salvos"),
                        createFolderItem(PODCAST_RECENTS, "🕒 Episódios Recentes", "Últimos reproduzidos"),
                        createFolderItem(PODCAST_TOP_BRAZIL, "🇧🇷 Top Podcasts Brasil", "Top 50 programas do Brasil"),
                        createFolderItem(PODCAST_TOP_WORLD, "🌍 Top Podcasts Mundo", "Top 50 programas globais"),
                        createFolderItem(PODCAST_CATEGORIES, "📂 Categorias", "Notícias, tecnologia, cultura"),
                        createFolderItem(PODCAST_CUSTOM, "🎙️ Meus Podcasts", "Feeds e programas personalizados")
                    )
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
                    )
                }

                RADIO_FAVORITES, ROOT_FAVORITES -> {
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

                RADIO_RECENTS, ROOT_RECENT -> {
                    val prefs = IpodPreferencesManager.getInstance(applicationContext)
                    val recents = prefs.getRecentStations().take(25)
                    val items = recents.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                RADIO_TOP_BRAZIL, SUB_BRAZIL -> {
                    val list = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                        it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true)
                    }.take(100)
                    val items = list.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                RADIO_TOP_WORLD, SUB_WORLD -> {
                    val list = CuratedData.CURATED_GLOBAL_STATIONS.take(35)
                    val items = list.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                RADIO_GENRES, SUB_GENRES -> {
                    val items = CuratedData.GENRES.map { genre ->
                        createFolderItem("genre_${genre.tag}", "${genre.iconEmoji} ${genre.name}", genre.description)
                    }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                RADIO_CUSTOM -> {
                    val prefs = IpodPreferencesManager.getInstance(applicationContext)
                    val customStations = prefs.getCustomStations()
                    val items = customStations.map { createStationItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                PODCAST_FAVORITES -> {
                    val favs = podcastRepository.favoritesFlow.value
                    val items = favs.map { createPodcastShowFolder(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                PODCAST_RECENTS -> {
                    val recents = podcastRepository.recentEpisodesFlow.value
                    val items = recents.map { createPodcastEpisodeItem(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                PODCAST_TOP_BRAZIL -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val list = podcastRepository.getTopPodcasts("BR", 100)
                            val items = list.map { createPodcastShowFolder(it) }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                PODCAST_TOP_WORLD -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val list = podcastRepository.getTopPodcasts("GLOBAL", 100)
                            val items = list.map { createPodcastShowFolder(it) }
                            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        } catch (e: Exception) {
                            future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                        }
                    }
                    return future
                }

                PODCAST_CATEGORIES -> {
                    val items = podcastRepository.categories.map { cat ->
                        createFolderItem("podcat_${cat.id}", cat.name, cat.description)
                    }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                PODCAST_CUSTOM -> {
                    val customShows = podcastRepository.customShowsFlow.value
                    val items = customShows.map { createPodcastShowFolder(it) }
                    return Futures.immediateFuture(
                        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                    )
                }

                else -> {
                    if (parentId.startsWith("state_")) {
                        val uf = parentId.removePrefix("state_")
                        val list = if (uf.equals("ALL", ignoreCase = true)) {
                            CuratedData.CURATED_GLOBAL_STATIONS.filter {
                                it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true)
                            }.take(100)
                        } else {
                            CuratedData.CURATED_GLOBAL_STATIONS.filter {
                                (it.countryCode.equals("BR", ignoreCase = true) || it.country.contains("Brasil", ignoreCase = true)) &&
                                com.example.util.RadioSearchEngine.matchesUf(it, uf)
                            }
                        }
                        val items = list.map { createStationItem(it) }
                        return Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                        )
                    }

                    if (parentId.startsWith("genre_")) {
                        val tag = parentId.removePrefix("genre_")
                        val list = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                            com.example.util.RadioSearchEngine.matchesGenre(it, tag)
                        }
                        val items = list.map { createStationItem(it) }
                        return Futures.immediateFuture(
                            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                        )
                    }

                    if (parentId.startsWith("podcat_")) {
                        val catId = parentId.removePrefix("podcat_")
                        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val list = podcastRepository.getPodcastsByCategory(catId)
                                val items = list.map { createPodcastShowFolder(it) }
                                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            } catch (e: Exception) {
                                future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                            }
                        }
                        return future
                    }

                    if (parentId.startsWith("podshow_")) {
                        val showId = parentId.removePrefix("podshow_")
                        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val allShows = podcastRepository.getTopPodcasts("BR", 100) +
                                        podcastRepository.getTopPodcasts("GLOBAL", 100) +
                                        podcastRepository.customShowsFlow.value
                                val targetShow = allShows.firstOrNull { it.id == showId }
                                if (targetShow != null) {
                                    val episodes = podcastRepository.getEpisodes(targetShow)
                                    val items = episodes.take(30).map { createPodcastEpisodeItem(it) }
                                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                                } else {
                                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("RadioMediaService", "Error loading episodes for AA: $showId", e)
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
                val streamUrl = target.requestMetadata.mediaUri?.toString() ?: mediaId

                // Verifica se é um episódio de Podcast
                if (mediaId.startsWith("ep_")) {
                    val episode = com.example.data.model.PodcastEpisode(
                        id = mediaId,
                        showId = "",
                        showTitle = target.mediaMetadata.artist?.toString() ?: "Podcast",
                        title = target.mediaMetadata.title?.toString() ?: "Episódio",
                        description = target.mediaMetadata.subtitle?.toString() ?: "",
                        audioUrl = streamUrl,
                        artworkUrl = target.mediaMetadata.artworkUri?.toString() ?: "",
                        publishDate = target.mediaMetadata.albumTitle?.toString() ?: ""
                    )
                    playerManager.playPodcastEpisode(episode)
                } else {
                    // É uma estação de rádio
                    val match = CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull { it.id == mediaId }
                        ?: IpodPreferencesManager.getInstance(applicationContext).getRecentStations().firstOrNull { it.id == mediaId }
                        ?: IpodPreferencesManager.getInstance(applicationContext).getCustomStations().firstOrNull { it.id == mediaId }
                        ?: RadioStation(
                            id = mediaId,
                            name = target.mediaMetadata.title?.toString() ?: "Rádio",
                            streamUrl = streamUrl,
                            alternativeStreamUrls = emptyList(),
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
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val currentEpisode = playerManager.currentPodcastEpisode.value
            if (currentEpisode != null) {
                playerManager.playPodcastEpisode(currentEpisode)
                val item = createPodcastEpisodeItem(currentEpisode)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0)
                )
            }

            val current = playerManager.currentStation.value
                ?: IpodPreferencesManager.getInstance(applicationContext).getLastPlayedStation()

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

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch(Dispatchers.Default) {
                val results = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                    com.example.util.RadioSearchEngine.matchesMultiToken(it, query)
                }
                session.notifySearchResultChanged(browser, query, results.size, params)
                val topMatch = results.firstOrNull()
                if (topMatch != null) {
                    playerManager.playStation(topMatch)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val list = CuratedData.CURATED_GLOBAL_STATIONS.filter {
                com.example.util.RadioSearchEngine.matchesMultiToken(it, query)
            }.take(50)
            val items = list.map { createStationItem(it) }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
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

        private fun createPodcastShowFolder(show: com.example.data.model.PodcastShow): MediaItem {
            val podcastIconUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.drawable.ic_podcast_retro}")
            val extras = Bundle().apply {
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }
            return MediaItem.Builder()
                .setMediaId("podshow_${show.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(show.title)
                        .setSubtitle("${show.author} • ${show.displayCountry}")
                        .setArtworkUri(podcastIconUri)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        private fun createPodcastEpisodeItem(episode: com.example.data.model.PodcastEpisode): MediaItem {
            val podcastIconUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.drawable.ic_podcast_retro}")
            return MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(episode.audioUrl)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(Uri.parse(episode.audioUrl))
                        .build()
                )
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(episode.showTitle)
                        .setAlbumTitle(episode.publishDate)
                        .setArtworkUri(podcastIconUri)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
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

            val radioIconUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.drawable.ic_radio_retro}")

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
                        .setArtworkUri(radioIconUri)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .build()
                )
                .build()
        }
    }
}
