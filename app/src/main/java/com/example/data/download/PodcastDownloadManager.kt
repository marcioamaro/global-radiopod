package com.example.data.download

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.example.data.model.PodcastEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val activeJobs = mutableMapOf<String, Job>()

    init {
        loadPersistedIndex()
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

    fun startDownload(episode: PodcastEpisode) {
        if (isDownloaded(episode.id)) {
            Log.d(TAG, "Episódio já baixado: ${episode.id}")
            return
        }

        if (isWifiOnlyEnabled() && !isConnectedToWifi()) {
            updateStatus(episode.id, DownloadStatus.Failed(episode.id, "Download restrito a redes Wi-Fi"))
            return
        }

        activeJobs[episode.id]?.cancel()

        updateStatus(episode.id, DownloadStatus.Queued(episode.id))

        val job = scope.launch {
            val sanitizedName = "pod_${episode.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}"
            val targetFile = File(downloadDir, "$sanitizedName.mp3")
            val tempFile = File(downloadDir, "$sanitizedName.tmp")

            var connection: HttpURLConnection? = null
            var input: InputStream? = null
            var output: FileOutputStream? = null

            try {
                val url = URL(episode.audioUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Servidor retornou HTTP ${connection.responseCode}")
                }

                val totalLength = connection.contentLengthLong
                input = connection.inputStream
                output = FileOutputStream(tempFile)

                val buffer = ByteArray(8192)
                var bytesDownloaded = 0L
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesDownloaded += read

                    val percent = if (totalLength > 0) {
                        ((bytesDownloaded * 100) / totalLength).toInt().coerceIn(0, 100)
                    } else 0

                    updateStatus(
                        episode.id,
                        DownloadStatus.Downloading(episode.id, percent, bytesDownloaded, totalLength)
                    )
                }

                output.flush()
                output.close()
                output = null

                // Renomeação atômica
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    throw IllegalStateException("Falha ao renomear arquivo temporário para definitivo")
                }

                persistEpisodeRecord(episode, targetFile.absolutePath, targetFile.length())
                updateStatus(episode.id, DownloadStatus.Completed(episode.id, targetFile.absolutePath, targetFile.length()))
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                updateStatus(episode.id, DownloadStatus.Failed(episode.id, e.message ?: "Erro desconhecido"))
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { output?.close() } catch (_: Exception) {}
                try { connection?.disconnect() } catch (_: Exception) {}
                activeJobs.remove(episode.id)
            }
        }

        activeJobs[episode.id] = job
    }

    fun cancelDownload(episodeId: String) {
        activeJobs[episodeId]?.cancel()
        activeJobs.remove(episodeId)
        val sanitizedName = "pod_${episodeId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}"
        val tempFile = File(downloadDir, "$sanitizedName.tmp")
        if (tempFile.exists()) tempFile.delete()
        updateStatus(episodeId, DownloadStatus.NotDownloaded)
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

    private fun updateStatus(episodeId: String, status: DownloadStatus) {
        val updated = _statusMap.value.toMutableMap()
        updated[episodeId] = status
        _statusMap.value = updated
    }

    private fun persistEpisodeRecord(episode: PodcastEpisode, localPath: String, fileSize: Long) {
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

    private fun removeEpisodeRecord(episodeId: String) {
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
            INSTANCE = null
        }
    }
}
