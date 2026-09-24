package com.marcioamaro.mediapod.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.marcioamaro.mediapod.data.model.RadioStation

@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["countryCode"]),
        Index(value = ["state"]),
        Index(value = ["name"]),
        Index(value = ["city"])
    ]
)
data class RadioStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val favicon: String = "",
    val homepage: String = "",
    val tags: String = "",
    val country: String = "Brasil",
    val countryCode: String = "BR",
    val state: String = "",
    val city: String = "",
    val codec: String = "AAC",
    val bitrate: Int = 128,
    val votes: Int = 0
)

fun RadioStationEntity.toDomain(isFavorite: Boolean = false): RadioStation {
    return RadioStation(
        id = id,
        name = name,
        streamUrl = streamUrl,
        favicon = favicon,
        homepage = homepage,
        tags = tags,
        country = country,
        countryCode = countryCode,
        state = state,
        city = city,
        codec = codec,
        bitrate = bitrate,
        votes = votes,
        isFavorite = isFavorite
    )
}

fun RadioStation.toCatalogEntity(): RadioStationEntity {
    return RadioStationEntity(
        id = id,
        name = name,
        streamUrl = streamUrl,
        favicon = favicon,
        homepage = homepage,
        tags = tags,
        country = country,
        countryCode = countryCode,
        state = state,
        city = city,
        codec = codec,
        bitrate = bitrate,
        votes = votes
    )
}
