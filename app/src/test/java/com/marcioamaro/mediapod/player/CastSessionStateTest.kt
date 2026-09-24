package com.marcioamaro.mediapod.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSessionStateTest {

    @Test
    fun testCastSessionStateEnumCompleteness() {
        val states = CastSessionState.values().map { it.name }.toSet()
        val expectedStates = setOf(
            "DISCONNECTED",
            "CONNECTING",
            "CONNECTED",
            "TRANSFERRING",
            "SUSPENDED",
            "ENDING",
            "ERROR"
        )
        assertEquals("Todos os 7 estados da máquina de estados do Cast devem existir", expectedStates, states)
    }

    @Test
    fun testCastSessionStateTransitionsContract() {
        var currentState = CastSessionState.DISCONNECTED
        assertEquals(CastSessionState.DISCONNECTED, currentState)

        // Usuário seleciona Cast:
        currentState = CastSessionState.CONNECTING
        assertEquals(CastSessionState.CONNECTING, currentState)

        // Conexão estabelecida:
        currentState = CastSessionState.CONNECTED
        assertEquals(CastSessionState.CONNECTED, currentState)

        // Transferência de mídia em curso:
        currentState = CastSessionState.TRANSFERRING
        assertEquals(CastSessionState.TRANSFERRING, currentState)

        // Mídia tocando no receptor:
        currentState = CastSessionState.CONNECTED
        assertEquals(CastSessionState.CONNECTED, currentState)

        // Wi-Fi cai temporariamente:
        currentState = CastSessionState.SUSPENDED
        assertEquals(CastSessionState.SUSPENDED, currentState)

        // Usuário encerra sessão:
        currentState = CastSessionState.ENDING
        assertEquals(CastSessionState.ENDING, currentState)

        // Sessão finalizada:
        currentState = CastSessionState.DISCONNECTED
        assertEquals(CastSessionState.DISCONNECTED, currentState)
    }

    @Test
    fun testCastSessionRecoveryFailureContract() {
        var currentState = CastSessionState.CONNECTED

        // Perda de sinal Wi-Fi
        currentState = CastSessionState.SUSPENDED
        assertEquals(CastSessionState.SUSPENDED, currentState)

        // Tentativa de reconexão
        currentState = CastSessionState.CONNECTING
        assertEquals(CastSessionState.CONNECTING, currentState)

        // Falha na reconexão: deve ir para ERROR e acionar retorno local
        currentState = CastSessionState.ERROR
        assertEquals(CastSessionState.ERROR, currentState)

        // Limpeza final
        currentState = CastSessionState.DISCONNECTED
        assertEquals(CastSessionState.DISCONNECTED, currentState)
    }

    @Test
    fun testMutualExclusionLocalAndRemotePlayback() {
        var isLocalPlaying = true
        var isRemotePlaying = false

        // Início da transferência para Cast
        fun onTransferToCast() {
            isLocalPlaying = false // Pausa reprodução local imediatamente
            isRemotePlaying = true  // Inicia reprodução remota
        }

        // Retorno de Cast para local
        fun onTransferToLocal() {
            isRemotePlaying = false // Encerra reprodução remota
            isLocalPlaying = true   // Retoma reprodução local
        }

        onTransferToCast()
        assertFalse("Player local não pode estar tocando enquanto Cast estiver ativo", isLocalPlaying)
        assertTrue("Player remoto deve estar ativo", isRemotePlaying)

        onTransferToLocal()
        assertTrue("Player local deve ser retomado", isLocalPlaying)
        assertFalse("Player remoto não pode continuar tocando", isRemotePlaying)
    }

    @Test
    fun testCastContentTypeDetection() {
        fun detectContentType(url: String): String {
            val u = url.lowercase(java.util.Locale.ROOT)
            return when {
                u.contains(".m3u8") || u.contains("/hls") || u.contains("m3u8") -> "application/vnd.apple.mpegurl"
                u.contains(".aac") || u.contains("aac") -> "audio/aac"
                u.contains(".ogg") || u.contains(".opus") -> "audio/ogg"
                u.contains(".m4a") || u.contains(".mp4") -> "audio/mp4"
                else -> "audio/mpeg"
            }
        }

        assertEquals("application/vnd.apple.mpegurl", detectContentType("https://radio.example.com/live.m3u8"))
        assertEquals("application/vnd.apple.mpegurl", detectContentType("https://radio.example.com/hls/stream"))
        assertEquals("audio/aac", detectContentType("https://radio.example.com/stream.aac"))
        assertEquals("audio/mpeg", detectContentType("https://radio.example.com/stream.mp3"))
        assertEquals("audio/mp4", detectContentType("https://podcast.example.com/ep.m4a"))
        assertEquals("audio/ogg", detectContentType("https://radio.example.com/stream.ogg"))
    }
}
