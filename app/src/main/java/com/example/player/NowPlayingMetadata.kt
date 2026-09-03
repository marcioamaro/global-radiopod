package com.example.player

import android.net.Uri

/**
 * Modelo imutável e unificado de metadados "Now Playing".
 *
 * Esta data class é a Single Source of Truth (SSOT) para título, artista e
 * estado de faixa exibidos em TODAS as superfícies do app:
 * - Player principal (iPod LCD / tela cheia)
 * - Modo Carro (UI dedicada do próprio app)
 * - Dock / Widget
 * - Notificação de controle de mídia
 * - Android Auto (Now Playing)
 * - Google Cast (Chromecast / Google Home)
 *
 * A igualdade estrutural (equals/hashCode) é garantida pela data class,
 * permitindo uso direto com distinctUntilChanged() sem comparação de referência.
 *
 * REGRA: O campo artworkUri é transportado para conveniência, mas sua lógica
 * de resolução é independente e desacoplada da lógica de fallback de texto.
 */
data class NowPlayingMetadata(
    /** Título principal exibido (nome da música, título do episódio, ou nome da rádio como fallback) */
    val title: String,
    /** Artista / subtítulo (artista da faixa, nome do podcast, ou "Ao Vivo" como fallback) */
    val artist: String,
    /** Álbum ou contexto (opcional — ex: "Ao Vivo", data de publicação do podcast) */
    val album: String? = null,
    /** URI do artwork (capa da faixa, logotipo da rádio, etc.). Pipeline independente do texto. */
    val artworkUri: Uri? = null,
    /** true se o conteúdo é um stream ao vivo (rádio), false para VOD (podcast, MP3) */
    val isLiveStream: Boolean,
    /** false quando não há dados reais de faixa/ICY — UI pode ocultar subtítulo de artista */
    val hasTrackInfo: Boolean
)
