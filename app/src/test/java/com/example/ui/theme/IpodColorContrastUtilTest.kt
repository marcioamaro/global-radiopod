package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpodColorContrastUtilTest {

    @Test
    fun testCalculateRelativeLuminance() {
        // WCAG relative luminance for pure black is 0.0
        val blackLumLong = IpodColorContrastUtil.calculateRelativeLuminance(0xFF000000)
        assertEquals(0.0, blackLumLong, 0.001)

        val blackLumColor = IpodColorContrastUtil.calculateRelativeLuminance(Color.Black)
        assertEquals(0.0, blackLumColor, 0.001)

        // WCAG relative luminance for pure white is 1.0
        val whiteLumLong = IpodColorContrastUtil.calculateRelativeLuminance(0xFFFFFFFF)
        assertEquals(1.0, whiteLumLong, 0.001)

        val whiteLumColor = IpodColorContrastUtil.calculateRelativeLuminance(Color.White)
        assertEquals(1.0, whiteLumColor, 0.001)

        // Mid-gray (0x808080) relative luminance
        val grayLum = IpodColorContrastUtil.calculateRelativeLuminance(Color(0xFF808080))
        assertTrue("Luminance of mid-gray must be between 0 and 1", grayLum > 0.0 && grayLum < 1.0)
    }

    @Test
    fun testGetContrastRatio() {
        // Pure black against pure white has maximum ratio 21.0
        val bwContrast = IpodColorContrastUtil.getContrastRatio(Color.White, Color.Black)
        assertEquals(21.0, bwContrast, 0.1)

        // Symmetry: ratio(A, B) == ratio(B, A)
        val wbContrast = IpodColorContrastUtil.getContrastRatio(Color.Black, Color.White)
        assertEquals(bwContrast, wbContrast, 0.001)

        // Same color contrast is 1.0
        val sameContrast = IpodColorContrastUtil.getContrastRatio(Color(0xFF808080), Color(0xFF808080))
        assertEquals(1.0, sameContrast, 0.01)
    }

    @Test
    fun testAdaptivePearLogoColor_RestrictedToGrayscale() {
        val testColors = listOf(
            Color.Black,
            Color.White,
            Color.Red,
            Color.Blue,
            Color.Green,
            Color(0xFF1E293B),
            Color(0xFFE2E8F0),
            Color(0xFF808080)
        )

        for (bg in testColors) {
            val result = IpodColorContrastUtil.getAdaptivePearLogoColor(bg)
            assertTrue(
                "Adaptive pear logo must strictly be either LOGO_LIGHT_GRAY or LOGO_DARK_GRAY",
                result == IpodColorContrastUtil.LOGO_LIGHT_GRAY || result == IpodColorContrastUtil.LOGO_DARK_GRAY
            )
        }
    }

    @Test
    fun testAdaptivePearLogoColor_DarkBackgrounds() {
        // Dark backgrounds must select LOGO_LIGHT_GRAY (#E0E0E0) with contrast >= 3.0:1 (WCAG 1.4.11)
        val darkBackgrounds = listOf(
            Color.Black,
            Color(0xFF0F172A), // Dark Navy
            Color(0xFF1E293B), // Slate Dark
            Color(0xFF7F1D1D)  // Deep Red
        )

        for (bg in darkBackgrounds) {
            val result = IpodColorContrastUtil.getAdaptivePearLogoColor(bg)
            assertEquals(
                "Dark background $bg must select LOGO_LIGHT_GRAY",
                IpodColorContrastUtil.LOGO_LIGHT_GRAY,
                result
            )
            val contrast = IpodColorContrastUtil.getContrastRatio(bg, result)
            assertTrue(
                "Contrast ratio $contrast must meet WCAG 1.4.11 non-text minimum (>= 3.0:1)",
                contrast >= 3.0
            )
        }
    }

    @Test
    fun testAdaptivePearLogoColor_LightBackgrounds() {
        // Light backgrounds must select LOGO_DARK_GRAY (#4A4A4A) with contrast >= 3.0:1 (WCAG 1.4.11)
        val lightBackgrounds = listOf(
            Color.White,
            Color(0xFFF1F5F9), // Slate Light
            Color(0xFFFEF3C7), // Light Amber
            Color(0xFFE2E8F0)  // Light Gray
        )

        for (bg in lightBackgrounds) {
            val result = IpodColorContrastUtil.getAdaptivePearLogoColor(bg)
            assertEquals(
                "Light background $bg must select LOGO_DARK_GRAY",
                IpodColorContrastUtil.LOGO_DARK_GRAY,
                result
            )
            val contrast = IpodColorContrastUtil.getContrastRatio(bg, result)
            assertTrue(
                "Contrast ratio $contrast must meet WCAG 1.4.11 non-text minimum (>= 3.0:1)",
                contrast >= 3.0
            )
        }
    }

    @Test
    fun testAdaptivePearLogoColor_SafetyFallback() {
        // Null adjacent color fallback
        val nullResult = IpodColorContrastUtil.getAdaptivePearLogoColor(null as Color?)
        assertEquals(IpodColorContrastUtil.LOGO_LIGHT_GRAY, nullResult)

        // Long overload test
        val darkLong = IpodColorContrastUtil.getAdaptivePearLogoColor(0xFF000000)
        assertEquals(IpodColorContrastUtil.LOGO_LIGHT_GRAY_HEX, darkLong)

        val lightLong = IpodColorContrastUtil.getAdaptivePearLogoColor(0xFFFFFFFF)
        assertEquals(IpodColorContrastUtil.LOGO_DARK_GRAY_HEX, lightLong)
    }
}
