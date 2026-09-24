package com.example.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PodcastShow
import com.example.data.model.YouTubeVideo
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.repository.*
import com.example.util.BackupCryptoHelper
import com.example.util.BackupRestoreManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MediaBackupRoundTripTest {
    @Before fun resetApplicationSingletons() {
        for (type in listOf(PodcastRepository::class.java, IpodPreferencesManager::class.java,
                MediaLibraryRepository::class.java, com.example.data.db.RadioDatabase::class.java)) {
            type.declaredFields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == type }
                .forEach { it.isAccessible = true; it.set(null, null) }
        }
    }
    @Test fun encryptedBackupRestoresPlayedEpisodesYoutubeAndCollectionsIdempotently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val podcasts = PodcastRepository.getInstance(context)
        val prefs = IpodPreferencesManager.getInstance(context)
        val library = MediaLibraryRepository.getInstance(context)
        val favorite = PodcastShow("backup_show", "Programa", feedUrl = "https://example.org/feed.xml")
        podcasts.markEpisodePlayed("episode_backup")
        podcasts.toggleFavorite(favorite)
        val a = YouTubeVideo("abcdefghijk", "Primeiro", "https://www.youtube.com/watch?v=abcdefghijk", addedAt = 1)
        val b = YouTubeVideo("lmnopqrstuv", "Segundo", "https://youtu.be/lmnopqrstuv", addedAt = 2)
        prefs.addYouTubeVideo(a)
        prefs.addYouTubeVideo(b)
        val playlistId = library.createPlaylist("Backup", LibraryKind.AUDIO, "AUDIO:/Music/test.mp3")
        library.toggleFavorite("VIDEO:/Movies/test.mp4")
        library.recordRecent("VIDEO:/Movies/test.mp4")
        library.savePosition("VIDEO:/Movies/test.mp4", 45000, 200000)
        com.example.util.DataUsagePolicy(context).remoteArtwork = false
        com.example.util.DataUsagePolicy(context).preferredBitrate = 64000
        val json = BackupRestoreManager.generateBackupJson(context)
        assertEquals(29, JSONObject(json).getInt("version"))
        val encrypted = BackupCryptoHelper.encryptBackupPayload(json, "test-password".toCharArray())
        val decrypted = BackupCryptoHelper.decryptBackupPayload(encrypted, "test-password".toCharArray())
        podcasts.markEpisodePlayed("episode_backup", false)
        podcasts.toggleFavorite(favorite)
        prefs.removeYouTubeVideo(a.id)
        prefs.removeYouTubeVideo(b.id)
        library.deletePlaylist(playlistId)
        library.toggleFavorite("VIDEO:/Movies/test.mp4")
        library.clearRecents(LibraryKind.VIDEO)
        library.savePosition("VIDEO:/Movies/test.mp4", 0, 200000)
        com.example.util.DataUsagePolicy(context).remoteArtwork = true
        com.example.util.DataUsagePolicy(context).preferredBitrate = 0

        repeat(2) { assertTrue(BackupRestoreManager.restoreFromJson(context, decrypted)) }
        assertEquals(45000L, library.position("VIDEO:/Movies/test.mp4"))
        assertFalse(com.example.util.DataUsagePolicy(context).remoteArtwork)
        assertEquals(64000, com.example.util.DataUsagePolicy(context).preferredBitrate)
        assertTrue(podcasts.isEpisodePlayed("episode_backup"))
        assertTrue("episode_backup" in podcasts.playedEpisodeIds.value)
        assertEquals(1, podcasts.favoritesFlow.value.count { it.id == favorite.id })
        assertEquals(listOf(b, a), prefs.getYouTubeVideos())
        assertEquals(listOf("AUDIO:/Music/test.mp3"), library.state.value.playlists.single { it.id == playlistId }.keys)
        assertTrue("VIDEO:/Movies/test.mp4" in library.state.value.favorites)
        assertEquals(listOf("VIDEO:/Movies/test.mp4"), library.state.value.recents)
        // Old backups without the new sections remain readable and do not erase them.
        assertTrue(BackupRestoreManager.restoreFromJson(context, "{\"version\":28,\"youtubeVideos\":[]}"))
        assertTrue(podcasts.isEpisodePlayed("episode_backup"))
        PodcastRepository.clearInstanceForTesting()
        assertTrue(PodcastRepository.getInstance(context).isEpisodePlayed("episode_backup"))
    }
}
