package com.example.player.context

import com.example.player.coordinator.PlaybackQueueItem

/**
 * Encapsula o contexto ativo de reprodução, armazenando a origem da fila,
 * um identificador opcional de consulta/pasta e a lista imutável de itens.
 */
data class NavigationContext(
    val source: QueueSource = QueueSource.GLOBAL,
    val queryId: String? = null,
    val items: List<PlaybackQueueItem> = emptyList()
) {
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    val isNotEmpty: Boolean get() = items.isNotEmpty()

    fun findIndex(itemId: String): Int = items.indexOfFirst { it.id == itemId }

    fun getItem(index: Int): PlaybackQueueItem? = items.getOrNull(index)

    fun getNextItem(currentIndex: Int): PlaybackQueueItem? {
        if (items.isEmpty()) return null
        val nextIdx = (currentIndex + 1) % items.size
        return items[nextIdx]
    }

    fun getPreviousItem(currentIndex: Int): PlaybackQueueItem? {
        if (items.isEmpty()) return null
        val prevIdx = if (currentIndex > 0) currentIndex - 1 else items.size - 1
        return items[prevIdx]
    }
}
