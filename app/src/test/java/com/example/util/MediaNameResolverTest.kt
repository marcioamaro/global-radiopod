package com.example.util

import com.example.data.repository.CuratedData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNameResolverTest {

    @Test
    fun testExtractUrlFromText() {
        // Teste com URL pura
        val url1 = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(url1, MediaNameResolver.extractUrlFromText(url1))

        // Teste com texto antes e depois (compartilhamento WhatsApp/Telegram)
        val textWithUrl = "Veja este clipe incrível: https://youtu.be/dQw4w9WgXcQ no YouTube!"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", MediaNameResolver.extractUrlFromText(textWithUrl))

        // Teste com parênteses e pontuação
        val textParen = "Ouça aqui (https://stream.servidor.com/live.mp3)."
        assertEquals("https://stream.servidor.com/live.mp3", MediaNameResolver.extractUrlFromText(textParen))

        // Teste com prefixo omitido
        val textNoScheme = "youtu.be/dQw4w9WgXcQ?si=abcdef"
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abcdef", MediaNameResolver.extractUrlFromText(textNoScheme))
    }

    @Test
    fun testYouTubeUrlValidatorWithEmbeddedText() {
        val sharedText = "Dá uma olhada nisso: https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared"
        val id = YouTubeUrlValidator.extractVideoId(sharedText)
        assertEquals("dQw4w9WgXcQ", id)

        val validation = YouTubeUrlValidator.validateUrl(sharedText)
        assertTrue(validation is YouTubeValidationResult.Success)
        val success = validation as YouTubeValidationResult.Success
        assertEquals("dQw4w9WgXcQ", success.videoId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", success.cleanUrl)
    }

    @Test
    fun testResolveRadioNameFromCuratedStations() = runBlocking {
        val firstCurated = CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull { it.streamUrl.isNotBlank() }
        if (firstCurated != null) {
            val resolvedName = MediaNameResolver.resolveRadioName(firstCurated.streamUrl)
            assertNotNull(resolvedName)
            assertEquals(firstCurated.name, resolvedName)
        }
    }

    @Test
    fun testResolveYouTubeTitleViaOEmbed() = runBlocking {
        // Vídeo público do Rick Astley
        val title = MediaNameResolver.resolveYouTubeTitle("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(title)
        assertTrue("O título deve conter Never Gonna Give You Up", title!!.contains("Never Gonna Give You Up", ignoreCase = true))
    }
}
