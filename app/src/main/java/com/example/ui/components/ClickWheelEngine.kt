package com.example.ui.components

import com.example.data.prefs.ClickWheelMode
import com.example.data.prefs.ClickWheelPreferences
import com.example.data.prefs.FixedSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Engine centralizado para cálculo de passos rotacionais da Click Wheel.
 *
 * Suporta:
 * - Modo [ClickWheelMode.PROGRESSIVE]: aceleração dinâmica por rad/s (1x, 2x ou 3x).
 * - Modo [ClickWheelMode.FIXED]: velocidade constante fixa via [FixedSpeed.multiplier] (1, 2 ou 5).
 */
class ClickWheelEngine(
    private val settingsFlow: Flow<ClickWheelPreferences>? = null,
    scope: CoroutineScope? = null
) {
    @Volatile
    private var currentSettings = ClickWheelPreferences()

    private var observationJob: Job? = null

    init {
        if (settingsFlow != null && scope != null) {
            observationJob = scope.launch(Dispatchers.Default) {
                settingsFlow.collectLatest { prefs ->
                    updateSettings(prefs)
                }
            }
        }
    }

    fun updateSettings(settings: ClickWheelPreferences) {
        currentSettings = settings
    }

    fun getSettings(): ClickWheelPreferences = currentSettings

    /**
     * Converte velocidade angular (rad/s) em número de passos de índice.
     * - Modo PROGRESSIVE: mantém a aceleração por velocidade de giro (1x, 2x ou 3x)
     * - Modo FIXED: retorna sempre o multiplicador configurado (1, 2 ou 5)
     */
    fun calculateSteps(angularVelocityRadPerSec: Float): Int {
        return when (currentSettings.mode) {
            ClickWheelMode.PROGRESSIVE -> {
                val absVelocity = abs(angularVelocityRadPerSec)
                when {
                    absVelocity > 12f -> 3  // Giro rápido
                    absVelocity > 6f  -> 2  // Giro médio
                    else              -> 1  // Giro lento / clique por clique
                }
            }
            ClickWheelMode.FIXED -> currentSettings.fixedSpeed.multiplier
        }
    }
}
