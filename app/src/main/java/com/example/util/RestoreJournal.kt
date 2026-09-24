package com.example.util

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import com.example.data.db.FavoriteStationEntity
import com.example.data.db.RadioDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable before-image: a failed or interrupted import can restore the exact old state. */
object RestoreJournal {
    private val lock = Mutex()
    private val preferenceFiles = listOf("ipod_radio_user_preferences", "podcast_preferences", "media_library")
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "restore-journal.json"))

    private suspend fun capture(context: Context): JSONObject {
        val root = JSONObject()
        for (name in preferenceFiles) {
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val type = when (value) { is String -> "string"; is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"; is Float -> "float"; is Set<*> -> "set"; else -> error("Unsupported preference") }
                values.put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
            }
            root.put(name, values)
        }
        val favorites = JSONArray()
        RadioDatabase.getDatabase(context).favoriteStationDao().getAllFavoritesDirect().forEach { s ->
            favorites.put(JSONObject().put("id", s.id).put("name", s.name).put("streamUrl", s.streamUrl)
                .put("favicon", s.favicon).put("homepage", s.homepage).put("tags", s.tags)
                .put("country", s.country).put("countryCode", s.countryCode).put("codec", s.codec)
                .put("bitrate", s.bitrate).put("votes", s.votes).put("addedTimestamp", s.addedTimestamp))
        }
        return root.put("favorites", favorites)
    }

    private fun write(context: Context, snapshot: JSONObject) {
        val journal = file(context)
        val output = journal.startWrite()
        try { output.write(snapshot.toString().toByteArray(Charsets.UTF_8)); journal.finishWrite(output) }
        catch (error: Throwable) { journal.failWrite(output); throw error }
    }

    private suspend fun recoverLocked(context: Context) {
        val journal = file(context)
        if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return
        val snapshot = JSONObject(journal.openRead().bufferedReader().use { it.readText() })
        val db = RadioDatabase.getDatabase(context)
        db.withTransaction {
            db.favoriteStationDao().deleteAllFavorites()
            val list = snapshot.getJSONArray("favorites")
            for (i in 0 until list.length()) {
                val s = list.getJSONObject(i)
                db.favoriteStationDao().insertFavorite(FavoriteStationEntity(s.getString("id"), s.getString("name"),
                    s.getString("streamUrl"), s.getString("favicon"), s.getString("homepage"), s.getString("tags"),
                    s.getString("country"), s.getString("countryCode"), s.getString("codec"), s.getInt("bitrate"),
                    s.getInt("votes"), s.getLong("addedTimestamp")))
            }
        }
        for (name in preferenceFiles) {
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            val values = snapshot.getJSONObject(name)
            values.keys().forEach { key ->
                val item = values.getJSONObject(key)
                when (item.getString("type")) {
                    "string" -> edit.putString(key, item.getString("value"))
                    "boolean" -> edit.putBoolean(key, item.getBoolean("value"))
                    "int" -> edit.putInt(key, item.getInt("value"))
                    "long" -> edit.putLong(key, item.getLong("value"))
                    "float" -> edit.putFloat(key, item.getDouble("value").toFloat())
                    "set" -> { val array = item.getJSONArray("value"); edit.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet()) }
                }
            }
            check(edit.commit()) { "Unable to recover preferences" }
        }
        journal.delete()
    }

    suspend fun recover(context: Context) = lock.withLock { recoverLocked(context) }

    suspend fun apply(context: Context, operation: suspend () -> Boolean): Boolean = lock.withLock {
        com.example.data.repository.MediaLibraryRepository.getInstance(context).withStorageLock {
        recoverLocked(context)
        write(context, capture(context))
        try {
            RadioDatabase.getDatabase(context).withTransaction { check(operation()) { "Invalid backup content" } }
            // Wait for preference writes before removing the recovery journal.
            preferenceFiles.forEach { check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().commit()) }
            file(context).delete()
            true
        } catch (error: Exception) {
            withContext(NonCancellable) {
                recoverLocked(context)
                com.example.data.repository.PodcastRepository.getInstance(context).reloadFromStorage()
                com.example.data.repository.MediaLibraryRepository.getInstance(context).reloadFromStorage()
            }
            if (error is kotlinx.coroutines.CancellationException) throw error
            false
        }
        }
    }
}
