package com.example.data.repository

import android.content.Context
import com.example.data.model.PodcastShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositório dedicado a rankings oficiais de Podcasts (Top Brasil e Top Mundial),
 * ordenando programas rigorosamente da melhor classificação (#1 mais popular) para a menor,
 * garantindo que a lista NUNCA seja exibida em ordem alfabética.
 */
class PodcastRankingRepository private constructor(
    private val context: Context,
    private val podcastRepository: PodcastRepository = PodcastRepository.getInstance(context)
) {

    // Ordem oficial de ranking dos podcasts mais populares e premiados do Brasil (#1 ao fim)
    private val brazilRankPriority = listOf(
        "br_podpah",
        "br_flow",
        "br_o_assunto",
        "br_nerdcast",
        "br_nao_inviabilize",
        "br_mano_a_mano",
        "br_inteligencia_ltda",
        "br_primocast",
        "br_os_socios",
        "br_cafe_brasil",
        "br_modus_operandi",
        "br_casa_abandonada",
        "br_sinapse",
        "br_ciencia_todo_dia",
        "br_xadrez_verbal",
        "br_foro_teresina",
        "br_durma_com_essa",
        "br_autoconsciente",
        "br_historia_meia_hora",
        "br_radio_novelo",
        "br_projeto_querino",
        "br_mamilos",
        "br_hipsters_tech",
        "br_naruhodo",
        "br_resumocast",
        "br_scicast",
        "br_loop_matinal",
        "br_petit_journal",
        "br_vidas_negras",
        "br_trip_fm",
        "br_angu_de_grilo",
        "br_praia_dos_ossos",
        "br_stock_pickers",
        "br_do_zero_ao_topo",
        "br_tecnocast",
        "br_e_noia_minha",
        "br_jornal_da_cbn",
        "br_cbn_noite",
        "br_devnaestrada",
        "br_pizza_de_dados",
        "br_gugacast",
        "br_cbn_madrugada",
        "br_fatos_desconhecidos",
        "br_papodesegunda",
        "br_mundo_agro",
        "br_desconectados",
        "br_trivela",
        "ref_trivela",
        "ref_ciencia_todo_dia"
    )

    // Ordem oficial de ranking dos podcasts mais ouvidos e influentes no mundo (#1 ao fim)
    private val worldRankPriority = listOf(
        "us_the_daily",
        "us_huberman_lab",
        "us_smartless",
        "us_radiolab",
        "us_serial",
        "us_stuff_you_should_know",
        "us_ted_talks_daily",
        "gb_global_news_podcast",
        "us_planet_money",
        "us_freakonomics_radio",
        "us_conan_obrien",
        "us_crime_junkie",
        "us_this_american_life",
        "us_hardcore_history",
        "us_hidden_brain",
        "us_joe_rogan",
        "us_how_i_built_this",
        "us_call_her_daddy",
        "us_lex_fridman",
        "us_99_percent_invisible"
    )

    suspend fun getTopPodcastsBrazil(limit: Int = 100): List<PodcastShow> = withContext(Dispatchers.IO) {
        val favIds = podcastRepository.favoritesFlow.value.map { it.id }.toSet()
        val curated = podcastRepository.getCuratedShows()
        val brazilShows = curated.filter { it.country.equals("BR", ignoreCase = true) }

        // Mapear posição no ranking oficial (menor índice = melhor classificação #1)
        val rankMap = brazilRankPriority.withIndex().associate { it.value to it.index }

        val rankedList = brazilShows
            .distinctBy { it.feedUrl.lowercase() }
            .sortedWith(
                compareBy<PodcastShow> { show ->
                    rankMap[show.id] ?: (1000 + (100000 - show.episodeCount.coerceAtLeast(0)))
                }.thenByDescending { it.episodeCount }
            )
            .map { it.copy(isFavorite = favIds.contains(it.id)) }

        rankedList.take(limit)
    }

    suspend fun getTopPodcastsWorld(limit: Int = 100): List<PodcastShow> = withContext(Dispatchers.IO) {
        val favIds = podcastRepository.favoritesFlow.value.map { it.id }.toSet()
        val curated = podcastRepository.getCuratedShows()
        val worldShows = curated.filter { !it.country.equals("BR", ignoreCase = true) }

        val rankMap = worldRankPriority.withIndex().associate { it.value to it.index }

        val rankedList = (if (worldShows.isNotEmpty()) worldShows else curated)
            .distinctBy { it.feedUrl.lowercase() }
            .sortedWith(
                compareBy<PodcastShow> { show ->
                    rankMap[show.id] ?: (1000 + (100000 - show.episodeCount.coerceAtLeast(0)))
                }.thenByDescending { it.episodeCount }
            )
            .map { it.copy(isFavorite = favIds.contains(it.id)) }

        rankedList.take(limit)
    }

    companion object {
        @Volatile
        private var INSTANCE: PodcastRankingRepository? = null

        fun getInstance(context: Context): PodcastRankingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PodcastRankingRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
