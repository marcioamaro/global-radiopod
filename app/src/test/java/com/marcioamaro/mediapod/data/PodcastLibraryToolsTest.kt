package com.marcioamaro.mediapod.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.model.PodcastShow
import com.marcioamaro.mediapod.data.repository.EpisodeBookmarks
import com.marcioamaro.mediapod.util.PodcastOpml
import com.marcioamaro.mediapod.util.PodcastLibraryBackup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PodcastLibraryToolsTest {
    @Test fun opmlRoundTripEscapesTextAndDeduplicatesFeeds() {
        val show = PodcastShow("one", "A & B", feedUrl = "https://example.org/feed%2Fprivate?a=1&b=2")
        val xml = PodcastOpml.export(listOf(show, show))
        val parsed = PodcastOpml.parse(xml.toByteArray())
        assertEquals(1, parsed.size)
        assertEquals(show.feedUrl, parsed.single().feedUrl)
        assertEquals(show.title, parsed.single().title)
        assertTrue(runCatching { PodcastOpml.parse("<!DOCTYPE opml><opml/>".toByteArray()) }.isFailure)
        assertTrue(runCatching { PodcastOpml.parse("<opml><body><outline xmlUrl='file:///private'/></body></opml>".toByteArray()) }.isFailure)
    }

    @Test fun bookmarkEditingAndBackupPreserveEpisodeAndTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = EpisodeBookmarks(context)
        val episode = PodcastEpisode("episode", "show", "Show", "Title", audioUrl = "https://example.org/audio")
        repository.save(episode, 15000, "First", "note")
        repository.save(episode, 25000, "Edited", "note")
        assertEquals(1, repository.items.value.size)
        val snapshot = PodcastLibraryBackup.export(context)
        repository.delete("note")
        assertTrue(repository.items.value.isEmpty())
        PodcastLibraryBackup.restore(context, snapshot)
        repository.reload()
        assertEquals(25000L, repository.items.value.single().positionMs)
        assertEquals("Edited", repository.items.value.single().note)
    }
}
