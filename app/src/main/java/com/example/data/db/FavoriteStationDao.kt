package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteStationDao {
    @Query("SELECT * FROM favorite_stations ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteStationEntity>>

    @Query("SELECT * FROM favorite_stations ORDER BY addedTimestamp DESC")
    suspend fun getAllFavoritesDirect(): List<FavoriteStationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stations WHERE id = :stationId)")
    fun isFavorite(stationId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stations WHERE id = :stationId)")
    suspend fun isFavoriteDirect(stationId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteStationEntity)

    @Query("DELETE FROM favorite_stations WHERE id = :stationId")
    suspend fun deleteFavoriteById(stationId: String)

    @Query("DELETE FROM favorite_stations")
    suspend fun deleteAllFavorites()

    @Delete
    suspend fun deleteFavorite(entity: FavoriteStationEntity)
}
