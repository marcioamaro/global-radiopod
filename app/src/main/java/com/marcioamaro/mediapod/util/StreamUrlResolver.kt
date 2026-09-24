package com.marcioamaro.mediapod.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pré-resolve URLs de streams de rádio seguindo redirects manualmente.
 *
 * Redes corporativas (Triton Digital / StreamTheWorld) emitem redirects 301/302/307
 * com tokens de sessão de curta duração (~4 s). O ExoPlayer segue o redirect
 * internamente, mas o token pode expirar antes de estabilizar o buffer,
 * causando EOF prematuro. Ao resolver a URL final aqui, o ExoPlayer conecta
 * diretamente ao CDN, evitando a camada de redirect/token.
 *
 * A resolução é feita com um OkHttpClient dedicado com followRedirects=false
 * para capturar cada hop manualmente e preservar o User-Agent em todos eles.
 */
object StreamUrlResolver {

    private const val TAG = "StreamUrlResolver"
    private const val MAX_REDIRECTS = 10

    // Cliente HTTP dedicado para resolução (sem seguir redirects automaticamente)
    private val resolverClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resolve a URL final de um stream, seguindo todos os redirects 301/302/307/308.
     *
     * @param initialUrl URL original do stream (pode conter redirect Triton, etc.)
     * @param userAgent  User-Agent a enviar em cada hop (crucial para evitar 403)
     * @return [Result.success] com a URL final resolvida, ou [Result.failure] em caso de erro
     */
    suspend fun resolveFinalUrl(initialUrl: String, userAgent: String): Result<String> {
        return try {
            var currentUrl = initialUrl
            var hopsRemaining = MAX_REDIRECTS

            while (hopsRemaining > 0) {
                val request = Request.Builder()
                    .url(currentUrl)
                    .head() // HEAD evita baixar o corpo do stream
                    .header("User-Agent", userAgent)
                    .header("Accept", "audio/*,*/*")
                    .header("Connection", "close") // Evita keep-alive stale entre hops
                    .build()

                resolverClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 300..399 -> {
                            val location = response.header("Location")
                            if (location != null) {
                                Log.d(TAG, "Redirect ${response.code}: $currentUrl → $location")
                                currentUrl = location
                                hopsRemaining--
                            } else {
                                Log.w(TAG, "Redirect ${response.code} sem header Location para $currentUrl")
                                return Result.failure(IOException("Redirect ${response.code} sem header Location"))
                            }
                        }
                        200 -> {
                            // Chegamos na URL final que retorna conteúdo
                            if (currentUrl != initialUrl) {
                                Log.i(TAG, "URL final resolvida: $currentUrl (de $initialUrl)")
                            } else {
                                Log.d(TAG, "URL não teve redirect: $currentUrl")
                            }
                            return Result.success(currentUrl)
                        }
                        else -> {
                            // Se HEAD retornar 405 (Method Not Allowed), tenta com GET
                            if (response.code == 405) {
                                Log.d(TAG, "HEAD retornou 405, tentando GET para $currentUrl")
                                return resolveViaGet(currentUrl, userAgent)
                            }
                            Log.w(TAG, "HTTP ${response.code} ao resolver $currentUrl")
                            return Result.failure(IOException("HTTP ${response.code} ao resolver URL"))
                        }
                    }
                }
            }
            Log.w(TAG, "Muitos redirects (>$MAX_REDIRECTS) para $initialUrl")
            Result.failure(IOException("Muitos redirects (>$MAX_REDIRECTS)"))
        } catch (e: Exception) {
            Log.w(TAG, "Exceção ao resolver URL $initialUrl: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Fallback: resolve via GET quando HEAD não é suportado.
     * Conecta, captura a URL final após redirects do OkHttpClient padrão, e fecha imediatamente.
     */
    private fun resolveViaGet(url: String, userAgent: String): Result<String> {
        return try {
            // Usa um cliente temporário que segue redirects para capturar a URL final via GET
            val followClient = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS) // Curto: só precisamos do redirect, não do stream
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "audio/*,*/*")
                .header("Icy-MetaData", "0") // Desativa metadata ICY para reduzir tráfego
                .build()

            val response = followClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()

            if (finalUrl != url) {
                Log.i(TAG, "URL resolvida via GET fallback: $url → $finalUrl")
            }
            Result.success(finalUrl)
        } catch (e: Exception) {
            // Mesmo timeout/IOException pode ocorrer se o stream não responde rápido,
            // mas a URL final já pode ter sido resolvida pelo OkHttp internamente.
            Log.w(TAG, "GET fallback falhou para $url: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifica se a URL é de um provedor que tipicamente usa redirects de sessão efêmeros.
     */
    fun isEphemeralRedirectProvider(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("streamtheworld.com") ||
                lower.contains("playerservices.") ||
                lower.contains("tritondigital.") ||
                lower.contains("livestream-redirect") ||
                lower.contains("stream.revma.") ||
                lower.contains("securenetsystems.") ||
                lower.contains("radiogarden.") ||
                lower.contains("zeno.fm") ||
                lower.contains("/api/livestream")
    }
}
