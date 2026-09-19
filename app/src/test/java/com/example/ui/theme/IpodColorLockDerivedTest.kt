package com.example.ui.theme

import com.example.data.model.IpodAppearanceSettings
import com.example.data.model.IpodPalette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpodColorLockDerivedTest {

    @Test
    fun testLockIsExclusivelyDerivedFromRandomMode() {
        // Default settings: randomHardwareColorsEnabled is false, manual editing MUST be true
        val defaultSettings = IpodAppearanceSettings()
        assertFalse(defaultSettings.randomHardwareColorsEnabled)
        assertTrue(defaultSettings.isManualColorEditingEnabled)

        // When random mode is enabled, manual editing MUST be false (locked)
        val randomEnabledSettings = defaultSettings.copy(randomHardwareColorsEnabled = true)
        assertTrue(randomEnabledSettings.randomHardwareColorsEnabled)
        assertFalse(randomEnabledSettings.isManualColorEditingEnabled)

        // When random mode is disabled, manual editing MUST be true (unlocked)
        val randomDisabledSettings = randomEnabledSettings.copy(randomHardwareColorsEnabled = false)
        assertFalse(randomDisabledSettings.randomHardwareColorsEnabled)
        assertTrue(randomDisabledSettings.isManualColorEditingEnabled)
    }

    @Test
    fun testNoIndependentLockVariable() {
        // Ensure manual color editing enabled property is purely computed
        val paletteA = IpodPalette(bodyColor = 0xFF111111)
        val paletteB = IpodPalette(bodyColor = 0xFF222222)

        val settings = IpodAppearanceSettings(
            randomHardwareColorsEnabled = true,
            manualPalette = paletteA,
            activePalette = paletteB,
            lastValidPalette = paletteB,
            lastGenerationId = 12345L
        )

        // Only randomHardwareColorsEnabled determines the lock
        assertFalse(settings.isManualColorEditingEnabled)

        val updated = settings.copy(randomHardwareColorsEnabled = false)
        assertTrue(updated.isManualColorEditingEnabled)
    }
}
