package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastVolumeControllerTest {

    private var simulatedTime: Long = 1000L
    private var sentCommands = mutableListOf<Float>()
    private lateinit var controller: CastVolumeController

    @Before
    fun setUp() {
        simulatedTime = 1000L
        sentCommands.clear()
        controller = CastVolumeController(
            onSendVolumeCommand = { vol -> sentCommands.add(vol) },
            timeProvider = { simulatedTime }
        )
    }

    @Test
    fun `echo report within debounce window should be ignored`() {
        controller.onUserVolumeChange(0.7f)
        assertEquals(0.7f, controller.castVolume.value, 0.001f)

        // Eco reportado quase imediatamente (apenas 50ms depois, bem dentro da janela de 250ms)
        simulatedTime += 50L
        controller.onCastStatusVolumeReported(0.68)

        // UI NÃO oscilou, permaneceu com o valor otimista enviado pelo usuário
        assertEquals(0.7f, controller.castVolume.value, 0.001f)
    }

    @Test
    fun `echo report within tolerance should be ignored`() {
        controller.onUserVolumeChange(0.7f)

        // Passa a janela de debounce (300ms > 250ms)
        simulatedTime += 300L

        // Eco reportado dentro da tolerância de 2% (0.71 vs 0.70 -> diferença de 0.01 < 0.02)
        controller.onCastStatusVolumeReported(0.71)

        // UI permanece estável em 0.70
        assertEquals(0.7f, controller.castVolume.value, 0.001f)
    }

    @Test
    fun `external change beyond debounce and tolerance should update UI`() {
        controller.onUserVolumeChange(0.7f)

        // Passa a janela de debounce
        simulatedTime += 500L

        // Mudança externa real (ex: controle remoto da TV para 45%)
        controller.onCastStatusVolumeReported(0.45)

        // UI reflete a mudança externa
        assertEquals(0.45f, controller.castVolume.value, 0.001f)
    }

    @Test
    fun `rapid successive user changes should not trigger feedback loop`() {
        repeat(10) { index ->
            simulatedTime += 20L
            controller.onUserVolumeChange(0.5f + index * 0.03f)
        }

        // Nenhuma atualização de UI vinda de callback deve ter interferido
        assertEquals(0.77f, controller.castVolume.value, 0.001f) // 0.5 + 9 * 0.03 = 0.77
        assertEquals(10, sentCommands.size)
    }
}
