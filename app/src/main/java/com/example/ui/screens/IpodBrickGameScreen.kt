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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import com.example.data.preferences.BrickHighScore
import com.example.data.preferences.IpodPreferencesManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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
    ENTER_INITIALS,
    LEADERBOARD,
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
    // Paleta LCD adaptativa conforme o tema selecionado para o LCD
    val lcdBackground = backlightBg
    val lcdPixelDark = backlightTextPrimary
    val lcdPixelMid = backlightTextSecondary

    // *** FIX: rememberUpdatedState garante que o loop de física SEMPRE leia
    // a posição atual do paddle, não a posição capturada pela closure ***
    val currentPaddlePos by rememberUpdatedState(paddlePositionRatio)

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val prefs = remember { IpodPreferencesManager.getInstance(context) }
    var highScores by remember { mutableStateOf(prefs.getBrickHighScores()) }
    var highlightedScoreRank by remember { mutableIntStateOf(-1) }

    // Iniciais retro para ranking arcade (3 letras)
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!?"
    val initials = remember { mutableStateListOf('A', 'A', 'A') }
    var currentInitialIndex by remember { mutableIntStateOf(0) }
    var lastWheelPosForInitials by remember { mutableFloatStateOf(paddlePositionRatio) }

    var score by rememberSaveable { mutableIntStateOf(0) }
    var level by rememberSaveable { mutableIntStateOf(1) }
    var lives by rememberSaveable { mutableIntStateOf(3) }
    var gameState by rememberSaveable { mutableStateOf(GameState.READY) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                if (gameState == GameState.PLAYING) {
                    gameState = GameState.PAUSED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dimensões normalizadas unificadas (física e renderização idênticas 1:1)
    val initialPaddleWidth = 0.24f     // 24% da largura da área de jogo (nível 1)
    val minPaddleWidth = 0.12f         // Mínimo de 12% (níveis altos)
    val paddleHeightRatio = 0.038f     // Altura da raquete
    val paddleY = 0.86f                // Posição vertical do topo da raquete
    val ballRadius = 0.018f            // Raio normalizado da bola
    val maxBallSpeed = 0.90f           // Trava de segurança anti-tunneling

    // Progressão dinâmica por nível
    val paddleWidthRatio = (initialPaddleWidth * (1f - (level - 1) * 0.07f)).coerceAtLeast(minPaddleWidth)
    val rows = (4 + (level - 1)).coerceAtMost(8)
    val cols = 8

    val brickTopMargin = 0.04f
    val brickTotalHeight = 0.28f
    val brickTotalWidth = 0.94f
    val brickLeftMargin = 0.03f

    // Estado da bola
    var ballX by rememberSaveable { mutableFloatStateOf(0.5f) }
    var ballY by rememberSaveable { mutableFloatStateOf(paddleY - ballRadius) }
    var ballVx by rememberSaveable { mutableFloatStateOf(0.35f) }
    var ballVy by rememberSaveable { mutableFloatStateOf(-0.55f) }

    val bricks = remember {
        mutableStateListOf<Brick>().apply {
            var idCounter = 0
            for (r in 0 until 4) {
                for (c in 0 until cols) {
                    add(Brick(idCounter++, r, c, isAlive = true, points = (4 - r) * 10))
                }
            }
        }
    }

    fun resetBricks(numRows: Int = rows) {
        bricks.clear()
        var idCounter = 0
        for (r in 0 until numRows) {
            for (c in 0 until cols) {
                bricks.add(Brick(idCounter++, r, c, isAlive = true, points = (numRows - r) * 10))
            }
        }
    }

    // Lança a bola a partir da posição atual do paddle mantendo vidas e pontuação
    fun launchBall() {
        val pos = currentPaddlePos.coerceIn(0.12f, 0.88f)
        ballX = pos
        ballY = paddleY - ballRadius - 0.002f
        val baseSpeed = (0.58f + (level - 1) * 0.05f).coerceAtMost(maxBallSpeed)
        val angle = (Random.nextFloat() - 0.5f) * 0.6f
        ballVx = baseSpeed * sin(angle)
        ballVy = -abs(baseSpeed * cos(angle))
        gameState = GameState.PLAYING
        soundAndHaptics.performHeavyHaptic()
    }

    fun startNewGame() {
        score = 0
        level = 1
        lives = 3
        resetBricks(4)
        launchBall()
    }

    fun nextLevel() {
        level++
        val newRows = (4 + (level - 1)).coerceAtMost(8)
        resetBricks(newRows)
        launchBall()
    }

    // Navegação pelas letras via Click Wheel (giro rotativo) na tela de iniciais
    LaunchedEffect(paddlePositionRatio, gameState) {
        if (gameState == GameState.ENTER_INITIALS) {
            val delta = paddlePositionRatio - lastWheelPosForInitials
            if (abs(delta) >= 0.02f) {
                val step = if (delta > 0f) 1 else -1
                val curChar = initials[currentInitialIndex]
                val curIdx = alphabet.indexOf(curChar).coerceAtLeast(0)
                val nextIdx = (curIdx + step).mod(alphabet.length)
                initials[currentInitialIndex] = alphabet[nextIdx]
                lastWheelPosForInitials = paddlePositionRatio
                soundAndHaptics.performClickHaptic()
            }
        } else {
            lastWheelPosForInitials = paddlePositionRatio
        }
    }

    // Acionamento síncrono pelo Botão Central do Click Wheel
    LaunchedEffect(centerActionTrigger) {
        if (centerActionTrigger > 0L) {
            when (gameState) {
                GameState.READY -> launchBall()
                GameState.PLAYING -> {
                    gameState = GameState.PAUSED
                    soundAndHaptics.performClickHaptic()
                }
                GameState.PAUSED -> {
                    gameState = GameState.PLAYING
                    soundAndHaptics.performClickHaptic()
                }
                GameState.ENTER_INITIALS -> {
                    if (currentInitialIndex < 2) {
                        currentInitialIndex++
                        soundAndHaptics.performClickHaptic()
                    } else {
                        // Salva score nas preferências e exibe o ranking arcade Top 10
                        val name = "${initials[0]}${initials[1]}${initials[2]}"
                        val updated = prefs.saveBrickHighScore(name, score)
                        highScores = updated
                        highlightedScoreRank = updated.indexOfFirst { it.initials == name && it.score == score }
                        gameState = GameState.LEADERBOARD
                        soundAndHaptics.performHeavyHaptic()
                    }
                }
                GameState.LEADERBOARD -> {
                    startNewGame()
                }
                GameState.GAME_OVER -> {
                    currentInitialIndex = 0
                    initials[0] = 'A'; initials[1] = 'A'; initials[2] = 'A'
                    gameState = GameState.ENTER_INITIALS
                }
                GameState.VICTORY -> {
                    currentInitialIndex = 0
                    initials[0] = 'A'; initials[1] = 'A'; initials[2] = 'A'
                    gameState = GameState.ENTER_INITIALS
                }
            }
        }
    }

    // Sincroniza a bola sobre a raquete enquanto em modo de espera (READY)
    LaunchedEffect(currentPaddlePos, gameState) {
        if (gameState == GameState.READY) {
            ballX = currentPaddlePos.coerceIn(0.12f, 0.88f)
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

                // Colisão com a Raquete (AABB Contínuo + Anti-Tunneling + Ângulo Dinâmico)
                // *** FIX: Usa currentPaddlePos (rememberUpdatedState) em vez do
                // paddlePositionRatio capturado pela closure do LaunchedEffect ***
                val livePaddlePos = currentPaddlePos
                val livePaddleW = paddleWidthRatio
                val paddleLeft = (livePaddlePos - livePaddleW / 2f).coerceAtLeast(0.01f)
                val paddleRight = (livePaddlePos + livePaddleW / 2f).coerceAtMost(0.99f)

                if (ballVy > 0f) {
                    val ballBottomPrev = ballY + ballRadius
                    val ballBottomNext = nextY + ballRadius
                    val paddleTop = paddleY
                    val paddleBottom = paddleY + paddleHeightRatio

                    // Detecção AABB contínua: a bola cruzou ou tocou a borda superior da raquete?
                    if (ballBottomNext >= paddleTop && ballBottomPrev <= paddleBottom) {
                        // Tolerância de hitbox generosa para evitar misses
                        val minX = paddleLeft - ballRadius
                        val maxX = paddleRight + ballRadius
                        if (nextX in minX..maxX || ballX in minX..maxX) {
                            // SNAP TO TOP: Reposiciona exatamente no topo da barra
                            nextY = paddleTop - ballRadius - 0.001f

                            // Cálculo do ângulo dinâmico (estilo Breakout clássico)
                            val hitOffset = ((nextX - livePaddlePos) / (livePaddleW / 2f)).coerceIn(-1.0f, 1.0f)
                            val maxAngle = 1.15f // ~65 graus de desvio
                            val bounceAngle = hitOffset * maxAngle
                            val speed = (0.58f + (level - 1) * 0.05f).coerceAtMost(maxBallSpeed)

                            ballVx = speed * sin(bounceAngle)
                            // Garante direção SEMPRE para cima após o rebote
                            ballVy = -abs(speed * cos(bounceAngle)).coerceAtLeast(0.15f)
                            soundAndHaptics.performClickHaptic()
                        }
                    }
                }

                // Perda de bola na parte inferior (abaixo da raquete)
                if (nextY >= 0.98f) {
                    lives--
                    soundAndHaptics.performHeavyHaptic()
                    if (lives <= 0) {
                        currentInitialIndex = 0
                        initials[0] = 'A'; initials[1] = 'A'; initials[2] = 'A'
                        highlightedScoreRank = -1
                        gameState = GameState.ENTER_INITIALS
                    } else {
                        // Reposiciona bola sobre a raquete e aguarda relançamento mantendo pontuação e blocos
                        ballX = currentPaddlePos.coerceIn(0.12f, 0.88f)
                        ballY = paddleY - ballRadius
                        ballVx = 0f
                        ballVy = 0f
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

                        // Microaceleração a cada bloco destruído (+2% velocidade, com speed cap)
                        val curSpeed = sqrt(ballVx * ballVx + ballVy * ballVy)
                        val newSpeed = (curSpeed * 1.02f).coerceAtMost(maxBallSpeed)
                        if (curSpeed > 0.01f) {
                            val factor = newSpeed / curSpeed
                            ballVx *= factor
                            ballVy *= factor
                        }
                        break
                    }
                }

                // Vitória: todos os blocos destruídos
                if (hitBrick && bricks.none { it.isAlive }) {
                    if (level >= 10) {
                        currentInitialIndex = 0
                        initials[0] = 'A'; initials[1] = 'A'; initials[2] = 'A'
                        highlightedScoreRank = -1
                        gameState = GameState.ENTER_INITIALS
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
                        GameState.READY -> launchBall()
                        GameState.PLAYING -> {
                            val touchRatio = offset.x / size.width
                            onPaddleMove(touchRatio.coerceIn(0.12f, 0.88f))
                        }
                        GameState.PAUSED -> gameState = GameState.PLAYING
                        GameState.ENTER_INITIALS -> { /* Controles interativos na tela */ }
                        GameState.LEADERBOARD -> startNewGame()
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

                // TELAS DE OVERLAY RETRÔ (Pronto / Pausado / Registro Iniciais / Ranking Top 10)
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
                        val statusSubtext = if (lives < 3 && score > 0) {
                            "VIDAS: " + "●".repeat(lives) + " • PTS: $score"
                        } else {
                            "Gire o Click Wheel para mover"
                        }
                        Text(
                            text = statusSubtext,
                            color = lcdPixelMid,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(lcdPixelDark)
                                    .clickable { launchBall() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (lives < 3) "▶ CONTINUAR" else "▶ JOGAR [CENTRO]",
                                    color = lcdBackground,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.2.dp, lcdPixelDark, RoundedCornerShape(4.dp))
                                    .clickable {
                                        highScores = prefs.getBrickHighScores()
                                        highlightedScoreRank = -1
                                        gameState = GameState.LEADERBOARD
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "🏆 TOP 10",
                                    color = lcdPixelDark,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
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
                } else if (gameState == GameState.ENTER_INITIALS) {
                    // TELA DE DIGITAÇÃO DE 3 INICIAIS (RETRO ARCADE)
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(lcdBackground.copy(alpha = 0.98f))
                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "★ FIM DE JOGO ★",
                            color = lcdPixelDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "PONTUAÇÃO: $score  •  LVL $level",
                            color = lcdPixelMid,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DIGITE SUAS 3 INICIAIS",
                            color = lcdPixelDark,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        // Seletores das 3 letras
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 3) {
                                val isSelected = (i == currentInitialIndex)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "▲",
                                        color = if (isSelected) lcdPixelDark else lcdPixelDark.copy(alpha = 0.25f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .clickable {
                                                currentInitialIndex = i
                                                val curChar = initials[i]
                                                val curIdx = alphabet.indexOf(curChar).coerceAtLeast(0)
                                                val nextIdx = (curIdx + 1).mod(alphabet.length)
                                                initials[i] = alphabet[nextIdx]
                                                soundAndHaptics.performClickHaptic()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp, 38.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) lcdPixelDark else lcdBackground)
                                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(4.dp))
                                            .clickable {
                                                currentInitialIndex = i
                                                soundAndHaptics.performClickHaptic()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials[i].toString(),
                                            color = if (isSelected) lcdBackground else lcdPixelDark,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "▼",
                                        color = if (isSelected) lcdPixelDark else lcdPixelDark.copy(alpha = 0.25f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .clickable {
                                                currentInitialIndex = i
                                                val curChar = initials[i]
                                                val curIdx = alphabet.indexOf(curChar).coerceAtLeast(0)
                                                val nextIdx = (curIdx - 1 + alphabet.length).mod(alphabet.length)
                                                initials[i] = alphabet[nextIdx]
                                                soundAndHaptics.performClickHaptic()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Gire o Click Wheel ou toque ▲/▼",
                            color = lcdPixelMid,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentInitialIndex > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, lcdPixelDark, RoundedCornerShape(4.dp))
                                        .clickable {
                                            currentInitialIndex--
                                            soundAndHaptics.performClickHaptic()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "◄ VOLTAR",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lcdPixelDark,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(lcdPixelDark)
                                    .clickable {
                                        if (currentInitialIndex < 2) {
                                            currentInitialIndex++
                                            soundAndHaptics.performClickHaptic()
                                        } else {
                                            val name = "${initials[0]}${initials[1]}${initials[2]}"
                                            val updated = prefs.saveBrickHighScore(name, score)
                                            highScores = updated
                                            highlightedScoreRank = updated.indexOfFirst { it.initials == name && it.score == score }
                                            gameState = GameState.LEADERBOARD
                                            soundAndHaptics.performHeavyHaptic()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (currentInitialIndex < 2) "PRÓXIMO [CENTRO] ►" else "✓ SALVAR RECORDE [CENTRO]",
                                    color = lcdBackground,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else if (gameState == GameState.LEADERBOARD) {
                    // TABELA DE RANKING TOP 10 (RETRO ARCADE)
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.94f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(lcdBackground.copy(alpha = 0.98f))
                            .border(1.5.dp, lcdPixelDark, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "★ TOP 10 RANKING ARCADE ★",
                            color = lcdPixelDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )

                        // Cabeçalho da tabela
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "POS  INICIAIS",
                                color = lcdPixelMid,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "PONTUAÇÃO",
                                color = lcdPixelMid,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(lcdPixelDark)
                        )

                        // Linhas do Top 10
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val displayList = highScores.take(10)
                            for (idx in displayList.indices) {
                                val item = displayList[idx]
                                val isHighlighted = (idx == highlightedScoreRank)
                                val rankStr = when (idx) {
                                    0 -> "1ST"
                                    1 -> "2ND"
                                    2 -> "3RD"
                                    else -> String.format(java.util.Locale.US, "%02d.", idx + 1)
                                }
                                val scoreStr = String.format(java.util.Locale.US, "%05d", item.score)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isHighlighted) lcdPixelDark else Color.Transparent)
                                        .padding(horizontal = 4.dp, vertical = 0.5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isHighlighted) "▶$rankStr" else " $rankStr",
                                            color = if (isHighlighted) lcdBackground else lcdPixelDark,
                                            fontSize = 8.5.sp,
                                            fontWeight = if (isHighlighted) FontWeight.Black else FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.initials,
                                            color = if (isHighlighted) lcdBackground else lcdPixelDark,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = scoreStr,
                                        color = if (isHighlighted) lcdBackground else lcdPixelDark,
                                        fontSize = 9.sp,
                                        fontWeight = if (isHighlighted) FontWeight.Black else FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(lcdPixelDark)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, lcdPixelDark, RoundedCornerShape(4.dp))
                                    .clickable { gameState = GameState.READY }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "VOLTAR",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lcdPixelDark,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(lcdPixelDark)
                                    .clickable { startNewGame() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "▶ JOGAR NOVAMENTE [CENTRO]",
                                    color = lcdBackground,
                                    fontSize = 8.sp,
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
}
