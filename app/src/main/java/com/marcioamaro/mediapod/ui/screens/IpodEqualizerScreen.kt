package com.marcioamaro.mediapod.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.R
import kotlin.math.roundToInt

@Composable
fun IpodEqualizerScreen(
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    currentPreset: String,
    onSelectPreset: (String) -> Unit,
    bandLevels: List<Float>, // 5 bands in dB (-12 to +12)
    onBandLevelChange: (Int, Float) -> Unit,
    isLoudnessEnabled: Boolean = false,
    onToggleLoudness: ((Boolean) -> Unit)? = null,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    val presets = listOf(
        "Flat", "Rock", "Pop", "Bass Booster", "Voz / Podcast", 
        "Jazz", "Clássica", "Eletrônica", "Blues", "Loudness", "Personalizado"
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
        // 1. Equalizer Power Bar Retrô (Switch Monocromático)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22000000))
                .border(1.dp, backlightHighlight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
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
                    text = if (isEnabled) stringResource(R.string.eq_active) else stringResource(R.string.eq_disabled),
                    color = if (isEnabled) backlightTextPrimary else backlightTextSecondary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }

            // Switch Retrô LCD Monocromático (estilo botão do iPod)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isEnabled) backlightHighlight else Color(0x33000000))
                    .border(
                        1.dp,
                        if (isEnabled) backlightTextPrimary else backlightTextSecondary.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleEnabled(!isEnabled) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnabled) "[ ${stringResource(R.string.eq_on)} ]" else "[ ${stringResource(R.string.eq_off)} ]",
                    color = if (isEnabled) Color.White else backlightTextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // 1.1 DSP Loudness / Normalização de Áudio Retrô
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22000000))
                .border(1.dp, backlightHighlight.copy(alpha = if (isLoudnessEnabled) 0.5f else 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DSP Loudness (Normalização)",
                        color = if (isLoudnessEnabled) backlightTextPrimary else backlightTextSecondary,
                        fontSize = (10.5f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "[+4 dB AGC]",
                        color = if (isLoudnessEnabled) backlightHighlight else backlightTextSecondary.copy(alpha = 0.5f),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
                Text(
                    text = "Nivela rádios baixas e comprime picos",
                    color = backlightTextSecondary.copy(alpha = 0.8f),
                    fontSize = 8.5.sp,
                    fontFamily = fontFamily
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isLoudnessEnabled) backlightHighlight else Color(0x33000000))
                    .border(
                        1.dp,
                        if (isLoudnessEnabled) backlightTextPrimary else backlightTextSecondary.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleLoudness?.invoke(!isLoudnessEnabled) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLoudnessEnabled) "[ ON ]" else "[ OFF ]",
                    color = if (isLoudnessEnabled) Color.White else backlightTextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }

        // 2. Presets de Áudio (Seleção Retrô)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.eq_presets),
                    color = backlightTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
                val presetBadge = when {
                    currentPreset.equals("Flat", ignoreCase = true) -> "[0 dB Linear]"
                    currentPreset.equals("Rock", ignoreCase = true) -> "[Curva V]"
                    currentPreset.equals("Pop", ignoreCase = true) -> "[Vocal & Brilho]"
                    currentPreset.contains("Bass", ignoreCase = true) -> "[Super Graves]"
                    currentPreset.contains("Voz", ignoreCase = true) || currentPreset.contains("Podcast", ignoreCase = true) -> "[Foco em Voz]"
                    currentPreset.equals("Jazz", ignoreCase = true) -> "[Quente & Suave]"
                    currentPreset.contains("Clássica", ignoreCase = true) -> "[Dinâmica Ampla]"
                    currentPreset.contains("Eletr", ignoreCase = true) -> "[Graves & Agudos]"
                    currentPreset.contains("Blues", ignoreCase = true) -> "[Orgânico Acústico]"
                    currentPreset.contains("Loud", ignoreCase = true) -> "[DSP Loudness +4dB]"
                    else -> "[Ajuste Livre]"
                }
                Text(
                    text = presetBadge,
                    color = backlightHighlight,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    val isSelected = preset.equals(currentPreset, ignoreCase = true) ||
                            (preset == "Bass Booster" && currentPreset.contains("Bass", ignoreCase = true)) ||
                            (preset == "Voz / Podcast" && (currentPreset.contains("Voz", ignoreCase = true) || currentPreset.contains("Podcast", ignoreCase = true) || currentPreset.contains("Vocal", ignoreCase = true))) ||
                            (preset == "Eletrônica" && currentPreset.contains("Eletr", ignoreCase = true)) ||
                            (preset == "Clássica" && currentPreset.contains("Clássica", ignoreCase = true))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) backlightHighlight else Color(0x22000000))
                            .border(
                                1.dp,
                                if (isSelected) backlightTextPrimary else backlightHighlight.copy(alpha = 0.25f),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                if (!isEnabled) onToggleEnabled(true)
                                onSelectPreset(preset)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
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

        // 3. Painel de Espectro VU-Meter Matricial Retrô LCD (Blocos Discretos)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33000000))
                .border(0.8.dp, backlightHighlight.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                bandLevels.take(5).forEachIndexed { idx, level ->
                    val totalBlocks = 8
                    val normalized = ((level + 12f) / 24f).coerceIn(0f, 1f)
                    val activeBlocks = (normalized * totalBlocks).roundToInt().coerceIn(1, totalBlocks)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (b in (totalBlocks downTo 1)) {
                                val isBlockActive = b <= activeBlocks
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(3.5.dp)
                                        .clip(RoundedCornerShape(0.5.dp))
                                        .background(
                                            if (isBlockActive) {
                                                if (isEnabled) backlightHighlight else backlightTextSecondary.copy(alpha = 0.4f)
                                            } else {
                                                Color(0x15000000)
                                            }
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bandLabels.getOrElse(idx) { "" }.replace(" Hz", "").replace(" kHz", "k"),
                            color = backlightTextSecondary,
                            fontSize = 8.sp,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. Controles de Frequência Segmentados em Blocos (-12dB a +12dB)
        Text(
            text = stringResource(R.string.eq_frequencies),
            color = backlightTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22000000))
                .border(0.8.dp, backlightHighlight.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            fontSize = (10f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        val formattedDb = if (level > 0) "+${"%.1f".format(level)} dB" else "${"%.1f".format(level)} dB"
                        Text(
                            text = formattedDb,
                            color = if (level != 0f) backlightHighlight else backlightTextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Barra Segmentada Monocromática com Botões [-] e [+]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Botão Diminuir [-]
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, backlightHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable {
                                    if (!isEnabled) onToggleEnabled(true)
                                    val newLevel = (level - 2f).coerceIn(-12f, 12f)
                                    onBandLevelChange(index, newLevel)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "-",
                                color = backlightTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }

                        // Display de 13 Segmentos Monocromáticos (-12 a +12 dB em passos de 2 dB)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33000000))
                                .border(0.8.dp, backlightHighlight.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 2.5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dbSteps = listOf(-12f, -10f, -8f, -6f, -4f, -2f, 0f, 2f, 4f, 6f, 8f, 10f, 12f)
                            dbSteps.forEach { stepDb ->
                                val isZero = stepDb == 0f
                                val isFilled = when {
                                    level == 0f -> isZero
                                    level > 0f -> stepDb in 0f..level
                                    else -> stepDb in level..0f
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 1.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            when {
                                                isFilled && isEnabled -> backlightHighlight
                                                isFilled && !isEnabled -> backlightTextSecondary.copy(alpha = 0.45f)
                                                isZero -> backlightTextSecondary.copy(alpha = 0.35f)
                                                else -> Color(0x15000000)
                                            }
                                        )
                                        .clickable {
                                            if (!isEnabled) onToggleEnabled(true)
                                            onBandLevelChange(index, stepDb)
                                        }
                                )
                            }
                        }

                        // Botão Aumentar [+]
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x33000000))
                                .border(1.dp, backlightHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable {
                                    if (!isEnabled) onToggleEnabled(true)
                                    val newLevel = (level + 2f).coerceIn(-12f, 12f)
                                    onBandLevelChange(index, newLevel)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = backlightTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }
            }
        }

        // 5. Botão Restaurar Padrão Retrô (Flat)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22000000))
                .border(0.8.dp, backlightHighlight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .clickable { onSelectPreset("Flat") }
                .padding(vertical = 6.dp),
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
