package com.example.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Fila FIFO de metadados com compensação de delay temporal.
 *
 * Streams de rádio frequentemente enviam metadados ANTES do áudio correspondente
 * chegar ao buffer. Sem compensação, o título exibido muda antecipadamente,
 * criando uma experiência desconcertante para o usuário.
 *
 * Esta fila:
 * 1. Recebe um item de metadados com timestamp de chegada.
 * 2. Aguarda [delayMs] milissegundos antes de publicar via [onReady].
 * 3. Mantém exatamente UM item pendente (o mais recente), descartando versões anteriores
 *    que ainda não foram exibidas se um novo item chegar.
 *
 * ## Regras de Ouro
 * - O áudio nunca é afetado por esta fila.
 * - O callback [onReady] é sempre chamado na Main thread.
 * - Se [delayMs] for 0, o item é publicado imediatamente (útil para testes).
 *
 * @param delayMs Delay em ms a aplicar antes de publicar (recomendado: 5_000 a 10_000ms)
 * @param scope CoroutineScope para o job interno de tick (deve ter ciclo de vida da sessão)
 * @param onReady Callback chamado quando um metadado está pronto para exibição
 */
class MetadataDelayQueue(
    private val delayMs: Long = 7_000L,
    private val scope: CoroutineScope,
    private val onReady: (text: String) -> Unit
) {
    data class QueuedMetadata(
        val text: String,
        val arrivedAtMs: Long = System.currentTimeMillis()
    )

    // Queue de tamanho 1: apenas o item mais recente importa
    private val queue = ConcurrentLinkedQueue<QueuedMetadata>()
    private var tickJob: Job? = null

    /**
     * Enfileira um novo texto de metadado.
     * Se já houver um item pendente, ele é descartado em favor do mais recente.
     *
     * @param text Texto de metadado limpo (já sanitizado)
     */
    fun enqueue(text: String) {
        if (text.isBlank()) return
        if (delayMs <= 0L) {
            // Sem delay configurado: publica imediatamente
            scope.launch(Dispatchers.Main) { onReady(text) }
            return
        }
        queue.clear() // Substitui qualquer item pendente
        queue.offer(QueuedMetadata(text = text))
        ensureTickerRunning()
    }

    /**
     * Cancela qualquer item pendente na fila.
     * Útil ao trocar de estação ou parar a reprodução.
     */
    fun clear() {
        queue.clear()
        tickJob?.cancel()
        tickJob = null
    }

    /**
     * Para o job de tick sem limpar a fila.
     * O ticker será reiniciado automaticamente ao próximo [enqueue].
     */
    fun pause() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun ensureTickerRunning() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val item = queue.peek()
                if (item == null) {
                    // Fila vazia: para o ticker
                    break
                }
                val ageMs = System.currentTimeMillis() - item.arrivedAtMs
                val remainingMs = delayMs - ageMs
                if (remainingMs <= 0L) {
                    // Item maduro: publica e remove da fila
                    queue.poll()
                    launch(Dispatchers.Main) { onReady(item.text) }
                } else {
                    // Ainda não é hora: espera o tempo restante
                    delay(remainingMs.coerceAtMost(500L)) // Tick máximo a cada 500ms
                }
            }
            tickJob = null
        }
    }
}
