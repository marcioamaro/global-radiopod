package com.example.player.coordinator

import com.example.player.context.NavigationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementação simulada e determinística do [PlaybackCoordinator] para testes unitários.
 */
class FakePlaybackCoordinator : PlaybackCoordinator {

    private val _state = MutableStateFlow(UnifiedPlaybackState())
    override val state: StateFlow<UnifiedPlaybackState> = _state.asStateFlow()

    private val _navigationContext = MutableStateFlow(NavigationContext())
    override val navigationContext: StateFlow<NavigationContext> = _navigationContext.asStateFlow()

    override fun setNavigationContext(context: NavigationContext) {
        _navigationContext.value = context
        if (context.items.isNotEmpty()) {
            _state.value = _state.value.copy(queue = context.items)
        }
    }

    override fun play() {
        val current = _state.value
        if (current.currentItem != null) {
            _state.value = current.copy(
                status = PlaybackStatus.PLAYING,
                error = null,
                lastChangeCause = "PLAY"
            )
        }
    }

    override fun pause() {
        val current = _state.value
        if (current.status == PlaybackStatus.PLAYING || current.status == PlaybackStatus.BUFFERING) {
            _state.value = current.copy(
                status = PlaybackStatus.PAUSED,
                lastChangeCause = "PAUSE"
            )
        }
    }

    override fun stop() {
        _state.value = _state.value.copy(
            status = PlaybackStatus.IDLE,
            positionMs = 0L,
            lastChangeCause = "STOP"
        )
    }

    override fun seekTo(positionMs: Long) {
        val current = _state.value
        if (current.currentItem?.isLiveStream == true) {
            // Live streams não suportam seek convencional
            return
        }
        val target = positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(0L))
        _state.value = current.copy(
            positionMs = target,
            lastChangeCause = "SEEK"
        )
    }

    override fun play(item: PlaybackQueueItem) {
        val finalQueue = _navigationContext.value.items.ifEmpty { listOf(item) }
        playItem(item, finalQueue)
    }

    override fun getCurrentItem(): PlaybackQueueItem? {
        return _state.value.currentItem
    }

    override fun playItem(item: PlaybackQueueItem, queue: List<PlaybackQueueItem>) {
        val finalQueue = if (queue.isEmpty()) listOf(item) else queue
        val index = finalQueue.indexOfFirst { it.id == item.id }.let { if (it == -1) 0 else it }

        _state.value = _state.value.copy(
            currentItem = item,
            queue = finalQueue,
            queueIndex = index,
            status = PlaybackStatus.PLAYING,
            positionMs = 0L,
            durationMs = item.durationMs ?: 0L,
            error = null,
            lastChangeCause = "PLAY_ITEM"
        )
    }

    override fun skipToNext() {
        val context = _navigationContext.value
        val currentItem = getCurrentItem()
        if (context.items.isNotEmpty()) {
            val currentIndex = if (currentItem != null) {
                context.items.indexOfFirst { it.mediaId == currentItem.mediaId || it.id == currentItem.id }
            } else -1
            if (currentIndex == -1) {
                play(context.items.first())
                return
            }
            val nextIndex = (currentIndex + 1) % context.items.size
            play(context.items[nextIndex])
            return
        }

        val current = _state.value
        if (current.queue.isEmpty()) return

        val nextIndex = when (current.repeatMode) {
            QueueRepeatMode.ONE -> current.queueIndex
            QueueRepeatMode.ALL -> (current.queueIndex + 1) % current.queue.size
            QueueRepeatMode.OFF -> {
                if (current.queueIndex < current.queue.size - 1) current.queueIndex + 1 else return
            }
        }

        val nextItem = current.queue[nextIndex]
        playItem(nextItem, current.queue)
    }

    override fun skipToPrevious() {
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
            val prevIndex = if (currentIndex == 0) context.items.size - 1 else currentIndex - 1
            play(context.items[prevIndex])
            return
        }

        val current = _state.value
        if (current.queue.isEmpty()) return

        // Se estiver reproduzindo arquivo sob demanda há mais de 3s, reinicia a faixa atual
        if (current.currentItem?.isLiveStream == false && current.positionMs > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = when (current.repeatMode) {
            QueueRepeatMode.ONE -> current.queueIndex
            QueueRepeatMode.ALL -> if (current.queueIndex > 0) current.queueIndex - 1 else current.queue.size - 1
            QueueRepeatMode.OFF -> {
                if (current.queueIndex > 0) current.queueIndex - 1 else return
            }
        }

        val prevItem = current.queue[prevIndex]
        playItem(prevItem, current.queue)
    }

    override fun skipToQueueIndex(index: Int) {
        val current = _state.value
        if (index in current.queue.indices) {
            val item = current.queue[index]
            playItem(item, current.queue)
        }
    }

    override fun setAudioRoute(route: AudioPlaybackRoute) {
        _state.value = _state.value.copy(
            route = route,
            lastChangeCause = "ROUTE_CHANGE"
        )
    }

    override fun setRepeatMode(mode: QueueRepeatMode) {
        _state.value = _state.value.copy(
            repeatMode = mode,
            lastChangeCause = "REPEAT_MODE_CHANGE"
        )
    }

    private val _activeVolume = MutableStateFlow(0.8f)
    override val activeVolume: StateFlow<Float> = _activeVolume.asStateFlow()

    override fun setActiveVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        _activeVolume.value = clamped
        _state.value = _state.value.copy(
            volume = clamped,
            lastChangeCause = "VOLUME_CHANGE"
        )
    }

    override fun setVolume(volume: Float) {
        setActiveVolume(volume)
    }

    override fun retry() {
        val current = _state.value
        if (current.error?.isRecoverable == true && current.currentItem != null) {
            playItem(current.currentItem, current.queue)
        }
    }

    private val fakeHistory = mutableListOf<PlaybackQueueItem>()

    override fun addToQueue(item: PlaybackQueueItem) {
        val current = _state.value
        val newQueue = current.queue + item
        _state.value = current.copy(queue = newQueue)
    }

    override fun playNextInQueue(item: PlaybackQueueItem) {
        val current = _state.value
        val newQueue = current.queue.toMutableList()
        val insertIndex = (current.queueIndex + 1).coerceIn(0, newQueue.size)
        newQueue.add(insertIndex, item)
        _state.value = current.copy(queue = newQueue)
    }

    override fun removeFromQueue(index: Int) {
        val current = _state.value
        if (index in current.queue.indices) {
            val newQueue = current.queue.toMutableList()
            newQueue.removeAt(index)
            val newIndex = if (index < current.queueIndex) current.queueIndex - 1 else current.queueIndex
            _state.value = current.copy(queue = newQueue, queueIndex = newIndex)
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val current = _state.value
        if (fromIndex in current.queue.indices && toIndex in current.queue.indices) {
            val newQueue = current.queue.toMutableList()
            val item = newQueue.removeAt(fromIndex)
            newQueue.add(toIndex, item)
            val newIndex = when (current.queueIndex) {
                fromIndex -> toIndex
                in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) -> {
                    if (fromIndex < toIndex) current.queueIndex - 1 else current.queueIndex + 1
                }
                else -> current.queueIndex
            }
            _state.value = current.copy(queue = newQueue, queueIndex = newIndex)
        }
    }

    override fun clearQueue() {
        val current = _state.value
        _state.value = current.copy(queue = emptyList(), queueIndex = -1)
    }

    override fun setShuffle(enabled: Boolean) {
        val current = _state.value
        if (enabled && current.queue.size > 1) {
            val currentItem = current.currentItem
            val remaining = current.queue.filter { it.id != currentItem?.id }.shuffled()
            val newQueue = if (currentItem != null) listOf(currentItem) + remaining else remaining
            _state.value = current.copy(queue = newQueue, queueIndex = 0)
        }
    }

    override fun getHistory(): List<PlaybackQueueItem> = fakeHistory.toList()


    // Métodos utilitários de simulação para testes
    fun simulateBuffering() {
        _state.value = _state.value.copy(
            status = PlaybackStatus.BUFFERING,
            lastChangeCause = "BUFFERING_EVENT"
        )
    }

    fun simulateError(error: ClassifiedPlaybackError) {
        _state.value = _state.value.copy(
            status = PlaybackStatus.ERROR,
            error = error,
            lastChangeCause = "ERROR_EVENT"
        )
    }

    fun simulateEnded() {
        _state.value = _state.value.copy(
            status = PlaybackStatus.ENDED,
            lastChangeCause = "PLAYBACK_ENDED"
        )
    }
}
