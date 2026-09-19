package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PodcastEpisode
import com.example.data.model.PodcastShow
import com.example.data.repository.PodcastRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Testes unitários para assinaturas de podcasts (subscriptions) e rastreamento de lidos/não lidos (Item 7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PodcastSubscriptionTest {

    private lateinit var context: Context
    private lateinit var repository: PodcastRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("podcast_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        PodcastRepository.clearInstanceForTesting()
        repository = PodcastRepository.getInstance(context)
    }

    @Test
    fun testSubscribeAndUnsubscribe() {
        val show = PodcastShow(
            id = "show_tech_1",
            title = "Tech Cast",
            feedUrl = "https://techcast.com/feed.xml",
            category = "Tecnologia"
        )

        assertFalse(repository.isSubscribed(show.id))
        assertEquals(0, repository.subscriptionsFlow.value.size)

        repository.subscribe(show)
        assertTrue(repository.isSubscribed(show.id))
        assertEquals(1, repository.subscriptionsFlow.value.size)
        assertEquals("Tech Cast", repository.subscriptionsFlow.value.first().title)

        // Reinicia repositório simulando nova sessão
        PodcastRepository.clearInstanceForTesting()
        val repo2 = PodcastRepository.getInstance(context)
        assertTrue(repo2.isSubscribed(show.id))
        assertEquals(1, repo2.subscriptionsFlow.value.size)

        repo2.unsubscribe(show.id)
        assertFalse(repo2.isSubscribed(show.id))
        assertEquals(0, repo2.subscriptionsFlow.value.size)
    }

    @Test
    fun testEpisodePlayedAndUnreadCount() {
        val ep1 = PodcastEpisode(id = "ep_1", showId = "s1", showTitle = "Show", title = "Ep 1", audioUrl = "http://a.mp3")
        val ep2 = PodcastEpisode(id = "ep_2", showId = "s1", showTitle = "Show", title = "Ep 2", audioUrl = "http://b.mp3")
        val ep3 = PodcastEpisode(id = "ep_3", showId = "s1", showTitle = "Show", title = "Ep 3", audioUrl = "http://c.mp3")

        val episodes = listOf(ep1, ep2, ep3)

        assertEquals(3, repository.getUnreadCount(episodes))
        assertFalse(repository.isEpisodePlayed(ep1.id))

        repository.markEpisodePlayed(ep1.id, true)
        assertTrue(repository.isEpisodePlayed(ep1.id))
        assertEquals(2, repository.getUnreadCount(episodes))

        repository.markEpisodePlayed(ep2.id, true)
        assertEquals(1, repository.getUnreadCount(episodes))
    }
}
