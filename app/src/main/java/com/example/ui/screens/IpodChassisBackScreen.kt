package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Easter Egg: Traseira de Aço Inox (Stainless Steel) do iPod Classic em Tela Cheia.
 * Renderiza o chassi traseiro com reflexos metálicos hiper-realistas, cantos 3D chanfrados,
 * conector dock de 30 pinos usinado na base e inscrições a laser clássicas.
 */
@Composable
fun IpodChassisBackScreen(
    onFlipBack: () -> Unit,
    isAnimationEnabled: Boolean = true,
    onToggleAnimationEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "metal_sheen")
    val sheenProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheen_anim"
    )

    // Paleta de Aço Inox Cromado / Espelhado Autêntico
    val metalEdgeHighlight = Color(0xFFFFFFFF)
    val metalLight = Color(0xFFF0F3F6)
    val metalMid = Color(0xFFD6DCE2)
    val metalDark = Color(0xFF9BA6B2)
    val metalDeepShadow = Color(0xFF5E6874)

    // Tinta de gravação a laser no metal
    val laserTextColor = Color(0xFF444C55)
    val laserHighlightColor = Color(0x66FFFFFF)

    val freeSpaceGb = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val availableBytes = stat.availableBytes
            val gb = (availableBytes / (1024L * 1024L * 1024L)).toInt()
            gb.coerceAtLeast(1)
        } catch (_: Exception) {
            16
        }
    }

    val deviceModel = remember {
        val model = android.os.Build.MODEL ?: ""
        if (model.isNotBlank()) " • $model" else ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1318)) // Fundo escuro atrás do chassi
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFlipBack
            )
            .padding(horizontal = 14.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Corpo Unibody de Aço Inox do iPod (3D Cantos Chanfrados)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.7f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(32.dp))
                .drawBehind {
                    val w = size.width
                    val h = size.height

                    // 1. Gradiente metálico base escovado e espelhado
                    val baseBrush = Brush.linearGradient(
                        colors = listOf(
                            metalMid,
                            metalLight,
                            metalMid,
                            metalDark,
                            metalLight,
                            metalMid,
                            metalLight
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                    drawRect(brush = baseBrush)

                    // 2. Reflexo dinâmico de luz especular deslizando pelo chassi
                    val sheenX = w * sheenProgress
                    val sheenBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        start = Offset(sheenX - 160f, 0f),
                        end = Offset(sheenX + 160f, h)
                    )
                    drawRect(brush = sheenBrush)

                    // 3. Cantos chanfrados 3D com destaque especular na borda externa
                    val bevelBrush = Brush.sweepGradient(
                        colors = listOf(
                            metalEdgeHighlight,
                            metalLight,
                            metalDeepShadow,
                            metalDark,
                            metalEdgeHighlight,
                            metalLight,
                            metalDeepShadow,
                            metalEdgeHighlight
                        ),
                        center = Offset(w / 2f, h / 2f)
                    )
                    drawRoundRect(
                        brush = bevelBrush,
                        size = size,
                        cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
                    )

                    // 4. Borda interna de profundidade chanfrada (Curvatura Tridimensional)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.25f),
                        topLeft = Offset(4f, 4f),
                        size = Size(w - 8f, h - 8f),
                        cornerRadius = CornerRadius(29.dp.toPx(), 29.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                }
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            // Layout dos elementos internos da traseira
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Topo da Traseira: Chave de Hold e Entrada de Fone (Simulação sutil de acabamento superior)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ícone discreto de trava HOLD
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF7A838E).copy(alpha = 0.45f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    )

                    // Indicador de fone 3.5mm
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF2A3036))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                    )
                }

                // Centro: Logotipo da Pera Mordida em Aço Gravado + Nome MediaPod + Badge 160GB
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    // Logotipo da Pêra Gravado a Laser no Metal com Efeito de Baixo-Relevo
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(68.dp)
                            .height(96.dp)
                    ) {
                        // Sombra de chanfro gravado no metal
                        Image(
                            painter = painterResource(id = R.drawable.ic_pear_logo),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(laserHighlightColor),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = 1.dp, y = 1.2.dp)
                        )
                        // Imagem gravada oficial
                        Image(
                            painter = painterResource(id = R.drawable.ic_pear_logo),
                            contentDescription = "Logotipo Pêra MediaPod",
                            colorFilter = ColorFilter.tint(laserTextColor),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tipografia do Nome "MediaPod" no Estilo Clássico do iPod
                    Text(
                        text = "MediaPod",
                        color = laserTextColor,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.75).sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Badge de Espaço Livre Gravado no Metal
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.2.dp, Color(0xFF6E7884).copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 14.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "FREE SPACE ${freeSpaceGb}GB",
                            color = laserTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Rodapé: Inscrições Regulatórias Gravadas a Laser com Dados do Autor e Versão
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Designed by Márcio Amaro in Araras, SP",
                        color = laserTextColor.copy(alpha = 0.85f),
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Model No: GP-2026 • Assembled with Jetpack Compose in Brazil",
                        color = laserTextColor.copy(alpha = 0.80f),
                        fontSize = 7.8.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Rated 5V ⎓ 1A Max. • Serial No: PROD-051-811312X-40.0$deviceModel",
                        color = laserTextColor.copy(alpha = 0.80f),
                        fontSize = 7.3.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Ícones de Certificação / Homologação Gravados a Laser
                    RegulatoryLaserIconsRow(color = laserTextColor.copy(alpha = 0.75f))

                    Spacer(modifier = Modifier.height(14.dp))

                    // Conector Dock de 30 Pinos Usinado na Base do Chassi
                    DockConnectorPortSimulation()
                }
            }
        }

        // Toggle discreto no rodapé: "Não mostrar mais essa tela"
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC0F1318))
                .border(0.8.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .clickable {
                    onToggleAnimationEnabled(!isAnimationEnabled)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.Checkbox(
                checked = !isAnimationEnabled,
                onCheckedChange = { checked -> onToggleAnimationEnabled(!checked) },
                modifier = Modifier.size(20.dp),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = Color(0xFF64748B),
                    uncheckedColor = Color(0xFFCBD5E1),
                    checkmarkColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Não mostrar mais essa tela",
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * Simulação usinada do clássico conector Dock de 30 pinos na base do iPod de aço inox
 */
@Composable
private fun DockConnectorPortSimulation() {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(11.dp)
            .shadow(2.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E2228)) // Interior escuro da porta dock
            .border(1.dp, Color(0xFF6E7884).copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Pinos internos dourados/metálicos
        Canvas(modifier = Modifier.fillMaxWidth(0.85f).height(3.dp)) {
            val pinCount = 18
            val pinWidth = size.width / (pinCount * 1.8f)
            val step = size.width / pinCount
            for (i in 0 until pinCount) {
                drawRect(
                    color = Color(0xFFB89D52).copy(alpha = 0.75f),
                    topLeft = Offset(i * step, 0f),
                    size = Size(pinWidth, size.height)
                )
            }
        }
    }
}

/**
 * Linha estilizada com símbolos de homologação e certificação clássicos (CE, WEEE, FCC)
 */
@Composable
private fun RegulatoryLaserIconsRow(color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [ CE ]
        Text(
            text = "C E",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.SansSerif
        )

        // [ 0682 ]
        Text(
            text = "0682",
            color = color,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        // [ WEEE Lixeira com X ]
        Canvas(modifier = Modifier.size(9.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.2f),
                size = Size(w * 0.7f, h * 0.75f),
                cornerRadius = CornerRadius(1.5f, 1.5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )
            drawLine(
                color = color,
                start = Offset(w * 0.25f, h * 0.35f),
                end = Offset(w * 0.75f, h * 0.85f),
                strokeWidth = 1.2f
            )
            drawLine(
                color = color,
                start = Offset(w * 0.75f, h * 0.35f),
                end = Offset(w * 0.25f, h * 0.85f),
                strokeWidth = 1.2f
            )
        }

        // [ FCC ]
        Text(
            text = "FC",
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif
        )

        // [ N122 ]
        Text(
            text = "N122",
            color = color,
            fontSize = 7.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
