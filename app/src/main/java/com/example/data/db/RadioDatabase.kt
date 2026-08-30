package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteStationEntity::class, RadioStationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun favoriteStationDao(): FavoriteStationDao
    abstract fun radioStationDao(): RadioStationDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun getDatabase(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "ipod_radio_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
