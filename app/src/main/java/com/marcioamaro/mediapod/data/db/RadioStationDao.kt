package com.marcioamaro.mediapod.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Query("SELECT * FROM stations ORDER BY name COLLATE NOCASE ASC")
    fun getAllStationsFlow(): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM stations ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllStationsDirect(): List<RadioStationEntity>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int

    @Query("SELECT * FROM stations WHERE countryCode = :countryCode OR country LIKE '%' || :countryCode || '%' ORDER BY name COLLATE NOCASE ASC")
    suspend fun getStationsByCountry(countryCode: String): List<RadioStationEntity>

    @Query("SELECT * FROM stations WHERE (countryCode = 'BR' OR country LIKE '%Brasil%') AND (:stateCode IS NULL OR state = :stateCode OR state LIKE '%' || :stateCode || '%') ORDER BY name COLLATE NOCASE ASC")
    suspend fun getStationsByState(stateCode: String?): List<RadioStationEntity>

    @Query("SELECT * FROM stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: String): RadioStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RadioStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RadioStationEntity)

    @Query("DELETE FROM stations")
    suspend fun clearStations()

    @Transaction
    suspend fun replaceCatalog(stations: List<RadioStationEntity>) {
        clearStations()
        insertStations(stations)
    }

}
