package com.example.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.IpodFontSizeScale
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.prefs.ClickWheelMode
import com.example.data.prefs.ClickWheelPreferencesRepository
import com.example.data.prefs.FixedSpeed
import com.example.data.prefs.OnboardingConfig
import com.example.data.prefs.OnboardingPreferencesRepository
import com.example.ui.IpodChassisTheme
import com.example.ui.LcdBacklight
import com.example.util.AppLocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OnboardingUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 6,
    val languageTag: String = "pt-BR",
    val is24HourClock: Boolean = true,
    val chassisTheme: IpodChassisTheme = IpodChassisTheme.CLASSIC_SILVER,
    val lcdBacklight: LcdBacklight = LcdBacklight.RETRO_IPOD_LCD,
    val fontSizeScale: Float = 1.0f,
    val highContrast: Boolean = true,
    val clickWheelSensitivity: Float = 1.0f,
    val clickWheelMode: ClickWheelMode = ClickWheelMode.PROGRESSIVE,
    val clickWheelFixedSpeed: FixedSpeed = FixedSpeed.STANDARD,
    val sandboxTickCount: Int = 0,
    val sandboxVelocity: Float = 0f,
    val isCompleted: Boolean = false,
    val isReady: Boolean = false
)

class OnboardingWizardViewModel @JvmOverloads constructor(
    application: Application,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : AndroidViewModel(application) {

    private val scope = coroutineScope ?: kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val onboardingRepo = OnboardingPreferencesRepository.getInstance(application)
    private val clickWheelRepo = ClickWheelPreferencesRepository.getInstance(application)
    private val ipodPrefs = IpodPreferencesManager.getInstance(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val initialConfig = onboardingRepo.onboardingConfig.first()
            val detectedLocale = AppLocaleManager.getCurrentLanguageTag()
            val languageToUse = if (initialConfig.languageTag.isNotBlank() && initialConfig.languageTag != "auto") {
                initialConfig.languageTag
            } else {
                detectedLocale
            }

            val parsedChassis = runCatching { IpodChassisTheme.valueOf(initialConfig.chassisTheme) }
                .getOrDefault(IpodChassisTheme.CLASSIC_SILVER)
            val parsedBacklight = runCatching { LcdBacklight.valueOf(initialConfig.lcdBacklight) }
                .getOrDefault(LcdBacklight.RETRO_IPOD_LCD)
            val parsedMode = runCatching { ClickWheelMode.valueOf(initialConfig.clickWheelMode) }
                .getOrDefault(ClickWheelMode.PROGRESSIVE)
            val parsedSpeed = runCatching { FixedSpeed.valueOf(initialConfig.clickWheelFixedSpeed) }
                .getOrDefault(FixedSpeed.STANDARD)

            _uiState.update { current ->
                if (!current.isReady) {
                    current.copy(
                        currentStep = initialConfig.currentStepIndex.coerceIn(0, 5),
                        languageTag = if (current.languageTag != "pt-BR") current.languageTag else languageToUse,
                        is24HourClock = initialConfig.is24HourClock,
                        chassisTheme = parsedChassis,
                        lcdBacklight = parsedBacklight,
                        fontSizeScale = initialConfig.fontSizeScale,
                        highContrast = initialConfig.highContrast,
                        clickWheelSensitivity = initialConfig.clickWheelSensitivity,
                        clickWheelMode = parsedMode,
                        clickWheelFixedSpeed = parsedSpeed,
                        isCompleted = initialConfig.isOnboardingCompleted,
                        isReady = true
                    )
                } else {
                    current.copy(isReady = true)
                }
            }
        }
    }

    fun selectLanguage(tag: String) {
        _uiState.update { it.copy(languageTag = tag) }
        AppLocaleManager.applyLocale(tag)
        persistPartialProgress()
    }

    fun setClock24H(is24h: Boolean) {
        _uiState.update { it.copy(is24HourClock = is24h) }
        persistPartialProgress()
    }

    fun setChassisTheme(theme: IpodChassisTheme) {
        _uiState.update { it.copy(chassisTheme = theme) }
        persistPartialProgress()
    }

    fun setLcdBacklight(backlight: LcdBacklight) {
        _uiState.update { it.copy(lcdBacklight = backlight) }
        persistPartialProgress()
    }

    fun setFontSizeScale(scale: Float) {
        _uiState.update { it.copy(fontSizeScale = scale) }
        persistPartialProgress()
    }

    fun setHighContrast(enabled: Boolean) {
        _uiState.update { it.copy(highContrast = enabled) }
        persistPartialProgress()
    }

    fun setClickWheelSensitivity(sensitivity: Float) {
        _uiState.update { it.copy(clickWheelSensitivity = sensitivity) }
        persistPartialProgress()
    }

    fun setClickWheelMode(mode: ClickWheelMode) {
        _uiState.update { it.copy(clickWheelMode = mode) }
        persistPartialProgress()
    }

    fun setClickWheelFixedSpeed(speed: FixedSpeed) {
        _uiState.update { it.copy(clickWheelFixedSpeed = speed) }
        persistPartialProgress()
    }

    fun onSandboxWheelSpun(velocity: Float) {
        _uiState.update {
            it.copy(
                sandboxTickCount = it.sandboxTickCount + 1,
                sandboxVelocity = velocity
            )
        }
    }

    fun nextStep() {
        if (_uiState.value.currentStep < _uiState.value.totalSteps - 1) {
            _uiState.update { it.copy(currentStep = it.currentStep + 1) }
            persistPartialProgress()
        }
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 0) {
            _uiState.update { it.copy(currentStep = it.currentStep - 1) }
            persistPartialProgress()
        }
    }

    fun goToStep(step: Int) {
        val clamped = step.coerceIn(0, _uiState.value.totalSteps - 1)
        _uiState.update { it.copy(currentStep = clamped) }
        persistPartialProgress()
    }

    private fun persistPartialProgress() {
        val current = _uiState.value
        scope.launch {
            val config = OnboardingConfig(
                isOnboardingCompleted = false,
                currentStepIndex = current.currentStep,
                languageTag = current.languageTag,
                is24HourClock = current.is24HourClock,
                chassisTheme = current.chassisTheme.name,
                lcdBacklight = current.lcdBacklight.name,
                fontSizeScale = current.fontSizeScale,
                highContrast = current.highContrast,
                clickWheelSensitivity = current.clickWheelSensitivity,
                clickWheelMode = current.clickWheelMode.name,
                clickWheelFixedSpeed = current.clickWheelFixedSpeed.name
            )
            onboardingRepo.savePartialProgress(current.currentStep, config)
        }
    }

    suspend fun completeOnboardingSync(onCompleted: () -> Unit = {}) {
        val current = _uiState.value
        val finalConfig = OnboardingConfig(
            isOnboardingCompleted = true,
            currentStepIndex = 5,
            languageTag = current.languageTag,
            is24HourClock = current.is24HourClock,
            chassisTheme = current.chassisTheme.name,
            lcdBacklight = current.lcdBacklight.name,
            fontSizeScale = current.fontSizeScale,
            highContrast = current.highContrast,
            clickWheelSensitivity = current.clickWheelSensitivity,
            clickWheelMode = current.clickWheelMode.name,
            clickWheelFixedSpeed = current.clickWheelFixedSpeed.name
        )

        // Atomic commit to DataStore
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(2500) {
                onboardingRepo.completeOnboarding(finalConfig)
            }
        }

        // Sync to ClickWheel preferences
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(2500) {
                clickWheelRepo.updateClickWheelMode(current.clickWheelMode)
                clickWheelRepo.updateFixedSpeed(current.clickWheelFixedSpeed)
            }
        }

        // Sync to IpodPreferencesManager
        ipodPrefs.chassisTheme = current.chassisTheme
        ipodPrefs.lcdBacklight = current.lcdBacklight
        val fontScaleEnum = when {
            current.fontSizeScale >= 2.5f -> IpodFontSizeScale.SCALE_250
            current.fontSizeScale >= 2.0f -> IpodFontSizeScale.SCALE_200
            current.fontSizeScale >= 1.5f -> IpodFontSizeScale.SCALE_150
            else -> IpodFontSizeScale.SCALE_100
        }
        ipodPrefs.fontSizeScale = fontScaleEnum
        ipodPrefs.isFontBold = current.highContrast

        _uiState.value = _uiState.value.copy(isCompleted = true)
        onCompleted()
    }

    fun completeOnboarding(onCompleted: () -> Unit = {}) {
        scope.launch {
            completeOnboardingSync(onCompleted)
        }
    }

    fun getFormattedTimePreview(): String {
        val pattern = if (_uiState.value.is24HourClock) "HH:mm:ss" else "hh:mm:ss a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date())
    }

    companion object {
        fun provideFactory(application: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return OnboardingWizardViewModel(application) as T
                }
            }
    }
}
