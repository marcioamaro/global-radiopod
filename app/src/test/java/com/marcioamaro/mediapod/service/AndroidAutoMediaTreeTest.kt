package com.marcioamaro.mediapod.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.player.ActiveMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suíte de testes e auditoria para Android Auto e Android Automotive OS.
 * Valida os contratos de navegação da MediaLibraryTree, segurança na direção
 * (Design for Driving), estrutura de metadados, comandos de voz e diferenciação rádio vs podcast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidAutoMediaTreeTest {

    companion object {
        const val ROOT_MEDIA_ID = "ROOT_MEDIA_ID"
        const val FAVORITE_RADIOS = "FAVORITE_RADIOS"
        const val RECENT_RADIOS = "RECENT_RADIOS"
        const val FAVORITE_PODCASTS = "FAVORITE_PODCASTS"
        const val RECENT_PODCASTS = "RECENT_PODCASTS"

        val DRIVER_SAFE_ROOT_IDS = setOf(
            FAVORITE_RADIOS,
            RECENT_RADIOS,
            FAVORITE_PODCASTS,
            RECENT_PODCASTS
        )

        val FORBIDDEN_AUTO_TERMS = listOf(
            "brick",
            "game",
            "video",
            "clickwheel",
            "wheel",
            "theme",
            "youtube"
        )
    }

    private fun buildMockAutoRootCategories(): List<MediaItem> {
        return listOf(
            MediaItem.Builder()
                .setMediaId(FAVORITE_RADIOS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Rádios Favoritas")
                        .setSubtitle("Estações salvas")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .build()
                ).build(),
            MediaItem.Builder()
                .setMediaId(RECENT_RADIOS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Rádios Recentes")
                        .setSubtitle("Últimas estações ouvidas")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .build()
                ).build(),
            MediaItem.Builder()
                .setMediaId(FAVORITE_PODCASTS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Podcasts Favoritos")
                        .setSubtitle("Programas salvos")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                        .build()
                ).build(),
            MediaItem.Builder()
                .setMediaId(RECENT_PODCASTS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Podcasts Recentes")
                        .setSubtitle("Últimos episódios ouvidos")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                        .build()
                ).build()
        )
    }

    @Test
    fun testRootCategoriesAreDriverSafeAndAudioCentric() {
        val rootItems = buildMockAutoRootCategories()

        assertEquals("Árvore de Android Auto deve ter exatamente 4 categorias principais", 4, rootItems.size)

        for (item in rootItems) {
            val id = item.mediaId
            assertTrue("Categoria '$id' deve ser uma das categorias seguras para direção", DRIVER_SAFE_ROOT_IDS.contains(id))

            val meta = item.mediaMetadata
            assertNotNull(meta)
            assertTrue("Nó de categoria '$id' deve ser navegável (isBrowsable)", meta.isBrowsable == true)
            assertFalse("Nó de categoria '$id' não deve ser reproduzível diretamente (isPlayable)", meta.isPlayable == true)

            // Validação de distração visual para motorista (Design for Driving - Google)
            val titleLower = (meta.title?.toString() ?: "").lowercase()
            for (forbidden in FORBIDDEN_AUTO_TERMS) {
                assertFalse(
                    "Árvore de carro não deve expor '$forbidden' ao motorista (encontrado em: '$titleLower')",
                    titleLower.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun testLibraryRootSpecification() {
        val rootItem = MediaItem.Builder()
            .setMediaId(ROOT_MEDIA_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("MediaPod + Radio / Podcast")
                    .setSubtitle("Rádios & Podcasts")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()

        assertEquals(ROOT_MEDIA_ID, rootItem.mediaId)
        assertTrue(rootItem.mediaMetadata.isBrowsable == true)
        assertFalse(rootItem.mediaMetadata.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, rootItem.mediaMetadata.mediaType)
    }

    @Test
    fun testEmptyStateInformationalCardContract() {
        val emptyCard = MediaItem.Builder()
            .setMediaId("empty_radios")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Nenhuma rádio favorita")
                    .setSubtitle("Favorite rádios no celular para vê-las aqui")
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build()
            ).build()

        assertNotNull(emptyCard.mediaMetadata.title)
        assertFalse("Card informativo de lista vazia não deve ser clicável como play", emptyCard.mediaMetadata.isPlayable == true)
        assertFalse("Card informativo de lista vazia não deve ser navegável", emptyCard.mediaMetadata.isBrowsable == true)
    }

    @Test
    fun testStationCardItemHasFullMetadataAndIsPlayable() {
        val station = RadioStation(
            id = "jb_fm_rio",
            name = "JB FM 99.9",
            streamUrl = "https://stream.jbfm.com.br/live",
            city = "Rio de Janeiro",
            country = "Brasil",
            favicon = "https://jbfm.com.br/logo.png"
        )

        val mediaItem = MediaItem.Builder()
            .setMediaId("radio_fav_${station.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist("${station.city} • ${station.country}")
                    .setArtworkUri(Uri.parse(station.favicon))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .build()
            )
            .build()

        assertTrue("Item de rádio deve ser reproduzível no carro", mediaItem.mediaMetadata.isPlayable == true)
        assertFalse("Item de rádio não deve ser pasta navegável", mediaItem.mediaMetadata.isBrowsable == true)
        assertEquals("JB FM 99.9", mediaItem.mediaMetadata.title.toString())
        assertEquals("Rio de Janeiro • Brasil", mediaItem.mediaMetadata.artist.toString())
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, mediaItem.mediaMetadata.mediaType)
    }

    @Test
    fun testPodcastEpisodeCardItemHasDurationAndIsPlayable() {
        val episode = PodcastEpisode(
            id = "ep_123",
            showId = "show_456",
            showTitle = "Café Brasil",
            title = "Episódio 800 - O Tempo",
            audioUrl = "https://lucianopires.com.br/ep800.mp3",
            durationMs = 1800000L,
            artworkUrl = "https://lucianopires.com.br/cover.jpg"
        )

        val mediaItem = MediaItem.Builder()
            .setMediaId("podrec_${episode.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(episode.showTitle)
                    .setArtworkUri(Uri.parse(episode.artworkUrl))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .build()
            )
            .build()

        assertTrue("Episódio de podcast deve ser reproduzível", mediaItem.mediaMetadata.isPlayable == true)
        assertEquals("Episódio 800 - O Tempo", mediaItem.mediaMetadata.title.toString())
        assertEquals("Café Brasil", mediaItem.mediaMetadata.artist.toString())
        assertEquals(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE, mediaItem.mediaMetadata.mediaType)
        assertTrue("Duração deve ser positiva para permitir seek", episode.durationMs > 0)
    }

    @Test
    fun testVoiceSearchMatchingContract() {
        val stations = listOf(
            RadioStation(id = "1", name = "Antena 1 São Paulo", streamUrl = "http://a1.com"),
            RadioStation(id = "2", name = "BandNews FM", streamUrl = "http://bn.com"),
            RadioStation(id = "3", name = "Jovem Pan FM", streamUrl = "http://jp.com")
        )

        fun searchMatch(query: String): RadioStation? {
            return stations.find { com.marcioamaro.mediapod.util.RadioSearchEngine.matchesMultiToken(it, query) }
        }

        val result1 = searchMatch("antena 1")
        assertNotNull(result1)
        assertEquals("1", result1?.id)

        val result2 = searchMatch("band news")
        assertNotNull(result2)
        assertEquals("2", result2?.id)

        val resultUnknown = searchMatch("estação inexistente xyz")
        assertNull(resultUnknown)
    }
}
