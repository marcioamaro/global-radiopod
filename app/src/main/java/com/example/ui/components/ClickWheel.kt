package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2

@Composable
fun ClickWheel(
    onRotaryScroll: (stepDelta: Int) -> Unit,
    onCenterClick: () -> Unit,
    onMenuClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    wheelColor: Color,
    textColor: Color = Color.White,
    centerButtonColor: Color = Color.White,
    wheelSize: Dp = 240.dp,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val effectiveSize = if (maxWidth.isSpecified && maxWidth > 0.dp && maxHeight.isSpecified && maxHeight > 0.dp) {
            minOf(maxWidth, maxHeight, wheelSize)
        } else if (maxWidth.isSpecified && maxWidth > 0.dp) {
            minOf(maxWidth, wheelSize)
        } else {
            wheelSize
        }
        val actualWheelSize = if (effectiveSize > 50.dp) effectiveSize else wheelSize

        var previousAngle by remember { mutableFloatStateOf(0f) }
        var accumulatedDelta by remember { mutableFloatStateOf(0f) }
        var wheelCenter by remember { mutableStateOf(Offset.Zero) }

        val ROTATION_THRESHOLD_RADIANS = (PI / 9).toFloat() // ~20 degrees per step

        // Exact classic iPod ClickWheel proportion:
        // Center button is exactly 38% of outer wheel diameter across all device modes
        val centerSize = actualWheelSize * 0.38f
        val offsetDist = actualWheelSize * 0.055f
        val fontMultiplier = (actualWheelSize.value / 240f).coerceIn(0.75f, 1.35f)

        Box(
            modifier = Modifier
                .size(actualWheelSize)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(wheelColor)
                .border(1.5.dp, textColor.copy(alpha = 0.25f), CircleShape)
                .onGloballyPositioned { coordinates ->
                    val width = coordinates.size.width.toFloat()
                    val height = coordinates.size.height.toFloat()
                    wheelCenter = Offset(width / 2f, height / 2f)
                }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val dx = offset.x - wheelCenter.x
                        val dy = offset.y - wheelCenter.y
                        previousAngle = atan2(dy, dx)
                        accumulatedDelta = 0f
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val dx = change.position.x - wheelCenter.x
                        val dy = change.position.y - wheelCenter.y
                        val currentAngle = atan2(dy, dx)

                        var diff = currentAngle - previousAngle
                        // Handle wrap-around across -PI / +PI
                        if (diff > PI) diff -= (2 * PI).toFloat()
                        if (diff < -PI) diff += (2 * PI).toFloat()

                        accumulatedDelta += diff
                        previousAngle = currentAngle

                        if (accumulatedDelta >= ROTATION_THRESHOLD_RADIANS) {
                            val steps = (accumulatedDelta / ROTATION_THRESHOLD_RADIANS).toInt()
                            onRotaryScroll(steps)
                            accumulatedDelta -= steps * ROTATION_THRESHOLD_RADIANS
                        } else if (accumulatedDelta <= -ROTATION_THRESHOLD_RADIANS) {
                            val steps = (accumulatedDelta / ROTATION_THRESHOLD_RADIANS).toInt()
                            onRotaryScroll(steps)
                            accumulatedDelta -= steps * ROTATION_THRESHOLD_RADIANS
                        }
                    }
                )
            }
            .testTag("ipod_click_wheel"),
        contentAlignment = Alignment.Center
    ) {
        // TOP: MENU Button
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = offsetDist)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 26.dp),
                    onClick = onMenuClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("click_wheel_menu_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MENU",
                color = textColor,
                fontSize = (13f * fontMultiplier).sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.2.sp
            )
        }

        // BOTTOM: PLAY / PAUSE Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -offsetDist)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 26.dp),
                    onClick = onPlayPauseClick
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("click_wheel_play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶ ❚❚",
                color = textColor,
                fontSize = (12.5f * fontMultiplier).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // LEFT: PREV / REWIND Button
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = offsetDist)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 26.dp),
                    onClick = onPrevClick
                )
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .testTag("click_wheel_prev_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "❚◀◀",
                color = textColor,
                fontSize = (12.5f * fontMultiplier).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // RIGHT: NEXT / FAST FORWARD Button
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -offsetDist)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 26.dp),
                    onClick = onNextClick
                )
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .testTag("click_wheel_next_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶▶❚",
                color = textColor,
                fontSize = (12.5f * fontMultiplier).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Center SELECT Button (White/custom disc with bitten pear silhouette)
        Box(
            modifier = Modifier
                .size(centerSize)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(centerButtonColor)
                .border(1.dp, textColor.copy(alpha = 0.2f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 40.dp),
                    onClick = onCenterClick
                )
                .testTag("click_wheel_center_button"),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_pear_logo),
                contentDescription = "Botão Central Pera",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(textColor.copy(alpha = 0.38f)),
                modifier = Modifier.size(centerSize * 0.35f)
            )
        }
    }
}
}
