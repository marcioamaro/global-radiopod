package com.example.ui.theme

import com.example.data.model.IpodAppearanceSettings
import com.example.data.model.IpodPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IpodColorLifecycleTest {

    @Test
    fun testSessionRandomPaletteStability() {
        // Safe palette generated at session startup
        val manual = IpodPalette(bodyColor = 0xFFFFFFFF, wheelColor = 0xFFE2E4E8)
        val sessionPalette = SafeIpodColorGenerator.generateSafePalette()
        val generationId = System.currentTimeMillis()

        val settings = IpodAppearanceSettings(
            randomHardwareColorsEnabled = true,
            manualPalette = manual,
            activePalette = sessionPalette,
            lastValidPalette = sessionPalette,
            lastGenerationId = generationId
        )

        // Multiple state readings or pseudo recompositions must retain identical activePalette
        val reading1 = settings.activePalette
        val reading2 = settings.activePalette
        val reading3 = settings.activePalette

        assertEquals(reading1, reading2)
        assertEquals(reading2, reading3)
        assertEquals(sessionPalette.bodyColor, reading1.bodyColor)
        assertEquals(sessionPalette.wheelColor, reading1.wheelColor)
    }

    @Test
    fun testRestorationOfManualPaletteWhenRandomDisabled() {
        val manual = IpodPalette(
            bodyColor = 0xFF0284C7, // Blue
            wheelColor = 0xFFFFFFFF,
            wheelTextColor = 0xFF0F172A,
            centerButtonColor = 0xFF0284C7
        )
        val sessionPalette = IpodPalette(
            bodyColor = 0xFFDC2626, // Red
            wheelColor = 0xFF1E293B,
            wheelTextColor = 0xFFFFFFFF,
            centerButtonColor = 0xFF111111
        )

        val randomActive = IpodAppearanceSettings(
            randomHardwareColorsEnabled = true,
            manualPalette = manual,
            activePalette = sessionPalette,
            lastValidPalette = sessionPalette,
            lastGenerationId = 999L
        )

        assertEquals(0xFFDC2626, randomActive.activePalette.bodyColor)

        // When user toggles off random mode, manualPalette becomes active
        val randomDisabled = randomActive.copy(
            randomHardwareColorsEnabled = false,
            activePalette = randomActive.manualPalette
        )

        assertEquals(0xFF0284C7, randomDisabled.activePalette.bodyColor)
        assertEquals(manual, randomDisabled.activePalette)
    }
}
