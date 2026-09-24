package com.marcioamaro.mediapod.data

import com.marcioamaro.mediapod.data.download.ResumableTransfer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ResumableTransferTest {
    @get:Rule val temp = TemporaryFolder()

    private fun response(status: String, headers: String, body: String, transfer: (String) -> Unit): String {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val request = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10000
                        val reader = socket.getInputStream().bufferedReader()
                        val lines = mutableListOf<String>()
                        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; lines += line }
                        socket.getOutputStream().write("HTTP/1.1 $status\r\n$headers\r\nConnection: close\r\n\r\n$body".toByteArray())
                        lines.joinToString("\n")
                    }
                }
                transfer("http://127.0.0.1:${server.localPort}/audio")
                return request.get(10, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun resumesOnlyAtTheRequestedOffset() {
        val partial = temp.newFile().apply { writeText("abc") }
        val validator = temp.newFile().apply { writeText("\"v1\"") }
        val request = response("206 Partial Content", "Content-Length: 3\r\nContent-Range: bytes 3-5/6\r\nETag: \"v1\"", "def") {
            ResumableTransfer.transfer(it, partial, validator, {}, { _, _ -> })
        }
        assertEquals("abcdef", partial.readText())
        assertTrue(request.contains("Range: bytes=3-"))
        assertTrue(request.contains("If-Range: \"v1\""))
    }

    @Test fun fullResponseReplacesPartialAndTruncationNeverCompletes() {
        val partial = temp.newFile().apply { writeText("old") }
        val validator = temp.newFile().apply { writeText("\"v1\"") }
        response("200 OK", "Content-Length: 6\r\nETag: \"v2\"", "abcdef") {
            ResumableTransfer.transfer(it, partial, validator, {}, { _, _ -> })
        }
        assertEquals("abcdef", partial.readText())
        response("200 OK", "Content-Length: 10\r\nETag: \"v3\"", "short") {
            assertTrue(runCatching { ResumableTransfer.transfer(it, partial, validator, {}, { _, _ -> }) }.isFailure)
        }
        assertEquals("short", partial.readText())
    }

    @Test fun inconsistentRangeDoesNotAppendWrongBytes() {
        val partial = temp.newFile().apply { writeText("abc") }
        val validator = temp.newFile().apply { writeText("\"v1\"") }
        response("206 Partial Content", "Content-Length: 3\r\nContent-Range: bytes 0-2/6", "bad") {
            assertTrue(runCatching { ResumableTransfer.transfer(it, partial, validator, {}, { _, _ -> }) }.isFailure)
        }
        assertEquals("abc", partial.readText())
    }
}
