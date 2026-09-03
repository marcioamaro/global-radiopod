package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extensão de nível de arquivo para o DataStore (singleton por context)
private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "radiopod_playback_state"
)

/**
 * Persistência crítica de estado de playback via DataStore Preferences.
 *
 * Persiste 3 campos críticos para restauração pós-morte do processo:
 * - [KEY_LAST_STATION_ID]: ID da última estação reproduzida
 * - [KEY_LAST_STATION_URL]: URL do stream da última estação
 * - [KEY_LAST_VOLUME]: Nível de volume (0.0f - 1.0f)
 * - [KEY_LAST_POSITION_MS]: Posição de playback em ms (para podcasts/áudio local)
 *
 * Coexiste com [IpodPreferencesManager] (SharedPreferences) sem conflitos.
 * Não migra dados existentes — cada sistema mantém seu próprio escopo.
 *
 * ## Thread Safety
 * DataStore é coroutine-safe. Todos os métodos são suspend ou retornam Flow.
 */
class PlaybackStateDataStore(private val context: Context) {

    companion object {
        val KEY_LAST_STATION_ID  = stringPreferencesKey("last_station_id")
        val KEY_LAST_STATION_URL = stringPreferencesKey("last_station_url")
        val KEY_LAST_STATION_NAME = stringPreferencesKey("last_station_name")
        val KEY_LAST_VOLUME      = floatPreferencesKey("last_volume")
        val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val KEY_LAST_MEDIA_TYPE  = stringPreferencesKey("last_media_type") // "RADIO", "PODCAST", "LOCAL"

        @Volatile private var INSTANCE: PlaybackStateDataStore? = null

        fun getInstance(context: Context): PlaybackStateDataStore {
            return INSTANCE ?: synchronized(this) {
                PlaybackStateDataStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flows de leitura reativa
    // ─────────────────────────────────────────────────────────────────────────

    val lastStationId: Flow<String?> = context.playbackDataStore.data
        .map { prefs -> prefs[KEY_LAST_STATION_ID] }

    val lastStationUrl: Flow<String?> = context.playbackDataStore.data
        .map { prefs -> prefs[KEY_LAST_STATION_URL] }

    val lastStationName: Flow<String?> = context.playbackDataStore.data
        .map { prefs -> prefs[KEY_LAST_STATION_NAME] }

    val lastVolume: Flow<Float> = context.playbackDataStore.data
        .map { prefs -> prefs[KEY_LAST_VOLUME] ?: 0.8f }

    val lastPositionMs: Flow<Long> = context.playbackDataStore.data
        .map { prefs -> prefs[KEY_LAST_POSITION_MS] ?: 0L }

    val lastMediaType: Flow<String?> = context.playbackDataStore.data
        .map { prefs -> prefs[KEY_LAST_MEDIA_TYPE] }

    // ─────────────────────────────────────────────────────────────────────────
    // Operações de escrita (suspend)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persiste o estado da rádio ao vivo atual.
     * Deve ser chamado ao iniciar uma rádio e ao pausar.
     */
    suspend fun saveRadioState(
        stationId: String,
        stationUrl: String,
        stationName: String,
        volume: Float
    ) {
        context.playbackDataStore.edit { prefs ->
            prefs[KEY_LAST_STATION_ID]   = stationId
            prefs[KEY_LAST_STATION_URL]  = stationUrl
            prefs[KEY_LAST_STATION_NAME] = stationName
            prefs[KEY_LAST_VOLUME]       = volume.coerceIn(0f, 1f)
            prefs[KEY_LAST_MEDIA_TYPE]   = "RADIO"
            prefs[KEY_LAST_POSITION_MS]  = 0L // Live streams não têm posição
        }
    }

    /**
     * Persiste o estado de podcast/áudio local.
     * Deve ser chamado periodicamente durante playback e ao pausar.
     */
    suspend fun saveMediaState(
        mediaId: String,
        mediaUrl: String,
        mediaTitle: String,
        positionMs: Long,
        volume: Float,
        mediaType: String = "PODCAST"
    ) {
        context.playbackDataStore.edit { prefs ->
            prefs[KEY_LAST_STATION_ID]   = mediaId
            prefs[KEY_LAST_STATION_URL]  = mediaUrl
            prefs[KEY_LAST_STATION_NAME] = mediaTitle
            prefs[KEY_LAST_VOLUME]       = volume.coerceIn(0f, 1f)
            prefs[KEY_LAST_POSITION_MS]  = positionMs.coerceAtLeast(0L)
            prefs[KEY_LAST_MEDIA_TYPE]   = mediaType
        }
    }

    /**
     * Atualiza apenas o volume (chamado quando o usuário muda o volume).
     */
    suspend fun saveVolume(volume: Float) {
        context.playbackDataStore.edit { prefs ->
            prefs[KEY_LAST_VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    /**
     * Atualiza apenas a posição (chamado periodicamente para podcasts/MP3).
     */
    suspend fun savePosition(positionMs: Long) {
        context.playbackDataStore.edit { prefs ->
            prefs[KEY_LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    /**
     * Limpa todo o estado persistido (ex: ao fazer logout ou reset).
     */
    suspend fun clear() {
        context.playbackDataStore.edit { it.clear() }
    }
}
