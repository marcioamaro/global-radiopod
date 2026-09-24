package com.marcioamaro.mediapod.player.coordinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.player.ActiveMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes de resiliência e integridade na recuperação de processo morto e restauração de estado (Item 6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackProcessRecoveryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        QueuePersistenceManager.clearInstanceForTesting()
        val manager = QueuePersistenceManager.getInstance(context)
        manager.clearQueue()
        manager.clearHistory()
    }

    @Test
    fun testProcessKillAndStateRestoration() {
        val manager1 = QueuePersistenceManager.getInstance(context)
        val items = listOf(
            PlaybackQueueItem("item_1", "https://stream1.com", "Rádio 1", mediaType = ActiveMediaType.LIVE_RADIO, isLiveStream = true),
            PlaybackQueueItem("item_2", "https://pod.com/2.mp3", "Podcast 2", mediaType = ActiveMediaType.PODCAST_EPISODE, isLiveStream = false),
            PlaybackQueueItem("item_3", "/storage/track3.mp3", "Música Local", mediaType = ActiveMediaType.LOCAL_AUDIO, isLiveStream = false)
        )

        manager1.saveQueueSnapshot(
            items = items,
            currentIndex = 1,
            positionMs = 45000L,
            repeatMode = QueueRepeatMode.ALL,
            isShuffle = true
        )
        manager1.addToHistory(items[0])

        // Fase 2: Simula novo processo lendo os dados gravados no disco
        val manager2 = QueuePersistenceManager.getInstance(context)
        val restoredState = manager2.loadQueueSnapshot()

        assertEquals(3, restoredState.items.size)
        assertEquals(1, restoredState.currentIndex)
        assertEquals(45000L, restoredState.positionMs)
        assertEquals(QueueRepeatMode.ALL, restoredState.repeatMode)
        assertTrue(restoredState.isShuffle)
        assertEquals("item_2", restoredState.items[restoredState.currentIndex].id)

        val history = manager2.getHistory()
        assertEquals(1, history.size)
        assertEquals("item_1", history.first().id)
    }

    @Test
    fun testCorruptedStorageGracefulRecovery() {
        // Grava JSON corrompido no armazenamento
        context.getSharedPreferences("radiopod_persistent_queue", Context.MODE_PRIVATE)
            .edit()
            .putString("key_queue_json", "{ invalid_json ]")
            .putInt("key_current_index", 999)
            .putString("key_repeat_mode", "UNKNOWN_CORRUPTED_VALUE")
            .commit()

        // Garante que a restauração não lance exceção e degrade com segurança
        val manager = QueuePersistenceManager.getInstance(context)
        val restored = manager.loadQueueSnapshot()
        assertTrue(restored.items.isEmpty())
        assertEquals(-1, restored.currentIndex)
        assertEquals(QueueRepeatMode.OFF, restored.repeatMode)
    }

    @Test
    fun testHistoryDeduplicationAcrossRestarts() {
        val manager = QueuePersistenceManager.getInstance(context)
        val item = PlaybackQueueItem("unique_1", "https://radio.org/live", "Notícias", mediaType = ActiveMediaType.LIVE_RADIO)
        manager.addToHistory(item)
        manager.addToHistory(item)
        manager.addToHistory(item)

        val history = manager.getHistory()
        assertEquals(1, history.size)
        assertEquals("unique_1", history.first().id)
    }
}
