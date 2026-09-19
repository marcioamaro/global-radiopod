package com.example.player.context

/**
 * Identifica a origem contextual da fila de reprodução ativa no aplicativo,
 * garantindo que comandos de avanço/retrocesso (skip next/previous) respeitem
 * a lista de onde o usuário partiu (Busca, Favoritos, Top Ranking, Categoria, etc.).
 */
enum class QueueSource {
    GLOBAL,
    FAVORITES,
    RECENTS,
    SEARCH,
    CATEGORY,
    RANKING,
    LOCAL_TRACKS,
    PODCAST_EPISODES
}
