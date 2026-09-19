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
import com.example.util.ServiceWatchdogWorker

class RadioApp : Application() {

    lateinit var database: RadioDatabase
        private set

    lateinit var repository: RadioRepository
        private set

    val playerManager: RadioPlayerManager
        get() = RadioPlayerManager.getInstance(this)

    val playbackCoordinator: com.example.player.coordinator.PlaybackCoordinator
        get() = com.example.player.coordinator.DefaultPlaybackCoordinator.getInstance(this)

    val downloadManager: com.example.data.download.PodcastDownloadManager
        get() = com.example.data.download.PodcastDownloadManager.getInstance(this)

    val streamRecorder: com.example.player.recorder.RadioStreamRecorder
        get() = com.example.player.recorder.RadioStreamRecorder.getInstance(this)

    lateinit var soundAndHaptics: IpodSoundAndHaptics
        private set

    lateinit var localMediaRepository: com.example.data.repository.LocalMediaRepository
        private set

    lateinit var podcastRepository: com.example.data.repository.PodcastRepository
        private set

    lateinit var clickWheelRepository: com.example.data.prefs.ClickWheelPreferencesRepository
        private set

    lateinit var clickWheelEngine: com.example.ui.components.ClickWheelEngine
        private set

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        database = RadioDatabase.getDatabase(this)
        repository = RadioRepository(database.favoriteStationDao(), database.radioStationDao())
        localMediaRepository = com.example.data.repository.LocalMediaRepository(this)
        podcastRepository = com.example.data.repository.PodcastRepository.getInstance(this)
        soundAndHaptics = IpodSoundAndHaptics.getInstance(this)
        clickWheelRepository = com.example.data.prefs.ClickWheelPreferencesRepository.getInstance(this)
        clickWheelEngine = com.example.ui.components.ClickWheelEngine(
            settingsFlow = clickWheelRepository.clickWheelPreferences,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
        )

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

        // WorkManager Watchdog: reinicia RadioMediaService se morto por OEM agressivo (Samsung/Xiaomi/Huawei)
        // Agenda apenas uma vez (KEEP policy garante que não duplica em restarts da app)
        ServiceWatchdogWorker.schedule(this)
    }
}
