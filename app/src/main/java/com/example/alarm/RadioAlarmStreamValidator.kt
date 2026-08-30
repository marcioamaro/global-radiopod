package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class AlarmValidationResult {
    object Success : AlarmValidationResult()
    data class Failure(val reason: String) : AlarmValidationResult()
}

/**
 * Validador rigoroso de streaming de rádio para alarmes.
 * Garante que a rádio selecionada está online, responde com HTTP Status OK e
 * reproduz áudio estável ininterruptamente por pelo menos 8 segundos antes de autorizar o salvamento.
 */
object RadioAlarmStreamValidator {

    private const val TAG = "RadioAlarmValidator"

    suspend fun validateStream(
        context: Context,
        streamUrl: String,
        requiredSeconds: Int = 8,
        onProgressSecond: ((Int) -> Unit)? = null
    ): AlarmValidationResult = withContext(Dispatchers.IO) {
        val trimmedUrl = streamUrl.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext AlarmValidationResult.Failure("URL inválida: O streaming deve iniciar com http:// ou https://")
        }

        // 1. Handshake prévio HTTP para verificar se o host está respondendo com sucesso
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(trimmedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:115.0) MediaPod-AlarmValidator/1.0")
                setRequestProperty("Icy-MetaData", "1")
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..399) {
                return@withContext AlarmValidationResult.Failure("Servidor da rádio retornou erro HTTP $responseCode. Stream offline.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha no handshake HTTP da URL: $trimmedUrl", e)
            return@withContext AlarmValidationResult.Failure("Não foi possível conectar ao servidor da rádio: ${e.localizedMessage ?: "Timeout de rede"}")
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }

        // 2. Teste de Reprodução Contínua por pelo menos 8 segundos via MediaPlayer
        var mediaPlayer: MediaPlayer? = null
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                // Volume audível e confortável durante o teste de validação
                setVolume(0.35f, 0.35f)
                setDataSource(trimmedUrl)
            }

            var isPrepared = false
            var prepareError: String? = null

            mediaPlayer.setOnPreparedListener { mp ->
                isPrepared = true
                try {
                    mp.start()
                } catch (e: Exception) {
                    prepareError = e.localizedMessage
                }
            }

            mediaPlayer.setOnErrorListener { _, what, extra ->
                prepareError = "Erro no streaming (código $what, extra $extra)"
                true
            }

            mediaPlayer.prepareAsync()

            // Aguarda o handshake de áudio e buffering inicial (timeout máximo de 10s)
            val startTime = System.currentTimeMillis()
            while (!isPrepared && prepareError == null && (System.currentTimeMillis() - startTime < 10000L)) {
                delay(150L)
            }

            if (prepareError != null) {
                return@withContext AlarmValidationResult.Failure("Falha ao inicializar o áudio: $prepareError")
            }

            if (!isPrepared) {
                return@withContext AlarmValidationResult.Failure("Tempo limite esgotado: O streaming não iniciou o buffer a tempo.")
            }

            // Exige no mínimo 8 segundos de reprodução ininterrupta
            for (currentSec in 1..requiredSeconds) {
                delay(1000L)
                if (prepareError != null) {
                    return@withContext AlarmValidationResult.Failure("Streaming interrompido antes de 8s: $prepareError")
                }
                val isPlaying = try { mediaPlayer.isPlaying } catch (_: Exception) { false }
                if (!isPlaying) {
                    return@withContext AlarmValidationResult.Failure("O streaming parou de tocar antes dos 8 segundos obrigatórios.")
                }
                onProgressSecond?.invoke(currentSec)
            }

            Log.i(TAG, "Validação de 8 segundos concluída com sucesso para: $trimmedUrl")
            AlarmValidationResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante a validação de 8s", e)
            AlarmValidationResult.Failure("Erro ao validar streaming: ${e.localizedMessage ?: "Falha na reprodução"}")
        } finally {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.stop()
                }
                mediaPlayer?.reset()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }
}
