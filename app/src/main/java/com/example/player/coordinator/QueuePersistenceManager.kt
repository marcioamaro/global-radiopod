package com.example.player.coordinator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.player.ActiveMediaType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Estado completo e atômico da fila para persistência e restauração.
 */
data class PersistedQueueState(
    val items: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val repeatMode: QueueRepeatMode = QueueRepeatMode.OFF,
    val isShuffle: Boolean = false,
    val lastUpdatedTimestamp: Long = 0L
)

/**
 * Gerenciador de persistência transacional da fila de reprodução.
 * Garante que rádios, podcasts e faixas locais coexistam na mesma fila,
 * suportando restauração após encerramento do processo, reinicialização ou crash.
 */
class QueuePersistenceManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Salva transacionalmente o estado completo da fila em disco.
     * Utiliza commit() síncrono para garantir atomicidade e evitar estados parciais.
     */
    @Synchronized
    fun saveQueueSnapshot(
        items: List<PlaybackQueueItem>,
        currentIndex: Int,
        positionMs: Long = 0L,
        repeatMode: QueueRepeatMode = QueueRepeatMode.OFF,
        isShuffle: Boolean = false
    ): Boolean {
        return try {
            val jsonArray = JSONArray()
            items.forEach { item ->
                jsonArray.put(item.toJson())
            }

            prefs.edit()
                .putString(KEY_QUEUE_JSON, jsonArray.toString())
                .putInt(KEY_CURRENT_INDEX, currentIndex.coerceIn(-1, items.size - 1))
                .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
                .putString(KEY_REPEAT_MODE, repeatMode.name)
                .putBoolean(KEY_SHUFFLE, isShuffle)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .commit()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao persistir snapshot da fila transacional: ${e.message}")
            false
        }
    }

    /**
     * Restaura o snapshot completo da fila persistida.
     */
    @Synchronized
    fun loadQueueSnapshot(): PersistedQueueState {
        return try {
            val rawJson = prefs.getString(KEY_QUEUE_JSON, null)
            if (rawJson.isNullOrBlank()) {
                return PersistedQueueState()
            }

            val jsonArray = JSONArray(rawJson)
            val items = mutableListOf<PlaybackQueueItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                obj.toPlaybackQueueItem()?.let { items.add(it) }
            }

            val index = prefs.getInt(KEY_CURRENT_INDEX, -1).coerceIn(-1, (items.size - 1).coerceAtLeast(-1))
            val pos = prefs.getLong(KEY_POSITION_MS, 0L)
            val repStr = prefs.getString(KEY_REPEAT_MODE, QueueRepeatMode.OFF.name) ?: QueueRepeatMode.OFF.name
            val repeatMode = try { QueueRepeatMode.valueOf(repStr) } catch (_: Exception) { QueueRepeatMode.OFF }
            val isShuffle = prefs.getBoolean(KEY_SHUFFLE, false)
            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)

            PersistedQueueState(
                items = items,
                currentIndex = index,
                positionMs = pos,
                repeatMode = repeatMode,
                isShuffle = isShuffle,
                lastUpdatedTimestamp = timestamp
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao carregar snapshot da fila: ${e.message}")
            PersistedQueueState()
        }
    }

    /**
     * Adiciona item ao histórico de reprodução com limite de retenção e deduplicação do mais recente.
     */
    @Synchronized
    fun addToHistory(item: PlaybackQueueItem) {
        try {
            val currentHistory = getHistory().toMutableList()
            currentHistory.removeAll { it.id == item.id }
            currentHistory.add(0, item)

            val trimmed = if (currentHistory.size > MAX_HISTORY_SIZE) {
                currentHistory.subList(0, MAX_HISTORY_SIZE)
            } else {
                currentHistory
            }

            val jsonArray = JSONArray()
            trimmed.forEach { jsonArray.put(it.toJson()) }
            prefs.edit().putString(KEY_HISTORY_JSON, jsonArray.toString()).commit()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar histórico de reprodução: ${e.message}")
        }
    }

    /**
     * Retorna a lista dos itens reproduzidos recentemente.
     */
    @Synchronized
    fun getHistory(): List<PlaybackQueueItem> {
        return try {
            val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<PlaybackQueueItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                obj.toPlaybackQueueItem()?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ler histórico: ${e.message}")
            emptyList()
        }
    }

    /**
     * Limpa a fila persistida.
     */
    @Synchronized
    fun clearQueue() {
        prefs.edit()
            .remove(KEY_QUEUE_JSON)
            .remove(KEY_CURRENT_INDEX)
            .remove(KEY_POSITION_MS)
            .remove(KEY_REPEAT_MODE)
            .remove(KEY_SHUFFLE)
            .remove(KEY_TIMESTAMP)
            .commit()
    }

    /**
     * Limpa o histórico.
     */
    @Synchronized
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY_JSON).commit()
    }

    companion object {
        private const val TAG = "QueuePersistence"
        private const val PREFS_NAME = "radiopod_persistent_queue"
        private const val KEY_QUEUE_JSON = "key_queue_json"
        private const val KEY_CURRENT_INDEX = "key_current_index"
        private const val KEY_POSITION_MS = "key_position_ms"
        private const val KEY_REPEAT_MODE = "key_repeat_mode"
        private const val KEY_SHUFFLE = "key_shuffle"
        private const val KEY_HISTORY_JSON = "key_history_json"
        private const val KEY_TIMESTAMP = "key_timestamp"
        private const val MAX_HISTORY_SIZE = 50

        @Volatile
        private var INSTANCE: QueuePersistenceManager? = null

        fun getInstance(context: Context): QueuePersistenceManager {
            return INSTANCE ?: synchronized(this) {
                val instance = QueuePersistenceManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        @androidx.annotation.VisibleForTesting
        fun clearInstanceForTesting() {
            INSTANCE = null
        }

        fun PlaybackQueueItem.toJson(): JSONObject {
            val json = JSONObject()
            json.put("id", id)
            json.put("mediaUri", mediaUri)
            json.put("title", title)
            if (subtitle != null) json.put("subtitle", subtitle)
            if (artworkUri != null) json.put("artworkUri", artworkUri)
            json.put("mediaType", mediaType.name)
            if (durationMs != null) json.put("durationMs", durationMs)
            json.put("isLiveStream", isLiveStream)
            return json
        }

        fun JSONObject.toPlaybackQueueItem(): PlaybackQueueItem? {
            val id = optString("id")
            val mediaUri = optString("mediaUri")
            val title = optString("title")
            if (id.isBlank() || mediaUri.isBlank() || title.isBlank()) return null
            val subtitle = if (has("subtitle") && !isNull("subtitle")) optString("subtitle") else null
            val artworkUri = if (has("artworkUri") && !isNull("artworkUri")) optString("artworkUri") else null
            val mediaTypeStr = optString("mediaType", ActiveMediaType.LIVE_RADIO.name)
            val mediaType = try {
                ActiveMediaType.valueOf(mediaTypeStr)
            } catch (_: Exception) {
                ActiveMediaType.LIVE_RADIO
            }
            val durationMs = if (has("durationMs") && !isNull("durationMs")) optLong("durationMs") else null
            val isLiveStream = optBoolean("isLiveStream", mediaType == ActiveMediaType.LIVE_RADIO)
            return PlaybackQueueItem(
                id = id,
                mediaUri = mediaUri,
                title = title,
                subtitle = subtitle,
                artworkUri = artworkUri,
                mediaType = mediaType,
                durationMs = durationMs,
                isLiveStream = isLiveStream
            )
        }
    }
}
