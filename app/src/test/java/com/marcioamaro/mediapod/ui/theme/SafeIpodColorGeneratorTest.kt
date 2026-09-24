package com.marcioamaro.mediapod.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SafeIpodColorGeneratorTest {

    @Test
    fun testLuminanceCalculations() {
        // WCAG relative luminance for pure black is 0.0
        val blackLum = IpodColorContrastUtil.calculateLuminance(0xFF000000)
        assertEquals(0.0, blackLum, 0.001)

        // WCAG relative luminance for pure white is 1.0
        val whiteLum = IpodColorContrastUtil.calculateLuminance(0xFFFFFFFF)
        assertEquals(1.0, whiteLum, 0.001)

        // Mid-grey has higher luminance than black and lower than white
        val greyLum = IpodColorContrastUtil.calculateLuminance(0xFF808080)
        assertTrue(greyLum > blackLum)
        assertTrue(greyLum < whiteLum)
    }

    @Test
    fun testContrastRatios() {
        // Pure black against pure white has maximum ratio 21.0
        val bwContrast = IpodColorContrastUtil.calculateContrastRatio(0xFFFFFFFF, 0xFF000000)
        assertEquals(21.0, bwContrast, 0.1)

        // Identical colors have contrast 1.0
        val sameContrast = IpodColorContrastUtil.calculateContrastRatio(0xFF123456, 0xFF123456)
        assertEquals(1.0, sameContrast, 0.01)

        // WCAG AA compliance checks
        assertTrue(IpodColorContrastUtil.isWcagNormalTextCompliant(0xFFFFFFFF, 0xFF000000))
        assertTrue(IpodColorContrastUtil.isWcagLargeTextCompliant(0xFFFFFFFF, 0xFF000000))

        assertFalse(IpodColorContrastUtil.isWcagNormalTextCompliant(0xFF888888, 0xFF999999))
        assertFalse(IpodColorContrastUtil.isWcagLargeTextCompliant(0xFF888888, 0xFF999999))
    }

    @Test
    fun testRejectionOfProhibitedCombinations() {
        // 1. Black on black must be rejected
        val blackOnBlack = IpodColorContrastUtil.isHardwareCombinationValid(
            bodyColor = 0xFF0F172A,
            wheelColor = 0xFF0F172A,
            centerButtonColor = 0xFF0F172A
        )
        assertFalse("Black on black combination must be rejected", blackOnBlack)

        // 2. Dark red on dark red must be rejected
        val darkRedOnDarkRed = IpodColorContrastUtil.isHardwareCombinationValid(
            bodyColor = 0xFF400000,
            wheelColor = 0xFF420000,
            centerButtonColor = 0xFFFFFFFF
        )
        assertFalse("Low contrast dark red on dark red must be rejected", darkRedOnDarkRed)

        // 3. Very low contrast body and wheel must be rejected
        val lowContrastBodyWheel = IpodColorContrastUtil.isHardwareCombinationValid(
            bodyColor = 0xFFE0E0E0,
            wheelColor = 0xFFE2E2E2,
            centerButtonColor = 0xFF000000
        )
        assertFalse("Low contrast body and wheel must be rejected", lowContrastBodyWheel)

        // 4. Legitimate high-contrast combination (e.g. Classic iPod or U2) must be valid
        val validClassic = IpodColorContrastUtil.isHardwareCombinationValid(
            bodyColor = 0xFFF1F5F9, // Silver
            wheelColor = 0xFFE2E4E8, // Grey wheel
            centerButtonColor = 0xFFFFFFFF // White center
        )
        // Check U2 Edition
        val validU2 = IpodColorContrastUtil.isHardwareCombinationValid(
            bodyColor = 0xFF0F172A, // Black body
            wheelColor = 0xFFDC2626, // Red wheel
            centerButtonColor = 0xFF111111 // Black center
        )
        assertTrue("U2 Edition combination should be valid", validU2)
    }

    @Test
    fun testSafeRandomGeneratorProducesCompliantPalettes() {
        // Run 100 random generations and verify all meet contrast and safety requirements
        val testRandom = Random(42)
        repeat(100) {
            val palette = SafeIpodColorGenerator.generateSafePalette(testRandom)

            // Must be a valid combination
            val isValid = IpodColorContrastUtil.isHardwareCombinationValid(
                palette.bodyColor,
                palette.wheelColor,
                palette.centerButtonColor
            )
            assertTrue("Generated palette must be valid: $palette", isValid)

            // Click wheel text contrast against wheel must be >= 3.0:1
            val textContrast = IpodColorContrastUtil.calculateContrastRatio(
                palette.wheelTextColor,
                palette.wheelColor
            )
            assertTrue("Wheel text contrast must be at least 3.0:1 (was $textContrast)", textContrast >= 3.0)
        }
    }
}
