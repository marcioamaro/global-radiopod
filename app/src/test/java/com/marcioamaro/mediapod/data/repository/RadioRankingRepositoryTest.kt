package com.marcioamaro.mediapod.data.repository

import kotlinx.coroutines.runBlocking
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RadioRankingRepositoryTest {
    @Test fun allTwentyStationsRetainPopularityOrder() = runBlocking {
        PublishedRankings.initialize(ApplicationProvider.getApplicationContext<Context>())
        val repo = RadioRankingRepository.getInstance()
        val brazil = repo.getTopBrazil(100)
        val world = repo.getTopWorld(100)
        for (stations in listOf(brazil, world)) {
            assertEquals(20, stations.size)
            assertEquals(20, stations.map { it.id }.distinct().size)
            assertEquals((1..20).toList(), stations.map { it.rankPosition })
            assertTrue(stations.all { it.streamUrl.startsWith("http") })
        }
        assertEquals("Band FM", brazil.first().name)
        assertEquals("Antena 1 FM", brazil[1].name)
        assertTrue(brazil.all { it.countryCode == "BR" })
        assertEquals("RTL", world.first().name)
        assertTrue(world.map { it.countryCode }.distinct().size > 3)
        assertEquals(brazil.take(3), repo.getTopBrazil(3))
        assertTrue(repo.getTopWorld(-1).isEmpty())
    }
}
