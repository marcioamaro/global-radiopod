package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.IpodSoundAndHaptics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Brick(
    val id: Int,
    val row: Int,
    val col: Int,
    var isAlive: Boolean = true,
    val points: Int = 10
)

enum class GameState {
    READY,
    PLAYING,
    PAUSED,
    GAME_OVER,
    VICTORY
}

/**
 * Authentic iPod Classic "Brick" game clone (Breakout style).
 * Monochromatic LCD display with pixelated aesthetics, high scores,
 * full Click Wheel paddle rotary control and responsive physics.
 */
@Composable
fun IpodBrickGameScreen(
    paddlePositionRatio: Float, // 0.0f (left) to 1.0f (right) controlled by Click Wheel
    onPaddleMove: (Float) -> Unit,
    soundAndHaptics: IpodSoundAndHaptics,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    modifier: Modifier = Modifier
) {
    // Authentic monochrome LCD palette (like image 1)
    val lcdBackground = Color(0xFF8E9E76) // Classic iPod/GameBoy olive-green LCD
    val lcdPixelDark = Color(0xFF142010)   // Dark LCD pixel
    val lcdPixelMid = Color(0xFF384A2C)    // Medium LCD pixel

    var score by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var gameState by remember { mutableStateOf(GameState.READY) }

    // Game physics state
    var ballX by remember { mutableFloatStateOf(0.5f) } // Normalized 0..1
    var ballY by remember { mutableFloatStateOf(0.78f) } // Normalized 0..1
    var ballVx by remember { mutableFloatStateOf(0.35f) }
    var ballVy by remember { mutableFloatStateOf(-0.45f) }

    val paddleWidthRatio = 0.22f // 22% of playfield width
    val paddleHeightRatio = 0.035f

    val rows = 4
    val cols = 8
    val bricks = remember {
        mutableStateListOf<Brick>().apply {
            var idCounter = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    add(Brick(idCounter++, r, c, isAlive = true, points = (rows - r) * 10))
                }
            }
        }
    }

    fun resetBricks() {
        bricks.clear()
        var idCounter = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                bricks.add(Brick(idCounter++, r, c, isAlive = true, points = (rows - r) * 10))
            }
        }
    }

    fun startNewGame() {
        score = 0
        level = 1
        lives = 3
        resetBricks()
        ballX = paddlePositionRatio.coerceIn(0.15f, 0.85f)
        ballY = 0.78f
        val angle = Random.nextFloat() * 0.6f - 0.3f
        val baseSpeed = 0.55f
        ballVx = baseSpeed * sin(angle)
        ballVy = -baseSpeed * cos(angle)
        gameState = GameState.PLAYING
        soundAndHaptics.performHeavyHaptic()
    }

    fun nextLevel() {
        level++
        resetBricks()
        ballX = 0.5f
        ballY = 0.75f
        val baseSpeed = (0.55f + level * 0.05f).coerceAtMost(0.85f)
        val angle = (Random.nextFloat() - 0.5f) * 0.6f
        ballVx = baseSpeed * sin(angle)
        ballVy = -abs(baseSpeed * cos(angle))
        gameState = GameState.PLAYING
        soundAndHaptics.performHeavyHaptic()
    }

    // Main Game Loop running with frame-rate sync
    LaunchedEffect(gameState, level) {
        if (gameState != GameState.PLAYING) return@LaunchedEffect

        var lastFrameTime = 0L
        while (gameState == GameState.PLAYING) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeNanos
                    return@withFrameNanos
                }

                val dt = ((frameTimeNanos - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
                lastFrameTime = frameTimeNanos

                // Move Ball
                var nextX = ballX + ballVx * dt
                var nextY = ballY + ballVy * dt

                val ballRadius = 0.018f

                // Left & Right Wall collisions
                if (nextX - ballRadius <= 0.02f) {
                    nextX = 0.02f + ballRadius
                    ballVx = abs(ballVx)
                    soundAndHaptics.performClickHaptic()
                } else if (nextX + ballRadius >= 0.98f) {
                    nextX = 0.98f - ballRadius
                    ballVx = -abs(ballVx)
                    soundAndHaptics.performClickHaptic()
                }

                // Top Wall collision (below status header)
                val topBoundary = 0.12f
                if (nextY - ballRadius <= topBoundary) {
                    nextY = topBoundary + ballRadius
                    ballVy = abs(ballVy)
                    soundAndHaptics.performClickHaptic()
                }

                // Bottom boundary (Ball Lost)
                if (nextY >= 0.96f) {
                    lives--
                    soundAndHaptics.performHeavyHaptic()
                    if (lives <= 0) {
                        gameState = GameState.GAME_OVER
                    } else {
                        // Reset ball to paddle
                        ballX = paddlePositionRatio.coerceIn(0.15f, 0.85f)
                        ballY = 0.78f
                        val baseSpeed = 0.55f + level * 0.05f
                        ballVx = baseSpeed * 0.4f * (if (Random.nextBoolean()) 1f else -1f)
                        ballVy = -baseSpeed * 0.8f
                        gameState = GameState.READY
                    }
                    return@withFrameNanos
                }

                // Paddle Collision
                val paddleY = 0.84f
                val paddleLeft = (paddlePositionRatio - paddleWidthRatio / 2).coerceAtLeast(0.02f)
                val paddleRight = (paddlePositionRatio + paddleWidthRatio / 2).coerceAtMost(0.98f)

                if (nextY + ballRadius >= paddleY && ballY + ballRadius <= paddleY + paddleHeightRatio) {
                    if (nextX >= paddleLeft - 0.02f && nextX <= paddleRight + 0.02f) {
                        nextY = paddleY - ballRadius
                        // Angle ball based on hit position relative to paddle center
                        val hitOffset = ((nextX - paddlePositionRatio) / (paddleWidthRatio / 2)).coerceIn(-1f, 1f)
                        val maxAngle = 1.05f // ~60 degrees
                        val bounceAngle = hitOffset * maxAngle
                        val speed = (0.55f + level * 0.05f).coerceAtMost(0.85f)

                        ballVx = speed * sin(bounceAngle)
                        ballVy = -abs(speed * cos(bounceAngle))
                        soundAndHaptics.performClickHaptic()
                    }
                }

                // Brick Collisions
                val brickTopMargin = 0.15f
                val brickTotalHeight = 0.22f
                val brickTotalWidth = 0.94f
                val brickLeftMargin = 0.03f

                val brickHeight = brickTotalHeight / rows
                val brickWidth = brickTotalWidth / cols

                var hitBrick = false
                for (brick in bricks) {
                    if (!brick.isAlive) continue

                    val bLeft = brickLeftMargin + brick.col * brickWidth + 0.005f
                    val bRight = bLeft + brickWidth - 0.01f
                    val bTop = brickTopMargin + brick.row * brickHeight + 0.005f
                    val bBottom = bTop + brickHeight - 0.01f

                    if (nextX + ballRadius >= bLeft && nextX - ballRadius <= bRight &&
                        nextY + ballRadius >= bTop && nextY - ballRadius <= bBottom
                    ) {
                        brick.isAlive = false
                        score += brick.points
                        hitBrick = true
                        soundAndHaptics.performClickHaptic()

                        // Determine collision face
                        val prevX = ballX
                        val prevY = ballY

                        if (prevX + ballRadius < bLeft || prevX - ballRadius > bRight) {
                            ballVx = -ballVx
                        } else {
                            ballVy = -ballVy
                        }
                        break
                    }
                }

                // Check victory (all bricks destroyed)
                if (hitBrick && bricks.none { it.isAlive }) {
                    nextLevel()
                    return@withFrameNanos
                }

                ballX = nextX
                ballY = nextY
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(lcdBackground)
            .border(2.dp, lcdPixelDark, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = dragAmount.x / size.width
                    onPaddleMove((paddlePositionRatio + delta).coerceIn(0.12f, 0.88f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (gameState == GameState.READY || gameState == GameState.GAME_OVER) {
                        startNewGame()
                    } else if (gameState == GameState.PAUSED) {
                        gameState = GameState.PLAYING
                    } else {
                        val touchRatio = offset.x / size.width
                        onPaddleMove(touchRatio.coerceIn(0.12f, 0.88f))
                    }
                }
            }
            .testTag("ipod_brick_game_screen")
    ) {
        // TOP LCD STATUS BAR (Authentic iPod layout: Image 1)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Icon
                Text(
                    text = if (gameState == GameState.PLAYING) "▶" else "❚❚",
                    color = lcdPixelDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                // Level / Score
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LVL $level",
                        color = lcdPixelDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "PTS: $score",
                        color = lcdPixelDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Lives & Battery Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "●".repeat(lives.coerceIn(0, 3)),
                        color = lcdPixelDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // 4-Bar iPod LCD Battery Icon
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(10.dp)
                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(1.dp))
                            .padding(1.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(lcdPixelDark)
                                        .padding(horizontal = 0.5.dp)
                                )
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                        }
                    }
                }
            }

            // Divider Line across LCD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(lcdPixelDark)
            )

            // PLAYFIELD CANVAS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw Bricks (Rectangles with crisp retro LCD pixel styling)
                    val brickTopMargin = h * 0.04f
                    val brickTotalHeight = h * 0.28f
                    val brickTotalWidth = w * 0.94f
                    val brickLeftMargin = w * 0.03f

                    val bHeight = brickTotalHeight / rows
                    val bWidth = brickTotalWidth / cols

                    for (brick in bricks) {
                        if (!brick.isAlive) continue
                        val bx = brickLeftMargin + brick.col * bWidth + 2.dp.toPx()
                        val by = brickTopMargin + brick.row * bHeight + 2.dp.toPx()
                        val bw = bWidth - 4.dp.toPx()
                        val bh = bHeight - 4.dp.toPx()

                        // Solid dark brick with pixel inner accent
                        drawRect(
                            color = lcdPixelDark,
                            topLeft = Offset(bx, by),
                            size = Size(bw, bh)
                        )
                        // Tiny highlight line
                        drawRect(
                            color = lcdBackground,
                            topLeft = Offset(bx + 1.dp.toPx(), by + 1.dp.toPx()),
                            size = Size(bw - 2.dp.toPx(), 1.dp.toPx())
                        )
                    }

                    // Draw Paddle (Raquete)
                    val pWidth = w * paddleWidthRatio
                    val pHeight = h * paddleHeightRatio
                    val pX = (w * paddlePositionRatio - pWidth / 2).coerceIn(4.dp.toPx(), w - pWidth - 4.dp.toPx())
                    val pY = h * 0.88f

                    drawRect(
                        color = lcdPixelDark,
                        topLeft = Offset(pX, pY),
                        size = Size(pWidth, pHeight)
                    )

                    // Draw Ball (Pixel square)
                    val ballSize = w * 0.032f
                    val ballPixelX = w * ballX - ballSize / 2
                    val ballPixelY = h * ballY - ballSize / 2

                    drawRect(
                        color = lcdPixelDark,
                        topLeft = Offset(ballPixelX, ballPixelY),
                        size = Size(ballSize, ballSize)
                    )
                }

                // OVERLAY SCREENS (Ready / Game Over / Pause)
                if (gameState == GameState.READY) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(6.dp))
                            .background(lcdBackground.copy(alpha = 0.95f))
                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "BRICK",
                            color = lcdPixelDark,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Gire o SlideCircle Click para mover a raquete",
                            color = lcdPixelMid,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lcdPixelDark)
                                .clickable { startNewGame() }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "▶ PRESSIONE CENTRO",
                                color = lcdBackground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else if (gameState == GameState.GAME_OVER) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(6.dp))
                            .background(lcdBackground.copy(alpha = 0.95f))
                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FIM DE JOGO",
                            color = lcdPixelDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "PONTUAÇÃO: $score",
                            color = lcdPixelMid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lcdPixelDark)
                                .clickable { startNewGame() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "JOGAR NOVAMENTE",
                                color = lcdBackground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
