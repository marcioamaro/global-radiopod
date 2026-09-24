package com.example.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.LibraryKind
import com.example.data.repository.MediaLibraryRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MediaLibraryPersistenceTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("media_library", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun playlistsKeepUserOrderAndSurviveRestart() {
        val repo = MediaLibraryRepository(context)
        val id = repo.createPlaylist("Viagem", LibraryKind.AUDIO, "AUDIO:/b.mp3")
        repo.addToPlaylist(id, "AUDIO:/a.mp3")
        repo.addToPlaylist(id, "AUDIO:/b.mp3")
        assertEquals(listOf("AUDIO:/b.mp3", "AUDIO:/a.mp3"), repo.state.value.playlists.single().keys)
        repo.moveInPlaylist(id, "AUDIO:/a.mp3", -1)
        repo.renamePlaylist(id, "Estrada")
        repo.toggleFavorite("AUDIO:/b.mp3")
        val reloaded = MediaLibraryRepository(context)
        assertEquals("Estrada", reloaded.state.value.playlists.single().name)
        assertEquals(listOf("AUDIO:/a.mp3", "AUDIO:/b.mp3"), reloaded.state.value.playlists.single().keys)
        assertTrue("AUDIO:/b.mp3" in reloaded.state.value.favorites)
        // Missing local files stay in the playlist but are omitted from the playable queue.
        assertEquals(listOf("AUDIO:/b.mp3"), reloaded.select("playlist:$id", listOf("AUDIO:/b.mp3")) { it })
        assertEquals(2, reloaded.state.value.playlists.single().keys.size)
        reloaded.removeFromPlaylist(id, "AUDIO:/a.mp3")
        assertEquals(1, reloaded.state.value.playlists.single().keys.size)
        reloaded.deletePlaylist(id)
        assertTrue(reloaded.state.value.playlists.isEmpty())
    }

    @Test fun recentOrderIsNewestFirstAndMediaTypesStaySeparate() {
        val repo = MediaLibraryRepository(context)
        repo.recordRecent("AUDIO:/a.mp3")
        repo.recordRecent("VIDEO:/b.mp4")
        repo.recordRecent("AUDIO:/a.mp3")
        assertEquals(listOf("AUDIO:/a.mp3", "VIDEO:/b.mp4"), repo.state.value.recents)
        repo.clearRecents(LibraryKind.VIDEO)
        assertEquals(listOf("AUDIO:/a.mp3"), repo.state.value.recents)
        val id = repo.createPlaylist("Cinema", LibraryKind.VIDEO)
        assertThrows(IllegalArgumentException::class.java) { repo.addToPlaylist(id, "AUDIO:/a.mp3") }
        assertThrows(IllegalArgumentException::class.java) { repo.createPlaylist(" cinema ", LibraryKind.VIDEO) }
        assertThrows(IllegalArgumentException::class.java) { repo.createPlaylist("  ", LibraryKind.AUDIO) }
    }
}
