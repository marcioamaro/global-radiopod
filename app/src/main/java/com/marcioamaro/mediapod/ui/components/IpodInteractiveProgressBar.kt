package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Barra de progresso interativa estilo iPod Classic com:
 * - Touch Target confortável expandido (40dp invisível)
 * - Toque direto (click-to-seek)
 * - Scrubbing/Arraste horizontal com atualização de timers em tempo real
 */
@Composable
fun IpodInteractiveProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    touchTargetHeight: Dp = 40.dp
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val actualProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val displayProgress = if (isDragging) dragRatio else actualProgress

    val currentDisplayMs = if (isDragging) {
        (dragRatio * durationMs).toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
    } else {
        positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    val posSecs = (currentDisplayMs / 1000).coerceAtLeast(0)
    val posFormatted = if (posSecs >= 3600) {
        String.format(Locale.US, "%d:%02d:%02d", posSecs / 3600, (posSecs % 3600) / 60, posSecs % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", posSecs / 60, posSecs % 60)
    }

    val remainingSecs = ((durationMs - currentDisplayMs) / 1000).coerceAtLeast(0)
    val remainingFormatted = if (remainingSecs >= 3600) {
        String.format(Locale.US, "-%d:%02d:%02d", remainingSecs / 3600, (remainingSecs % 3600) / 60, remainingSecs % 60)
    } else {
        String.format(Locale.US, "-%02d:%02d", remainingSecs / 60, remainingSecs % 60)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Área interativa expandida de toque e arraste
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchTargetHeight)
                .onSizeChanged { size ->
                    if (size.width > 0) {
                        barWidthPx = size.width.toFloat()
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (barWidthPx > 0 && durationMs > 0) {
                            val ratio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            val target = (ratio * durationMs).toLong()
                            onSeekTo(target)
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (barWidthPx > 0 && durationMs > 0) {
                                isDragging = true
                                dragRatio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (barWidthPx > 0) {
                                val deltaRatio = dragAmount / barWidthPx
                                dragRatio = (dragRatio + deltaRatio).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (isDragging && durationMs > 0) {
                                isDragging = false
                                val target = (dragRatio * durationMs).toLong()
                                onSeekTo(target)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Track de fundo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(backlightTextPrimary.copy(alpha = 0.25f))
            ) {
                // Progresso preenchido
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress)
                        .fillMaxHeight()
                        .background(backlightTextPrimary)
                )
            }

            // Indicador de scrubbing retrô (Thumb) quando arrastando ou ativo
            if (isDragging && displayProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(touchTargetHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = ((displayProgress * barWidthPx) - 6f).coerceAtLeast(0f).dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(backlightTextPrimary)
                            .border(1.5.dp, backlightTextSecondary, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Timers: Esquerda = Decorrido, Direita = Restante (-mm:ss)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = posFormatted,
                color = backlightTextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
            if (isDragging) {
                Text(
                    text = "[SEEK]",
                    color = backlightTextPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily
                )
            }
            Text(
                text = remainingFormatted,
                color = backlightTextSecondary,
                fontSize = 9.sp,
                fontFamily = fontFamily
            )
        }
    }
}
