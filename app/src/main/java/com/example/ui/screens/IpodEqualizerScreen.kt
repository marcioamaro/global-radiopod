package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun IpodEqualizerScreen(
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    currentPreset: String,
    onSelectPreset: (String) -> Unit,
    bandLevels: List<Float>, // 5 bands in dB (-12 to +12)
    onBandLevelChange: (Int, Float) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    val presets = listOf(
        "Rock", "Pop", "Blues", "Jazz", "Clássica", 
        "Bass Boost", "Eletrônica", "Vocal", "Flat", "Personalizado"
    )

    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    val bandDescriptions = listOf("Sub", "Grave", "Médio", "Méd-Alt", "Agudo")

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Equalizer Power Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x22000000))
                .border(1.dp, backlightHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = if (isEnabled) backlightHighlight else backlightTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnabled) "EQUALIZADOR ATIVO" else "EQUALIZADOR DESATIVADO",
                    color = if (isEnabled) backlightTextPrimary else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = backlightHighlight,
                    checkedTrackColor = backlightHighlight.copy(alpha = 0.35f),
                    uncheckedThumbColor = backlightTextSecondary,
                    uncheckedTrackColor = Color(0x33000000)
                ),
                modifier = Modifier.height(26.dp)
            )
        }

        // Presets Horizontal Selector
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRESETS DE ÁUDIO:",
                    color = backlightTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
                if (currentPreset == "Personalizado") {
                    Text(
                        text = "Ajuste Livre",
                        color = backlightHighlight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    val isSelected = preset.equals(currentPreset, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .border(
                                1.dp,
                                if (isSelected) backlightTextPrimary else backlightHighlight.copy(alpha = 0.25f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset,
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (10f * fontScale).sp,
                            fontWeight = if (isSelected || isBold) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = fontFamily
                        )
                    }
                }
            }
        }

        // Visual Curve Display Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x33000000))
                .border(0.8.dp, backlightHighlight.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bandLevels.take(5).forEachIndexed { idx, level ->
                    val heightRatio = ((level + 12f) / 24f).coerceIn(0.1f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight(heightRatio)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isEnabled) backlightHighlight else backlightTextSecondary.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        // 5 Individual Frequency Band Sliders
        Text(
            text = "CONTROLES DE FREQUÊNCIA (-12dB a +12dB):",
            color = backlightTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x22000000))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            bandLabels.forEachIndexed { index, freqLabel ->
                val level = bandLevels.getOrElse(index) { 0f }
                val desc = bandDescriptions.getOrElse(index) { "" }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$freqLabel ($desc)",
                            color = backlightTextPrimary,
                            fontSize = (10.5f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        val formattedDb = if (level > 0) "+${"%.1f".format(level)} dB" else "${"%.1f".format(level)} dB"
                        Text(
                            text = formattedDb,
                            color = if (level != 0f) backlightHighlight else backlightTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    Slider(
                        value = level,
                        onValueChange = { newVal ->
                            onBandLevelChange(index, (newVal * 2).roundToInt() / 2f)
                        },
                        valueRange = -12f..12f,
                        steps = 23, // 0.5 dB steps
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = backlightHighlight,
                            activeTrackColor = backlightHighlight,
                            inactiveTrackColor = backlightTextSecondary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        // Reset Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22000000))
                .border(0.8.dp, backlightHighlight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .clickable { onSelectPreset("Flat") }
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Resetar",
                tint = backlightTextPrimary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Restaurar Padrão (Flat)",
                color = backlightTextPrimary,
                fontSize = (10f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
        }
    }
}
