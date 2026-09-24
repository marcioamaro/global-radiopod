package com.marcioamaro.mediapod.data.download

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gerenciador robusto de downloads offline para episódios de podcast.
 * Suporta restrição Wi-Fi, integridade transacional de arquivo (*.tmp -> *.mp3),
 * cancelamento, exclusão, verificação de espaço e observabilidade reativa de progresso.
 */
class PodcastDownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _statusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val statusMap: StateFlow<Map<String, DownloadStatus>> = _statusMap.asStateFlow()

    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val connections = java.util.concurrent.ConcurrentHashMap<String, HttpURLConnection>()
    private val slots = Semaphore(2)
    private val cancelling = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val episodeAdapter = com.squareup.moshi.Moshi.Builder().build().adapter(PodcastEpisode::class.java)

    init {
        loadPersistedIndex()
        prefs.all.filterKeys { it.startsWith("pending_") }.forEach { (key, value) ->
            val episode = runCatching { episodeAdapter.fromJson(value as String) }.getOrNull()
            if (episode != null) {
                if (prefs.getBoolean("paused_${episode.id}", false)) updateStatus(episode.id, DownloadStatus.Paused(episode.id, 0))
                else startDownload(episode)
            }
        }
    }

    private val downloadDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun isWifiOnlyEnabled(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnlyPreference(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    private fun isConnectedToWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isDownloaded(episodeId: String): Boolean {
        val path = prefs.getString("path_$episodeId", null) ?: return false
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    fun getLocalFilePath(episodeId: String): String? {
        val path = prefs.getString("path_$episodeId", null) ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private fun partialFile(id: String): File {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(downloadDir, "pod_$hash.tmp")
    }

    @Synchronized fun startDownload(episode: PodcastEpisode) {
        if (episode.id in cancelling || isDownloaded(episode.id) || activeJobs[episode.id]?.isActive == true) return
        activeJobs[episode.id]?.takeUnless { it.isCompleted }?.let { previous ->
            scope.launch { previous.join(); startDownload(episode) }
            return
        }
        check(prefs.edit().putString("pending_${episode.id}", episodeAdapter.toJson(episode))
            .putBoolean("paused_${episode.id}", false).commit())
        updateStatus(episode.id, DownloadStatus.Queued(episode.id))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                slots.withPermit {
                    if (isWifiOnlyEnabled() && !isConnectedToWifi()) error("Conecte ao Wi-Fi e toque em retomar")
                    val partial = partialFile(episode.id)
                    val validator = File(partial.path + ".validator")
                    val jobContext = coroutineContext
                    ResumableTransfer.transfer(episode.audioUrl, partial, validator, {
                        jobContext.ensureActive()
                        if (isWifiOnlyEnabled() && !isConnectedToWifi()) error("Conecte ao Wi-Fi e toque em retomar")
                    }, { bytes, total ->
                        val percent = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0
                        updateStatus(episode.id, DownloadStatus.Downloading(episode.id, percent, bytes, total))
                    }, { connection ->
                        if (connection == null) connections.remove(episode.id) else connections[episode.id] = connection
                    })
                    coroutineContext.ensureActive()
                    val target = File(partial.path.removeSuffix(".tmp") + ".mp3")
                    check(partial.renameTo(target)) { "Não foi possível finalizar o arquivo" }
                    persistEpisodeRecord(episode, target.absolutePath, target.length())
                    prefs.edit().remove("pending_${episode.id}").remove("paused_${episode.id}").commit()
                    validator.delete()
                    updateStatus(episode.id, DownloadStatus.Completed(episode.id, target.absolutePath, target.length()))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (coroutineContext[Job]?.isActive == true) {
                    com.marcioamaro.mediapod.util.Diagnostics.record(context, com.marcioamaro.mediapod.util.Diagnostics.Event.DOWNLOAD_FAILED)
                    prefs.edit().putBoolean("paused_${episode.id}", true).commit()
                    updateStatus(episode.id, DownloadStatus.Failed(episode.id, "Não foi possível concluir. Verifique rede e espaço e tente retomar."))
                }
            } finally { activeJobs.remove(episode.id, coroutineContext[Job]) }
        }
        activeJobs[episode.id] = job
        job.start()
    }

    @Synchronized fun pauseDownload(episodeId: String) {
        prefs.edit().putBoolean("paused_$episodeId", true).commit()
        activeJobs[episodeId]?.cancel()
        connections.remove(episodeId)?.disconnect()
        val progress = (_statusMap.value[episodeId] as? DownloadStatus.Downloading)?.progressPercent ?: 0
        updateStatus(episodeId, DownloadStatus.Paused(episodeId, progress))
    }

    fun cancelDownload(episodeId: String) {
        cancelling.add(episodeId)
        pauseDownload(episodeId)
        scope.launch {
            activeJobs[episodeId]?.join()
            partialFile(episodeId).delete()
            File(partialFile(episodeId).path + ".validator").delete()
            prefs.edit().remove("pending_$episodeId").remove("paused_$episodeId").commit()
            updateStatus(episodeId, DownloadStatus.NotDownloaded)
            cancelling.remove(episodeId)
        }
    }

    fun deleteDownload(episodeId: String): Boolean {
        cancelDownload(episodeId)
        val path = prefs.getString("path_$episodeId", null)
        val deleted = if (path != null) {
            val f = File(path)
            if (f.exists()) f.delete() else false
        } else false

        removeEpisodeRecord(episodeId)
        updateStatus(episodeId, DownloadStatus.NotDownloaded)
        return deleted
    }

    @Synchronized private fun updateStatus(episodeId: String, status: DownloadStatus) {
        val updated = _statusMap.value.toMutableMap()
        updated[episodeId] = status
        _statusMap.value = updated
    }

    @Synchronized private fun persistEpisodeRecord(episode: PodcastEpisode, localPath: String, fileSize: Long) {
        val list = getDownloadedEpisodes().toMutableList()
        list.removeAll { it.id == episode.id }
        val updatedEpisode = episode.copy(localFilePath = localPath)
        list.add(0, updatedEpisode)

        val jsonArray = JSONArray()
        list.forEach { ep ->
            val obj = JSONObject().apply {
                put("id", ep.id)
                put("showId", ep.showId)
                put("showTitle", ep.showTitle)
                put("title", ep.title)
                put("description", ep.description)
                put("audioUrl", ep.audioUrl)
                put("durationMs", ep.durationMs)
                put("publishDate", ep.publishDate)
                put("artworkUrl", ep.artworkUrl)
                put("localFilePath", ep.localFilePath)
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putString("path_${episode.id}", localPath)
            .putString(KEY_DOWNLOAD_INDEX, jsonArray.toString())
            .commit()
    }

    @Synchronized private fun removeEpisodeRecord(episodeId: String) {
        val list = getDownloadedEpisodes().filterNot { it.id == episodeId }
        val jsonArray = JSONArray()
        list.forEach { ep ->
            val obj = JSONObject().apply {
                put("id", ep.id)
                put("showId", ep.showId)
                put("showTitle", ep.showTitle)
                put("title", ep.title)
                put("description", ep.description)
                put("audioUrl", ep.audioUrl)
                put("durationMs", ep.durationMs)
                put("publishDate", ep.publishDate)
                put("artworkUrl", ep.artworkUrl)
                put("localFilePath", ep.localFilePath)
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .remove("path_$episodeId")
            .putString(KEY_DOWNLOAD_INDEX, jsonArray.toString())
            .commit()
    }

    fun getDownloadedEpisodes(): List<PodcastEpisode> {
        val raw = prefs.getString(KEY_DOWNLOAD_INDEX, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PodcastEpisode>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val path = o.optString("localFilePath")
                if (path.isNotBlank() && File(path).exists()) {
                    list.add(
                        PodcastEpisode(
                            id = o.optString("id"),
                            showId = o.optString("showId"),
                            showTitle = o.optString("showTitle"),
                            title = o.optString("title"),
                            description = o.optString("description"),
                            audioUrl = o.optString("audioUrl"),
                            durationMs = o.optLong("durationMs"),
                            publishDate = o.optString("publishDate"),
                            artworkUrl = o.optString("artworkUrl"),
                            localFilePath = path
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler episódios baixados: ${e.message}")
            emptyList()
        }
    }

    fun getTotalStorageUsedBytes(): Long {
        return getDownloadedEpisodes().sumOf { ep ->
            ep.localFilePath?.let { File(it).length() } ?: 0L
        }
    }

    fun cleanupStorage(maxAllowedBytes: Long) {
        var currentSize = getTotalStorageUsedBytes()
        if (currentSize <= maxAllowedBytes) return

        val episodes = getDownloadedEpisodes().sortedBy { ep ->
            ep.localFilePath?.let { File(it).lastModified() } ?: 0L
        }

        for (ep in episodes) {
            if (currentSize <= maxAllowedBytes) break
            val size = ep.localFilePath?.let { File(it).length() } ?: 0L
            deleteDownload(ep.id)
            currentSize -= size
        }
    }

    private fun loadPersistedIndex() {
        val map = mutableMapOf<String, DownloadStatus>()
        getDownloadedEpisodes().forEach { ep ->
            val path = ep.localFilePath
            if (path != null && File(path).exists()) {
                map[ep.id] = DownloadStatus.Completed(ep.id, path, File(path).length())
            }
        }
        _statusMap.value = map
    }

    fun registerCompletedDownload(episode: PodcastEpisode, localPath: String, fileSize: Long = 0L) {
        persistEpisodeRecord(episode, localPath, fileSize)
        updateStatus(episode.id, DownloadStatus.Completed(episode.id, localPath, fileSize))
    }

    companion object {
        private const val TAG = "PodcastDownloadMgr"
        private const val PREFS_NAME = "radiopod_downloads"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_DOWNLOAD_INDEX = "download_index_json"

        @Volatile
        private var INSTANCE: PodcastDownloadManager? = null

        fun getInstance(context: Context): PodcastDownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PodcastDownloadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        @androidx.annotation.VisibleForTesting
        fun clearInstanceForTesting() {
            INSTANCE?.scope?.coroutineContext?.get(Job)?.cancel()
            INSTANCE?.connections?.values?.forEach { it.disconnect() }
            INSTANCE = null
        }
    }
}
