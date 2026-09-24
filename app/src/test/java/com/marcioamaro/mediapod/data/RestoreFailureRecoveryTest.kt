package com.marcioamaro.mediapod.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcioamaro.mediapod.util.BackupRestoreManager
import com.marcioamaro.mediapod.util.RestoreJournal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RestoreFailureRecoveryTest {
    @Test fun interruptedImportRecoversDurableSnapshotOnNextStartup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("media_library", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("partial", "discard").commit()
        val snapshot = org.json.JSONObject().apply {
            put("ipod_radio_user_preferences", org.json.JSONObject())
            put("podcast_preferences", org.json.JSONObject())
            put("media_library", org.json.JSONObject("""{"original":{"type":"string","value":"keep"}}"""))
            put("favorites", org.json.JSONArray())
        }
        java.io.File(context.noBackupFilesDir, "restore-journal.json").writeText(snapshot.toString())
        RestoreJournal.recover(context)
        assertEquals(mapOf("original" to "keep"), prefs.all)
        RestoreJournal.recover(context)
        assertEquals(mapOf("original" to "keep"), prefs.all)
    }

    @Test fun malformedCollectionsAreRejectedBeforeWriting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("ipod_radio_user_preferences", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        assertFalse(BackupRestoreManager.restoreFromJson(context,
            """{"version":29,"visualPreferences":{"customBodyColor":0},"podcastData":{"favorites":["invalid"]}}"""))
        assertEquals(before, prefs.all)
        assertFalse(java.io.File(context.noBackupFilesDir, "restore-journal.json").exists())
    }

    @Test fun failedImportRestoresExactPreferencesAndRemovesPartialAdditions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("media_library", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("original", "keep").commit()
        assertFalse(RestoreJournal.apply(context) {
            prefs.edit().clear().putString("partial", "must disappear").commit()
            false
        })
        assertEquals(mapOf("original" to "keep"), prefs.all)
        assertFalse(java.io.File(context.noBackupFilesDir, "restore-journal.json").exists())
    }

    @Test fun unsupportedVersionIsRejectedBeforeAnyModification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("ipod_radio_user_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("keep", "original").commit()
        val before = prefs.all.toMap()
        assertFalse(BackupRestoreManager.restoreFromJson(context, """{"version":999,"visualPreferences":{"customBodyColor":0}}"""))
        assertEquals(before, prefs.all)
    }
}
