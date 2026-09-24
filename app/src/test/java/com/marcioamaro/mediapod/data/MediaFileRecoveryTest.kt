package com.marcioamaro.mediapod.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MediaFileRecoveryTest {
    private fun file(key: String, content: String = "audio-content") = MediaFileReference(key,
        content.toByteArray().size.toLong(), 1L) { content.byteInputStream() }

    @Test fun movedFilePreservesFavoritesPlaylistOrderAndBackupIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MediaLibraryRepository(context)
        repo.toggleFavorite("AUDIO:/old/song.mp3")
        repo.recordRecent("AUDIO:/old/song.mp3")
        repo.savePosition("AUDIO:/old/song.mp3", 30000, 180000)
        repo.createPlaylist("Music", LibraryKind.AUDIO, "AUDIO:/old/song.mp3")
        repo.registerFiles(LibraryKind.AUDIO, listOf(file("AUDIO:/old/song.mp3")))
        val exported = repo.exportJson()
        assertTrue(exported.getJSONObject("identities").has("AUDIO:/old/song.mp3"))
        repo.registerFiles(LibraryKind.AUDIO, listOf(file("AUDIO:/new/renamed.mp3")))
        assertEquals(setOf("AUDIO:/new/renamed.mp3"), repo.state.value.favorites)
        assertEquals(listOf("AUDIO:/new/renamed.mp3"), repo.state.value.playlists.single().keys)
        assertEquals(listOf("AUDIO:/new/renamed.mp3"), repo.state.value.recents)
        assertEquals(30000L, repo.position("AUDIO:/new/renamed.mp3"))
    }

    @Test fun identicalCopiesRemainUnresolvedInsteadOfChoosingArbitrarily() = runBlocking {
        val repo = MediaLibraryRepository(ApplicationProvider.getApplicationContext())
        repo.toggleFavorite("AUDIO:/old.mp3")
        repo.registerFiles(LibraryKind.AUDIO, listOf(file("AUDIO:/old.mp3")))
        repo.registerFiles(LibraryKind.AUDIO, listOf(file("AUDIO:/a.mp3"), file("AUDIO:/b.mp3")))
        assertEquals(setOf("AUDIO:/old.mp3"), repo.state.value.favorites)
    }
}
