package com.marcioamaro.mediapod.data.api

import com.marcioamaro.mediapod.data.model.RadioStationDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RadioBrowserApi {

    @GET("stations/topvote/{limit}")
    suspend fun getTopVotedStations(
        @Path("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioStationDto>

    @GET("stations/topclick/{limit}")
    suspend fun getTopClickedStations(
        @Path("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioStationDto>

    @GET("stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStationDto>

    @GET("stations/bycountryexact/{country}")
    suspend fun getStationsByCountry(
        @Path("country") country: String,
        @Query("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStationDto>

    @GET("stations/bycountrycodeexact/{countrycode}")
    suspend fun getStationsByCountryCode(
        @Path("countrycode") countryCode: String,
        @Query("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStationDto>

    @GET("stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("tag") tag: String? = null,
        @Query("country") country: String? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("state") state: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStationDto>
}

object RadioApiClient {
    private val SERVERS = listOf(
        "https://de1.api.radio-browser.info/json/",
        "https://nl1.api.radio-browser.info/json/",
        "https://at1.api.radio-browser.info/json/",
        "https://all.api.radio-browser.info/json/"
    )

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "iClassicPodWorldRadioApp/1.0 (Android Auto Compatible)")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private var currentApi: RadioBrowserApi? = null
    private var currentServerIndex = 0

    fun getService(): RadioBrowserApi {
        if (currentApi == null) {
            val serverUrl = SERVERS[currentServerIndex % SERVERS.size]
            currentApi = Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(RadioBrowserApi::class.java)
        }
        return currentApi!!
    }

    fun rotateServer(): RadioBrowserApi {
        currentServerIndex = (currentServerIndex + 1) % SERVERS.size
        val serverUrl = SERVERS[currentServerIndex]
        currentApi = Retrofit.Builder()
            .baseUrl(serverUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(RadioBrowserApi::class.java)
        return currentApi!!
    }
}
