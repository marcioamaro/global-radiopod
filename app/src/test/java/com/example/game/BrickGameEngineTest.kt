package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrickGameEngineTest {

    private lateinit var engine: BrickGameEngine

    @Before
    fun setup() {
        engine = BrickGameEngine(rows = 4, cols = 6, initialLives = 3)
    }

    @Test
    fun testInitialStateIsReadyWithBricks() {
        assertEquals(BrickEngineState.READY, engine.gameState)
        assertEquals(24, engine.bricks.size)
        assertTrue(engine.bricks.all { it.isAlive })
        assertEquals(0, engine.score)
        assertEquals(3, engine.lives)
        assertEquals(1, engine.level)
    }

    @Test
    fun testStartPauseResume() {
        engine.startOrLaunch()
        assertEquals(BrickEngineState.PLAYING, engine.gameState)

        engine.pause()
        assertEquals(BrickEngineState.PAUSED, engine.gameState)

        engine.resume()
        assertEquals(BrickEngineState.PLAYING, engine.gameState)
    }

    @Test
    fun testPaddleMovementCoerced() {
        engine.updatePaddle(0.75f)
        assertEquals(0.75f, engine.paddleRatio, 0.001f)

        engine.updatePaddle(-0.5f)
        assertEquals(0.0f, engine.paddleRatio, 0.001f)

        engine.updatePaddle(1.5f)
        assertEquals(1.0f, engine.paddleRatio, 0.001f)
    }

    @Test
    fun testWallCollisionRebounds() {
        engine.startOrLaunch()
        // Move paddle to center
        engine.updatePaddle(0.5f)
        
        // Advance time multiple ticks to verify wall bounces
        var hitAnyWall = false
        for (i in 0 until 100) {
            val res = engine.tick(0.02f)
            if (res.hitWall) {
                hitAnyWall = true
                break
            }
        }
        assertTrue("Ball should hit the wall/ceiling and rebound", hitAnyWall)
    }

    @Test
    fun testBallLossDecrementsLife() {
        engine.startOrLaunch()
        // Position paddle away so ball falls
        engine.updatePaddle(0.0f)
        
        // Force ball downward
        var lifeLost = false
        for (i in 0 until 100) {
            val res = engine.tick(0.03f)
            if (res.lifeLost) {
                lifeLost = true
                break
            }
        }
        assertTrue("Ball should fall past bottom and lose a life", lifeLost)
        assertEquals("Lives should decrement from 3 to 2", 2, engine.lives)
    }

    @Test
    fun testGameOverWhenAllLivesLost() {
        engine.startOrLaunch()
        // Drain all lives
        for (life in 1..3) {
            engine.startOrLaunch()
            engine.updatePaddle(0.0f)
            for (i in 0 until 100) {
                val res = engine.tick(0.03f)
                if (res.lifeLost) break
            }
        }
        assertEquals(0, engine.lives)
        assertEquals(BrickEngineState.GAME_OVER, engine.gameState)
    }
}
