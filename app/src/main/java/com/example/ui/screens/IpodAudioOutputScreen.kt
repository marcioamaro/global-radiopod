package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AudioDeviceType
import com.example.player.AudioRouteDevice

@Composable
fun IpodAudioOutputScreen(
    devices: List<AudioRouteDevice>,
    selectedDevice: AudioRouteDevice?,
    selectedIndex: Int,
    onSelectDevice: (AudioRouteDevice) -> Unit,
    onOpenNativeChooser: () -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val listState = rememberLazyListState()
    val totalCount = devices.size + 1 // Dispositivos + Opção do Seletor Nativo

    val currentFocusedDevice = devices.getOrNull(selectedIndex)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
    ) {
        // Painel Esquerdo: Lista de Dispositivos com estilo do iPod Classic
        com.example.ui.components.SelectableLazyColumn(
            items = devices,
            selectedIndex = selectedIndex,
            key = { _, dev -> dev.id },
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
        ) { index, device, isFocused ->
            val isDeviceActive = device.isSelected || device.id == selectedDevice?.id

            val icon: androidx.compose.ui.graphics.vector.ImageVector = when (device.deviceType) {
                AudioDeviceType.THIS_DEVICE -> Icons.Default.PhoneAndroid
                AudioDeviceType.BLUETOOTH -> Icons.Default.Headphones
                AudioDeviceType.CAST_REMOTE -> Icons.Default.Cast
                AudioDeviceType.OTHER -> Icons.Default.Speaker
            }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isFocused) backlightHighlight.copy(alpha = 0.88f)
                            else Color.Transparent
                        )
                        .clickable { onSelectDevice(device) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) Color.White else backlightTextPrimary,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            color = if (isFocused) Color.White else backlightTextPrimary,
                            fontSize = (11.5f * fontScale).sp,
                            fontWeight = if (isBold || isFocused || isDeviceActive) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = fontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (device.description.isNotBlank()) {
                            Text(
                                text = device.description,
                                color = if (isFocused) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                                fontSize = (8.5f * fontScale).sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Marcador retrô de dispositivo ativo no momento
                    if (isDeviceActive) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isFocused) Color.White else backlightTextPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Ativo",
                                tint = if (isFocused) backlightHighlight else backlightBg,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isFocused) Color.White else backlightTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

        // Divisor vertical retrô do LCD
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(backlightTextPrimary.copy(alpha = 0.2f))
        )

        // Painel Direito: Pré-visualização e detalhes retrô do dispositivo selecionado
        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .background(backlightHighlight.copy(alpha = 0.08f))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val focusedType = currentFocusedDevice?.deviceType ?: selectedDevice?.deviceType ?: AudioDeviceType.THIS_DEVICE
                val previewIcon = when (focusedType) {
                    AudioDeviceType.THIS_DEVICE -> Icons.Default.PhoneAndroid
                    AudioDeviceType.BLUETOOTH -> Icons.Default.BluetoothAudio
                    AudioDeviceType.CAST_REMOTE -> Icons.Default.Cast
                    else -> Icons.Default.VolumeUp
                }

                // Moldura LCD plana com fundo suave e ícone em tinta escura de alto contraste
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1F000000))
                        .border(1.5.dp, backlightTextPrimary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = previewIcon,
                        contentDescription = null,
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val devName = currentFocusedDevice?.name ?: selectedDevice?.name ?: "Áudio Nativo"
                val devTypeDesc = when (currentFocusedDevice?.deviceType ?: selectedDevice?.deviceType) {
                    AudioDeviceType.THIS_DEVICE -> "ALTO-FALANTE DO DISPOSITIVO"
                    AudioDeviceType.BLUETOOTH -> "FONE/CAIXA BLUETOOTH"
                    AudioDeviceType.CAST_REMOTE -> "GOOGLE CAST / NEST / CHROMECAST"
                    else -> "DISPOSITIVO DE ÁUDIO"
                }

                Text(
                    text = devName,
                    color = backlightTextPrimary,
                    fontSize = (10f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = devTypeDesc,
                    color = backlightTextSecondary,
                    fontSize = 8.sp,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(6.dp))

                val isCurrentActive = currentFocusedDevice?.id == selectedDevice?.id || (currentFocusedDevice?.isSelected == true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isCurrentActive) backlightTextPrimary.copy(alpha = 0.25f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isCurrentActive) "● ATIVO AGORA" else "DISPONÍVEL",
                        color = backlightTextPrimary,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }
}
