package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
 * Autêntico jogo iPod Classic "Brick" (estilo Breakout).
 * Display monocromático LCD com física precisa e sincronizada,
 * controle rotativo pelo Click Wheel e acionamento pelo Botão Central.
 */
@Composable
fun IpodBrickGameScreen(
    paddlePositionRatio: Float, // 0.0f (esquerda) a 1.0f (direita) controlado pelo Click Wheel
    onPaddleMove: (Float) -> Unit,
    centerActionTrigger: Long = 0L, // Gatilho do botão central do Click Wheel
    soundAndHaptics: IpodSoundAndHaptics,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    modifier: Modifier = Modifier
) {
    // Paleta LCD monocromática autêntica do iPod Classic
    val lcdBackground = Color(0xFF8E9E76) // Verde-oliva clássico LCD retrô
    val lcdPixelDark = Color(0xFF142010)   // Pixel escuro LCD
    val lcdPixelMid = Color(0xFF384A2C)    // Pixel médio LCD

    var score by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(1) }
    var lives by remember { mutableIntStateOf(3) }
    var gameState by remember { mutableStateOf(GameState.READY) }

    // Dimensões normalizadas unificadas (física e renderização idênticas 1:1)
    val paddleWidthRatio = 0.24f       // 24% da largura da área de jogo
    val paddleHeightRatio = 0.038f     // Altura da raquete
    val paddleY = 0.86f                // Posição vertical do topo da raquete
    val ballRadius = 0.018f            // Raio normalizado da bola

    val brickTopMargin = 0.04f
    val brickTotalHeight = 0.28f
    val brickTotalWidth = 0.94f
    val brickLeftMargin = 0.03f

    // Estado da bola
    var ballX by remember { mutableFloatStateOf(0.5f) }
    var ballY by remember { mutableFloatStateOf(paddleY - ballRadius) }
    var ballVx by remember { mutableFloatStateOf(0.35f) }
    var ballVy by remember { mutableFloatStateOf(-0.55f) }

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
        ballX = paddlePositionRatio.coerceIn(0.12f, 0.88f)
        ballY = paddleY - ballRadius
        val angle = (Random.nextFloat() - 0.5f) * 0.6f
        val baseSpeed = 0.58f
        ballVx = baseSpeed * sin(angle)
        ballVy = -abs(baseSpeed * cos(angle))
        gameState = GameState.PLAYING
        soundAndHaptics.performHeavyHaptic()
    }

    fun nextLevel() {
        level++
        resetBricks()
        ballX = paddlePositionRatio.coerceIn(0.12f, 0.88f)
        ballY = paddleY - ballRadius
        val baseSpeed = (0.58f + level * 0.05f).coerceAtMost(0.90f)
        val angle = (Random.nextFloat() - 0.5f) * 0.6f
        ballVx = baseSpeed * sin(angle)
        ballVy = -abs(baseSpeed * cos(angle))
        gameState = GameState.PLAYING
        soundAndHaptics.performHeavyHaptic()
    }

    // Acionamento síncrono pelo Botão Central do Click Wheel
    LaunchedEffect(centerActionTrigger) {
        if (centerActionTrigger > 0L) {
            when (gameState) {
                GameState.READY -> startNewGame()
                GameState.PLAYING -> {
                    gameState = GameState.PAUSED
                    soundAndHaptics.performClickHaptic()
                }
                GameState.PAUSED -> {
                    gameState = GameState.PLAYING
                    soundAndHaptics.performClickHaptic()
                }
                GameState.GAME_OVER -> startNewGame()
                GameState.VICTORY -> nextLevel()
            }
        }
    }

    // Sincroniza a bola sobre a raquete enquanto em modo de espera (READY)
    LaunchedEffect(paddlePositionRatio, gameState) {
        if (gameState == GameState.READY) {
            ballX = paddlePositionRatio.coerceIn(0.12f, 0.88f)
            ballY = paddleY - ballRadius
        }
    }

    // Loop principal da física do jogo com sincronização de taxas de quadros
    LaunchedEffect(gameState, level) {
        if (gameState != GameState.PLAYING) return@LaunchedEffect

        var lastFrameTime = 0L
        while (gameState == GameState.PLAYING) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTime == 0L) {
                    lastFrameTime = frameTimeNanos
                    return@withFrameNanos
                }

                val dt = ((frameTimeNanos - lastFrameTime) / 1_000_000_000f).coerceIn(0.001f, 0.035f)
                lastFrameTime = frameTimeNanos

                // Movimentação da bola
                var nextX = ballX + ballVx * dt
                var nextY = ballY + ballVy * dt

                // Colisão com paredes laterais
                if (nextX - ballRadius <= 0.015f) {
                    nextX = 0.015f + ballRadius
                    ballVx = abs(ballVx)
                    soundAndHaptics.performClickHaptic()
                } else if (nextX + ballRadius >= 0.985f) {
                    nextX = 0.985f - ballRadius
                    ballVx = -abs(ballVx)
                    soundAndHaptics.performClickHaptic()
                }

                // Colisão com teto
                val topBoundary = 0.02f
                if (nextY - ballRadius <= topBoundary) {
                    nextY = topBoundary + ballRadius
                    ballVy = abs(ballVy)
                    soundAndHaptics.performClickHaptic()
                }

                // Colisão com a Raquete (Continuous Detection / Anti-Tunneling)
                val paddleLeft = (paddlePositionRatio - paddleWidthRatio / 2f).coerceAtLeast(0.01f)
                val paddleRight = (paddlePositionRatio + paddleWidthRatio / 2f).coerceAtMost(0.99f)

                if (ballVy > 0f) {
                    val ballBottomPrev = ballY + ballRadius
                    val ballBottomNext = nextY + ballRadius
                    val paddleTop = paddleY
                    val paddleBottom = paddleY + paddleHeightRatio

                    // Verifica se a bola tocou ou cruzou a borda superior da raquete neste frame
                    if (ballBottomNext >= paddleTop && ballBottomPrev <= paddleBottom) {
                        val minX = paddleLeft - ballRadius * 0.8f
                        val maxX = paddleRight + ballRadius * 0.8f
                        if (nextX in minX..maxX || ballX in minX..maxX) {
                            nextY = paddleTop - ballRadius
                            val hitOffset = ((nextX - paddlePositionRatio) / (paddleWidthRatio / 2f)).coerceIn(-1.0f, 1.0f)
                            val maxAngle = 1.15f // ~65 graus de desvio
                            val bounceAngle = hitOffset * maxAngle
                            val speed = (0.58f + level * 0.05f).coerceAtMost(0.90f)

                            ballVx = speed * sin(bounceAngle)
                            ballVy = -abs(speed * cos(bounceAngle))
                            soundAndHaptics.performClickHaptic()
                        }
                    }
                }

                // Perda de bola na parte inferior (abaixo da raquete)
                if (nextY >= 0.98f) {
                    lives--
                    soundAndHaptics.performHeavyHaptic()
                    if (lives <= 0) {
                        gameState = GameState.GAME_OVER
                    } else {
                        // Reposiciona bola sobre a raquete
                        ballX = paddlePositionRatio.coerceIn(0.12f, 0.88f)
                        ballY = paddleY - ballRadius
                        val baseSpeed = 0.58f + level * 0.05f
                        val angle = (Random.nextFloat() - 0.5f) * 0.6f
                        ballVx = baseSpeed * sin(angle)
                        ballVy = -abs(baseSpeed * cos(angle))
                        gameState = GameState.READY
                    }
                    return@withFrameNanos
                }

                // Colisão com os Blocos (Bricks)
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

                        // Determina face de impacto
                        val prevX = ballX
                        if (prevX + ballRadius < bLeft || prevX - ballRadius > bRight) {
                            ballVx = -ballVx
                        } else {
                            ballVy = -ballVy
                        }
                        break
                    }
                }

                // Vitória: todos os blocos destruídos
                if (hitBrick && bricks.none { it.isAlive }) {
                    if (level >= 10) {
                        gameState = GameState.VICTORY
                    } else {
                        nextLevel()
                    }
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
                    when (gameState) {
                        GameState.READY -> startNewGame()
                        GameState.PLAYING -> {
                            val touchRatio = offset.x / size.width
                            onPaddleMove(touchRatio.coerceIn(0.12f, 0.88f))
                        }
                        GameState.PAUSED -> gameState = GameState.PLAYING
                        GameState.GAME_OVER -> startNewGame()
                        GameState.VICTORY -> nextLevel()
                    }
                }
            }
            .testTag("ipod_brick_game_screen")
    ) {
        // BARRA DE STATUS SUPERIOR DO LCD
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone de Play / Pause
                Text(
                    text = if (gameState == GameState.PLAYING) "▶" else "❚❚",
                    color = lcdPixelDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                // Nível e Pontuação
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

                // Vidas e Bateria
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "●".repeat(lives.coerceIn(0, 3)),
                        color = lcdPixelDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Ícone de bateria LCD de 4 barras
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

            // Linha divisória do display LCD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(lcdPixelDark)
            )

            // CANVAS DA ÁREA DE JOGO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Desenha os blocos com pixel art retrô LCD
                    val bTotalTop = h * brickTopMargin
                    val bTotalH = h * brickTotalHeight
                    val bTotalW = w * brickTotalWidth
                    val bTotalLeft = w * brickLeftMargin

                    val bHeight = bTotalH / rows
                    val bWidth = bTotalW / cols

                    for (brick in bricks) {
                        if (!brick.isAlive) continue
                        val bx = bTotalLeft + brick.col * bWidth + 2.dp.toPx()
                        val by = bTotalTop + brick.row * bHeight + 2.dp.toPx()
                        val bw = bWidth - 4.dp.toPx()
                        val bh = bHeight - 4.dp.toPx()

                        // Bloco escuro sólido
                        drawRect(
                            color = lcdPixelDark,
                            topLeft = Offset(bx, by),
                            size = Size(bw, bh)
                        )
                        // Linha interna de brilho sutil
                        drawRect(
                            color = lcdBackground,
                            topLeft = Offset(bx + 1.dp.toPx(), by + 1.dp.toPx()),
                            size = Size(bw - 2.dp.toPx(), 1.dp.toPx())
                        )
                    }

                    // Desenha a Raquete (coordenadas perfeitamente alinhadas com a física)
                    val pWidth = w * paddleWidthRatio
                    val pHeight = h * paddleHeightRatio
                    val pX = (w * paddlePositionRatio - pWidth / 2f).coerceIn(2.dp.toPx(), w - pWidth - 2.dp.toPx())
                    val pY = h * paddleY

                    drawRect(
                        color = lcdPixelDark,
                        topLeft = Offset(pX, pY),
                        size = Size(pWidth, pHeight)
                    )

                    // Desenha a Bola (tamanho exato do diâmetro normalizado)
                    val ballDiameter = w * (ballRadius * 2f)
                    val ballPixelX = w * ballX - ballDiameter / 2f
                    val ballPixelY = h * ballY - ballDiameter / 2f

                    drawRect(
                        color = lcdPixelDark,
                        topLeft = Offset(ballPixelX, ballPixelY),
                        size = Size(ballDiameter, ballDiameter)
                    )
                }

                // TELAS DE OVERLAY RETRÔ (Pronto / Pausado / Fim de Jogo / Vitória)
                if (gameState == GameState.READY) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(6.dp))
                            .background(lcdBackground.copy(alpha = 0.95f))
                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            text = "Gire o Click Wheel para mover",
                            color = lcdPixelMid,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lcdPixelDark)
                                .clickable { startNewGame() }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
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
                } else if (gameState == GameState.PAUSED) {
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
                            text = "PAUSADO",
                            color = lcdPixelDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lcdPixelDark)
                                .clickable { gameState = GameState.PLAYING }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lcdPixelDark)
                                .clickable { startNewGame() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "▶ JOGAR NOVAMENTE",
                                color = lcdBackground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else if (gameState == GameState.VICTORY) {
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
                            text = "VITÓRIA!",
                            color = lcdPixelDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "PARABÉNS! PONTOS: $score",
                            color = lcdPixelMid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lcdPixelDark)
                                .clickable { startNewGame() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "▶ REINICIAR JOGO",
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
