package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ClickWheelMode {
    PROGRESSIVE,
    FIXED
}

enum class FixedSpeed(val multiplier: Int) {
    SLOW(1),
    STANDARD(2),
    FAST(5);

    fun displayName(): String = when (this) {
        SLOW -> "🐢 Lenta"
        STANDARD -> "⚙️ Padrão"
        FAST -> "🚀 Rápida"
    }
}

data class ClickWheelPreferences(
    val mode: ClickWheelMode = ClickWheelMode.PROGRESSIVE,
    val fixedSpeed: FixedSpeed = FixedSpeed.STANDARD
)

val Context.clickWheelDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "click_wheel_settings"
)

class ClickWheelPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val MODE_KEY = stringPreferencesKey("click_wheel_mode")
        val SPEED_KEY = stringPreferencesKey("click_wheel_fixed_speed")

        @Volatile
        private var INSTANCE: ClickWheelPreferencesRepository? = null

        fun getInstance(context: Context): ClickWheelPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClickWheelPreferencesRepository(context.applicationContext.clickWheelDataStore).also {
                    INSTANCE = it
                }
            }
        }
    }

    val clickWheelPreferences: Flow<ClickWheelPreferences> = dataStore.data.map { prefs ->
        val mode = prefs[MODE_KEY]?.let { raw ->
            runCatching { ClickWheelMode.valueOf(raw) }.getOrNull()
        } ?: ClickWheelMode.PROGRESSIVE

        val speed = prefs[SPEED_KEY]?.let { raw ->
            runCatching { FixedSpeed.valueOf(raw) }.getOrNull()
        } ?: FixedSpeed.STANDARD

        ClickWheelPreferences(mode = mode, fixedSpeed = speed)
    }

    suspend fun updateClickWheelMode(mode: ClickWheelMode) {
        dataStore.edit { prefs ->
            prefs[MODE_KEY] = mode.name
        }
    }

    suspend fun updateFixedSpeed(speed: FixedSpeed) {
        dataStore.edit { prefs ->
            prefs[SPEED_KEY] = speed.name
        }
    }
}
