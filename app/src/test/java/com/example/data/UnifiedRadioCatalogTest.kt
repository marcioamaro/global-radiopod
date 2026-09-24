package com.example.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.RadioDatabase
import com.example.data.repository.*
import com.example.util.RadioSearchIndex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UnifiedRadioCatalogTest {
    @Test fun everyRankedRadioExistsInGeneralSearchWithSameStream() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RadioCatalog.initialize(context)
        PublishedRankings.initialize(context)
        val stations = RadioCatalog.stations
        assertTrue(stations.size >= 41551)
        val byId = stations.associateBy { it.id }
        val search = RadioSearchIndex(stations)
        for (key in listOf("radio_brazil", "radio_world")) {
            for (ranked in PublishedRankings.radios(key, 20)) {
                val station = requireNotNull(byId[ranked.id])
                assertEquals(ranked.streamUrl, station.streamUrl)
                assertTrue(search.search(station.name, station.countryCode, null, null, null).any { it.id == station.id })
            }
        }
    }

    @Test fun unchangedCatalogDoesNotRewriteDatabaseOnNextRepositoryInstance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RadioCatalog.initialize(context)
        val prefs = context.getSharedPreferences("catalog-sync-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(context, RadioDatabase::class.java).build()
        try {
            RadioRepository(db.favoriteStationDao(), db.radioStationDao(), catalogPreferences = prefs).getStationsByCountry("BR")
            assertEquals(RadioCatalog.version, prefs.getString("version", null))
            // A trigger proves the second load performs no writes, even for a catalog of the same size.
            db.openHelper.writableDatabase.execSQL("CREATE TABLE sync_probe (attempt INTEGER NOT NULL)")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER record_catalog_rewrite BEFORE DELETE ON stations BEGIN INSERT INTO sync_probe VALUES (1); END")
            prefs.edit().putString("version", RadioCatalog.version).commit()
            RadioRepository(db.favoriteStationDao(), db.radioStationDao(), catalogPreferences = prefs).getStationsByCountry("BR")
            assertEquals(RadioCatalog.stations.size, db.radioStationDao().getStationCount())
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_probe").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
        } finally { db.close() }
    }
}
