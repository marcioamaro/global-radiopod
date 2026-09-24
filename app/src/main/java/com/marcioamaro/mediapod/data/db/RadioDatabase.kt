package com.marcioamaro.mediapod.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteStationEntity::class, RadioStationEntity::class],
    version = 2,
    exportSchema = true
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun favoriteStationDao(): FavoriteStationDao
    abstract fun radioStationDao(): RadioStationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS stations (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, streamUrl TEXT NOT NULL,
                    favicon TEXT NOT NULL, homepage TEXT NOT NULL, tags TEXT NOT NULL,
                    country TEXT NOT NULL, countryCode TEXT NOT NULL, state TEXT NOT NULL,
                    city TEXT NOT NULL, codec TEXT NOT NULL, bitrate INTEGER NOT NULL, votes INTEGER NOT NULL
                )""")
                for (column in listOf("countryCode", "state", "name", "city")) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_stations_$column ON stations ($column)")
                }
            }
        }
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun getDatabase(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "ipod_radio_database.db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
