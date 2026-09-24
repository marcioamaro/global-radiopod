package com.marcioamaro.mediapod.player.coordinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.player.ActiveMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QueuePersistenceTest {

    private lateinit var context: Context
    private lateinit var persistence: QueuePersistenceManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        persistence = QueuePersistenceManager.getInstance(context)
        persistence.clearQueue()
        persistence.clearHistory()
    }

    @Test
    fun testSaveAndLoadQueueSnapshot_mixedRadioAndPodcast() {
        val radioItem = PlaybackQueueItem(
            id = "radio_cbn",
            mediaUri = "https://stream.cbn.com.br/live",
            title = "CBN São Paulo",
            subtitle = "São Paulo • Notícias",
            artworkUri = "https://cbn.com.br/logo.png",
            mediaType = ActiveMediaType.LIVE_RADIO,
            durationMs = null,
            isLiveStream = true
        )

        val podcastItem = PlaybackQueueItem(
            id = "pod_nerdcast_100",
            mediaUri = "https://jovemnerd.com.br/nerdcast_100.mp3",
            title = "NerdCast 100",
            subtitle = "Jovem Nerd",
            artworkUri = "https://jovemnerd.com.br/logo.png",
            mediaType = ActiveMediaType.PODCAST_EPISODE,
            durationMs = 3600000L,
            isLiveStream = false
        )

        val localItem = PlaybackQueueItem(
            id = "local_track_1",
            mediaUri = "content://media/external/audio/media/123",
            title = "Bohemian Rhapsody",
            subtitle = "Queen",
            artworkUri = null,
            mediaType = ActiveMediaType.LOCAL_AUDIO,
            durationMs = 354000L,
            isLiveStream = false
        )

        val queue = listOf(radioItem, podcastItem, localItem)

        val saved = persistence.saveQueueSnapshot(
            items = queue,
            currentIndex = 1,
            positionMs = 120000L,
            repeatMode = QueueRepeatMode.ALL,
            isShuffle = true
        )
        assertTrue("A gravação da fila deve retornar true", saved)

        val restored = persistence.loadQueueSnapshot()
        assertEquals(3, restored.items.size)
        assertEquals(1, restored.currentIndex)
        assertEquals(120000L, restored.positionMs)
        assertEquals(QueueRepeatMode.ALL, restored.repeatMode)
        assertTrue(restored.isShuffle)

        // Verificar restauração dos itens mistos
        assertEquals("radio_cbn", restored.items[0].id)
        assertTrue(restored.items[0].isLiveStream)
        assertEquals("pod_nerdcast_100", restored.items[1].id)
        assertEquals(3600000L, restored.items[1].durationMs)
        assertEquals("local_track_1", restored.items[2].id)
        assertEquals(ActiveMediaType.LOCAL_AUDIO, restored.items[2].mediaType)
    }

    @Test
    fun testHistory_deduplicationAndOrder() {
        val item1 = PlaybackQueueItem(id = "item_1", mediaUri = "uri1", title = "Faixa 1")
        val item2 = PlaybackQueueItem(id = "item_2", mediaUri = "uri2", title = "Faixa 2")
        val item3 = PlaybackQueueItem(id = "item_3", mediaUri = "uri3", title = "Faixa 3")

        persistence.addToHistory(item1)
        persistence.addToHistory(item2)
        persistence.addToHistory(item3)

        var history = persistence.getHistory()
        assertEquals(3, history.size)
        assertEquals("item_3", history[0].id)
        assertEquals("item_2", history[1].id)
        assertEquals("item_1", history[2].id)

        // Re-ouvir item1: deve ser movido para o topo sem duplicar
        persistence.addToHistory(item1)
        history = persistence.getHistory()
        assertEquals(3, history.size)
        assertEquals("item_1", history[0].id)
        assertEquals("item_3", history[1].id)
        assertEquals("item_2", history[2].id)
    }

    @Test
    fun testClearQueueAndHistory() {
        val item = PlaybackQueueItem(id = "test", mediaUri = "uri", title = "Teste")
        persistence.saveQueueSnapshot(listOf(item), 0)
        persistence.addToHistory(item)

        assertEquals(1, persistence.loadQueueSnapshot().items.size)
        assertEquals(1, persistence.getHistory().size)

        persistence.clearQueue()
        assertEquals(0, persistence.loadQueueSnapshot().items.size)
        assertEquals(1, persistence.getHistory().size) // Fila limpa não apaga histórico

        persistence.clearHistory()
        assertEquals(0, persistence.getHistory().size)
    }

    @Test
    fun testCoordinatorQueueOperations_addToQueueAndPlayNext() {
        val coordinator = DefaultPlaybackCoordinator.getInstance(context)
        coordinator.clearQueue()

        val itemA = PlaybackQueueItem(id = "A", mediaUri = "uri_a", title = "Item A")
        val itemB = PlaybackQueueItem(id = "B", mediaUri = "uri_b", title = "Item B")
        val itemC = PlaybackQueueItem(id = "C", mediaUri = "uri_c", title = "Item C")

        coordinator.playItem(itemA, listOf(itemA))
        assertEquals(1, coordinator.state.value.queue.size)
        assertEquals(0, coordinator.state.value.queueIndex)

        // Adiciona ao final da fila
        coordinator.addToQueue(itemC)
        assertEquals(2, coordinator.state.value.queue.size)
        assertEquals("C", coordinator.state.value.queue[1].id)

        // Tocar depois (imediato após o atual A)
        coordinator.playNextInQueue(itemB)
        val queue = coordinator.state.value.queue
        assertEquals(3, queue.size)
        assertEquals("A", queue[0].id)
        assertEquals("B", queue[1].id)
        assertEquals("C", queue[2].id)

        // Mover C para o meio
        coordinator.moveQueueItem(2, 1)
        val movedQueue = coordinator.state.value.queue
        assertEquals("A", movedQueue[0].id)
        assertEquals("C", movedQueue[1].id)
        assertEquals("B", movedQueue[2].id)

        // Remover C
        coordinator.removeFromQueue(1)
        val finalQueue = coordinator.state.value.queue
        assertEquals(2, finalQueue.size)
        assertEquals("A", finalQueue[0].id)
        assertEquals("B", finalQueue[1].id)
    }
}
