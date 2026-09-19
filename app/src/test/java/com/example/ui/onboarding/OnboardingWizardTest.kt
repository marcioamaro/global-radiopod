package com.example.ui.onboarding

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.prefs.ClickWheelMode
import com.example.data.prefs.ClickWheelPreferencesRepository
import com.example.data.prefs.FixedSpeed
import com.example.data.prefs.OnboardingPreferencesRepository
import com.example.ui.IpodChassisTheme
import com.example.ui.LcdBacklight
import com.example.util.AppLocaleManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingWizardTest {

    private lateinit var context: Context
    private lateinit var onboardingRepo: OnboardingPreferencesRepository
    private lateinit var ipodPrefs: IpodPreferencesManager
    private lateinit var clickWheelRepo: ClickWheelPreferencesRepository
    private lateinit var viewModel: OnboardingWizardViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        onboardingRepo = OnboardingPreferencesRepository.getInstance(context)
        ipodPrefs = IpodPreferencesManager.getInstance(context)
        clickWheelRepo = ClickWheelPreferencesRepository.getInstance(context)

        viewModel = OnboardingWizardViewModel(context.applicationContext as Application)
        val deadline = System.currentTimeMillis() + 2000
        while (!viewModel.uiState.value.isReady && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    @Test
    fun testSupportedLocalesMetadata() {
        val locales = AppLocaleManager.SUPPORTED_LOCALES
        assertTrue(locales.isNotEmpty())
        assertTrue("Must support pt-BR", locales.any { it.tag == "pt-BR" })
        assertTrue("Must support en-US", locales.any { it.tag == "en-US" })
        assertTrue("Must support es-ES", locales.any { it.tag == "es-ES" })
        assertTrue("Must support de-DE", locales.any { it.tag == "de-DE" })
        assertTrue("Must support fr-FR", locales.any { it.tag == "fr-FR" })
        assertTrue("Must support it-IT", locales.any { it.tag == "it-IT" })
        assertTrue("Must support ja-JP", locales.any { it.tag == "ja-JP" })
    }

    @Test
    fun testStepNavigationBounds() {
        assertEquals("Initial step should be 0", 0, viewModel.uiState.value.currentStep)
        assertEquals(6, viewModel.uiState.value.totalSteps)

        // Previous step at 0 should remain 0
        viewModel.previousStep()
        assertEquals(0, viewModel.uiState.value.currentStep)

        // Advance through steps
        viewModel.nextStep() // Step 1: Clock
        assertEquals(1, viewModel.uiState.value.currentStep)

        viewModel.nextStep() // Step 2: Theme
        assertEquals(2, viewModel.uiState.value.currentStep)

        viewModel.nextStep() // Step 3: Typography
        assertEquals(3, viewModel.uiState.value.currentStep)

        viewModel.nextStep() // Step 4: Clickwheel
        assertEquals(4, viewModel.uiState.value.currentStep)

        viewModel.nextStep() // Step 5: Summary
        assertEquals(5, viewModel.uiState.value.currentStep)

        // Cannot advance past step 5
        viewModel.nextStep()
        assertEquals(5, viewModel.uiState.value.currentStep)

        // Step navigation jump
        viewModel.goToStep(2)
        assertEquals(2, viewModel.uiState.value.currentStep)
    }

    @Test
    fun testLanguageSelectionUpdatesImmediately() {
        viewModel.selectLanguage("es-ES")
        assertEquals("es-ES", viewModel.uiState.value.languageTag)

        viewModel.selectLanguage("en-US")
        assertEquals("en-US", viewModel.uiState.value.languageTag)
    }

    @Test
    fun testClockFormatToggle() {
        viewModel.setClock24H(true)
        assertTrue(viewModel.uiState.value.is24HourClock)

        viewModel.setClock24H(false)
        assertFalse(viewModel.uiState.value.is24HourClock)

        val preview = viewModel.getFormattedTimePreview()
        assertNotNull(preview)
        assertTrue(preview.isNotBlank())
    }

    @Test
    fun testThemeAndBacklightSelection() {
        viewModel.setChassisTheme(IpodChassisTheme.SPACE_GRAY)
        assertEquals(IpodChassisTheme.SPACE_GRAY, viewModel.uiState.value.chassisTheme)

        viewModel.setLcdBacklight(LcdBacklight.CLASSIC_BLUE)
        assertEquals(LcdBacklight.CLASSIC_BLUE, viewModel.uiState.value.lcdBacklight)
    }

    @Test
    fun testTypographyAndHighContrastWCAG() {
        viewModel.setFontSizeScale(1.5f)
        assertEquals(1.5f, viewModel.uiState.value.fontSizeScale, 0.01f)

        viewModel.setHighContrast(true)
        assertTrue(viewModel.uiState.value.highContrast)

        viewModel.setHighContrast(false)
        assertFalse(viewModel.uiState.value.highContrast)
    }

    @Test
    fun testClickWheelCalibrationAndSandbox() {
        viewModel.setClickWheelSensitivity(1.25f)
        assertEquals(1.25f, viewModel.uiState.value.clickWheelSensitivity, 0.01f)

        viewModel.setClickWheelMode(ClickWheelMode.FIXED)
        assertEquals(ClickWheelMode.FIXED, viewModel.uiState.value.clickWheelMode)

        viewModel.setClickWheelFixedSpeed(FixedSpeed.FAST)
        assertEquals(FixedSpeed.FAST, viewModel.uiState.value.clickWheelFixedSpeed)

        assertEquals(0, viewModel.uiState.value.sandboxTickCount)
        viewModel.onSandboxWheelSpun(3.4f)
        assertEquals(1, viewModel.uiState.value.sandboxTickCount)
        assertEquals(3.4f, viewModel.uiState.value.sandboxVelocity, 0.01f)
    }

    @Test
    fun testCompleteOnboardingAtomicPersistence() = runBlocking {
        viewModel.selectLanguage("pt-BR")
        viewModel.setClock24H(true)
        viewModel.setChassisTheme(IpodChassisTheme.STEALTH_BLACK)
        viewModel.setLcdBacklight(LcdBacklight.OLED_MATRIX)
        viewModel.setFontSizeScale(1.5f)
        viewModel.setHighContrast(true)
        viewModel.setClickWheelSensitivity(1.0f)
        viewModel.setClickWheelMode(ClickWheelMode.PROGRESSIVE)

        var completionCallbackInvoked = false
        viewModel.completeOnboardingSync {
            completionCallbackInvoked = true
        }

        assertTrue("Callback must be invoked", completionCallbackInvoked)
        assertTrue("UI state completed flag must be true", viewModel.uiState.value.isCompleted)

        // Check IpodPreferencesManager synchronization
        assertEquals(IpodChassisTheme.STEALTH_BLACK, ipodPrefs.chassisTheme)
        assertEquals(LcdBacklight.OLED_MATRIX, ipodPrefs.lcdBacklight)
        assertTrue(ipodPrefs.isFontBold)
    }
}
