package com.example.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackSelection
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

/**
 * Política de retry inteligente para ExoPlayer.
 *
 * Classifica erros e aplica estratégias de retry diferenciadas:
 *
 * | Tipo de Erro            | Ação                               | Delay               |
 * |-------------------------|------------------------------------|---------------------|
 * | IOException / Timeout   | Retry rápido                       | 1-2s                |
 * | HTTP 503 (Overload)     | Respeita cabeçalho Retry-After     | 3s+                 |
 * | ParserException/Formato | Falha imediata (fatal, sem retry)  | N/A                 |
 * | Outros erros de rede    | Backoff exponencial + Jitter       | 2s→4s→8s…(max 30s)  |
 *
 * Esta política atua na camada de DataSource do ExoPlayer, complementando o
 * mecanismo de reconexão de alto nível em [RadioPlayerManager].
 */
@UnstableApi
class SmartLoadErrorHandlingPolicy(
    private val maxRetries: Int = 6
) : LoadErrorHandlingPolicy {

    private val defaultPolicy = DefaultLoadErrorHandlingPolicy()

    /**
     * Retorna o tempo de espera (ms) antes do próximo retry.
     * Retorna [C.TIME_UNSET] para indicar "não faça retry" (erro fatal).
     */
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        val errorCount = loadErrorInfo.errorCount

        // Limite máximo de tentativas
        if (errorCount > maxRetries) return C.TIME_UNSET

        return when {
            // ─── HTTP 503: Servidor sobrecarregado — respeita Retry-After ──────────
            is503Error(error) -> {
                val retryAfterMs = extractRetryAfterMs(error) ?: 3_000L
                android.util.Log.d("SmartRetryPolicy", "HTTP 503 detectado. Retry-After: ${retryAfterMs}ms (tentativa $errorCount)")
                retryAfterMs
            }

            // ─── Parser / Formato de mídia — falha imediata, sem retry ─────────────
            isFormatError(error) -> {
                android.util.Log.w("SmartRetryPolicy", "Erro de formato/parsing detectado — falha fatal, sem retry: ${error.message}")
                C.TIME_UNSET // Fatal: sem retry
            }

            // ─── IOException / Timeout — retry rápido (1-2s) ───────────────────────
            isNetworkTimeoutOrBasicIO(error) -> {
                val delay = if (errorCount <= 2) 1_000L else 2_000L
                android.util.Log.d("SmartRetryPolicy", "IOException/Timeout — retry rápido em ${delay}ms (tentativa $errorCount)")
                delay
            }

            // ─── Outros erros de rede — backoff exponencial + jitter ───────────────
            isNetworkError(error) -> {
                val base = (2_000L shl (errorCount - 1).coerceAtMost(4)) // 2s, 4s, 8s, 16s, 30s max
                val capped = min(base, 30_000L)
                val jitter = Random.nextLong(0, capped / 4 + 1) // ±25% de jitter
                val delay = capped + jitter
                android.util.Log.d("SmartRetryPolicy", "Erro de rede — backoff exponencial: ${delay}ms (tentativa $errorCount)")
                delay
            }

            // ─── Fallback para política padrão ─────────────────────────────────────
            else -> defaultPolicy.getRetryDelayMsFor(loadErrorInfo)
        }
    }

    /**
     * Número mínimo de carregamentos que devem falhar antes de excluir uma localização.
     * Usamos 1 para reagir imediatamente a erros e acionar o fallback de fonte.
     */
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 1

    /**
     * Seleciona entre localizações de fallback (ex: CDN alternativo).
     * Delegamos ao comportamento padrão, pois o fallback multi-fonte é gerenciado
     * em nível mais alto pelo [RadioPlayerManager].
     */
    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): FallbackSelection? = defaultPolicy.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)

    // ─────────────────────────────────────────────────────────────────────────
    // Classificadores de erro
    // ─────────────────────────────────────────────────────────────────────────

    private fun is503Error(error: IOException): Boolean {
        if (error is HttpDataSource.InvalidResponseCodeException) {
            return error.responseCode == 503
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 503) return true
            cause = cause.cause
        }
        return false
    }

    private fun isFormatError(error: IOException): Boolean {
        val name = error.javaClass.name
        return name.contains("ParserException") ||
            name.contains("NotSeekableException") ||
            name.contains("UnsupportedFormatException") ||
            name.contains("BehindLiveWindowException")
    }

    private fun isNetworkTimeoutOrBasicIO(error: IOException): Boolean {
        if (error is java.net.SocketTimeoutException) return true
        if (error is java.net.ConnectException) return true
        if (error is java.net.UnknownHostException) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.net.SocketTimeoutException) return true
            if (cause is java.net.ConnectException) return true
            if (cause is java.net.UnknownHostException) return true
            cause = cause.cause
        }
        return false
    }

    private fun isNetworkError(error: IOException): Boolean {
        if (error is HttpDataSource.HttpDataSourceException) return true
        if (error is java.io.IOException) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.io.IOException) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Tenta extrair o valor do cabeçalho `Retry-After` de uma exceção HTTP 503.
     * Suporta formatos: segundos inteiros (ex: "30") ou HTTP-date.
     * Retorna null se não encontrado ou não parseável.
     */
    private fun extractRetryAfterMs(error: IOException): Long? {
        val httpError = error as? HttpDataSource.InvalidResponseCodeException ?: return null
        val retryAfter = httpError.headerFields["Retry-After"]?.firstOrNull() ?: return null
        return try {
            val seconds = retryAfter.trim().toLong()
            (seconds * 1_000L).coerceIn(1_000L, 60_000L) // Entre 1s e 60s
        } catch (_: NumberFormatException) {
            // Formato de data (RFC 7231) — não suportado; usa valor padrão conservador
            5_000L
        }
    }
}
