package com.example.player.coordinator

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.LocalAudioTrack
import com.example.data.model.PodcastEpisode
import com.example.data.model.RadioStation
import com.example.player.ActiveMediaType
import com.example.player.AudioRouteManager
import com.example.player.CastSessionState
import com.example.player.RadioPlaybackStatus
import com.example.player.RadioPlayerManager
import kotlinx.coroutines.CoroutineScope
import com.example.player.context.NavigationContext
import com.example.player.context.QueueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * Implementação padrão e concreta do [PlaybackCoordinator].
 * Atua como fonte única de verdade (Single Source of Truth), unificando
 * e orquestrando o estado do [RadioPlayerManager], [AudioRouteManager],
 * fila transacional via [QueuePersistenceManager] e mídias ativas.
 */
class DefaultPlaybackCoordinator internal constructor(
    private val context: Context? = null,
    private val playerManager: RadioPlayerManager? = null,
    private val audioRouteManager: AudioRouteManager? = null,
    private val queuePersistence: QueuePersistenceManager? = null,
    private val downloadManager: com.example.data.download.PodcastDownloadManager? = null,
    private val volumeManager: com.example.audio.VolumeManager? = null,
    coroutineDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main
) : PlaybackCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + coroutineDispatcher)

    private val vm: com.example.audio.VolumeManager? = volumeManager ?: context?.let { com.example.audio.VolumeManager.getInstance(it) }

    private val _fallbackVolume = MutableStateFlow(0.8f)
    override val activeVolume: StateFlow<Float> = vm?.activeVolume ?: _fallbackVolume.asStateFlow()

    private val _state = MutableStateFlow(UnifiedPlaybackState())
    override val state: StateFlow<UnifiedPlaybackState> = _state.asStateFlow()

    private val _navigationContext = MutableStateFlow(NavigationContext())
    override val navigationContext: StateFlow<NavigationContext> = _navigationContext.asStateFlow()

    private var currentQueueList: List<PlaybackQueueItem> = emptyList()
    private var currentRepeatMode: QueueRepeatMode = QueueRepeatMode.OFF
    private var isShuffleActive: Boolean = false

    override fun setNavigationContext(context: NavigationContext) {
        val previousContext = _navigationContext.value
        if (previousContext.source == context.source && previousContext.items == context.items) {
            return
        }
        try {
            Log.d(TAG, "setNavigationContext() chamado: source=${context.source}, items=${context.items.size}")
        } catch (_: Exception) {}
        _navigationContext.value = context
        if (context.items.isNotEmpty()) {
            currentQueueList = context.items
            _state.value = _state.value.copy(queue = context.items)
            persistCurrentQueue()
            playerManager?.updatePlaylist(context.items.map { it.toRadioStation() })
        }
    }

    init {
        if (queuePersistence != null) {
            restorePersistedQueue()
        }
        if (playerManager != null && audioRouteManager != null) {
            observeUnderlyingState()
        }
    }

    private fun restorePersistedQueue() {
        val qp = queuePersistence ?: return
        try {
            val snapshot = qp.loadQueueSnapshot()
            if (snapshot.items.isNotEmpty()) {
                currentQueueList = snapshot.items
                currentRepeatMode = snapshot.repeatMode
                isShuffleActive = snapshot.isShuffle
                val item = if (snapshot.currentIndex in snapshot.items.indices) snapshot.items[snapshot.currentIndex] else snapshot.items.firstOrNull()
                _state.value = _state.value.copy(
                    queue = snapshot.items,
                    queueIndex = snapshot.currentIndex,
                    currentItem = item,
                    repeatMode = snapshot.repeatMode,
                    positionMs = snapshot.positionMs,
                    lastChangeCause = "RESTORE_PERSISTED_QUEUE"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao restaurar fila persistida: ${e.message}")
        }
    }

    private fun persistCurrentQueue(isShuffle: Boolean = isShuffleActive) {
        val qp = queuePersistence ?: return
        try {
            val stateVal = _state.value
            qp.saveQueueSnapshot(
                items = currentQueueList,
                currentIndex = stateVal.queueIndex,
                positionMs = stateVal.positionMs,
                repeatMode = currentRepeatMode,
                isShuffle = isShuffle
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao persistir snapshot da fila: ${e.message}")
        }
    }

    private fun syncState(lastCause: String = "UNDERLYING_SYNC") {
        val pm = playerManager ?: return
        val arm = audioRouteManager
        val status = pm.playbackStatus.value
        val mediaType = pm.activeMediaType.value
        val station = pm.currentStation.value
        val podcast = pm.currentPodcastEpisode.value
        val localAudio = pm.currentLocalAudio.value
        val castState = arm?.castSessionState?.value ?: CastSessionState.DISCONNECTED

        val mappedStatus = when (status) {
            RadioPlaybackStatus.IDLE -> PlaybackStatus.IDLE
            RadioPlaybackStatus.BUFFERING -> PlaybackStatus.BUFFERING
            RadioPlaybackStatus.PLAYING -> PlaybackStatus.PLAYING
            RadioPlaybackStatus.PAUSED -> PlaybackStatus.PAUSED
            RadioPlaybackStatus.ERROR, RadioPlaybackStatus.NO_INTERNET -> PlaybackStatus.ERROR
        }

        val route = when (castState) {
            CastSessionState.CONNECTED, CastSessionState.TRANSFERRING -> AudioPlaybackRoute.GOOGLE_CAST
            else -> AudioPlaybackRoute.SPEAKER
        }

        val activeItem: PlaybackQueueItem? = when (mediaType) {
            ActiveMediaType.LIVE_RADIO -> station?.let { s ->
                val subtitle = if (s.city.isNotBlank()) "${s.city} • ${s.country}" else s.country
                PlaybackQueueItem(
                    id = s.id,
                    mediaUri = s.streamUrl,
                    title = s.name,
                    subtitle = subtitle,
                    artworkUri = s.favicon.ifBlank { null },
                    mediaType = ActiveMediaType.LIVE_RADIO,
                    durationMs = null,
                    isLiveStream = true
                )
            }
            ActiveMediaType.PODCAST_EPISODE -> podcast?.let { p ->
                PlaybackQueueItem(
                    id = p.id,
                    mediaUri = p.audioUrl,
                    title = p.title,
                    subtitle = p.showTitle,
                    artworkUri = p.artworkUrl.ifBlank { null },
                    mediaType = ActiveMediaType.PODCAST_EPISODE,
                    durationMs = p.durationMs,
                    isLiveStream = false
                )
            }
            ActiveMediaType.LOCAL_AUDIO -> localAudio?.let { l ->
                PlaybackQueueItem(
                    id = l.id.toString(),
                    mediaUri = l.contentUri.toString(),
                    title = l.title,
                    subtitle = l.artist,
                    artworkUri = l.albumArtUrl,
                    mediaType = ActiveMediaType.LOCAL_AUDIO,
                    durationMs = l.durationMs,
                    isLiveStream = false
                )
            }
            else -> null
        }

        if (activeItem != null && currentQueueList.isEmpty()) {
            currentQueueList = listOf(activeItem)
        }

        val queueIdx = if (activeItem != null) {
            currentQueueList.indexOfFirst { it.id == activeItem.id }.coerceAtLeast(0)
        } else -1

        _state.value = _state.value.copy(
            currentItem = activeItem ?: _state.value.currentItem,
            status = mappedStatus,
            route = route,
            queue = currentQueueList,
            queueIndex = queueIdx,
            repeatMode = currentRepeatMode,
            volume = pm.volume.value,
            lastChangeCause = lastCause
        )
    }

    @androidx.annotation.VisibleForTesting
    fun syncStateForTesting() {
        syncState("TEST_SYNC")
    }

    private fun observeUnderlyingState() {
        val pm = playerManager ?: return
        val arm = audioRouteManager ?: return
        merge(
            pm.playbackStatus,
            pm.activeMediaType,
            pm.currentStation,
            pm.currentPodcastEpisode,
            pm.currentLocalAudio,
            arm.castSessionState
        ).onEach {
            syncState()
        }.launchIn(scope)

        pm.audioPositionMs.onEach { pos ->
            if (_state.value.currentItem?.isLiveStream == false) {
                _state.value = _state.value.copy(positionMs = pos)
            }
        }.launchIn(scope)

        pm.audioDurationMs.onEach { dur ->
            if (_state.value.currentItem?.isLiveStream == false && dur > 0) {
                _state.value = _state.value.copy(durationMs = dur)
            }
        }.launchIn(scope)

        pm.errorMessage.onEach { errStr ->
            val classified = errStr?.let {
                ClassifiedPlaybackError(
                    kind = if (it.contains("rede", ignoreCase = true) || it.contains("internet", ignoreCase = true)) {
                        PlaybackErrorKind.NETWORK_DISCONNECTED
                    } else if (it.contains("timeout", ignoreCase = true)) {
                        PlaybackErrorKind.STREAM_TIMEOUT
                    } else {
                        PlaybackErrorKind.UNKNOWN
                    },
                    message = it,
                    isRecoverable = true
                )
            }
            _state.value = _state.value.copy(error = classified)
        }.launchIn(scope)
    }

    override fun play() {
        try {
            if (audioRouteManager?.isCastingActive() == true) {
                audioRouteManager.play()
            } else {
                playerManager?.resume()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao executar play(): ${e.message}")
        }
    }

    override fun pause() {
        try {
            if (audioRouteManager?.isCastingActive() == true) {
                audioRouteManager.pause()
            } else {
                playerManager?.pause()
            }
            persistCurrentQueue()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao executar pause(): ${e.message}")
        }
    }

    override fun stop() {
        try {
            playerManager?.stop()
            persistCurrentQueue()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao executar stop(): ${e.message}")
        }
    }

    override fun seekTo(positionMs: Long) {
        val current = _state.value.currentItem
        if (current == null || current.isLiveStream) {
            Log.d(TAG, "seekTo ignorado: item é rádio ao vivo ou nulo")
            return
        }
        val clamped = positionMs.coerceAtLeast(0L)
        when (current.mediaType) {
            ActiveMediaType.PODCAST_EPISODE -> playerManager?.seekToPosition(clamped)
            ActiveMediaType.LOCAL_AUDIO -> playerManager?.seekLocalAudioTo(clamped)
            else -> Unit
        }
        persistCurrentQueue()
    }

    override fun play(item: PlaybackQueueItem) {
        val targetQueue = _navigationContext.value.items.ifEmpty { listOf(item) }
        playItem(item, targetQueue)
    }

    override fun playItem(item: PlaybackQueueItem, queue: List<PlaybackQueueItem>) {
        currentQueueList = queue.ifEmpty { listOf(item) }
        val targetIndex = currentQueueList.indexOfFirst { it.id == item.id }.coerceAtLeast(0)

        when (item.mediaType) {
            ActiveMediaType.LIVE_RADIO -> {
                val station = RadioStation(
                    id = item.id,
                    name = item.title,
                    streamUrl = item.mediaUri,
                    favicon = item.artworkUri ?: "",
                    city = item.subtitle ?: "",
                    country = "Global"
                )
                playerManager?.playStation(station)
            }
            ActiveMediaType.PODCAST_EPISODE -> {
                val localPath = downloadManager?.getLocalFilePath(item.id)
                val targetUri = localPath ?: item.mediaUri
                val episode = PodcastEpisode(
                    id = item.id,
                    showId = "",
                    showTitle = item.subtitle ?: "",
                    title = item.title,
                    audioUrl = targetUri,
                    artworkUrl = item.artworkUri ?: "",
                    durationMs = item.durationMs ?: 0L,
                    localFilePath = localPath
                )
                playerManager?.playPodcastEpisode(episode)
            }
            ActiveMediaType.LOCAL_AUDIO -> {
                val localTrack = LocalAudioTrack(
                    id = item.id.toLongOrNull() ?: 0L,
                    title = item.title,
                    artist = item.subtitle ?: "",
                    album = "",
                    durationMs = item.durationMs ?: 0L,
                    contentUri = Uri.parse(item.mediaUri),
                    filePath = "",
                    folderName = "",
                    albumId = 0L,
                    albumArtUrl = item.artworkUri
                )
                playerManager?.playLocalAudio(localTrack)
            }
            else -> {
                Log.w(TAG, "Tipo de mídia não suportado para playItem: ${item.mediaType}")
            }
        }

        _state.value = _state.value.copy(
            currentItem = item,
            queue = currentQueueList,
            queueIndex = targetIndex,
            status = PlaybackStatus.PLAYING,
            lastChangeCause = "PLAY_ITEM"
        )

        persistCurrentQueue()
        queuePersistence?.addToHistory(item)
    }

    override fun getCurrentItem(): PlaybackQueueItem? {
        return _state.value.currentItem
            ?: playerManager?.currentStation?.value?.toPlaybackQueueItem()
            ?: playerManager?.currentPodcastEpisode?.value?.let { p ->
                PlaybackQueueItem(
                    id = p.id,
                    mediaUri = p.audioUrl,
                    title = p.title,
                    subtitle = p.showTitle,
                    artworkUri = p.artworkUrl.ifBlank { null },
                    mediaType = ActiveMediaType.PODCAST_EPISODE,
                    durationMs = p.durationMs,
                    isLiveStream = false
                )
            }
    }

    override fun skipToNext() {
        try {
            val context = _navigationContext.value
            val currentItem = getCurrentItem()

            if (context.items.isNotEmpty()) {
                val currentIndex = if (currentItem != null) {
                    context.items.indexOfFirst { it.mediaId == currentItem.mediaId || it.id == currentItem.id }
                } else -1

                if (currentIndex == -1) {
                    // Item atual não está no contexto (foi removido?) - toca o primeiro
                    play(context.items.first())
                    return
                }

                // Calcula próximo índice com LOOP CIRCULAR
                val nextIndex = (currentIndex + 1) % context.items.size
                play(context.items[nextIndex])
                return
            }

            if (currentQueueList.isNotEmpty()) {
                val currentIdx = currentQueueList.indexOfFirst { it.id == currentItem?.id }
                    .let { if (it >= 0) it else _state.value.queueIndex.coerceIn(currentQueueList.indices) }
                val nextIdx = (currentIdx + 1) % currentQueueList.size
                play(currentQueueList[nextIdx])
                return
            }
            playerManager?.playNext()
        } catch (e: Exception) {
            Log.w(TAG, "Falha em skipToNext(): ${e.message}")
        }
    }

    override fun skipToPrevious() {
        try {
            val context = _navigationContext.value
            val currentItem = getCurrentItem()

            if (context.items.isNotEmpty()) {
                val currentIndex = if (currentItem != null) {
                    context.items.indexOfFirst { it.mediaId == currentItem.mediaId || it.id == currentItem.id }
                } else -1

                if (currentIndex == -1) {
                    play(context.items.last())
                    return
                }

                // Loop reverso: se está no 0, vai para o último
                val prevIndex = if (currentIndex == 0) context.items.size - 1 else currentIndex - 1
                play(context.items[prevIndex])
                return
            }

            if (currentQueueList.isNotEmpty()) {
                if (currentItem?.isLiveStream == false && _state.value.positionMs > 3000L) {
                    seekTo(0L)
                    return
                }
                val currentIdx = currentQueueList.indexOfFirst { it.id == currentItem?.id }
                    .let { if (it >= 0) it else _state.value.queueIndex.coerceIn(currentQueueList.indices) }
                val prevIdx = if (currentIdx == 0) currentQueueList.size - 1 else currentIdx - 1
                play(currentQueueList[prevIdx])
                return
            }
            playerManager?.playPrevious()
        } catch (e: Exception) {
            Log.w(TAG, "Falha em skipToPrevious(): ${e.message}")
        }
    }

    override fun skipToQueueIndex(index: Int) {
        if (index in currentQueueList.indices) {
            playItem(currentQueueList[index], currentQueueList)
        }
    }

    override fun addToQueue(item: PlaybackQueueItem) {
        currentQueueList = currentQueueList + item
        _state.value = _state.value.copy(queue = currentQueueList)
        persistCurrentQueue()
    }

    override fun playNextInQueue(item: PlaybackQueueItem) {
        val list = currentQueueList.toMutableList()
        val insertIndex = (_state.value.queueIndex + 1).coerceIn(0, list.size)
        list.add(insertIndex, item)
        currentQueueList = list
        _state.value = _state.value.copy(queue = currentQueueList)
        persistCurrentQueue()
    }

    override fun removeFromQueue(index: Int) {
        if (index in currentQueueList.indices) {
            val list = currentQueueList.toMutableList()
            list.removeAt(index)
            currentQueueList = list
            val currentIdx = _state.value.queueIndex
            val newIndex = if (index < currentIdx) currentIdx - 1 else currentIdx
            _state.value = _state.value.copy(queue = currentQueueList, queueIndex = newIndex)
            persistCurrentQueue()
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentQueueList.indices && toIndex in currentQueueList.indices) {
            val list = currentQueueList.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            currentQueueList = list
            val currentIdx = _state.value.queueIndex
            val newIdx = when (currentIdx) {
                fromIndex -> toIndex
                in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) -> {
                    if (fromIndex < toIndex) currentIdx - 1 else currentIdx + 1
                }
                else -> currentIdx
            }
            _state.value = _state.value.copy(queue = currentQueueList, queueIndex = newIdx)
            persistCurrentQueue()
        }
    }

    override fun clearQueue() {
        currentQueueList = emptyList()
        _state.value = _state.value.copy(queue = emptyList(), queueIndex = -1)
        queuePersistence?.clearQueue()
    }

    override fun setShuffle(enabled: Boolean) {
        isShuffleActive = enabled
        if (enabled && currentQueueList.size > 1) {
            val current = _state.value.currentItem
            val remaining = currentQueueList.filter { it.id != current?.id }.shuffled()
            currentQueueList = if (current != null) listOf(current) + remaining else remaining
            _state.value = _state.value.copy(queue = currentQueueList, queueIndex = 0)
            persistCurrentQueue(isShuffle = true)
        }
    }

    override fun getHistory(): List<PlaybackQueueItem> = queuePersistence?.getHistory() ?: emptyList()

    override fun setAudioRoute(route: AudioPlaybackRoute) {
        when (route) {
            AudioPlaybackRoute.GOOGLE_CAST -> {
                Log.i(TAG, "Solicitando rota Cast")
            }
            AudioPlaybackRoute.SPEAKER, AudioPlaybackRoute.BLUETOOTH, AudioPlaybackRoute.WIRED_HEADSET -> {
                if (audioRouteManager?.isCastingActive() == true) {
                    audioRouteManager.transferPlaybackToLocal()
                }
            }
        }
    }

    override fun setRepeatMode(mode: QueueRepeatMode) {
        currentRepeatMode = mode
        _state.value = _state.value.copy(repeatMode = mode)
        persistCurrentQueue()
    }

    override fun setActiveVolume(volume: Float) {
        val manager = vm
        val clamped = volume.coerceIn(0f, 1f)
        if (manager != null) {
            manager.setActiveVolume(clamped)
            if (manager.activeRoute.value == com.example.audio.VolumeManager.AudioRoute.LOCAL) {
                playerManager?.setVolumeLevel(clamped)
            } else {
                audioRouteManager?.setCastVolume(clamped)
            }
        } else {
            _fallbackVolume.value = clamped
            playerManager?.setVolumeLevel(clamped)
        }
        _state.value = _state.value.copy(volume = clamped)
    }

    override fun setVolume(volume: Float) {
        setActiveVolume(volume)
    }

    override fun retry() {
        playerManager?.retryPlayback()
    }

    companion object {
        private const val TAG = "DefaultPlaybackCoord"

        @Volatile
        private var INSTANCE: DefaultPlaybackCoordinator? = null

        fun getInstance(context: Context): DefaultPlaybackCoordinator {
            return INSTANCE ?: synchronized(this) {
                val instance = DefaultPlaybackCoordinator(
                    context = context.applicationContext,
                    playerManager = RadioPlayerManager.getInstance(context.applicationContext),
                    audioRouteManager = AudioRouteManager.getInstance(context.applicationContext),
                    queuePersistence = QueuePersistenceManager.getInstance(context.applicationContext),
                    downloadManager = com.example.data.download.PodcastDownloadManager.getInstance(context.applicationContext),
                    volumeManager = com.example.audio.VolumeManager.getInstance(context.applicationContext)
                )
                INSTANCE = instance
                instance
            }
        }

        fun createForTest(
            playerManager: RadioPlayerManager? = null,
            audioRouteManager: AudioRouteManager? = null,
            queuePersistence: QueuePersistenceManager? = null,
            downloadManager: com.example.data.download.PodcastDownloadManager? = null,
            volumeManager: com.example.audio.VolumeManager? = null
        ): DefaultPlaybackCoordinator {
            return DefaultPlaybackCoordinator(
                context = null,
                playerManager = playerManager,
                audioRouteManager = audioRouteManager,
                queuePersistence = queuePersistence,
                downloadManager = downloadManager,
                volumeManager = volumeManager,
                coroutineDispatcher = Dispatchers.Unconfined
            )
        }
    }
}
