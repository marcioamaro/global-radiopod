package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.PodcastRepository
import com.example.util.PodcastOpml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OpmlControls(repository: PodcastRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = try {
                val count = withContext(Dispatchers.IO) {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 2 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val shows = PodcastOpml.parse(bytes)
                    val before = repository.subscriptionsFlow.value.size
                    shows.forEach(repository::subscribe)
                    repository.subscriptionsFlow.value.size - before
                }
                context.getString(com.example.R.string.opml_added, count)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { context.getString(com.example.R.string.opml_invalid) }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use {
                        it.write(PodcastOpml.export(repository.subscriptionsFlow.value))
                    }
                }
                context.getString(com.example.R.string.opml_exported)
            } catch (_: Exception) { context.getString(com.example.R.string.file_save_error) }
        }
    }
    TextButton(enabled = !busy, onClick = { import.launch(arrayOf("text/*", "application/xml", "application/octet-stream")) }) { Text(context.getString(com.example.R.string.opml_import)) }
    TextButton(enabled = !busy, onClick = { export.launch("mediapod-assinaturas.opml") }) { Text(context.getString(com.example.R.string.opml_export)) }
    message?.let { Text(it) }
}
