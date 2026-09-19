package com.example.ui.components

import com.example.data.prefs.ClickWheelMode
import com.example.data.prefs.ClickWheelPreferences
import com.example.data.prefs.FixedSpeed
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClickWheelEngineTest {

    private lateinit var engine: ClickWheelEngine

    @Before
    fun setup() {
        engine = ClickWheelEngine()
    }

    @Test
    fun `progressive mode should return 1 step for slow rotation`() {
        engine.updateSettings(ClickWheelPreferences(mode = ClickWheelMode.PROGRESSIVE))
        assertEquals(1, engine.calculateSteps(2f))
    }

    @Test
    fun `progressive mode should return 3 steps for fast rotation`() {
        engine.updateSettings(ClickWheelPreferences(mode = ClickWheelMode.PROGRESSIVE))
        assertEquals(3, engine.calculateSteps(15f))
    }

    @Test
    fun `fixed mode slow should always return 1 step regardless of velocity`() {
        engine.updateSettings(ClickWheelPreferences(mode = ClickWheelMode.FIXED, fixedSpeed = FixedSpeed.SLOW))
        assertEquals(1, engine.calculateSteps(2f))
        assertEquals(1, engine.calculateSteps(20f))
    }

    @Test
    fun `fixed mode fast should always return 5 steps`() {
        engine.updateSettings(ClickWheelPreferences(mode = ClickWheelMode.FIXED, fixedSpeed = FixedSpeed.FAST))
        assertEquals(5, engine.calculateSteps(1f))
        assertEquals(5, engine.calculateSteps(20f))
    }

    @Test
    fun `progressive mode should return 2 steps for medium rotation`() {
        engine.updateSettings(ClickWheelPreferences(mode = ClickWheelMode.PROGRESSIVE))
        assertEquals(2, engine.calculateSteps(8f))
    }

    @Test
    fun `fixed mode standard should always return 2 steps`() {
        engine.updateSettings(ClickWheelPreferences(mode = ClickWheelMode.FIXED, fixedSpeed = FixedSpeed.STANDARD))
        assertEquals(2, engine.calculateSteps(1f))
        assertEquals(2, engine.calculateSteps(15f))
    }
}
