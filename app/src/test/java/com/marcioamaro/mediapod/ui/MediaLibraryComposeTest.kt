package com.marcioamaro.mediapod.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.model.PodcastShow
import com.marcioamaro.mediapod.data.repository.LibraryKind
import com.marcioamaro.mediapod.data.repository.MediaLibraryRepository
import com.marcioamaro.mediapod.ui.components.MediaItemActions
import com.marcioamaro.mediapod.ui.components.LcdPlaylistPalette
import com.marcioamaro.mediapod.ui.components.LcdPlaylistModalHost
import com.marcioamaro.mediapod.ui.screens.IpodPodcastEpisodesScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaLibraryComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rankingHeaderHasNoExternalBrandOrLink() {
        compose.setContent {
            com.marcioamaro.mediapod.ui.components.RankingSourceHeader(
                com.marcioamaro.mediapod.data.repository.RankingInfo("Spotify / RadiosNet", "https://example.org", "Popularidade", "20 programas populares.", true),
                Color.Black)
        }
        compose.onNodeWithText("Top 20 • Popularidade").assertExists().assertHasNoClickAction()
        compose.onNodeWithText("Spotify", substring = true).assertDoesNotExist()
        compose.onNodeWithText("RadiosNet", substring = true).assertDoesNotExist()
    }

    @Test fun earCanMarkAndUnmarkAnEpisodeWithoutStartingPlayback() {
        var played = false
        var playback = false
        compose.setContent {
            var episode by remember { mutableStateOf(PodcastEpisode("ep", "show", "Programa", "Episódio", audioUrl = "https://example.org/audio.mp3")) }
            MaterialTheme {
                IpodPodcastEpisodesScreen(PodcastShow("show", "Programa", feedUrl = "https://example.org/feed"),
                    listOf(episode), 0, { playback = true }, false, Color(0xFFB9C8A1), Color.Black,
                    Color.DarkGray, Color.DarkGray, FontFamily.Monospace, 1f, true,
                    onTogglePlayed = { episode = it.copy(isPlayed = !it.isPlayed); played = episode.isPlayed })
            }
        }
        compose.onNodeWithContentDescription("Não ouvido").performClick()
        compose.onNodeWithContentDescription("Já ouvido").assertExists()
        assertTrue(played)
        assertFalse(playback)
        compose.onNodeWithContentDescription("Já ouvido").performClick()
        compose.onNodeWithContentDescription("Não ouvido").assertExists()
        assertFalse(played)
    }

    @Test fun aVideoCanBeFavoritedAndAddedToANewPlaylistFromItsRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("media_library", Context.MODE_PRIVATE).edit().clear().commit()
        val repo = MediaLibraryRepository(context)
        compose.setContent {
            MaterialTheme {
                LcdPlaylistModalHost {
                    Row { MediaItemActions(repo, LibraryKind.VIDEO, "VIDEO:/Movies/clip.mp4", null, Color.Black, LcdPlaylistPalette(Color.Black, Color.White, Color.LightGray, Color.Cyan)) }
                }
            }
        }
        compose.onNodeWithContentDescription("Favoritar").assertHeightIsAtLeast(48.dp).performClick()
        assertTrue("VIDEO:/Movies/clip.mp4" in repo.state.value.favorites)
        compose.onNodeWithContentDescription("Opções do arquivo").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("OPÇÕES DO ARQUIVO").assertExists()
        compose.onNodeWithText("ADICIONAR À PLAYLIST").performClick()
        compose.onNodeWithText("CRIAR PLAYLIST").performClick()
        compose.onNodeWithText("Nome").performTextInput("Cinema")
        compose.onNodeWithText("SALVAR").performClick()
        assertEquals("Cinema", repo.state.value.playlists.single().name)
        assertEquals(listOf("VIDEO:/Movies/clip.mp4"), repo.state.value.playlists.single().keys)
    }
}
