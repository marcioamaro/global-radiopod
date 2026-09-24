package com.marcioamaro.mediapod.util

import com.marcioamaro.mediapod.data.repository.RadioRankingRepository
import com.marcioamaro.mediapod.data.repository.PublishedRankings
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ApprovedRadioRankingTest {
    @Test fun popularityListsAreAvailableWithoutInventedHistoricalAudience() = runBlocking {
        PublishedRankings.initialize(ApplicationProvider.getApplicationContext<Context>())
        val repo = RadioRankingRepository()
        assertEquals(20, repo.getTopWorld(20).size)
        assertEquals(20, repo.getTopBrazil(20).size)
        assertEquals("Popularidade", PublishedRankings.info("radio_brazil").period)
        assertEquals("Popularidade", PublishedRankings.info("radio_world").period)
        assertTrue(repo.getTopWorld(-1).isEmpty())
    }
}
