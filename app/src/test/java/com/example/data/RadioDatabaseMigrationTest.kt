package com.example.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.RadioDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RadioDatabaseMigrationTest {
    @Test fun upgradeFromVersionOnePreservesFavoriteAndTimestamp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("""CREATE TABLE favorite_stations (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, streamUrl TEXT NOT NULL,
                favicon TEXT NOT NULL, homepage TEXT NOT NULL, tags TEXT NOT NULL,
                country TEXT NOT NULL, countryCode TEXT NOT NULL, codec TEXT NOT NULL,
                bitrate INTEGER NOT NULL, votes INTEGER NOT NULL, addedTimestamp INTEGER NOT NULL)""")
            db.execSQL("INSERT INTO favorite_stations VALUES ('saved','Minha rádio','https://example.org/live','','','jazz','Brasil','BR','AAC',128,0,1234)")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, RadioDatabase::class.java, name)
            .addMigrations(RadioDatabase.MIGRATION_1_2).build()
        try {
            val saved = db.favoriteStationDao().getAllFavoritesDirect().single()
            assertEquals("saved", saved.id)
            assertEquals(1234L, saved.addedTimestamp)
            assertEquals(0, db.radioStationDao().getStationCount())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
