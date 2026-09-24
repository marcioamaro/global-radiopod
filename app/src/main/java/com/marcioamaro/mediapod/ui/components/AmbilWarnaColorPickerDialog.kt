package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.marcioamaro.mediapod.data.model.IpodPalette
import com.marcioamaro.mediapod.ui.theme.IpodColorContrastUtil
import kotlin.math.roundToInt

enum class ColorPickerTarget {
    CHASSIS,
    CLICK_WHEEL,
    CENTER_BUTTON
}

/**
 * Diálogo no estilo clássico AmbilWarna (HSV Saturation-Value 2D box + Hue bar)
 * com prévia realista do hardware do iPod Classic antes da confirmação.
 */
@Composable
fun AmbilWarnaColorPickerDialog(
    title: String,
    initialColor: Long,
    target: ColorPickerTarget,
    currentPalette: IpodPalette,
    onColorSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    // Converter cor ARGB para HSV
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        val r = ((initialColor shr 16) and 0xFF).toInt()
        val g = ((initialColor shr 8) and 0xFF).toInt()
        val b = (initialColor and 0xFF).toInt()
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColorLong = remember(hue, saturation, value) {
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        // Garantir canal alfa 0xFF
        (colorInt.toLong() and 0xFFFFFFFFL) or 0xFF000000L
    }

    // Prévia da paleta com a cor selecionada aplicada
    val previewPalette = remember(currentColorLong, target, currentPalette) {
        when (target) {
            ColorPickerTarget.CHASSIS -> currentPalette.copy(bodyColor = currentColorLong)
            ColorPickerTarget.CLICK_WHEEL -> {
                val optText = IpodColorContrastUtil.getOptimalWheelTextColor(currentColorLong)
                currentPalette.copy(wheelColor = currentColorLong, wheelTextColor = optText)
            }
            ColorPickerTarget.CENTER_BUTTON -> currentPalette.copy(centerButtonColor = currentColorLong)
        }
    }

    val isCombinationValid = remember(previewPalette) {
        IpodColorContrastUtil.isHardwareCombinationValid(
            bodyColor = previewPalette.bodyColor,
            wheelColor = previewPalette.wheelColor,
            centerButtonColor = previewPalette.centerButtonColor
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Saturation / Value Box (2D)
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                val baseHueColor = remember(hue) {
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (boxSize.width > 0 && boxSize.height > 0) {
                                        saturation = (offset.x / boxSize.width).coerceIn(0f, 1f)
                                        value = (1f - (offset.y / boxSize.height)).coerceIn(0f, 1f)
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (boxSize.width > 0 && boxSize.height > 0) {
                                        saturation = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                                        value = (1f - (change.position.y / boxSize.height)).coerceIn(0f, 1f)
                                    }
                                }
                            )
                        }
                ) {
                    // Gradiente horizontal (Branco para Cor pura)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.White, baseHueColor)
                                )
                            )
                    )
                    // Gradiente vertical (Transparente para Preto)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black)
                                )
                            )
                    )

                    // Indicador de ponto selecionado
                    if (boxSize.width > 0 && boxSize.height > 0) {
                        val posX = (saturation * boxSize.width)
                        val posY = ((1f - value) * boxSize.height)
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = (posX - 8).dp.coerceAtLeast(0.dp),
                                    y = (posY - 8).dp.coerceAtLeast(0.dp)
                                )
                                .size(16.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .border(1.dp, Color.Black, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Hue Slider (0 - 360)
                var hueBarSize by remember { mutableStateOf(IntSize.Zero) }
                val rainbowColors = remember {
                    listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient(rainbowColors))
                        .onSizeChanged { hueBarSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (hueBarSize.width > 0) {
                                        hue = (offset.x / hueBarSize.width).coerceIn(0f, 1f) * 360f
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (hueBarSize.width > 0) {
                                        hue = (change.position.x / hueBarSize.width).coerceIn(0f, 1f) * 360f
                                    }
                                }
                            )
                        }
                ) {
                    if (hueBarSize.width > 0) {
                        val thumbX = ((hue / 360f) * hueBarSize.width).coerceIn(0f, hueBarSize.width.toFloat())
                        Box(
                            modifier = Modifier
                                .offset(x = (thumbX - 4).dp.coerceAtLeast(0.dp))
                                .width(8.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                                .border(1.dp, Color.Black)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Comparação de Cores e Hexadecimal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Swatches Anterior vs Nova
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(6.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color(initialColor))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Anterior", color = if (IpodColorContrastUtil.calculateLuminance(initialColor) > 0.5) Color.Black else Color.White, fontSize = 9.sp)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color(currentColorLong))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Nova", color = if (IpodColorContrastUtil.calculateLuminance(currentColorLong) > 0.5) Color.Black else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Código Hex
                    Text(
                        text = String.format("#%06X", (0xFFFFFF and currentColorLong.toInt())),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. PREVIEW REALISTA DO IPOD
                Text(
                    text = "PRÉVIA REALISTA DO IPOD",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(previewPalette.bodyColor))
                        .border(1.5.dp, Color(0x44000000), RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Mini Tela LCD
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF9EA792)) // LCD clássico
                                .border(0.8.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                .padding(3.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(Color(0xFF334155))
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .background(Color(0xFF1E293B))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(4.dp)
                                        .background(Color(0xFF475569))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Mini Click Wheel
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color(previewPalette.wheelColor))
                                .border(1.dp, Color(0x33000000), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Rótulos da roda
                            Text(
                                text = "MENU",
                                color = Color(previewPalette.wheelTextColor),
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
                            )

                            // Mini Botão Central
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(previewPalette.centerButtonColor))
                                    .border(0.6.dp, Color(0x33000000), CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Alerta de Contraste se a combinação for perigosa
                if (!isCombinationValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33DC2626), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFDC2626), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aviso WCAG: Baixo contraste ou cores muito semelhantes.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancelar", color = Color(0xFF94A3B8))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onColorSelected(currentColorLong)
                            onDismissRequest()
                        },
                        modifier = Modifier.testTag("ambilwarna_confirm_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("Confirmar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
