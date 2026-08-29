package com.example

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.db.RadioDatabase
import com.example.data.repository.RadioRepository
import com.example.player.RadioPlayerManager
import com.example.util.IpodSoundAndHaptics

class RadioApp : Application() {

    lateinit var database: RadioDatabase
        private set

    lateinit var repository: RadioRepository
        private set

    lateinit var playerManager: RadioPlayerManager
        private set

    lateinit var soundAndHaptics: IpodSoundAndHaptics
        private set

    lateinit var localMediaRepository: com.example.data.repository.LocalMediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = RadioDatabase.getDatabase(this)
        repository = RadioRepository(database.favoriteStationDao())
        localMediaRepository = com.example.data.repository.LocalMediaRepository(this)
        playerManager = RadioPlayerManager.getInstance(this)
        soundAndHaptics = IpodSoundAndHaptics.getInstance(this)

        // Strict lightweight memory and disk limits for image loading to prevent phone heating & GC pauses
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10) // Limit to 10% heap max
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(15L * 1024 * 1024) // 15MB disk cache max
                    .build()
            }
            .crossfade(false) // Low GPU/memory recomposition overhead
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
