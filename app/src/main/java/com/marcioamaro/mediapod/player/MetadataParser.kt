package com.marcioamaro.mediapod.player

import android.net.Uri

/**
 * Parser centralizado e único ponto de decisão para metadados Now Playing.
 *
 * TODA lógica de parsing ICY/ID3, sanitização de encoding, detecção de
 * promo/vinheta, e fallback de texto reside EXCLUSIVAMENTE aqui.
 * Nenhuma superfície (UI, notificação, Android Auto, Cast) deve implementar
 * lógica própria de fallback — todas consomem o NowPlayingMetadata produzido
 * por este parser.
 *
 * REGRA DE ESCOPO: este parser decide apenas campos de TEXTO (title, artist, album).
 * A lógica de artwork/capa/logotipo é independente e não é afetada por nenhuma
 * decisão tomada aqui.
 */
object MetadataParser {

    // ========================================================================
    // Parsing de ICY StreamTitle para Rádios Ao Vivo
    // ========================================================================

    /**
     * Processa um StreamTitle ICY bruto e produz um NowPlayingMetadata.
     *
     * @param rawStreamTitle O texto ICY bruto recebido do stream (pode ser nulo/vazio/corrompido)
     * @param stationName O nome da rádio cadastrado no catálogo do app
     * @param stationUrl A URL do stream (para detectar auto-referência)
     * @param artworkUri URI do artwork (passado sem alteração, pipeline independente)
     * @param lastValidTitle Último título real de música detectado (para manter durante comerciais)
     * @return Par de (NowPlayingMetadata, títuloRealDetectado?) — o segundo valor é non-null
     *         apenas quando um título real de música foi identificado (não promo/fallback)
     */
    fun parseIcyForRadio(
        rawStreamTitle: String?,
        stationName: String,
        stationUrl: String = "",
        artworkUri: Uri? = null,
        lastValidTitle: String? = null
    ): Pair<NowPlayingMetadata, String?> {
        val sanitized = sanitizeStreamTitle(rawStreamTitle)

        // Caso 1: Sem informação de faixa (vazio, nulo, corrompido)
        if (sanitized.isBlank()) {
            return buildFallbackMetadata(stationName, artworkUri, lastValidTitle)
        }

        // Caso 2: StreamTitle é igual ao nome/URL da rádio (stream mal configurado)
        if (isStationSelfReference(sanitized, stationName, stationUrl)) {
            return buildFallbackMetadata(stationName, artworkUri, lastValidTitle)
        }

        // Caso 3: Comercial, vinheta ou promo (não é música real)
        if (isCommercialOrPromo(sanitized, stationName)) {
            val displayArtist = if (!lastValidTitle.isNullOrBlank()) {
                "COMERCIAL • ANTERIOR: $lastValidTitle"
            } else {
                "[sem informações]"
            }
            return NowPlayingMetadata(
                title = stationName,
                artist = displayArtist,
                album = "Ao Vivo",
                artworkUri = artworkUri,
                isLiveStream = true,
                hasTrackInfo = !lastValidTitle.isNullOrBlank()
            ) to null // Não atualiza lastValidTitle (mantém o anterior)
        }

        // Caso 4: Título real de música detectado
        val (artist, songTitle) = splitArtistTitle(sanitized)
        return NowPlayingMetadata(
            title = stationName,
            artist = sanitized, // Texto completo para exibição (ex: "ÍCARO E GILMAR IMPERFEITOS (AO VIVO)")
            album = "Ao Vivo",
            artworkUri = artworkUri,
            isLiveStream = true,
            hasTrackInfo = true
        ) to sanitized // Atualiza lastValidTitle
    }

    /**
     * Produz metadados de fallback quando o stream não fornece ICY válido.
     */
    private fun buildFallbackMetadata(
        stationName: String,
        artworkUri: Uri?,
        lastValidTitle: String?
    ): Pair<NowPlayingMetadata, String?> {
        val artist = if (!lastValidTitle.isNullOrBlank()) lastValidTitle else "[sem informações]"
        val hasTrack = !lastValidTitle.isNullOrBlank()
        return NowPlayingMetadata(
            title = stationName,
            artist = artist,
            album = "Ao Vivo",
            artworkUri = artworkUri,
            isLiveStream = true,
            hasTrackInfo = hasTrack
        ) to null // Não altera lastValidTitle
    }

    /**
     * Produz metadados iniciais para uma rádio ao conectar (antes de qualquer ICY).
     */
    fun buildInitialRadioMetadata(
        stationName: String,
        artworkUri: Uri?
    ): NowPlayingMetadata {
        return NowPlayingMetadata(
            title = stationName,
            artist = "[sem informações]",
            album = "Ao Vivo",
            artworkUri = artworkUri,
            isLiveStream = true,
            hasTrackInfo = false
        )
    }

    // ========================================================================
    // Parsing para Podcasts (VOD)
    // ========================================================================

    /**
     * Produz metadados para um episódio de podcast.
     * Fallback: se o episódio não tiver metadados, usa título + nome do show.
     */
    fun buildPodcastMetadata(
        episodeTitle: String,
        showTitle: String,
        publishDate: String = "",
        artworkUri: Uri? = null
    ): NowPlayingMetadata {
        val title = episodeTitle.ifBlank { showTitle.ifBlank { "Episódio" } }
        val artist = showTitle.ifBlank { "Podcast" }
        val album = publishDate.ifBlank { "Podcast" }
        return NowPlayingMetadata(
            title = title,
            artist = artist,
            album = album,
            artworkUri = artworkUri,
            isLiveStream = false,
            hasTrackInfo = true
        )
    }

    // ========================================================================
    // Parsing para Áudio Local (MP3)
    // ========================================================================

    /**
     * Produz metadados para uma faixa de áudio local.
     */
    fun buildLocalAudioMetadata(
        trackTitle: String,
        trackArtist: String,
        trackAlbum: String = "",
        artworkUri: Uri? = null
    ): NowPlayingMetadata {
        return NowPlayingMetadata(
            title = trackTitle.ifBlank { "Faixa Desconhecida" },
            artist = trackArtist.ifBlank { "Artista Desconhecido" },
            album = trackAlbum.ifBlank { null },
            artworkUri = artworkUri,
            isLiveStream = false,
            hasTrackInfo = true
        )
    }

    // ========================================================================
    // Sanitização e Utilidades Privadas
    // ========================================================================

    /**
     * Sanitiza um StreamTitle ICY bruto:
     * - Remove prefixos ICY residuais (StreamTitle=, aspas, ponto-e-vírgula)
     * - Remove /RDS tags
     * - Corrige encoding UTF-8 malformado (caracteres de controle, HTML entities)
     * - Trim e normaliza espaços
     */
    fun sanitizeStreamTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        var clean = raw
            // Remove prefixos ICY residuais
            .replace("StreamTitle=", "", ignoreCase = true)
            .replace("'", "")
            .replace(";", "")
            // Remove tags RDS residuais
            .replace("/RDS", "", ignoreCase = true)
            .replace("/ RDS", "", ignoreCase = true)

        // Decodifica HTML entities comuns
        clean = clean
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")

        // Remove caracteres de controle (0x00-0x1F exceto \t, \n, \r)
        clean = clean.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")

        // Normaliza espaços múltiplos
        clean = clean.replace(Regex("\\s{2,}"), " ")

        return clean.trim().uppercase()
    }

    /**
     * Detecta se o StreamTitle é apenas uma auto-referência ao nome/URL da rádio.
     */
    private fun isStationSelfReference(
        sanitizedTitle: String,
        stationName: String,
        stationUrl: String
    ): Boolean {
        val upper = sanitizedTitle.uppercase()
        val nameUpper = stationName.uppercase()

        // Exatamente igual ao nome da rádio
        if (upper == nameUpper) return true

        // Contém a URL do stream (stream mal configurado)
        if (stationUrl.isNotBlank()) {
            val urlClean = stationUrl
                .removePrefix("http://")
                .removePrefix("https://")
                .removeSuffix("/")
                .uppercase()
            if (upper.contains(urlClean) || urlClean.contains(upper)) return true
        }

        return false
    }

    /**
     * Detecta se o texto é um comercial, vinheta ou promo da rádio.
     */
    private fun isCommercialOrPromo(text: String, stationName: String): Boolean {
        val upper = text.uppercase()
        val stUpper = stationName.uppercase()

        if (upper == stUpper) return true

        val commercialKeywords = listOf(
            "COMERCIAL", "INTERVALO", "PROPAGANDA", "VINHETA", "SPOT", "BLOCO",
            "HORA CERTA", "A MELHOR", "AO VIVO", "SINTONIA", "RADIO", "RÁDIO",
            "FM", "WEB", "TUDORADIO"
        )

        // Sem separador artista-música e contém keywords comerciais → promo
        if (!upper.contains(" - ") && !upper.contains(" – ")) {
            if (commercialKeywords.any { upper.contains(it) } || upper.length < 4) {
                return true
            }
        }

        return false
    }

    /**
     * Tenta separar artista e título a partir de separadores comuns.
     * Retorna (artist, title) ou (texto completo, "") se não houver separador.
     */
    private fun splitArtistTitle(text: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " : ", " / ")
        for (sep in separators) {
            val idx = text.indexOf(sep, ignoreCase = true)
            if (idx > 0 && idx < text.length - sep.length) {
                val artist = text.substring(0, idx).trim()
                val title = text.substring(idx + sep.length).trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return artist to title
                }
            }
        }
        return text to ""
    }
}
