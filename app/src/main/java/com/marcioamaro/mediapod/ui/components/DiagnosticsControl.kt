package com.marcioamaro.mediapod.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.marcioamaro.mediapod.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsControl() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(Diagnostics.report(context)) }
                }
                context.getString(com.marcioamaro.mediapod.R.string.diagnostic_saved)
            } catch (_: Exception) { context.getString(com.marcioamaro.mediapod.R.string.diagnostic_save_error) }
        }
    }
    TextButton(onClick = { export.launch("mediapod-diagnostico.json") }) { Text(context.getString(com.marcioamaro.mediapod.R.string.diagnostic_export)) }
    TextButton(onClick = { Diagnostics.clear(context); message = context.getString(com.marcioamaro.mediapod.R.string.diagnostic_cleared) }) { Text(context.getString(com.marcioamaro.mediapod.R.string.diagnostic_clear)) }
    message?.let { Text(it) }
}
