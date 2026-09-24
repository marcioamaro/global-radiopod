package com.marcioamaro.mediapod.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.repository.PodcastRepository
import com.marcioamaro.mediapod.data.repository.PublishedRankings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HistoricalRankingTest {
    @Test fun historicalOrderIncludesBrazilAndNeverPadsToAnInventedTop100() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PublishedRankings.initialize(context)
        val shows = PublishedRankings.podcasts("podcast_world", 100)
        assertEquals(20, shows.size)
        assertEquals((1..20).toList(), shows.map { it.rankPosition })
        assertEquals("The Joe Rogan Experience", shows.first().title)
        assertEquals("Não Inviabilize", shows[11].title)
        assertTrue(shows.all { it.feedUrl.startsWith("https://") || it.feedUrl.startsWith("http://") })
        assertTrue(PublishedRankings.info("podcast_world").period.contains("Todos os tempos"))
        for (key in listOf("podcast_brazil", "radio_brazil", "radio_world", "podcast_world")) {
            assertTrue(PublishedRankings.info(key).available)
            assertEquals("", PublishedRankings.info(key).source)
            assertEquals("", PublishedRankings.info(key).sourceUrl)
        }
        val brazil = PublishedRankings.podcasts("podcast_brazil", 100)
        assertEquals(20, brazil.size)
        assertEquals((1..20).toList(), brazil.map { it.rankPosition })
        assertEquals(20, brazil.map { it.id }.distinct().size)
        assertTrue(brazil.all { it.country == "BR" && it.feedUrl.isNotBlank() && it.externalUrl.isEmpty() })
        assertTrue(shows.all { it.externalUrl.isEmpty() })
        assertTrue(PublishedRankings.podcasts("podcast_world", -5).isEmpty())
    }

    @Test fun brazilianCatalogBrowsingRemainsAvailableWithoutAnAllTimeChart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shows = PodcastRepository.getInstance(context).getTopPodcasts("BR", 500)
        assertTrue(shows.size >= 350)
        assertTrue(shows.all { it.country == "BR" && it.feedUrl.isNotBlank() })
    }
}
