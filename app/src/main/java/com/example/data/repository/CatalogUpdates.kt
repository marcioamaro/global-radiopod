package com.example.data.repository

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Explicit local package imports. Active generation changes only after validation. */
object CatalogUpdates {
    private val names = setOf("radio_catalog.json", "media_rankings.json")
    private fun root(context: Context) = File(context.noBackupFilesDir, "catalog-updates").apply { mkdirs() }
    @Volatile private var selected: File? = null

    fun initialize(context: Context) {
        selected = runCatching {
            val directory = root(context)
            val id = AtomicFile(File(directory, "active")).openRead().bufferedReader().use { it.readText() }
            require(id.matches(Regex("[a-f0-9-]{36}")))
            File(directory, id).also(::validate)
        }.getOrNull() // The bundled catalog remains available if an update is damaged.
    }

    fun open(context: Context, name: String): InputStream {
        require(name in names)
        return selected?.resolve(name)?.inputStream() ?: context.assets.open(name)
    }

    @Synchronized fun import(context: Context, input: InputStream) {
        val directory = root(context)
        val generation = File(directory, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val found = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entry.name in names && found.add(entry.name) && !entry.isDirectory) { "Pacote inválido" }
                    generation.resolve(entry.name).outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 64L * 1024 * 1024) { "Pacote excede 64 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            }
            require(found == names) { "Catálogo ou rankings ausentes" }
            validate(generation)
            val pointer = AtomicFile(File(directory, "active"))
            val output = pointer.startWrite()
            try {
                output.write(generation.name.toByteArray())
                pointer.finishWrite(output)
            } catch (error: Exception) { pointer.failWrite(output); throw error }
            // Current session keeps its original generation; restart switches all sources together.
        } catch (error: Exception) {
            names.forEach { generation.resolve(it).delete() }
            generation.delete()
            throw error
        }
    }

    private fun validate(directory: File) {
        val stations = directory.resolve("radio_catalog.json").inputStream().use(RadioCatalog::read).associateBy { it.id }
        val rankingFile = directory.resolve("media_rankings.json")
        require(rankingFile.length() <= 2 * 1024 * 1024)
        val rankings = JSONObject(rankingFile.readText())
        val keys = listOf("radio_brazil", "radio_world", "podcast_brazil", "podcast_world")
        // Existing packages use these same published keys, with no provider-specific UI text.
        val actual = rankings.keys().asSequence().filter { rankings.optJSONObject(it)?.has("entries") == true }.toList()
        require(actual.toSet() == keys.toSet())
        for (key in actual) {
            val entries = rankings.getJSONObject(key).getJSONArray("entries")
            require(entries.length() == 20)
            val ids = mutableSetOf<String>()
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                require(entry.getInt("rank") == i + 1 && entry.getString("title").isNotBlank())
                if (key.startsWith("radio_")) {
                    val station = entry.getJSONObject("station")
                    require(ids.add(station.getString("id")))
                    require(stations[station.getString("id")]?.streamUrl == station.getString("primary_stream_url"))
                } else {
                    val show = entry.getJSONObject("show")
                    require(ids.add(show.getString("id")))
                    val url = java.net.URI(show.getString("feedUrl"))
                    require(url.scheme in setOf("http", "https") && !url.host.isNullOrBlank() && url.userInfo == null)
                }
            }
        }
    }
}
