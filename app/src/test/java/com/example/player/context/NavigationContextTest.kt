package com.example.player.context

import com.example.player.ActiveMediaType
import com.example.player.coordinator.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContextTest {

    private fun createItem(id: String, title: String): PlaybackQueueItem {
        return PlaybackQueueItem(
            id = id,
            mediaUri = "http://stream.example.com/$id",
            title = title,
            subtitle = "Teste",
            artworkUri = null,
            mediaType = ActiveMediaType.LIVE_RADIO,
            durationMs = null,
            isLiveStream = true
        )
    }

    @Test
    fun testEmptyNavigationContext() {
        val ctx = NavigationContext(source = QueueSource.GLOBAL)
        assertTrue(ctx.isEmpty)
        assertFalse(ctx.isNotEmpty)
        assertEquals(0, ctx.size)
        assertNull(ctx.getNextItem(0))
        assertNull(ctx.getPreviousItem(0))
        assertEquals(-1, ctx.findIndex("non_existent"))
    }

    @Test
    fun testCircularNextNavigation() {
        val items = listOf(
            createItem("1", "Rádio 1"),
            createItem("2", "Rádio 2"),
            createItem("3", "Rádio 3")
        )
        val ctx = NavigationContext(source = QueueSource.SEARCH, queryId = "Rock", items = items)

        assertEquals(3, ctx.size)
        assertEquals("2", ctx.getNextItem(0)?.id)
        assertEquals("3", ctx.getNextItem(1)?.id)
        // Circular: da última volta para a primeira
        assertEquals("1", ctx.getNextItem(2)?.id)
    }

    @Test
    fun testCircularPreviousNavigation() {
        val items = listOf(
            createItem("1", "Rádio 1"),
            createItem("2", "Rádio 2"),
            createItem("3", "Rádio 3")
        )
        val ctx = NavigationContext(source = QueueSource.FAVORITES, items = items)

        assertEquals("2", ctx.getPreviousItem(2)?.id)
        assertEquals("1", ctx.getPreviousItem(1)?.id)
        // Circular: da primeira volta para a última
        assertEquals("3", ctx.getPreviousItem(0)?.id)
    }

    @Test
    fun testSingleItemNavigation() {
        val items = listOf(createItem("single", "Única Rádio"))
        val ctx = NavigationContext(source = QueueSource.RANKING, items = items)

        assertEquals("single", ctx.getNextItem(0)?.id)
        assertEquals("single", ctx.getPreviousItem(0)?.id)
    }

    @Test
    fun testQueueSourceValues() {
        val sources = QueueSource.values()
        assertTrue(sources.contains(QueueSource.GLOBAL))
        assertTrue(sources.contains(QueueSource.FAVORITES))
        assertTrue(sources.contains(QueueSource.RECENTS))
        assertTrue(sources.contains(QueueSource.SEARCH))
        assertTrue(sources.contains(QueueSource.CATEGORY))
        assertTrue(sources.contains(QueueSource.RANKING))
    }

    @Test
    fun `next should iterate only within current context`() {
        val coordinator = com.example.player.coordinator.DefaultPlaybackCoordinator.createForTest()
        val radio1 = createItem("rock1", "Rock 1")
        val radio2 = createItem("rock2", "Rock 2")
        val radio3 = createItem("rock3", "Rock 3")

        // Setup: Contexto com 3 rádios de rock
        val context = NavigationContext(QueueSource.SEARCH, items = listOf(radio1, radio2, radio3))
        coordinator.setNavigationContext(context)
        coordinator.play(radio2)

        // Ação
        coordinator.skipToNext()

        // Assert: Deve tocar radio3, NÃO uma rádio aleatória global
        assertEquals(radio3.mediaId, coordinator.getCurrentItem()?.mediaId)
    }

    @Test
    fun `changing source should update navigation context`() {
        val coordinator = com.example.player.coordinator.DefaultPlaybackCoordinator.createForTest()
        val searchRadio = createItem("search1", "Search Radio")
        val searchResults = listOf(searchRadio, createItem("search2", "Search Radio 2"))

        val favoriteRadio = createItem("fav1", "Favorite 1")
        val favoriteRadio2 = createItem("fav2", "Favorite 2")
        val favorites = listOf(favoriteRadio, favoriteRadio2)

        // Setup: Toca rádio da busca
        coordinator.setNavigationContext(NavigationContext(QueueSource.SEARCH, items = searchResults))
        coordinator.play(searchRadio)

        // Ação: Muda para favoritos
        coordinator.setNavigationContext(NavigationContext(QueueSource.FAVORITES, items = favorites))
        coordinator.play(favoriteRadio)

        // Assert: Next deve usar favoritos
        coordinator.skipToNext()
        assertTrue(favorites.contains(coordinator.getCurrentItem()))
        assertEquals(favoriteRadio2.mediaId, coordinator.getCurrentItem()?.mediaId)
    }

    @Test
    fun `next should loop circular within context`() {
        val coordinator = com.example.player.coordinator.DefaultPlaybackCoordinator.createForTest()
        val radio1 = createItem("fav1", "Favorite 1")
        val radio2 = createItem("fav2", "Favorite 2")

        val context = NavigationContext(QueueSource.FAVORITES, items = listOf(radio1, radio2))
        coordinator.setNavigationContext(context)
        coordinator.play(radio2) // Última da lista

        coordinator.skipToNext()

        assertEquals(radio1.mediaId, coordinator.getCurrentItem()?.mediaId) // Loop para primeira
    }

    @Test
    fun `previous should loop circular reverse within context`() {
        val coordinator = com.example.player.coordinator.DefaultPlaybackCoordinator.createForTest()
        val radio1 = createItem("fav1", "Favorite 1")
        val radio2 = createItem("fav2", "Favorite 2")
        val radio3 = createItem("fav3", "Favorite 3")

        val context = NavigationContext(QueueSource.FAVORITES, items = listOf(radio1, radio2, radio3))
        coordinator.setNavigationContext(context)
        coordinator.play(radio1) // Primeira da lista

        coordinator.skipToPrevious()

        assertEquals(radio3.mediaId, coordinator.getCurrentItem()?.mediaId) // Loop reverso para última
    }
}
