package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class DialTunerStationDto(
    val id: Long? = null,
    val name: String = "",
    val stream: String? = null,
    val backup: String? = null,
    val cover: String? = null,
    val format: String? = null,
    val bitrate: Int? = null,
    val freq: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val cc: String? = null,
    val genre: String? = null,
    val genres: List<String>? = null,
    val url: String? = null
)

interface DialTunerApi {
    @GET("wp-json/longwave/v1/search")
    suspend fun search(
        @Query("q") query: String
    ): List<DialTunerStationDto>
}

object DialTunerApiClient {
    private const val BASE_URL = "https://dialtuner.com.br/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: DialTunerApi = retrofit.create(DialTunerApi::class.java)
}
