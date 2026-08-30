package com.example.player

/**
 * Representa exclusivamente o tipo de mídia ativo no momento,
 * isolando o ciclo de vida, fila de reprodução e comandos de transporte (Próximo / Anterior).
 */
enum class ActiveMediaType {
    LOCAL_AUDIO,      // MP3 / M4A / FLAC local
    LIVE_RADIO,       // Rádios Online (Icecast/Shoutcast/HLS)
    PODCAST_EPISODE,  // Episódios de Podcast
    LOCAL_VIDEO,      // Vídeos MP4/MKV locais
    YOUTUBE_STREAM    // Vídeos / Streams do YouTube
}
