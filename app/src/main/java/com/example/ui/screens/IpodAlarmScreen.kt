package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.RadioAlarmScheduler
import com.example.data.model.RadioAlarmConfig
import com.example.data.model.RadioStation
import com.example.data.preferences.IpodPreferencesManager
import java.util.Calendar

@Composable
fun IpodAlarmScreen(
    favorites: List<RadioStation>,
    onBack: () -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { IpodPreferencesManager.getInstance(context) }
    var alarmConfig by remember { mutableStateOf(prefs.getRadioAlarmConfig()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var isSelectingStation by remember { mutableStateOf(false) }

    fun updateConfig(newConfig: RadioAlarmConfig) {
        alarmConfig = newConfig
        prefs.saveRadioAlarmConfig(newConfig)
        if (newConfig.isEnabled) {
            RadioAlarmScheduler.scheduleAlarm(context, newConfig)
        } else {
            RadioAlarmScheduler.cancelAlarm(context)
        }
    }

    if (isSelectingStation) {
        // Sub-tela para escolher a rádio favorita do despertador
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(backlightBg)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECIONAR RÁDIO",
                    color = backlightTextPrimary,
                    fontSize = (12f * fontScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(backlightHighlight)
                        .clickable { isSelectingStation = false }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "VOLTAR",
                        color = Color.White,
                        fontSize = (10f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma rádio favorita salva.\nAdicione rádios aos favoritos para despertar com elas!",
                        color = backlightTextSecondary,
                        fontSize = (11f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(favorites) { _, station ->
                        val isChosen = alarmConfig.stationId == station.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isChosen) backlightHighlight else Color(0x18000000))
                                .clickable {
                                    updateConfig(
                                        alarmConfig.copy(
                                            stationId = station.id,
                                            stationName = station.name,
                                            stationStreamUrl = station.streamUrl,
                                            stationFavicon = station.favicon
                                        )
                                    )
                                    isSelectingStation = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = station.name,
                                color = if (isChosen) Color.White else backlightTextPrimary,
                                fontSize = (11.5f * fontScale).sp,
                                fontWeight = if (isChosen || isBold) FontWeight.Black else FontWeight.Bold,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Tela Principal de Configuração do Alarme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // Status do Alarme no Topo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (alarmConfig.isEnabled) backlightHighlight.copy(alpha = 0.25f) else Color(0x15000000))
                .border(1.dp, if (alarmConfig.isEnabled) backlightHighlight else backlightTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (alarmConfig.isEnabled) Icons.Default.Alarm else Icons.Default.AlarmOff,
                        contentDescription = null,
                        tint = if (alarmConfig.isEnabled) backlightHighlight else backlightTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = if (alarmConfig.isEnabled) "DESPERTADOR ATIVO" else "DESPERTADOR DESATIVADO",
                            color = backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )
                        if (alarmConfig.isEnabled) {
                            Text(
                                text = "Toca às ${alarmConfig.formattedTime} (${alarmConfig.getRemainingTimeString()})",
                                color = backlightHighlight,
                                fontSize = (9.5f * fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily
                            )
                        }
                    }
                }

                // Chave Liga / Desliga
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (alarmConfig.isEnabled) backlightTextPrimary else backlightTextPrimary.copy(alpha = 0.12f))
                        .border(1.dp, backlightTextPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            val toggled = !alarmConfig.isEnabled
                            // Se ligar e não tiver rádio selecionada, pega a primeira dos favoritos
                            val targetStation = if (alarmConfig.stationStreamUrl.isBlank() && favorites.isNotEmpty()) {
                                favorites.first()
                            } else null

                            updateConfig(
                                alarmConfig.copy(
                                    isEnabled = toggled,
                                    stationId = targetStation?.id ?: alarmConfig.stationId,
                                    stationName = targetStation?.name ?: alarmConfig.stationName,
                                    stationStreamUrl = targetStation?.streamUrl ?: alarmConfig.stationStreamUrl,
                                    stationFavicon = targetStation?.favicon ?: alarmConfig.stationFavicon
                                )
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (alarmConfig.isEnabled) "LIGADO" else "DESLIGADO",
                        color = if (alarmConfig.isEnabled) backlightBg else backlightTextPrimary,
                        fontSize = (10f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Lista de Opções de Configuração
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            // 1. Ajuste de Hora
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x12000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hora do Despertador",
                        color = backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botão Diminuir Hora
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(backlightHighlight.copy(alpha = 0.3f))
                                .clickable {
                                    val newHour = if (alarmConfig.hour == 0) 23 else alarmConfig.hour - 1
                                    updateConfig(alarmConfig.copy(hour = newHour))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = backlightTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }

                        Text(
                            text = String.format(java.util.Locale.US, " %02d ", alarmConfig.hour),
                            color = backlightTextPrimary,
                            fontSize = (13f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )

                        // Botão Aumentar Hora
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(backlightHighlight.copy(alpha = 0.3f))
                                .clickable {
                                    val newHour = (alarmConfig.hour + 1) % 24
                                    updateConfig(alarmConfig.copy(hour = newHour))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = backlightTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }

            // 2. Ajuste de Minutos
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x12000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Minutos",
                        color = backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(backlightHighlight.copy(alpha = 0.3f))
                                .clickable {
                                    val newMin = if (alarmConfig.minute < 5) 55 else (alarmConfig.minute - 5) / 5 * 5
                                    updateConfig(alarmConfig.copy(minute = newMin))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = backlightTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }

                        Text(
                            text = String.format(java.util.Locale.US, " %02d ", alarmConfig.minute),
                            color = backlightTextPrimary,
                            fontSize = (13f * fontScale).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = fontFamily
                        )

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(backlightHighlight.copy(alpha = 0.3f))
                                .clickable {
                                    val newMin = (alarmConfig.minute + 5) % 60
                                    updateConfig(alarmConfig.copy(minute = newMin))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = backlightTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }

            // 3. Rádio Selecionada
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x12000000))
                        .clickable { isSelectingStation = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rádio do Despertador",
                            color = backlightTextSecondary,
                            fontSize = (9f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = if (alarmConfig.stationName.isNotBlank()) alarmConfig.stationName else "Nenhuma (Toque padrão)",
                            color = backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 4. Repetição por Dias da Semana
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x12000000))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Repetição Semanal",
                        color = backlightTextSecondary,
                        fontSize = (9f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayLabels = listOf(
                            Calendar.MONDAY to "S",
                            Calendar.TUESDAY to "T",
                            Calendar.WEDNESDAY to "Q",
                            Calendar.THURSDAY to "Q",
                            Calendar.FRIDAY to "S",
                            Calendar.SATURDAY to "S",
                            Calendar.SUNDAY to "D"
                        )
                        dayLabels.forEach { (calDay, label) ->
                            val isDaySelected = alarmConfig.daysOfWeek.contains(calDay)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isDaySelected) backlightHighlight else Color(0x22000000))
                                    .clickable {
                                        val newDays = alarmConfig.daysOfWeek.toMutableSet()
                                        if (isDaySelected) newDays.remove(calDay) else newDays.add(calDay)
                                        updateConfig(alarmConfig.copy(daysOfWeek = newDays))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isDaySelected) Color.White else backlightTextPrimary,
                                    fontSize = (9.5f * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = fontFamily
                                )
                            }
                        }
                    }
                }
            }

            // 5. Vibração
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x12000000))
                        .clickable { updateConfig(alarmConfig.copy(vibrate = !alarmConfig.vibrate)) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vibração",
                        color = backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily
                    )
                    Text(
                        text = if (alarmConfig.vibrate) "LIGADA" else "DESLIGADA",
                        color = if (alarmConfig.vibrate) backlightHighlight else backlightTextSecondary,
                        fontSize = (10.5f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }
            }

            // 6. Tempo de Soneca
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x12000000))
                        .clickable {
                            val nextSnooze = when (alarmConfig.snoozeMinutes) {
                                5 -> 10
                                10 -> 15
                                else -> 5
                            }
                            updateConfig(alarmConfig.copy(snoozeMinutes = nextSnooze))
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tempo de Soneca",
                        color = backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily
                    )
                    Text(
                        text = "${alarmConfig.snoozeMinutes} MINUTOS",
                        color = backlightHighlight,
                        fontSize = (10.5f * fontScale).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Botão Voltar ao Menu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(backlightHighlight.copy(alpha = 0.2f))
                .clickable(onClick = onBack)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CONCLUIR E VOLTAR ✕",
                color = backlightTextPrimary,
                fontSize = (10.5f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )
        }
    }
}
