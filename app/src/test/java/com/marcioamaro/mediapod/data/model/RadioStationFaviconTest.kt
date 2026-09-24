package com.marcioamaro.mediapod.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationFaviconTest {

    @Test
    fun testPlaceholderFaviconsDetection() {
        val placeholders = listOf(
            "https://tudoradio.com/img/layout/icone_tudoradio.jpg",
            "http://tudoradio.com/img/layout/icone_tudoradio.jpg",
            "https://www.tudoradio.com/img/layout/logo.png",
            "https://radios.com.br/img/logo_default.png",
            "https://radios.com.br/img/placeholder.jpg",
            "https://example.com/assets/default_radio.png",
            "https://example.com/no_image.png",
            "https://example.com/no-image.jpg",
            "https://example.com/images/no_logo.jpg",
            "https://example.com/placeholder.svg",
            "",
            "   "
        )

        for (url in placeholders) {
            assertTrue("Expected placeholder for: $url", RadioStation.isPlaceholderFavicon(url))
        }
    }

    @Test
    fun testValidFaviconsPass() {
        val validUrls = listOf(
            "https://radioglobo.globo.com/logo.png",
            "https://cdn.jovempan.com.br/jp_fm.jpg",
            "https://statick.akamaized.net/antena1.png",
            "https://alphafm.com.br/favicon.ico"
        )

        for (url in validUrls) {
            assertFalse("Expected valid for: $url", RadioStation.isPlaceholderFavicon(url))
        }
    }

    @Test
    fun testRadioStationEffectiveFavicon() {
        val stationWithPlaceholder = RadioStation(
            id = "test_1",
            name = "Rádio Teste",
            streamUrl = "https://stream.test/live",
            favicon = "https://tudoradio.com/img/layout/icone_tudoradio.jpg"
        )

        assertEquals("", stationWithPlaceholder.effectiveFavicon)
        assertFalse(stationWithPlaceholder.hasValidFavicon)

        val stationWithRealLogo = RadioStation(
            id = "test_2",
            name = "Rádio Real",
            streamUrl = "https://stream.real/live",
            favicon = "https://radioreal.com/logo.png"
        )

        assertEquals("https://radioreal.com/logo.png", stationWithRealLogo.effectiveFavicon)
        assertTrue(stationWithRealLogo.hasValidFavicon)
    }
}
