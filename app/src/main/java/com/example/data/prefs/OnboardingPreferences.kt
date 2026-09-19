package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.IpodChassisTheme
import com.example.ui.LcdBacklight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding_preferences")

data class OnboardingConfig(
    val isOnboardingCompleted: Boolean = false,
    val currentStepIndex: Int = 0,
    val languageTag: String = "pt-BR",
    val is24HourClock: Boolean = true,
    val chassisTheme: String = IpodChassisTheme.CLASSIC_SILVER.name,
    val lcdBacklight: String = LcdBacklight.RETRO_IPOD_LCD.name,
    val fontSizeScale: Float = 1.0f,
    val highContrast: Boolean = true,
    val clickWheelSensitivity: Float = 1.0f,
    val clickWheelMode: String = ClickWheelMode.PROGRESSIVE.name,
    val clickWheelFixedSpeed: String = FixedSpeed.STANDARD.name
)

class OnboardingPreferencesRepository private constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_IS_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_STEP_INDEX = intPreferencesKey("onboarding_step_index")
        val KEY_LANGUAGE_TAG = stringPreferencesKey("onboarding_language_tag")
        val KEY_IS_24H_CLOCK = booleanPreferencesKey("onboarding_is_24h_clock")
        val KEY_CHASSIS_THEME = stringPreferencesKey("onboarding_chassis_theme")
        val KEY_LCD_BACKLIGHT = stringPreferencesKey("onboarding_lcd_backlight")
        val KEY_FONT_SIZE_SCALE = floatPreferencesKey("onboarding_font_size_scale")
        val KEY_HIGH_CONTRAST = booleanPreferencesKey("onboarding_high_contrast")
        val KEY_WHEEL_SENSITIVITY = floatPreferencesKey("onboarding_wheel_sensitivity")
        val KEY_WHEEL_MODE = stringPreferencesKey("onboarding_wheel_mode")
        val KEY_WHEEL_FIXED_SPEED = stringPreferencesKey("onboarding_wheel_fixed_speed")

        @Volatile
        private var INSTANCE: OnboardingPreferencesRepository? = null

        fun getInstance(context: Context): OnboardingPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnboardingPreferencesRepository(context.applicationContext.onboardingDataStore).also {
                    INSTANCE = it
                }
            }
        }
    }

    val onboardingConfig: Flow<OnboardingConfig> = dataStore.data.map { prefs ->
        OnboardingConfig(
            isOnboardingCompleted = prefs[KEY_IS_COMPLETED] ?: false,
            currentStepIndex = prefs[KEY_STEP_INDEX] ?: 0,
            languageTag = prefs[KEY_LANGUAGE_TAG] ?: "pt-BR",
            is24HourClock = prefs[KEY_IS_24H_CLOCK] ?: true,
            chassisTheme = prefs[KEY_CHASSIS_THEME] ?: IpodChassisTheme.CLASSIC_SILVER.name,
            lcdBacklight = prefs[KEY_LCD_BACKLIGHT] ?: LcdBacklight.RETRO_IPOD_LCD.name,
            fontSizeScale = prefs[KEY_FONT_SIZE_SCALE] ?: 1.0f,
            highContrast = prefs[KEY_HIGH_CONTRAST] ?: true,
            clickWheelSensitivity = prefs[KEY_WHEEL_SENSITIVITY] ?: 1.0f,
            clickWheelMode = prefs[KEY_WHEEL_MODE] ?: ClickWheelMode.PROGRESSIVE.name,
            clickWheelFixedSpeed = prefs[KEY_WHEEL_FIXED_SPEED] ?: FixedSpeed.STANDARD.name
        )
    }

    suspend fun savePartialProgress(stepIndex: Int, config: OnboardingConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_STEP_INDEX] = stepIndex
            prefs[KEY_LANGUAGE_TAG] = config.languageTag
            prefs[KEY_IS_24H_CLOCK] = config.is24HourClock
            prefs[KEY_CHASSIS_THEME] = config.chassisTheme
            prefs[KEY_LCD_BACKLIGHT] = config.lcdBacklight
            prefs[KEY_FONT_SIZE_SCALE] = config.fontSizeScale
            prefs[KEY_HIGH_CONTRAST] = config.highContrast
            prefs[KEY_WHEEL_SENSITIVITY] = config.clickWheelSensitivity
            prefs[KEY_WHEEL_MODE] = config.clickWheelMode
            prefs[KEY_WHEEL_FIXED_SPEED] = config.clickWheelFixedSpeed
        }
    }

    suspend fun completeOnboarding(finalConfig: OnboardingConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_COMPLETED] = true
            prefs[KEY_STEP_INDEX] = 5
            prefs[KEY_LANGUAGE_TAG] = finalConfig.languageTag
            prefs[KEY_IS_24H_CLOCK] = finalConfig.is24HourClock
            prefs[KEY_CHASSIS_THEME] = finalConfig.chassisTheme
            prefs[KEY_LCD_BACKLIGHT] = finalConfig.lcdBacklight
            prefs[KEY_FONT_SIZE_SCALE] = finalConfig.fontSizeScale
            prefs[KEY_HIGH_CONTRAST] = finalConfig.highContrast
            prefs[KEY_WHEEL_SENSITIVITY] = finalConfig.clickWheelSensitivity
            prefs[KEY_WHEEL_MODE] = finalConfig.clickWheelMode
            prefs[KEY_WHEEL_FIXED_SPEED] = finalConfig.clickWheelFixedSpeed
        }
    }

    suspend fun resetOnboarding() {
        dataStore.edit { prefs ->
            prefs[KEY_IS_COMPLETED] = false
            prefs[KEY_STEP_INDEX] = 0
        }
    }
}
