package com.marcioamaro.mediapod

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.marcioamaro.mediapod.data.db.RadioDatabase
import com.marcioamaro.mediapod.data.repository.RadioRepository
import com.marcioamaro.mediapod.player.RadioPlayerManager
import com.marcioamaro.mediapod.util.IpodSoundAndHaptics
import com.marcioamaro.mediapod.util.ServiceWatchdogWorker
import kotlinx.coroutines.launch

class RadioApp : Application() {

    lateinit var database: RadioDatabase
        private set

    lateinit var repository: RadioRepository
        private set

    val playerManager: RadioPlayerManager
        get() = RadioPlayerManager.getInstance(this)

    val playbackCoordinator: com.marcioamaro.mediapod.player.coordinator.PlaybackCoordinator
        get() = com.marcioamaro.mediapod.player.coordinator.DefaultPlaybackCoordinator.getInstance(this)

    val downloadManager: com.marcioamaro.mediapod.data.download.PodcastDownloadManager
        get() = com.marcioamaro.mediapod.data.download.PodcastDownloadManager.getInstance(this)

    val streamRecorder: com.marcioamaro.mediapod.player.recorder.RadioStreamRecorder
        get() = com.marcioamaro.mediapod.player.recorder.RadioStreamRecorder.getInstance(this)

    lateinit var soundAndHaptics: IpodSoundAndHaptics
        private set

    lateinit var localMediaRepository: com.marcioamaro.mediapod.data.repository.LocalMediaRepository
        private set

    lateinit var podcastRepository: com.marcioamaro.mediapod.data.repository.PodcastRepository
        private set

    lateinit var clickWheelRepository: com.marcioamaro.mediapod.data.prefs.ClickWheelPreferencesRepository
        private set

    lateinit var clickWheelEngine: com.marcioamaro.mediapod.ui.components.ClickWheelEngine
        private set

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        // CORREÇÃO P1 (auditoria item 2 — 24/09/2026):
        // runBlocking substituído por launch assíncrono. Application.onCreate() roda na Main Thread;
        // runBlocking bloqueava a thread principal durante I/O do RestoreJournal (risco de ANR).
        // RestoreJournal.recover é uma operação de limpeza não-crítica: pode rodar em background.
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        ).launch {
            com.marcioamaro.mediapod.util.RestoreJournal.recover(this@RadioApp)
        }
        com.marcioamaro.mediapod.data.repository.CatalogUpdates.initialize(this)
        com.marcioamaro.mediapod.data.repository.RadioCatalog.initialize(this)
        com.marcioamaro.mediapod.data.repository.PublishedRankings.initialize(this)
        database = RadioDatabase.getDatabase(this)
        repository = RadioRepository(database.favoriteStationDao(), database.radioStationDao(),
            catalogPreferences = getSharedPreferences("catalog_sync", MODE_PRIVATE))
        localMediaRepository = com.marcioamaro.mediapod.data.repository.LocalMediaRepository(this)
        podcastRepository = com.marcioamaro.mediapod.data.repository.PodcastRepository.getInstance(this)
        downloadManager // Recover persisted download queue when the app opens.
        soundAndHaptics = IpodSoundAndHaptics.getInstance(this)
        clickWheelRepository = com.marcioamaro.mediapod.data.prefs.ClickWheelPreferencesRepository.getInstance(this)
        clickWheelEngine = com.marcioamaro.mediapod.ui.components.ClickWheelEngine(
            settingsFlow = clickWheelRepository.clickWheelPreferences,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
        )

        // Strict lightweight memory and disk limits for image loading to prevent phone heating & GC pauses
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(object : coil.intercept.Interceptor {
                    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                        val remote = chain.request.data.toString().startsWith("http://") || chain.request.data.toString().startsWith("https://")
                        if (remote && !com.marcioamaro.mediapod.util.DataUsagePolicy(this@RadioApp).remoteArtwork) {
                            return coil.request.ErrorResult(null, chain.request, java.io.IOException("Remote artwork disabled"))
                        }
                        return chain.proceed(chain.request)
                    }
                })
            }
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
