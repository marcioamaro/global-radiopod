package com.marcioamaro.mediapod.player.coordinator

import com.marcioamaro.mediapod.player.ActiveMediaType

/**
 * Estados fundamentais de reprodução do motor de áudio.
 */
enum class PlaybackStatus {
    IDLE,
    LOADING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

/**
 * Rota física ou remota de saída de áudio.
 */
enum class AudioPlaybackRoute {
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET,
    GOOGLE_CAST
}

/**
 * Modo de repetição para listas e podcasts.
 */
enum class QueueRepeatMode {
    OFF,
    ONE,
    ALL
}

/**
 * Tipificação de erro para recuperação diagnóstica sem exceções silenciosas.
 */
enum class PlaybackErrorKind {
    NETWORK_DISCONNECTED,
    STREAM_TIMEOUT,
    HTTP_CLIENT_ERROR,     // 4xx
    HTTP_SERVER_ERROR,     // 5xx
    DECODER_UNSUPPORTED,
    CAST_TRANSFER_FAILED,
    UNKNOWN
}

data class ClassifiedPlaybackError(
    val kind: PlaybackErrorKind,
    val message: String,
    val isRecoverable: Boolean,
    val rootCause: Throwable? = null
)

/**
 * Item atômico de mídia para rádio ao vivo, podcast sob demanda ou arquivo local.
 */
data class PlaybackQueueItem(
    val id: String,
    val mediaUri: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val mediaType: ActiveMediaType = ActiveMediaType.LIVE_RADIO,
    val durationMs: Long? = null,
    val isLiveStream: Boolean = (mediaType == ActiveMediaType.LIVE_RADIO)
) {
    val mediaId: String get() = id
}

fun com.marcioamaro.mediapod.data.model.RadioStation.toPlaybackQueueItem(): PlaybackQueueItem {
    val sub = if (city.isNotBlank()) "$city • $country" else country
    return PlaybackQueueItem(
        id = id,
        mediaUri = streamUrl,
        title = name,
        subtitle = sub,
        artworkUri = favicon.ifBlank { null },
        mediaType = ActiveMediaType.LIVE_RADIO,
        durationMs = null,
        isLiveStream = true
    )
}

fun PlaybackQueueItem.toRadioStation(): com.marcioamaro.mediapod.data.model.RadioStation {
    return com.marcioamaro.mediapod.data.model.RadioStation(
        id = id,
        name = title,
        streamUrl = mediaUri,
        favicon = artworkUri ?: "",
        city = subtitle ?: "",
        country = "Global"
    )
}

/**
 * Estado unificado e imutável de reprodução (Single Source of Truth).
 */
data class UnifiedPlaybackState(
    val currentItem: PlaybackQueueItem? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val route: AudioPlaybackRoute = AudioPlaybackRoute.SPEAKER,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<PlaybackQueueItem> = emptyList(),
    val queueIndex: Int = -1,
    val repeatMode: QueueRepeatMode = QueueRepeatMode.OFF,
    val volume: Float = 1.0f,
    val error: ClassifiedPlaybackError? = null,
    val lastChangeCause: String = "INIT"
) {
    /**
     * Indica se há um próximo item disponível.
     * Para rádios ao vivo (isLiveStream=true) com fila de origem: SEMPRE true (loop circular).
     * Para podcasts/áudio local: segue o RepeatMode.
     */
    val hasNext: Boolean
        get() {
            // Rádio ao vivo com contexto de lista → loop circular sempre habilitado
            if (currentItem?.isLiveStream == true && queue.size > 1) return true
            return when (repeatMode) {
                QueueRepeatMode.ALL -> queue.isNotEmpty()
                QueueRepeatMode.ONE -> currentItem != null
                QueueRepeatMode.OFF -> queueIndex in 0 until (queue.size - 1)
            }
        }

    /**
     * Indica se há um item anterior disponível.
     * Para rádios ao vivo (isLiveStream=true) com fila de origem: SEMPRE true (loop circular).
     * Para podcasts/áudio local: segue o RepeatMode.
     */
    val hasPrevious: Boolean
        get() {
            // Rádio ao vivo com contexto de lista → loop circular sempre habilitado
            if (currentItem?.isLiveStream == true && queue.size > 1) return true
            return when (repeatMode) {
                QueueRepeatMode.ALL -> queue.isNotEmpty()
                QueueRepeatMode.ONE -> currentItem != null
                QueueRepeatMode.OFF -> queueIndex > 0
            }
        }

    val isPlaying: Boolean
        get() = status == PlaybackStatus.PLAYING
}
