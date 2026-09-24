package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Pixel grid, rendered in the active LCD foreground color. */
@Composable
fun LcdEarIcon(color: Color, played: Boolean, modifier: Modifier = Modifier) {
    val pixels = listOf("0001111000", "0010000100", "0100110010", "0101001010", "0101001010",
        "0001010010", "0001010010", "0000100100", "0010001000", "0010010000", "0001100000")
    Canvas(modifier.size(20.dp).semantics { contentDescription = if (played) "Já ouvido" else "Não ouvido" }) {
        val cell = minOf(size.width / 10, size.height / 11)
        pixels.forEachIndexed { y, row -> row.forEachIndexed { x, pixel ->
            if (pixel == '1') drawRect(color.copy(alpha = if (played) 1f else 0.3f),
                Offset(x * cell, y * cell), Size(cell * 0.88f, cell * 0.88f))
        } }
    }
}
