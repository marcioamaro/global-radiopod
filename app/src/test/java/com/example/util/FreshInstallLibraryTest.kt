package com.example.util

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.data.db.RadioDatabase
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.repository.PodcastRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FreshInstallLibraryTest {
    @Before fun resetProcessSingletons() {
        // Each scenario represents a different Android installation/process.
        for (type in listOf(IpodPreferencesManager::class.java, PodcastRepository::class.java)) {
            type.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == type }
                .forEach { it.isAccessible = true; it.set(null, null) }
        }
    }

    @Test fun newInstallStartsWithEmptyFavoritesAndRecents() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        val db = Room.inMemoryDatabaseBuilder(context, RadioDatabase::class.java).allowMainThreadQueries().build()
        try {
            assertTrue(db.favoriteStationDao().getAllFavoritesDirect().isEmpty())
            assertTrue(IpodPreferencesManager.getInstance(context).getRecentStations().isEmpty())
            val podcasts = PodcastRepository.getInstance(context)
            assertTrue(podcasts.favoritesFlow.value.isEmpty())
            assertTrue(podcasts.recentEpisodesFlow.value.isEmpty())
            assertTrue(podcasts.recentShowsFlow.value.isEmpty())
        } finally { db.close() }
    }

    @Test fun existingOrRestoredHistoryIsPreserved() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("ipod_radio_user_preferences", Context.MODE_PRIVATE).edit()
            .putString("key_recents_json", """[{"id":"radio","name":"Minha rádio","streamUrl":"https://example.org/live"}]""").commit()
        context.getSharedPreferences("podcast_preferences", Context.MODE_PRIVATE).edit()
            .putString("podcast_favorites_json", """[{"id":"show","title":"Meu podcast","feedUrl":"https://example.org/feed"}]""")
            .putString("podcast_recents_json", """[{"id":"episode","showId":"show","title":"Meu episódio","audioUrl":"https://example.org/episode"}]""").commit()
        assertEquals("radio", IpodPreferencesManager.getInstance(context).getRecentStations().single().id)
        val podcasts = PodcastRepository.getInstance(context)
        assertEquals("show", podcasts.favoritesFlow.value.single().id)
        assertEquals("episode", podcasts.recentEpisodesFlow.value.single().id)
    }
}
