package com.example.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PodcastEpisode
import com.example.data.model.PodcastShow
import com.example.data.model.RadioStation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackPersistenceTest {

    private lateinit var context: Context
    private lateinit var ipodPrefs: IpodPreferencesManager
    private lateinit var dataStore: PlaybackStateDataStore

    private val sampleStation = RadioStation(
        id = "br_sp_alpha_fm",
        name = "Alpha FM 101.7",
        streamUrl = "https://stream.alphafm.com.br/hls/live.m3u8",
        favicon = "https://alphafm.com.br/logo.png",
        country = "Brasil",
        countryCode = "BR",
        state = "SP",
        city = "São Paulo",
        tags = "adult contemporary, pop, soft rock",
        bitrate = 192,
        codec = "AAC",
        votes = 12500
    )

    private val sampleEpisode = PodcastEpisode(
        id = "ep_101",
        showId = "show_radio_tech",
        showTitle = "Radio Tech Cast",
        title = "Episódio 101: Media3 & Resiliência de Áudio",
        description = "Discussão aprofundada sobre foreground service e recuperação pós-process death",
        audioUrl = "https://cdn.radiotech.com/episodes/101.mp3",
        durationMs = 2700000L,
        publishDate = "2026-09-18",
        artworkUrl = "https://cdn.radiotech.com/artwork.jpg"
    )

    private val sampleShow = PodcastShow(
        id = "show_radio_tech",
        title = "Radio Tech Cast",
        author = "Engenharia de Streaming",
        description = "Podcasts semanais sobre tecnologia de rádio e áudio",
        feedUrl = "https://cdn.radiotech.com/feed.xml",
        artworkUrl = "https://cdn.radiotech.com/show_art.jpg",
        country = "BR",
        category = "Technology"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ipodPrefs = IpodPreferencesManager.getInstance(context)
        dataStore = PlaybackStateDataStore.getInstance(context)
    }

    @Test
    fun testSaveAndRestoreLastPlayedStationInSharedPreferences() {
        ipodPrefs.saveLastPlayedStation(sampleStation)

        val restored = ipodPrefs.getLastPlayedStation()
        assertNotNull("Estação restaurada não deve ser nula", restored)
        assertEquals("br_sp_alpha_fm", restored?.id)
        assertEquals("Alpha FM 101.7", restored?.name)
        assertEquals("https://stream.alphafm.com.br/hls/live.m3u8", restored?.streamUrl)
        assertEquals("SP", restored?.state)
        assertEquals("BR", restored?.countryCode)
        assertEquals(192, restored?.bitrate)
        assertEquals(12500, restored?.votes)
        assertEquals("RADIO", ipodPrefs.getLastMediaType())
    }

    @Test
    fun testSaveAndRestoreLastPlayedPodcastWithShow() {
        ipodPrefs.saveLastPlayedPodcast(sampleEpisode, sampleShow)

        val restored = ipodPrefs.getLastPlayedPodcast()
        assertNotNull("Par (episódio, show) restaurado não deve ser nulo", restored)
        val (episode, show) = requireNotNull(restored)

        assertEquals("ep_101", episode.id)
        assertEquals("Episódio 101: Media3 & Resiliência de Áudio", episode.title)
        assertEquals(2700000L, episode.durationMs)
        assertEquals("https://cdn.radiotech.com/episodes/101.mp3", episode.audioUrl)

        assertNotNull("Show associado não deve ser nulo", show)
        assertEquals("show_radio_tech", show?.id)
        assertEquals("Radio Tech Cast", show?.title)
        assertEquals("Technology", show?.category)
        assertEquals("PODCAST", ipodPrefs.getLastMediaType())
    }

    @Test
    fun testSaveAndRestoreLastPlayedPodcastWithoutShow() {
        ipodPrefs.saveLastPlayedPodcast(sampleEpisode, null)

        val restored = ipodPrefs.getLastPlayedPodcast()
        assertNotNull(restored)
        val (episode, show) = requireNotNull(restored)

        assertEquals("ep_101", episode.id)
        assertNull("Show deve ser nulo quando salvo sem show", show)
    }

    @Test
    fun testRecentStationsQueueDeduplicationAndOrder() {
        val stationA = sampleStation.copy(id = "station_a", name = "Station A")
        val stationB = sampleStation.copy(id = "station_b", name = "Station B")
        val stationC = sampleStation.copy(id = "station_c", name = "Station C")

        ipodPrefs.addRecentStation(stationA)
        ipodPrefs.addRecentStation(stationB)
        ipodPrefs.addRecentStation(stationC)
        // Re-adiciona stationA para validar que passa ao topo da fila
        ipodPrefs.addRecentStation(stationA)

        val recents = ipodPrefs.getRecentStations()
        assertTrue("Fila de recentes deve conter 3 itens distintos", recents.size >= 3)
        assertEquals("O item mais recente deve ser Station A", "station_a", recents[0].id)
        assertEquals("O segundo item deve ser Station C", "station_c", recents[1].id)
        assertEquals("O terceiro item deve ser Station B", "station_b", recents[2].id)
    }

    @Test
    fun testPlaybackStateDataStoreRadioPersistence() = runBlocking {
        dataStore.saveRadioState(
            stationId = sampleStation.id,
            stationUrl = sampleStation.streamUrl,
            stationName = sampleStation.name,
            volume = 0.85f
        )

        val lastId = dataStore.lastStationId.first()
        val lastUrl = dataStore.lastStationUrl.first()
        val lastName = dataStore.lastStationName.first()
        val lastVolume = dataStore.lastVolume.first()
        val lastMediaType = dataStore.lastMediaType.first()
        val lastPos = dataStore.lastPositionMs.first()

        assertEquals("br_sp_alpha_fm", lastId)
        assertEquals(sampleStation.streamUrl, lastUrl)
        assertEquals(sampleStation.name, lastName)
        assertEquals(0.85f, lastVolume, 0.001f)
        assertEquals("RADIO", lastMediaType)
        assertEquals(0L, lastPos) // Live stream deve persistir posição 0L
    }

    @Test
    fun testPlaybackStateDataStorePodcastPositionPersistence() = runBlocking {
        dataStore.saveMediaState(
            mediaId = sampleEpisode.id,
            mediaUrl = sampleEpisode.audioUrl,
            mediaTitle = sampleEpisode.title,
            positionMs = 125400L,
            volume = 0.90f,
            mediaType = "PODCAST"
        )

        assertEquals("ep_101", dataStore.lastStationId.first())
        assertEquals(125400L, dataStore.lastPositionMs.first())
        assertEquals("PODCAST", dataStore.lastMediaType.first())

        // Atualização incremental de posição (seek ou tick periódico)
        dataStore.savePosition(340000L)
        assertEquals(340000L, dataStore.lastPositionMs.first())
    }
}
