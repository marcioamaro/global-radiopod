package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.RadioStation

@Entity(tableName = "favorite_stations")
data class FavoriteStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val homepage: String,
    val tags: String,
    val country: String,
    val countryCode: String,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
    val addedTimestamp: Long = System.currentTimeMillis()
)

fun FavoriteStationEntity.toDomain(): RadioStation {
    return RadioStation(
        id = id,
        name = name,
        streamUrl = streamUrl,
        favicon = favicon,
        homepage = homepage,
        tags = tags,
        country = country,
        countryCode = countryCode,
        codec = codec,
        bitrate = bitrate,
        votes = votes,
        isFavorite = true
    )
}

fun RadioStation.toEntity(): FavoriteStationEntity {
    return FavoriteStationEntity(
        id = id,
        name = name,
        streamUrl = streamUrl,
        favicon = favicon,
        homepage = homepage,
        tags = tags,
        country = country,
        countryCode = countryCode,
        codec = codec,
        bitrate = bitrate,
        votes = votes
    )
}
