package com.example.game

data class BrickModel(
    val id: Int,
    val row: Int,
    val col: Int,
    var isAlive: Boolean = true,
    val points: Int = 10
)

enum class BrickEngineState {
    READY,
    PLAYING,
    PAUSED,
    ENTER_INITIALS,
    LEADERBOARD,
    GAME_OVER,
    VICTORY
}

data class GameTickResult(
    val hitPaddle: Boolean = false,
    val hitBrick: Boolean = false,
    val hitWall: Boolean = false,
    val lifeLost: Boolean = false,
    val levelCleared: Boolean = false,
    val gameOver: Boolean = false
)

/**
 * Motor de física e regras de negócio do jogo Brick do iPod.
 * Desacoplado da renderização do Compose, com relógio injetável e 100% testável.
 */
class BrickGameEngine(
    val rows: Int = 4,
    val cols: Int = 6,
    initialLives: Int = 3
) {
    var gameState: BrickEngineState = BrickEngineState.READY
        private set

    var score: Int = 0
        private set

    var lives: Int = initialLives
        private set

    var level: Int = 1
        private set

    var paddleRatio: Float = 0.5f // 0.0 (esquerda) a 1.0 (direita)
        private set

    var ballX: Float = 0.5f
        private set
    var ballY: Float = 0.8f
        private set
    var ballVx: Float = 0.4f
        private set
    var ballVy: Float = -0.5f
        private set

    val bricks: MutableList<BrickModel> = mutableListOf()

    init {
        resetBricks()
    }

    fun resetBricks() {
        bricks.clear()
        var idCounter = 0
        for (r in 0 until rows) {
            val points = (rows - r) * 10
            for (c in 0 until cols) {
                bricks.add(BrickModel(id = idCounter++, row = r, col = c, isAlive = true, points = points))
            }
        }
    }

    fun startOrLaunch() {
        if (gameState == BrickEngineState.READY || gameState == BrickEngineState.PAUSED) {
            gameState = BrickEngineState.PLAYING
        }
    }

    fun pause() {
        if (gameState == BrickEngineState.PLAYING) {
            gameState = BrickEngineState.PAUSED
        }
    }

    fun resume() {
        if (gameState == BrickEngineState.PAUSED) {
            gameState = BrickEngineState.PLAYING
        }
    }

    fun restart() {
        score = 0
        lives = 3
        level = 1
        paddleRatio = 0.5f
        ballX = 0.5f
        ballY = 0.8f
        ballVx = 0.4f
        ballVy = -0.5f
        resetBricks()
        gameState = BrickEngineState.READY
    }

    fun updatePaddle(ratio: Float) {
        paddleRatio = ratio.coerceIn(0.0f, 1.0f)
    }

    /**
     * Executa um tick de física no motor com deltaTime em segundos.
     * Retorna GameTickResult detalhando os eventos ocorridos no frame.
     */
    fun tick(deltaTimeSec: Float): GameTickResult {
        if (gameState != BrickEngineState.PLAYING) {
            return GameTickResult()
        }

        var hitPaddle = false
        var hitBrick = false
        var hitWall = false
        var lifeLost = false
        var levelCleared = false
        var isGameOver = false

        val dt = deltaTimeSec.coerceIn(0.001f, 0.05f)

        // Atualiza posição da bola
        ballX += ballVx * dt
        ballY += ballVy * dt

        // Colisão com paredes laterais
        if (ballX <= 0.03f) {
            ballX = 0.03f
            ballVx = kotlin.math.abs(ballVx)
            hitWall = true
        } else if (ballX >= 0.97f) {
            ballX = 0.97f
            ballVx = -kotlin.math.abs(ballVx)
            hitWall = true
        }

        // Colisão com o teto
        if (ballY <= 0.05f) {
            ballY = 0.05f
            ballVy = kotlin.math.abs(ballVy)
            hitWall = true
        }

        // Colisão com o Paddle (Paddle fica em Y ~ 0.90 com largura ~ 0.22)
        val paddleWidthRatio = 0.22f
        val paddleLeft = (paddleRatio - paddleWidthRatio / 2f).coerceAtLeast(0.0f)
        val paddleRight = (paddleRatio + paddleWidthRatio / 2f).coerceAtMost(1.0f)
        val paddleTop = 0.88f
        val paddleBottom = 0.92f

        if (ballY in paddleTop..paddleBottom && ballVy > 0f) {
            if (ballX in paddleLeft..paddleRight) {
                ballY = paddleTop
                val hitOffset = (ballX - paddleRatio) / (paddleWidthRatio / 2f) // -1.0 a 1.0
                ballVx = hitOffset * 0.7f
                ballVy = -kotlin.math.sqrt((0.6f * 0.6f - ballVx * ballVx).coerceAtLeast(0.1f))
                hitPaddle = true
            }
        }

        // Colisão com os Bricks (área dos tijolos: Y de 0.12 a 0.40)
        if (ballY in 0.10f..0.45f) {
            val brickHeight = 0.30f / rows
            val brickWidth = 0.90f / cols
            val startX = 0.05f

            for (brick in bricks) {
                if (!brick.isAlive) continue
                val bLeft = startX + brick.col * brickWidth
                val bRight = bLeft + brickWidth
                val bTop = 0.12f + brick.row * brickHeight
                val bBottom = bTop + brickHeight

                if (ballX in bLeft..bRight && ballY in bTop..bBottom) {
                    brick.isAlive = false
                    score += brick.points
                    ballVy = -ballVy
                    hitBrick = true
                    break
                }
            }

            // Verifica vitória / limpeza de fase
            if (bricks.none { it.isAlive }) {
                levelCleared = true
                level++
                resetBricks()
                ballX = 0.5f
                ballY = 0.7f
                ballVy = -0.55f
                gameState = BrickEngineState.VICTORY
            }
        }

        // Queda da bola (perda de vida)
        if (ballY >= 1.0f) {
            lives--
            lifeLost = true
            if (lives <= 0) {
                gameState = BrickEngineState.GAME_OVER
                isGameOver = true
            } else {
                ballX = paddleRatio
                ballY = 0.8f
                ballVx = 0.35f
                ballVy = -0.5f
                gameState = BrickEngineState.READY
            }
        }

        return GameTickResult(
            hitPaddle = hitPaddle,
            hitBrick = hitBrick,
            hitWall = hitWall,
            lifeLost = lifeLost,
            levelCleared = levelCleared,
            gameOver = isGameOver
        )
    }
}
