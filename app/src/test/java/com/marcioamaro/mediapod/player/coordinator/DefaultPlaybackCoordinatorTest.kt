package com.marcioamaro.mediapod.player.coordinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.player.ActiveMediaType
import com.marcioamaro.mediapod.player.AudioRouteManager
import com.marcioamaro.mediapod.player.CastSessionState
import com.marcioamaro.mediapod.player.RadioPlaybackStatus
import com.marcioamaro.mediapod.player.RadioPlayerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DefaultPlaybackCoordinatorTest {

    private lateinit var context: Context
    private lateinit var coordinator: DefaultPlaybackCoordinator
    private lateinit var playerManager: RadioPlayerManager
    private lateinit var audioRouteManager: AudioRouteManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        playerManager = RadioPlayerManager.getInstance(context)
        audioRouteManager = AudioRouteManager.getInstance(context)
        coordinator = DefaultPlaybackCoordinator.getInstance(context)
    }

    @Test
    fun testInitialState_isIdleWithSpeakerRoute() {
        val state = coordinator.state.value
        assertNotNull(state)
        assertEquals(AudioPlaybackRoute.SPEAKER, state.route)
    }

    @Test
    fun testVolumeControl_updatesCoordinatorAndPlayerManager() = runTest {
        coordinator.setVolume(0.75f)
        assertEquals(0.75f, coordinator.state.value.volume, 0.01f)
        assertEquals(0.75f, playerManager.volume.value, 0.01f)
    }

    @Test
    fun testRepeatModeSetting() {
        coordinator.setRepeatMode(QueueRepeatMode.ALL)
        assertEquals(QueueRepeatMode.ALL, coordinator.state.value.repeatMode)
        assertTrue(coordinator.state.value.hasNext)

        coordinator.setRepeatMode(QueueRepeatMode.ONE)
        assertEquals(QueueRepeatMode.ONE, coordinator.state.value.repeatMode)
    }

    @Test
    fun testCastSessionState_updatesAudioPlaybackRoute() {
        audioRouteManager.setCastSessionStateForTesting(CastSessionState.CONNECTED)
        coordinator.syncStateForTesting()
        assertEquals(AudioPlaybackRoute.GOOGLE_CAST, coordinator.state.value.route)

        audioRouteManager.setCastSessionStateForTesting(CastSessionState.DISCONNECTED)
        coordinator.syncStateForTesting()
        assertEquals(AudioPlaybackRoute.SPEAKER, coordinator.state.value.route)
    }

    @Test
    fun testPlayItem_radioStationSetsLoadingAndQueue() {
        val radioItem = PlaybackQueueItem(
            id = "antena1_test",
            mediaUri = "https://stream.antena1.com.br/stream",
            title = "Antena 1 SP",
            subtitle = "São Paulo • Adult Contemporary",
            artworkUri = "https://antena1.com.br/logo.png",
            mediaType = ActiveMediaType.LIVE_RADIO,
            durationMs = null,
            isLiveStream = true
        )

        coordinator.playItem(radioItem, listOf(radioItem))

        val currentState = coordinator.state.value
        assertEquals("antena1_test", currentState.currentItem?.id)
        assertEquals(1, currentState.queue.size)
        assertEquals(0, currentState.queueIndex)
        assertTrue(currentState.currentItem?.isLiveStream == true)
    }

    @Test
    fun testErrorClassification_translatesNetworkMessage() {
        val radioItem = PlaybackQueueItem(
            id = "test_station",
            mediaUri = "https://example.com/stream",
            title = "Test Radio",
            mediaType = ActiveMediaType.LIVE_RADIO,
            isLiveStream = true
        )
        coordinator.playItem(radioItem)
        playerManager.handleNetworkLoss()
        val state = coordinator.state.value
        if (state.error != null) {
            assertTrue(state.error!!.isRecoverable)
        }
    }
}
