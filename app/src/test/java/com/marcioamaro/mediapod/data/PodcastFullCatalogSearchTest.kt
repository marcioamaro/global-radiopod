package com.marcioamaro.mediapod.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.repository.PodcastRepository
import com.marcioamaro.mediapod.data.repository.PublishedRankings
import com.marcioamaro.mediapod.ui.UiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PodcastFullCatalogSearchTest {
    @Test fun topListsDoNotLimitFullCatalogOrSearchResults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PodcastRepository.clearInstanceForTesting()
        PublishedRankings.initialize(context)
        val repo = PodcastRepository.getInstance(context)
        val top = PublishedRankings.podcasts("podcast_brazil", 20)
        val all = repo.searchPodcasts("")
        assertTrue(all.size > 350)
        val outsideTop = all.first { show -> top.none { it.id == show.id } }
        assertTrue(repo.searchCatalog(outsideTop.title).any { it.id == outsideTop.id })
        val state = UiState(podcastShows = top, podcastSearchResults = all)
        val afterWorldTop = state.copy(podcastShows = PublishedRankings.podcasts("podcast_world", 20))
        assertEquals(all, afterWorldTop.podcastSearchResults)
        assertEquals(20, afterWorldTop.podcastShows.size)
        assertTrue(all.all { it.rankPosition == null })
    }

    @Test fun customPodcastWithUnknownEpisodeCountIsSearchable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PodcastRepository.clearInstanceForTesting()
        val repo = PodcastRepository.getInstance(context)
        val custom = repo.addCustomPodcast("Ciência inédita regressão", "https://example.org/new-feed.xml")
        assertEquals(0, custom.episodeCount)
        assertTrue(repo.searchCatalog("ciencia inedita").any { it.id == custom.id })
        assertTrue(repo.searchCatalog("").any { it.id == custom.id })
    }
}
