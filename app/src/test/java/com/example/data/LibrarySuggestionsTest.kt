package com.example.data

import com.example.data.model.*
import com.example.data.repository.LibrarySuggestions
import org.junit.Assert.*
import org.junit.Test

class LibrarySuggestionsTest {
    private fun episode(id: String, minutes: Int) = PodcastEpisode(id, "show", "Show", id, audioUrl = "https://example.org/$id", durationMs = minutes * 60000L)
    @Test fun smartFiltersUseKnownDurationPlayedStateAndExplicitLimit() {
        val episodes = listOf(episode("played", 10), episode("short", 15), episode("long", 90), episode("unknown", 0))
        assertEquals(listOf("short", "long"), LibrarySuggestions.playlist(episodes, LibrarySuggestions.Mode.UNPLAYED,
            setOf("played"), emptySet(), emptySet(), 20, 2).map { it.id })
        assertEquals(listOf("played", "short"), LibrarySuggestions.playlist(episodes, LibrarySuggestions.Mode.SHORT,
            emptySet(), emptySet(), emptySet(), 20, 20).map { it.id })
        assertEquals(listOf("long"), LibrarySuggestions.playlist(episodes, LibrarySuggestions.Mode.DOWNLOADED,
            emptySet(), setOf("long"), emptySet(), 20, 20).map { it.id })
    }
    @Test fun discoveryIsOptInAndHistoryCanBeExcluded() {
        val science = PodcastShow("science", "Ciência", feedUrl = "https://example.org/science", category = "Ciência")
        val music = PodcastShow("music", "Música", feedUrl = "https://example.org/music", category = "Música")
        val catalog = listOf(science, music)
        assertTrue(LibrarySuggestions.discover(catalog, false, "ciência", listOf(music), true, emptySet()).isEmpty())
        assertEquals(listOf(science), LibrarySuggestions.discover(catalog, true, "ciência", listOf(music), false, emptySet()))
        assertEquals(listOf(music), LibrarySuggestions.discover(catalog, true, "", listOf(music), true, emptySet()))
        assertTrue(LibrarySuggestions.discover(catalog, true, "", emptyList(), false, emptySet()).isEmpty())
    }
}
