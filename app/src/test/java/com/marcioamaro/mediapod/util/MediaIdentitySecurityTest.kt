package com.marcioamaro.mediapod.util

import com.marcioamaro.mediapod.data.model.PodcastShow
import org.junit.Assert.*
import org.junit.Test

class MediaIdentitySecurityTest {
    @Test fun secureStreamsArePreferredAndLegacyHttpRemainsAvailable() {
        val station = com.marcioamaro.mediapod.data.model.RadioStation("id", "Radio", "http://example.org/live",
            alternativeStreamUrls = listOf("https://example.org/secure"))
        assertEquals("https://example.org/secure", station.getAllStreamCandidates().first())
        assertTrue(station.getAllStreamCandidates().contains("http://example.org/live"))
    }
    @Test fun youtubeNavigationRejectsUntrustedOriginsAndCredentials() {
        for (url in listOf("https://www.youtube.com/watch?v=123", "https://m.youtube.com", "https://accounts.google.com")) {
            assertTrue(url, YouTubeNavigationPolicy.allowsNavigation(url))
        }
        for (url in listOf("http://youtube.com", "https://youtube.com.evil.org", "https://youtube.com@evil.org", "file:///video", "javascript:alert(1)", "https://evil.org", "https://www.youtube.com:8080")) {
            assertFalse(url, YouTubeNavigationPolicy.allowsNavigation(url))
        }
        assertFalse(YouTubeNavigationPolicy.isPlayerOrigin("https://accounts.google.com"))
    }

    @Test fun feedRecoveryRequiresExactTitleAndPublisher() {
        val original = PodcastShow("a", "Ciência Hoje", author = "Editora Alfa", feedUrl = "https://example.org/old")
        assertTrue(PodcastIdentity.samePublisherAndTitle(original, original.copy(title = "Ciencia hoje", feedUrl = "https://example.org/new")))
        assertFalse(PodcastIdentity.samePublisherAndTitle(original, original.copy(title = "Ciência Hoje — Cortes")))
        assertFalse(PodcastIdentity.samePublisherAndTitle(original, original.copy(author = "Outra pessoa")))
        assertFalse(PodcastIdentity.samePublisherAndTitle(original.copy(author = ""), original.copy(author = "")))
    }
}
