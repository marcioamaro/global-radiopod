package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
        .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.weight(1f).padding(horizontal = 8.dp))
    }
}

@Composable
fun LcdFeatureTheme(background: Color, foreground: Color, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = foreground, onPrimary = background,
        surface = background, onSurface = foreground, onSurfaceVariant = foreground, outline = foreground)) {
        CompositionLocalProvider(LocalContentColor provides foreground, content = content)
    }
}
