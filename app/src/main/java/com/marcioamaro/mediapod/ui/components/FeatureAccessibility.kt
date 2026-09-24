package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PreferenceToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
        .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.weight(1f).padding(horizontal = 8.dp))
    }
}

@Composable
fun LcdFeatureTheme(
    background: Color,
    foreground: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1f,
    isBold: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = foreground, onPrimary = background,
        surface = background, onSurface = foreground, onSurfaceVariant = foreground, outline = foreground)) {
        CompositionLocalProvider(LocalContentColor provides foreground,
            LocalTextStyle provides TextStyle(fontFamily = fontFamily, fontSize = (14f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal), content = content)
    }
}
