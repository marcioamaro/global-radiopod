package com.marcioamaro.mediapod.util

import com.marcioamaro.mediapod.data.model.RadioStation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/** Immutable normalized catalog, built once off the UI thread. */
class RadioSearchIndex(stations: List<RadioStation>) {
    private data class Entry(val station: RadioStation, val text: String, val city: String, val name: String)
    private val entries = stations.sortedBy { it.name.trim().lowercase(java.util.Locale.ROOT) }.map {
        Entry(it, RadioSearchEngine.searchableText(it), RadioSearchEngine.normalize(it.city), RadioSearchEngine.normalize(it.name))
    }
    private val genres = ConcurrentHashMap<String, Set<String>>()

    suspend fun search(query: String, country: String?, genre: String?, state: String?, city: String?): List<RadioStation> {
        val tokens = RadioSearchEngine.normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        val cityText = RadioSearchEngine.normalize(city)
        val brazil = country.equals("BR", ignoreCase = true)
        val context = currentCoroutineContext()
        val genreIds = genre?.let { tag ->
            genres[tag] ?: buildSet {
                entries.forEachIndexed { index, e ->
                    if (index % 256 == 0) context.ensureActive()
                    if (RadioSearchEngine.matchesGenre(e.station, tag)) add(e.station.id)
                }
            }.also { genres[tag] = it }
        }
        return buildList {
            entries.forEachIndexed { index, e ->
                if (index % 256 == 0) context.ensureActive()
                val s = e.station
                if (country != null && !s.countryCode.equals(country, true) && !(brazil && s.country.contains("Brasil", true))) return@forEachIndexed
                if (genreIds != null && s.id !in genreIds) return@forEachIndexed
                if (brazil && state != null && !RadioSearchEngine.matchesUf(s, state)) return@forEachIndexed
                if (brazil && cityText.isNotEmpty() && !e.city.contains(cityText) && !e.name.contains(cityText)) return@forEachIndexed
                if (tokens.all { e.text.contains(it) }) add(s)
            }
        }
    }
}
