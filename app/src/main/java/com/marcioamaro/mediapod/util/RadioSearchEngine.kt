package com.marcioamaro.mediapod.util

import com.marcioamaro.mediapod.data.model.RadioStation
import java.text.Normalizer
import java.util.Locale

object RadioSearchEngine {
    private val diacritics = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val whitespace = "\\s+".toRegex()
    private val decimalSeparator = "(?<=\\d)[,.](?=\\d)".toRegex()

    /**
     * Remove acentos e caracteres diacríticos, espaços extras e converte para minúsculas.
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return nfd.replace(diacritics, "")
            .replace(decimalSeparator, ".")
            .lowercase(Locale.ROOT)
            .trim()
    }

    private val BRAZILIAN_UF_ALIASES = mapOf(
        "AC" to listOf("acre", "ac"),
        "AL" to listOf("alagoas", "maceio", "al"),
        "AM" to listOf("amazonas", "manaus", "am"),
        "AP" to listOf("amapa", "macapa", "ap"),
        "BA" to listOf("bahia", "salvador", "catu", "ba", "bahia blanca"),
        "CE" to listOf("ceara", "fortaleza", "ce"),
        "DF" to listOf("distrito federal", "brasilia", "df"),
        "ES" to listOf("espirito santo", "vitoria", "es"),
        "GO" to listOf("goias", "goiania", "go"),
        "MA" to listOf("maranhao", "sao luis", "ma"),
        "MG" to listOf("minas gerais", "belo horizonte", "patos de minas", "mg"),
        "MS" to listOf("mato grosso do sul", "campo grande", "ms"),
        "MT" to listOf("mato grosso", "cuiaba", "rondonopolis", "mt"),
        "PA" to listOf("para", "belem", "pa"),
        "PB" to listOf("paraiba", "joao pessoa", "pb"),
        "PE" to listOf("pernambuco", "recife", "pe"),
        "PI" to listOf("piaui", "teresina", "pi"),
        "PR" to listOf("parana", "curitiba", "londrina", "pr"),
        "RJ" to listOf("rio de janeiro", "resende", "niteroi", "rj"),
        "RN" to listOf("rio grande do norte", "natal", "rn"),
        "RO" to listOf("rondonia", "porto velho", "ro"),
        "RR" to listOf("roraima", "boa vista", "rr"),
        "RS" to listOf("rio grande do sul", "porto alegre", "rs"),
        "SC" to listOf("santa catarina", "florianopolis", "joinville", "sc"),
        "SE" to listOf("sergipe", "aracaju", "se"),
        "SP" to listOf("sao paulo", "campinas", "santos", "sorocaba", "atibaia", "batatais", "aparecida", "sp"),
        "TO" to listOf("tocantins", "palmas", "to")
    )

    /**
     * Motor de busca multi-termo cumulativo (AND).
     * Todos os termos separados por espaço devem ser encontrados na estação.
     */
    fun matchesMultiToken(station: RadioStation, query: String): Boolean {
        val cleanQuery = normalize(query)
        if (cleanQuery.isBlank()) return true

        val tokens = cleanQuery.split(whitespace).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true

        val corpus = searchableText(station)
        return tokens.all { token -> corpus.contains(token) }
    }

    fun searchableText(station: RadioStation): String {
        // Shared by the indexed and compatibility search paths.
        val ufAliases = BRAZILIAN_UF_ALIASES[station.state.uppercase(Locale.ROOT)] ?: emptyList()
        val extraUfCorpus = ufAliases.joinToString(" ")

        // Construir corpus textual normalizado da emissora
        return normalize(
            "${station.name} ${station.country} ${station.countryCode} ${station.state} ${station.city} ${station.tags} ${station.primaryGenre} $extraUfCorpus"
        )

    }

    /**
     * Valida correspondência de UF para emissoras do Brasil.
     * Se selectedUf for nulo, em branco ou "ALL", retorna true para todas as rádios brasileiras,
     * incluindo obrigatoriamente as sem UF cadastrada.
     */
    fun matchesUf(station: RadioStation, selectedUf: String?): Boolean {
        if (selectedUf.isNullOrBlank() || selectedUf.equals("ALL", ignoreCase = true)) {
            return true
        }
        val targetUf = selectedUf.trim().uppercase(Locale.ROOT)
        val stationState = station.state.trim().uppercase(Locale.ROOT)

        if (stationState == targetUf) return true

        val aliases = BRAZILIAN_UF_ALIASES[targetUf] ?: listOf(targetUf.lowercase(Locale.ROOT))
        val stateNorm = normalize(station.state)
        val cityNorm = normalize(station.city)
        val tagsNorm = normalize(station.tags)

        return aliases.any { alias ->
            stateNorm.contains(alias) || cityNorm.contains(alias) || tagsNorm.contains(alias)
        }
    }

    /**
     * Mapeamento completo dos 39 gêneros acordados na entrevista /grill-me.
     */
    val TAXONOMY_KEYWORDS: Map<String, List<String>> = mapOf(
        "alternativo" to listOf("alternative", "indie", "indie pop", "indie rock", "underground", "experimental", "avant-garde", "eclectic", "eclectic prog"),
        "ambient_chill" to listOf("ambient", "lounge", "chill", "chillout", "downtempo", "lo-fi", "relax", "relaxing", "calm", "meditation", "sleep", "soft music", "suave", "smooth"),
        "anime_games" to listOf("anime", "anime endings", "anime openings", "game music", "vgm ost", "video game music", "videogame music"),
        "axe" to listOf("axe", "carnaval", "trio eletrico", "micareta", "bahia hits"),
        "blues" to listOf("blues", "blues rock"),
        "bossa_nova" to listOf("bossa nova", "bossa jazz", "choro", "chorinho"),
        "brega_arrocha" to listOf("brega", "arrocha", "so brega", "brega pop"),
        "classica_instrumental" to listOf("classical", "classical music", "light classics", "new classics", "musica classica", "classica", "instrumental", "instrumental music", "orchestra", "orchestral", "orquestrada", "choral", "erudita", "sinfonica", "opera"),
        "comedia" to listOf("comedy music", "musica humoristica", "parodia", "novelty music", "humor"),
        "country" to listOf("country", "country music", "classic country", "country pop", "today's best country", "bluegrass"),
        "disco" to listOf("disco", "disco dance", "disco funk", "70s disco", "80s disco", "italo disco"),
        "easy_listening" to listOf("easy listening", "light", "lite", "soft", "adult", "adult contemporary", "adult hits", "soft adult contemporary", "alvorada", "alpha", "antena 1"),
        "eletronica" to listOf("electronic", "eletronica", "electronic dance music", "edm", "electronica", "electro", "club dance", "dance music", "dance hits", "dancefloor", "techno", "trance", "house", "deep house", "minimal", "breaks", "drum and bass", "jungle", "eurodance", "italo dance", "freestyle", "dance"),
        "esportes" to listOf("esporte", "esportes", "sports", "sport", "futebol", "soccer", "grenal", "torcida", "campeonato", "arena", "gol", "craque", "jogo", "transmissao esportiva"),
        "folk" to listOf("folk", "folk brasileiro", "chanson", "shanson", "turku"),
        "forro" to listOf("forro", "xote", "vaquejada", "pisadinha", "forro e sertanejo", "baiao", "sanfona"),
        "funk" to listOf("funk", "funky", "disco funk", "p-funk", "miami bass", "black music", "rare groove", "funk melody", "baile"),
        "gospel" to listOf("gospel", "gospel music", "gospel contemporary", "gospel country", "gospel dance", "gospel eletronica", "gospel pop", "reggae gospel", "urban gospel", "christian music", "christian contemporary", "contemporary christian", "christian rock", "christian praise & worship", "traditional christian", "southern gospel", "worship", "praise", "louvor", "louvores", "hymns", "adoracao", "evangelica", "crista", "fe"),
        "hip_hop" to listOf("hip hop", "hip-hop", "hip hop soul", "classic hip hop", "rap", "cloud rap", "memphis rap", "phonk", "trap", "r&b", "soul hip hop r&b"),
        "internacional" to listOf("international", "internacional", "internacionais", "international music", "musicas nacionais e internacionais", "world hits"),
        "jazz" to listOf("jazz", "classic jazz", "contemporary jazz", "cool jazz", "free jazz", "jazz lounge", "mainstream jazz", "smooth jazz", "straight-ahead", "bebop", "hard bop", "post-bop", "big band", "modern big band", "bossa jazz"),
        "latin" to listOf("latin", "latin music", "latin pop", "latino", "musica latino-americana", "caribbean music", "reggaeton", "salsa", "bachata", "cumbia"),
        "metal" to listOf("metal", "heavy metal", "glam metal", "hair metal", "thrash", "death metal"),
        "mpb" to listOf("mpb", "musica popular brasileira", "musica brasileira", "brazilian music", "musica popular", "musica regional", "musica gaucha", "folk brasileiro", "local music", "musica autoral", "musica independente", "musica nacional", "novabrasil", "so mpb"),
        "musica_religiosa" to listOf("musica religiosa", "religiosa", "religious", "spiritual", "mantra", "catolica", "aparecida", "cancao nova", "oracao"),
        "new_wave_synth" to listOf("new wave", "synthpop", "uk synthpop", "new retro wave", "vaporwave", "sigthwave"),
        "noticias_talk" to listOf("news", "noticias", "noticia", "jornalismo", "talk", "informacao", "debates", "entrevista", "politica", "prestacao", "comunidade", "cbn", "bandnews", "jovem pan news", "senado", "camara", "tupi"),
        "pisadinha" to listOf("pisadinha", "piseiro"),
        "pop" to listOf("pop", "pop music", "brazilian pop", "pop brasileiro", "pop dance", "pop latino", "indie pop", "teen pop", "electropop", "russian pop", "j-pop", "synthpop", "uk synthpop", "top 40", "contemporary hits", "hot adult contemporary", "hits", "jovem", "sucessos"),
        "reggae" to listOf("reggae", "reggae gospel", "dub", "roots"),
        "regional" to listOf("regional", "musica regional", "nativa", "nativista", "tradicional", "tradicionalista", "campeira", "gauchesca", "gaucha", "vaneira", "vanerao"),
        "retro" to listOf("oldies", "old school", "oldschool", "nostalgia", "nostalgic", "flashback", "flash back", "recordacoes", "retro", "sixties", "60s", "70s", "80s", "90s", "2000s", "2010s", "greatest hits", "classic hits", "golden music", "new retro wave", "anos 80", "anos 70", "anos 90", "maquina do tempo"),
        "rock" to listOf("rock", "alternative rock", "classic rock", "hard rock", "indie rock", "modern rock", "progressive rock", "psychedelic rock", "soft rock", "blues rock", "brazilian rock", "rock nacional", "rock pop", "pop rock", "rock & roll", "aor", "mellow album rock", "yacht rock", "kiss fm", "89 fm"),
        "romantica" to listOf("romantic", "romantic music", "romanticas", "love", "love hits", "love songs"),
        "samba_pagode" to listOf("samba", "pagode", "samba & pagode", "samba pagode", "choro", "chorinho", "hits sertanejo pagode", "liga samba"),
        "sertanejo" to listOf("sertanejo", "sertaneja", "sertanejo raiz", "moda de viola", "viola", "caipira", "modao", "forro e sertanejo", "universitario"),
        "soul_rnb" to listOf("soul", "r&b", "motown", "black music", "rare groove"),
        "variedades_musicais" to listOf("music", "musica", "musicas", "contemporary music", "popular music", "eclectic programming", "misc", "miscellaneous", "various", "variety", "variedades", "full service", "ecletica", "comunitaria", "comercial"),
        "world" to listOf("world", "world music", "middle eastern music", "persian", "armenian", "desi", "bollywood", "hindi", "ethno")
    )

    /**
     * Verifica se a rádio corresponde ao gênero fornecido.
     */
    fun matchesGenre(station: RadioStation, genreTag: String): Boolean {
        if (genreTag.isBlank() || genreTag.equals("ALL", ignoreCase = true)) {
            return true
        }
        val cleanTag = normalize(genreTag)
        val kws = TAXONOMY_KEYWORDS[cleanTag] ?: listOf(cleanTag)

        val targetCorpus = normalize("${station.name} ${station.tags} ${station.primaryGenre}")

        if (kws.any { kw -> targetCorpus.contains(kw) }) {
            return true
        }

        // Variedades Musicais (eclética/comunitária) acolhe emissoras com programação geral sem gênero específico
        if (cleanTag == "variedades_musicais") {
            val matchesOther = TAXONOMY_KEYWORDS.entries
                .filter { it.key != "variedades_musicais" }
                .any { (_, otherKws) -> otherKws.any { kw -> targetCorpus.contains(kw) } }
            if (!matchesOther) {
                return true
            }
        }

        return false
    }
}
