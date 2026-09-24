package com.marcioamaro.mediapod.util

import android.app.Application
import androidx.room.Room
import com.marcioamaro.mediapod.data.db.RadioDatabase
import com.marcioamaro.mediapod.data.db.toCatalogEntity
import com.marcioamaro.mediapod.data.db.toEntity
import com.marcioamaro.mediapod.data.model.RadioStation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RadioCatalogRefreshTest {
    @Test fun sameSizeCatalogAppliesRemovalsCorrectionsAndPreservesFavorites() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RadioDatabase::class.java).allowMainThreadQueries().build()
        try {
            val old = RadioStation("old", "Old station", "https://example.org/old")
            val atual = RadioStation("atual", "Atual", "https://example.org/wrong")
            val corrected = atual.copy(streamUrl = "https://example.org/correct")
            val added = RadioStation("new", "New station", "https://example.org/new")
            db.radioStationDao().insertStations(listOf(old, atual).map { it.toCatalogEntity() })
            db.favoriteStationDao().insertFavorite(old.toEntity())
            db.radioStationDao().replaceCatalog(listOf(corrected, added).map { it.toCatalogEntity() })
            assertNull(db.radioStationDao().getStationById("old"))
            assertEquals(corrected.streamUrl, db.radioStationDao().getStationById("atual")?.streamUrl)
            assertNotNull(db.radioStationDao().getStationById("new"))
            assertTrue(db.favoriteStationDao().isFavoriteDirect("old"))
            assertEquals(2, db.radioStationDao().getStationCount())
        } finally {
            db.close()
        }
    }
}
