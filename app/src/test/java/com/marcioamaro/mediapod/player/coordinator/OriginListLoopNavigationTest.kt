package com.marcioamaro.mediapod.player.coordinator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.player.context.NavigationContext
import com.marcioamaro.mediapod.player.context.QueueSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OriginListLoopNavigationTest {

    private lateinit var context: Context
    private lateinit var coordinator: DefaultPlaybackCoordinator

    private fun createStation(id: String, name: String, genre: String = "Rock"): RadioStation {
        return RadioStation(
            id = id,
            name = name,
            streamUrl = "https://example.com/stream/$id",
            favicon = "https://example.com/icon/$id.png",
            city = "São Paulo",
            country = "Brasil",
            countryCode = "BR",
            tags = genre
        )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = DefaultPlaybackCoordinator.getInstance(context)
    }

    @Test
    fun testFavoritesListNavigation_loopsCircularyWithinFavorites() {
        val fav1 = createStation("fav_1", "Rádio Favorita 1")
        val fav2 = createStation("fav_2", "Rádio Favorita 2")
        val fav3 = createStation("fav_3", "Rádio Favorita 3")
        val favoritesList = listOf(fav1, fav2, fav3)

        // Usuário iniciou a reprodução da rádio 2 nos favoritos
        val navContext = NavigationContext(
            source = QueueSource.FAVORITES,
            items = favoritesList.map { it.toPlaybackQueueItem() }
        )
        coordinator.setNavigationContext(navContext)
        coordinator.play(fav2.toPlaybackQueueItem())

        assertEquals("fav_2", coordinator.state.value.currentItem?.id)

        // Avançar no player -> deve ser a próxima rádio dos favoritos (fav_3)
        coordinator.skipToNext()
        assertEquals("fav_3", coordinator.state.value.currentItem?.id)

        // Avançar no final da lista -> deve fazer loop circular para a primeira (fav_1)
        coordinator.skipToNext()
        assertEquals("fav_1", coordinator.state.value.currentItem?.id)

        // Retroceder a partir da primeira -> deve fazer loop circular para a última (fav_3)
        coordinator.skipToPrevious()
        assertEquals("fav_3", coordinator.state.value.currentItem?.id)

        // Retroceder novamente -> fav_2
        coordinator.skipToPrevious()
        assertEquals("fav_2", coordinator.state.value.currentItem?.id)
    }

    @Test
    fun testCategoryRockNavigation_startsAtThirdAndLoopsWithinRockCategory() {
        val rock1 = createStation("rock_1", "Rock Radio 1", "Rock")
        val rock2 = createStation("rock_2", "Rock Radio 2", "Rock")
        val rock3 = createStation("rock_3", "Rock Radio 3", "Rock")
        val rock4 = createStation("rock_4", "Rock Radio 4", "Rock")
        val rock5 = createStation("rock_5", "Rock Radio 5", "Rock")
        val rockCategoryList = listOf(rock1, rock2, rock3, rock4, rock5)

        // Usuário filtrou por categoria 'Rock' e iniciou a reprodução da 3ª rádio (rock3)
        val navContext = NavigationContext(
            source = QueueSource.CATEGORY,
            queryId = "Rock",
            items = rockCategoryList.map { it.toPlaybackQueueItem() }
        )
        coordinator.setNavigationContext(navContext)
        coordinator.play(rock3.toPlaybackQueueItem())

        assertEquals("rock_3", coordinator.state.value.currentItem?.id)

        // Próxima rádio -> 4ª da categoria Rock
        coordinator.skipToNext()
        assertEquals("rock_4", coordinator.state.value.currentItem?.id)

        // Próxima rádio -> 5ª da categoria Rock
        coordinator.skipToNext()
        assertEquals("rock_5", coordinator.state.value.currentItem?.id)

        // Próxima rádio -> Loop para a 1ª da categoria Rock
        coordinator.skipToNext()
        assertEquals("rock_1", coordinator.state.value.currentItem?.id)

        // Anterior a partir da 1ª -> Loop para a última (5ª) da categoria Rock
        coordinator.skipToPrevious()
        assertEquals("rock_5", coordinator.state.value.currentItem?.id)
    }

    @Test
    fun testNavigationContextIsIsolatedWhenSwitchingLists() {
        val rockList = listOf(createStation("r1", "Rock 1"), createStation("r2", "Rock 2"))
        val recentsList = listOf(createStation("rec1", "Recente 1"), createStation("rec2", "Recente 2"), createStation("rec3", "Recente 3"))

        // Inicia em Rock
        coordinator.setNavigationContext(NavigationContext(source = QueueSource.CATEGORY, items = rockList.map { it.toPlaybackQueueItem() }))
        coordinator.play(rockList[0].toPlaybackQueueItem())
        assertEquals(2, coordinator.navigationContext.value.size)

        // Usuário muda para Recentes e toca a primeira de recentes
        coordinator.setNavigationContext(NavigationContext(source = QueueSource.RECENTS, items = recentsList.map { it.toPlaybackQueueItem() }))
        coordinator.play(recentsList[0].toPlaybackQueueItem())

        // A fila agora pertence exclusivamente a Recentes
        assertEquals(3, coordinator.navigationContext.value.size)
        assertEquals(QueueSource.RECENTS, coordinator.navigationContext.value.source)

        coordinator.skipToNext()
        assertEquals("rec2", coordinator.state.value.currentItem?.id)
    }
}
