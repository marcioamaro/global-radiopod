package com.marcioamaro.mediapod.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsControl(background: Color, primary: Color, secondary: Color, highlight: Color, fontFamily: FontFamily, fontScale: Float, isBold: Boolean) {
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
    fun action(label: String, onClick: () -> Unit) = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .background(highlight.copy(alpha = 0.16f), RoundedCornerShape(4.dp)).border(1.dp, highlight.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(context.getString(com.marcioamaro.mediapod.R.string.diagnostic_export), color = primary, fontSize = (11f * fontScale).sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, fontFamily = fontFamily, modifier = action(context.getString(com.marcioamaro.mediapod.R.string.diagnostic_export)) { export.launch("mediapod-diagnostico.json") })
        Text(context.getString(com.marcioamaro.mediapod.R.string.diagnostic_clear), color = primary, fontSize = (11f * fontScale).sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, fontFamily = fontFamily, modifier = action(context.getString(com.marcioamaro.mediapod.R.string.diagnostic_clear)) { Diagnostics.clear(context); message = context.getString(com.marcioamaro.mediapod.R.string.diagnostic_cleared) })
        message?.let { Text(it, color = secondary, fontSize = (10f * fontScale).sp, fontFamily = fontFamily) }
    }
}
