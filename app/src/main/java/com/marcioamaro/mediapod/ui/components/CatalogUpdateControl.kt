package com.marcioamaro.mediapod.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.marcioamaro.mediapod.data.repository.CatalogUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CatalogUpdateControl() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                message = try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { CatalogUpdates.import(context, it) }
                    }
                    context.getString(com.marcioamaro.mediapod.R.string.catalog_imported)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { context.getString(com.marcioamaro.mediapod.R.string.catalog_invalid) }
                finally { busy = false }
            }
        }
    }
    TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
        Text(if (busy) context.getString(com.marcioamaro.mediapod.R.string.catalog_validating) else context.getString(com.marcioamaro.mediapod.R.string.catalog_import))
    }
    message?.let { Text(it) }
}
