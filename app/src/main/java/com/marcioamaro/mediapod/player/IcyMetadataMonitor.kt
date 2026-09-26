package com.marcioamaro.mediapod.player

import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lê ICY sem reproduzir áudio local; usado enquanto o áudio está no Cast. */
class IcyMetadataMonitor(private val onTitle: (String) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var connection: HttpURLConnection? = null

    @Synchronized
    fun start(url: String) {
        if (job?.isActive == true) return
        job = scope.launch { monitorWithReconnect(url) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        connection?.disconnect()
        connection = null
    }

    private suspend fun monitorWithReconnect(url: String) {
        var retryDelay = 2_000L
        while (currentCoroutineContext().isActive) {
            try {
                readStream(url)
                retryDelay = 2_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Leitura ICY interrompida; nova tentativa em ${retryDelay}ms: ${e.message}")
            } finally {
                connection?.disconnect()
                connection = null
            }
            if (currentCoroutineContext().isActive) {
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun readStream(url: String) {
        var currentUrl = url
        var redirects = 0
        var input: InputStream? = null
        try {
            while (redirects < MAX_REDIRECTS) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Icy-MetaData", "1")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }
                connection = conn
                conn.connect()
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    307, 308 -> {
                        val location = conn.getHeaderField("Location") ?: error("redirect sem Location")
                        currentUrl = URL(URL(currentUrl), location).toString()
                        conn.disconnect()
                        redirects++
                    }
                    in 200..299 -> {
                        input = BufferedInputStream(conn.inputStream, 32 * 1024)
                        val interval = conn.getHeaderField("icy-metaint")?.toIntOrNull()
                        if (interval == null || interval <= 0) {
                            Log.d(TAG, "Rádio sem icy-metaint: $currentUrl")
                            return
                        }
                        readIcyBlocks(input!!, interval)
                        return
                    }
                    else -> error("HTTP $code")
                }
            }
            error("redirecionamentos demais")
        } finally {
            try { input?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
    }

    private suspend fun readIcyBlocks(input: InputStream, interval: Int) {
        val buffer = ByteArray(16 * 1024)
        while (currentCoroutineContext().isActive) {
            if (!discardFully(input, interval, buffer)) return
            val lengthByte = input.read()
            if (lengthByte < 0) return
            val metadata = ByteArray(lengthByte * 16)
            if (metadata.isNotEmpty() && !readFully(input, metadata)) return
            parseStreamTitle(String(metadata, Charsets.ISO_8859_1))?.let(onTitle)
        }
    }

    private suspend fun discardFully(input: InputStream, count: Int, buffer: ByteArray): Boolean {
        var remaining = count
        while (remaining > 0 && currentCoroutineContext().isActive) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size))
            if (read < 0) return false
            remaining -= read
        }
        return remaining == 0
    }

    private suspend fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size && currentCoroutineContext().isActive) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) return false
            offset += read
        }
        return offset == target.size
    }

    private fun parseStreamTitle(metadata: String): String? {
        val marker = "StreamTitle="
        val start = metadata.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val valueStart = start + marker.length
        val quote = metadata.getOrNull(valueStart) ?: return null
        if (quote != '\'' && quote != '"') return null
        val end = metadata.indexOf(quote, valueStart + 1)
        return if (end > valueStart + 1) metadata.substring(valueStart + 1, end).trim().takeIf { it.isNotBlank() } else null
    }

    companion object {
        private const val TAG = "IcyMetadataMonitor"
        private const val MAX_REDIRECTS = 6
        private const val USER_AGENT = "MediaPod/1.0 (Android; ICY metadata)"
    }
}
