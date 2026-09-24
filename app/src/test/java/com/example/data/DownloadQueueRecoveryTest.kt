package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.*
import com.example.data.model.PodcastEpisode
import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class DownloadQueueRecoveryTest {
    @Test fun pausedQueueSurvivesNewManagerWithoutStartingNetworkRequests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PodcastDownloadManager.clearInstanceForTesting()
        val episode = PodcastEpisode("queued", "show", "Show", "Episode", audioUrl = "https://example.invalid/audio")
        val prefs = context.getSharedPreferences("radiopod_downloads", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_queued", Moshi.Builder().build().adapter(PodcastEpisode::class.java).toJson(episode))
            .putBoolean("paused_queued", true).commit()
        assertTrue(PodcastDownloadManager.getInstance(context).statusMap.value["queued"] is DownloadStatus.Paused)
        PodcastDownloadManager.clearInstanceForTesting()
        assertTrue(PodcastDownloadManager.getInstance(context).statusMap.value["queued"] is DownloadStatus.Paused)
        assertTrue(prefs.contains("pending_queued"))
        PodcastDownloadManager.clearInstanceForTesting()
    }
}
