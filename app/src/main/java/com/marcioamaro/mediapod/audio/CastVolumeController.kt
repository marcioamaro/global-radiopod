package com.marcioamaro.mediapod.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Controlador de volume unidirecional para Google Cast com proteção anti-eco e tolerância.
 * Elimina o loop de feedback ("vai e volta") entre a Click Wheel / UI e os callbacks de status do Cast.
 */
class CastVolumeController(
    private val onSendVolumeCommand: ((Float) -> Unit)? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _castVolume = MutableStateFlow(1.0f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    private var lastSentVolume: Double = -1.0
    private var lastSentTimestamp: Long = 0L

    private fun logDebug(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {}
    }

    companion object {
        const val DEBOUNCE_MS = 250L
        const val TOLERANCE = 0.02 // 2% — ignora diferenças de arredondamento
    }

    /**
     * Define o volume inicial lido ao conectar no CastSession.
     */
    fun setInitialVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _castVolume.value = clamped
        lastSentVolume = clamped.toDouble()
        lastSentTimestamp = 0L
    }

    /**
     * Chamado quando o usuário gira a Click Wheel ou ajusta o slider de volume no app.
     * Atualiza a UI imediatamente de forma otimista e envia o comando canônico.
     */
    fun onUserVolumeChange(newVolume: Float) {
        val clamped = newVolume.coerceIn(0f, 1f)
        lastSentVolume = clamped.toDouble()
        lastSentTimestamp = timeProvider()
        _castVolume.value = clamped
        logDebug("CAST_VOL", "onUserVolumeChange: $clamped enviado")
        onSendVolumeCommand?.invoke(clamped)
    }

    /**
     * Chamado pelo callback canônico do Cast (ex: Cast.Listener.onVolumeChanged()).
     * Filtra ecos do próprio comando (janela de debounce de 250ms e tolerância de 2%),
     * aceitando apenas mudanças externas reais (controle remoto da TV, Google Home, etc.).
     */
    fun onCastStatusVolumeReported(reportedVolume: Double) {
        val now = timeProvider()

        // REGRA 1: Se enviamos um volume há menos de DEBOUNCE_MS, IGNORAR o report (eco)
        if (now - lastSentTimestamp < DEBOUNCE_MS) {
            logDebug("CAST_VOL", "Report ignorado (eco do nosso envio): $reportedVolume")
            return
        }

        // REGRA 2: Se o valor reportado é ~igual ao que enviamos (dentro da tolerância), ignorar
        if (lastSentVolume >= 0.0 && abs(reportedVolume - lastSentVolume) < TOLERANCE) {
            logDebug("CAST_VOL", "Report ignorado (dentro da tolerância): $reportedVolume")
            return
        }

        // REGRA 3: Só atualizar a UI se for uma mudança EXTERNA real
        logDebug("CAST_VOL", "Mudança externa detectada: $reportedVolume")
        val clamped = reportedVolume.toFloat().coerceIn(0f, 1f)
        _castVolume.value = clamped
        lastSentVolume = reportedVolume
    }
}
