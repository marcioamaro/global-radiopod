package com.marcioamaro.mediapod.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.RadioApp
import com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager
import com.marcioamaro.mediapod.data.preferences.IpodWheelPreset
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = RadioApp::class, sdk = [34])
class RadioViewModelColorCustomizationTest {

    private lateinit var app: Application
    private lateinit var prefs: IpodPreferencesManager
    private lateinit var viewModel: RadioViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<RadioApp>()
        prefs = IpodPreferencesManager.getInstance(app)
        // Reset to known state with random mode disabled
        prefs.randomHardwareColorsEnabled = false
        prefs.customBodyColor = 0xFFF1F5F9
        prefs.customWheelColor = 0xFFE2E4E8
        prefs.customWheelTextColor = 0xFF475569
        prefs.customCenterButtonColor = 0xFFFFFFFF
        prefs.chassisTheme = IpodChassisTheme.CLASSIC_SILVER
        prefs.wheelPreset = IpodWheelPreset.CLASSIC_GREY

        viewModel = RadioViewModel(app)
    }

    @Test
    fun testInitialStateReflectsManualPreferences() {
        val state = viewModel.uiState.value
        assertFalse("Random hardware colors should be disabled by default", state.appearanceSettings.randomHardwareColorsEnabled)
        assertTrue("Manual editing must be enabled when random is off", state.appearanceSettings.isManualColorEditingEnabled)
        assertEquals(0xFFF1F5F9, state.customBodyColor)
        assertEquals(0xFFE2E4E8, state.customWheelColor)
    }

    @Test
    fun testEnablingRandomModeLocksManualEditingAndUpdatesColors() {
        viewModel.setRandomHardwareColorsEnabled(true)
        val state = viewModel.uiState.value

        assertTrue(state.appearanceSettings.randomHardwareColorsEnabled)
        assertFalse("Manual editing MUST be locked when random mode is active", state.appearanceSettings.isManualColorEditingEnabled)
        assertTrue(prefs.randomHardwareColorsEnabled)

        val activePalette = state.appearanceSettings.activePalette
        assertEquals(activePalette.bodyColor, state.customBodyColor)
        assertEquals(activePalette.wheelColor, state.customWheelColor)
        assertEquals(activePalette.wheelTextColor, state.customWheelTextColor)
        assertEquals(activePalette.centerButtonColor, state.customCenterButtonColor)
    }

    @Test
    fun testManualColorChangesAreBlockedWhenRandomModeIsActive() {
        // Enable random mode
        viewModel.setRandomHardwareColorsEnabled(true)
        val initialBodyColor = viewModel.uiState.value.customBodyColor
        val initialWheelColor = viewModel.uiState.value.customWheelColor

        // Attempt manual modifications
        viewModel.setCustomBodyColor(0xFFDC2626)
        viewModel.setCustomWheelColor(0xFF000000)
        viewModel.setChassisTheme(IpodChassisTheme.STEALTH_BLACK)
        viewModel.setWheelPreset(IpodWheelPreset.U2_RED)

        val stateAfterAttempts = viewModel.uiState.value
        assertEquals("Body color must NOT change when random mode is active", initialBodyColor, stateAfterAttempts.customBodyColor)
        assertEquals("Wheel color must NOT change when random mode is active", initialWheelColor, stateAfterAttempts.customWheelColor)
    }

    @Test
    fun testDisablingRandomModeRestoresManualPalette() {
        // Set manual colors first
        val manualBody = 0xFF0284C7
        val manualWheel = 0xFF1E293B
        viewModel.setCustomBodyColor(manualBody)
        viewModel.setCustomWheelColors(manualWheel, 0xFFFFFFFF, 0xFFFFFFFF)

        assertEquals(manualBody, viewModel.uiState.value.customBodyColor)
        assertEquals(manualWheel, viewModel.uiState.value.customWheelColor)

        // Turn on random mode
        viewModel.setRandomHardwareColorsEnabled(true)
        assertTrue(viewModel.uiState.value.appearanceSettings.randomHardwareColorsEnabled)
        assertFalse(viewModel.uiState.value.appearanceSettings.isManualColorEditingEnabled)

        // Turn off random mode -> must restore original manual colors
        viewModel.setRandomHardwareColorsEnabled(false)
        val restoredState = viewModel.uiState.value

        assertFalse(restoredState.appearanceSettings.randomHardwareColorsEnabled)
        assertTrue(restoredState.appearanceSettings.isManualColorEditingEnabled)
        assertEquals(manualBody, restoredState.customBodyColor)
        assertEquals(manualWheel, restoredState.customWheelColor)
    }

    @Test
    fun testGenerateNewRandomHardwarePaletteUpdatesSessionPalette() {
        viewModel.setRandomHardwareColorsEnabled(true)
        val genId1 = viewModel.uiState.value.appearanceSettings.lastGenerationId

        viewModel.generateNewRandomHardwarePalette()
        val genId2 = viewModel.uiState.value.appearanceSettings.lastGenerationId

        assertTrue(genId2 >= genId1)
        assertTrue(viewModel.uiState.value.appearanceSettings.randomHardwareColorsEnabled)
    }
}
