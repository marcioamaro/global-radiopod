package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.CatalogUpdates
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CatalogUpdatesTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
        return output.toByteArray()
    }

    @Test fun validPackageSwitchesTogetherAndRejectedImportPreservesActiveVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val names = listOf("radio_catalog.json", "media_rankings.json")
        val entries = names.map { it to context.assets.open(it).use { input -> input.readBytes() } }
        CatalogUpdates.import(context, archive(*entries.toTypedArray()).inputStream())
        val pointer = File(context.noBackupFilesDir, "catalog-updates/active")
        val before = pointer.readText()
        assertTrue(runCatching {
            CatalogUpdates.import(context, archive("../outside" to byteArrayOf(1)).inputStream())
        }.isFailure)
        assertEquals(before, pointer.readText())
        CatalogUpdates.initialize(context)
        names.forEachIndexed { index, name ->
            assertArrayEquals(entries[index].second, CatalogUpdates.open(context, name).use { it.readBytes() })
        }
        File(context.noBackupFilesDir, "catalog-updates/$before/media_rankings.json").writeText("broken")
        CatalogUpdates.initialize(context)
        assertArrayEquals(entries[1].second, CatalogUpdates.open(context, names[1]).use { it.readBytes() })
    }
}
