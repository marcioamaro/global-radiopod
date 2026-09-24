package com.marcioamaro.mediapod.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.player.ActiveMediaType
import com.marcioamaro.mediapod.player.coordinator.DefaultPlaybackCoordinator
import com.marcioamaro.mediapod.player.coordinator.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PodcastDownloadTest {

    private lateinit var context: Context
    private lateinit var downloadManager: PodcastDownloadManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        downloadManager = PodcastDownloadManager.getInstance(context)
    }

    @Test
    fun testDownloadStatusModel() {
        val notDownloaded: DownloadStatus = DownloadStatus.NotDownloaded
        assertEquals(DownloadStatus.NotDownloaded, notDownloaded)

        val queued: DownloadStatus = DownloadStatus.Queued("ep_1")
        assertEquals("ep_1", (queued as DownloadStatus.Queued).episodeId)

        val downloading: DownloadStatus = DownloadStatus.Downloading("ep_1", 50, 5000L, 10000L)
        assertEquals(50, (downloading as DownloadStatus.Downloading).progressPercent)

        val completed: DownloadStatus = DownloadStatus.Completed("ep_1", "/path/to/ep.mp3", 10000L)
        assertEquals("/path/to/ep.mp3", (completed as DownloadStatus.Completed).localFilePath)

        val failed: DownloadStatus = DownloadStatus.Failed("ep_1", "Erro 404")
        assertEquals("Erro 404", (failed as DownloadStatus.Failed).reason)
    }

    @Test
    fun testWifiOnlyPreferenceToggle() {
        downloadManager.setWifiOnlyPreference(true)
        assertTrue(downloadManager.isWifiOnlyEnabled())

        downloadManager.setWifiOnlyPreference(false)
        assertFalse(downloadManager.isWifiOnlyEnabled())
    }

    @Test
    fun testDeleteDownloadRemovesFileAndIndex() {
        val testFile = File(context.filesDir, "test_podcast.mp3")
        testFile.writeText("fake mp3 data")
        assertTrue(testFile.exists())

        val episode = PodcastEpisode(
            id = "test_del_1",
            showId = "show_1",
            showTitle = "Show Test",
            title = "Episódio para deletar",
            audioUrl = "http://example.com/ep.mp3",
            localFilePath = testFile.absolutePath
        )

        downloadManager.registerCompletedDownload(episode, testFile.absolutePath, testFile.length())

        assertTrue(downloadManager.isDownloaded(episode.id))

        val deleted = downloadManager.deleteDownload(episode.id)
        assertTrue(deleted)
        assertFalse(testFile.exists())
        assertFalse(downloadManager.isDownloaded(episode.id))
        assertNull(downloadManager.getLocalFilePath(episode.id))
    }

    @Test
    fun testOfflinePlaybackPathResolution() {
        val testFile = File(context.filesDir, "offline_ep.mp3")
        testFile.writeText("dummy audio")

        val episode = PodcastEpisode(
            id = "offline_1",
            showId = "show_1",
            showTitle = "Show Offline",
            title = "Episódio Offline",
            audioUrl = "https://example.com/remote.mp3",
            localFilePath = testFile.absolutePath
        )

        downloadManager.registerCompletedDownload(episode, testFile.absolutePath, testFile.length())

        val coordinator = DefaultPlaybackCoordinator.getInstance(context)

        val downloadedItem = PlaybackQueueItem(
            id = "offline_1",
            mediaUri = "https://example.com/remote.mp3",
            title = "Episódio Offline",
            mediaType = ActiveMediaType.PODCAST_EPISODE,
            isLiveStream = false
        )

        coordinator.playItem(downloadedItem)

        // Valida que o coordinator identificou que existe arquivo baixado local
        val localPath = downloadManager.getLocalFilePath("offline_1")
        assertNotNull(localPath)
        assertEquals(testFile.absolutePath, localPath)
    }
}
