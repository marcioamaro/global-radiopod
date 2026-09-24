package com.marcioamaro.mediapod.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.data.repository.CatalogUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CatalogUpdateControl(background: Color, primary: Color, secondary: Color, highlight: Color, fontFamily: FontFamily, fontScale: Float, isBold: Boolean) {
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
    Text(if (busy) context.getString(com.marcioamaro.mediapod.R.string.catalog_validating) else context.getString(com.marcioamaro.mediapod.R.string.catalog_import),
        color = primary, fontSize = (11f * fontScale).sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, fontFamily = fontFamily,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).background(highlight.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .border(1.dp, highlight.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).clickable(enabled = !busy) { picker.launch(arrayOf("application/zip", "application/octet-stream")) }.padding(12.dp))
    message?.let { Text(it, color = secondary, fontSize = (10f * fontScale).sp, fontFamily = fontFamily) }
}
