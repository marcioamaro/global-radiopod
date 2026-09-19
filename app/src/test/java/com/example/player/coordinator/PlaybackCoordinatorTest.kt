package com.example.player.coordinator

import com.example.player.ActiveMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackCoordinatorTest {

    private lateinit var coordinator: FakePlaybackCoordinator

    private val radioStationItem = PlaybackQueueItem(
        id = "br_sp_antena1",
        mediaUri = "https://stream.antena1.com.br/stream",
        title = "Antena 1 FM",
        subtitle = "São Paulo",
        mediaType = ActiveMediaType.LIVE_RADIO,
        isLiveStream = true
    )

    private val podcastItem1 = PlaybackQueueItem(
        id = "podcast_ep_01",
        mediaUri = "https://feeds.example.com/ep01.mp3",
        title = "Episódio 1 - Arquitetura Android",
        subtitle = "Android Podcast",
        mediaType = ActiveMediaType.PODCAST_EPISODE,
        durationMs = 1800000L, // 30min
        isLiveStream = false
    )

    private val podcastItem2 = PlaybackQueueItem(
        id = "podcast_ep_02",
        mediaUri = "https://feeds.example.com/ep02.mp3",
        title = "Episódio 2 - Media3 & Cast",
        subtitle = "Android Podcast",
        mediaType = ActiveMediaType.PODCAST_EPISODE,
        durationMs = 2400000L, // 40min
        isLiveStream = false
    )

    @Before
    fun setUp() {
        coordinator = FakePlaybackCoordinator()
    }

    @Test
    fun testInitialStateIsIdleAndSpeaker() {
        val state = coordinator.state.value
        assertEquals(PlaybackStatus.IDLE, state.status)
        assertEquals(AudioPlaybackRoute.SPEAKER, state.route)
        assertEquals(QueueRepeatMode.OFF, state.repeatMode)
        assertNull(state.currentItem)
        assertEquals(0, state.queue.size)
    }

    @Test
    fun testPlayLiveRadioStreamResetsPositionAndDurationToZero() {
        coordinator.playItem(radioStationItem)

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals("br_sp_antena1", state.currentItem?.id)
        assertTrue(state.currentItem?.isLiveStream == true)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(1, state.queue.size)
        assertEquals(0, state.queueIndex)
    }

    @Test
    fun testPlayOnDemandPodcastSetsDuration() {
        coordinator.playItem(podcastItem1)

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals("podcast_ep_01", state.currentItem?.id)
        assertFalse(state.currentItem?.isLiveStream == true)
        assertEquals(1800000L, state.durationMs)
    }

    @Test
    fun testPauseRetainsPosition() {
        coordinator.playItem(podcastItem1)
        coordinator.seekTo(15000L)
        coordinator.pause()

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.PAUSED, state.status)
        assertEquals(15000L, state.positionMs)
        assertFalse(state.isPlaying)
    }

    @Test
    fun testSeekOnLiveStreamIsIgnored() {
        coordinator.playItem(radioStationItem)
        coordinator.seekTo(30000L)

        val state = coordinator.state.value
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun testSeekOnDemandPodcastClampsToDuration() {
        coordinator.playItem(podcastItem1)
        coordinator.seekTo(100000L)
        assertEquals(100000L, coordinator.state.value.positionMs)

        coordinator.seekTo(5000000L) // além do fim
        assertEquals(1800000L, coordinator.state.value.positionMs)
    }

    @Test
    fun testQueueNavigationRepeatOff() {
        val queue = listOf(podcastItem1, podcastItem2)
        coordinator.playItem(podcastItem1, queue)

        assertTrue(coordinator.state.value.hasNext)
        assertFalse(coordinator.state.value.hasPrevious)

        coordinator.skipToNext()
        assertEquals("podcast_ep_02", coordinator.state.value.currentItem?.id)
        assertEquals(1, coordinator.state.value.queueIndex)
        assertFalse(coordinator.state.value.hasNext)
        assertTrue(coordinator.state.value.hasPrevious)

        // Próximo no fim da fila com Repeat OFF não faz nada
        coordinator.skipToNext()
        assertEquals("podcast_ep_02", coordinator.state.value.currentItem?.id)
    }

    @Test
    fun testQueueNavigationRepeatAllWrapsAround() {
        val queue = listOf(podcastItem1, podcastItem2)
        coordinator.playItem(podcastItem2, queue)
        coordinator.setRepeatMode(QueueRepeatMode.ALL)

        assertTrue(coordinator.state.value.hasNext)
        coordinator.skipToNext()
        assertEquals("podcast_ep_01", coordinator.state.value.currentItem?.id)
        assertEquals(0, coordinator.state.value.queueIndex)
    }

    @Test
    fun testQueueNavigationRepeatOneMaintainsCurrentItem() {
        val queue = listOf(podcastItem1, podcastItem2)
        coordinator.playItem(podcastItem1, queue)
        coordinator.setRepeatMode(QueueRepeatMode.ONE)

        coordinator.skipToNext()
        assertEquals("podcast_ep_01", coordinator.state.value.currentItem?.id)
    }

    @Test
    fun testSkipPreviousRestartsTrackIfPlayedMoreThan3Seconds() {
        val queue = listOf(podcastItem1, podcastItem2)
        coordinator.playItem(podcastItem2, queue)
        coordinator.seekTo(10000L) // 10 segundos de playback

        coordinator.skipToPrevious()
        // Deve reiniciar a faixa atual (posição 0L), sem voltar para item1
        assertEquals("podcast_ep_02", coordinator.state.value.currentItem?.id)
        assertEquals(0L, coordinator.state.value.positionMs)
    }

    @Test
    fun testAudioRouteSwitching() {
        coordinator.playItem(radioStationItem)
        coordinator.setAudioRoute(AudioPlaybackRoute.GOOGLE_CAST)

        assertEquals(AudioPlaybackRoute.GOOGLE_CAST, coordinator.state.value.route)
        assertEquals("ROUTE_CHANGE", coordinator.state.value.lastChangeCause)

        coordinator.setAudioRoute(AudioPlaybackRoute.BLUETOOTH)
        assertEquals(AudioPlaybackRoute.BLUETOOTH, coordinator.state.value.route)
    }

    @Test
    fun testClassifiedErrorAndRecovery() {
        coordinator.playItem(radioStationItem)
        val error = ClassifiedPlaybackError(
            kind = PlaybackErrorKind.NETWORK_DISCONNECTED,
            message = "Conexão de rede perdida ao carregar stream",
            isRecoverable = true
        )
        coordinator.simulateError(error)

        val state = coordinator.state.value
        assertEquals(PlaybackStatus.ERROR, state.status)
        assertNotNull(state.error)
        assertEquals(PlaybackErrorKind.NETWORK_DISCONNECTED, state.error?.kind)
        assertTrue(state.error?.isRecoverable == true)

        // Retry deve tentar reproduzir novamente
        coordinator.retry()
        assertEquals(PlaybackStatus.PLAYING, coordinator.state.value.status)
        assertNull(coordinator.state.value.error)
    }
}
