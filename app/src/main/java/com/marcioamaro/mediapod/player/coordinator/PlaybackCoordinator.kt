package com.marcioamaro.mediapod.player.coordinator

import com.marcioamaro.mediapod.player.context.NavigationContext
import kotlinx.coroutines.flow.StateFlow

/**
 * Orquestrador central e fonte única de verdade para toda reprodução
 * em UI Compose, Foreground Service, Android Auto e Google Cast.
 */
interface PlaybackCoordinator {

    val state: StateFlow<UnifiedPlaybackState>
    val navigationContext: StateFlow<NavigationContext>

    /**
     * Define o contexto navegacional ativo (Busca, Favoritos, Top Ranking, etc.),
     * garantindo que avanços e retrocessos respeitem a lista de origem.
     */
    fun setNavigationContext(context: NavigationContext)

    /**
     * Inicia ou retoma a reprodução do item atual.
     */
    fun play()

    /**
     * Pausa a reprodução.
     */
    fun pause()

    /**
     * Interrompe o playback e limpa buffers.
     */
    fun stop()

    /**
     * Avança ou retrocede para uma posição em milissegundos (quando suportado pela mídia).
     */
    fun seekTo(positionMs: Long)

    /**
     * Carrega e inicia um novo item de mídia diretamente.
     */
    fun play(item: PlaybackQueueItem)

    /**
     * Retorna o item atualmente em reprodução ou selecionado no contexto.
     */
    fun getCurrentItem(): PlaybackQueueItem?

    /**
     * Carrega e inicia um novo item de mídia, opcionalmente com uma lista de fila contextually associada.
     */
    fun playItem(item: PlaybackQueueItem, queue: List<PlaybackQueueItem> = listOf(item))

    /**
     * Avança para a próxima faixa ou rádio da fila.
     */
    fun skipToNext()

    /**
     * Retorna para a faixa anterior ou reinicia a atual se decorridos mais de 3s.
     */
    fun skipToPrevious()

    /**
     * Salta diretamente para um índice específico da fila.
     */
    fun skipToQueueIndex(index: Int)

    /**
     * Altera a rota de reprodução (Local, Bluetooth, Cast).
     */
    fun setAudioRoute(route: AudioPlaybackRoute)

    /**
     * Ajusta o modo de repetição.
     */
    fun setRepeatMode(mode: QueueRepeatMode)

    /**
     * Ajusta o volume relativo de reprodução (0.0f a 1.0f).
     */
    fun setVolume(volume: Float)

    /**
     * Volume ativo sincronizado conforme a rota (Local vs Cast).
     */
    val activeVolume: StateFlow<Float>

    /**
     * Define o volume da rota ativa (Local vs Cast).
     */
    fun setActiveVolume(volume: Float)

    /**
     * Tenta recuperação em caso de erro recuperável.
     */
    fun retry()

    /**
     * Adiciona um item ao final da fila atual.
     */
    fun addToQueue(item: PlaybackQueueItem)

    /**
     * Adiciona um item imediatamente após o item atual ("Tocar depois").
     */
    fun playNextInQueue(item: PlaybackQueueItem)

    /**
     * Remove um item da fila no índice especificado.
     */
    fun removeFromQueue(index: Int)

    /**
     * Reordena um item da fila de uma posição para outra.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int)

    /**
     * Limpa a fila de reprodução atual.
     */
    fun clearQueue()

    /**
     * Ativa ou desativa o modo aleatório (shuffle).
     */
    fun setShuffle(enabled: Boolean)

    /**
     * Obtém o histórico de itens reproduzidos.
     */
    fun getHistory(): List<PlaybackQueueItem>
}

