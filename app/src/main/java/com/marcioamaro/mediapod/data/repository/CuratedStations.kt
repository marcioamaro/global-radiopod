package com.marcioamaro.mediapod.data.repository

import com.marcioamaro.mediapod.data.model.RadioStation

data class GenreCategory(
    val id: String,
    val name: String,
    val tag: String,
    val iconEmoji: String,
    val description: String
)

data class CountryCategory(
    val code: String,
    val name: String,
    val flag: String,
    val region: String
)

object CuratedData {

    val GENRES = listOf(
        GenreCategory("alternativo", "Alternativo & Indie", "alternativo", "🎸", "Indie, Rock Alternativo e Experimental"),
        GenreCategory("ambient_chill", "Ambient & Chillout", "ambient_chill", "☕", "Lounge, Downtempo e Lo-Fi"),
        GenreCategory("anime_games", "Anime & Games", "anime_games", "🎮", "Trilhas de Anime, VGM e Games"),
        GenreCategory("axe", "Axé & Carnaval", "axe", "🎉", "Axé Music, Carnaval e Trio Elétrico"),
        GenreCategory("blues", "Blues", "blues", "🎷", "Classic Blues e Blues Rock"),
        GenreCategory("bossa_nova", "Bossa Nova & Choro", "bossa_nova", "🇧🇷", "Bossa Nova, Choro e Bossa Jazz"),
        GenreCategory("brega_arrocha", "Brega & Arrocha", "brega_arrocha", "💃", "Brega clássico e Arrocha romântico"),
        GenreCategory("classica_instrumental", "Clássica & Instrumental", "classica_instrumental", "🎻", "Orquestras, Sonatas e Erudita"),
        GenreCategory("comedia", "Comédia & Humor", "comedia", "🎭", "Programas humorísticos e paródias"),
        GenreCategory("country", "Country", "country", "🪕", "Classic Country e Bluegrass"),
        GenreCategory("disco", "Disco & 70s/80s", "disco", "🪩", "Disco Music e Italo Disco"),
        GenreCategory("easy_listening", "Easy Listening", "easy_listening", "🛋️", "Light, Soft e Adult Contemporary"),
        GenreCategory("eletronica", "Eletrônica & Dance", "eletronica", "🎧", "EDM, House, Techno e Trance"),
        GenreCategory("esportes", "Esportes & Futebol", "esportes", "⚽", "Transmissões, jogos e debates esportivos"),
        GenreCategory("flashback", "Flashback & Retrô", "retro", "🕺", "Clássicos dos anos 70, 80 e 90"),
        GenreCategory("folk", "Folk", "folk", "🍃", "Folk tradicional, acústico e canções"),
        GenreCategory("forro", "Forró & Xote", "forro", "🪗", "Forró tradicional, Xote e Vaquejada"),
        GenreCategory("funk", "Funk & Black Music", "funk", "🔊", "Funk, Miami Bass e Black Music"),
        GenreCategory("gospel", "Gospel & Louvor", "gospel", "🙏", "Música cristã, adoração e louvores"),
        GenreCategory("hip_hop", "Hip-Hop & Rap", "hip_hop", "🎤", "Rap, Trap e R&B contemporâneo"),
        GenreCategory("internacional", "Internacional", "internacional", "🌐", "Grandes sucessos internacionais"),
        GenreCategory("jazz", "Jazz", "jazz", "🎺", "Smooth Jazz, Bebop e Cool Jazz"),
        GenreCategory("latin", "Latina & Reggaeton", "latin", "💃", "Salsa, Bachata, Reggaeton e Cumbia"),
        GenreCategory("metal", "Metal & Heavy Metal", "metal", "⚡", "Heavy Metal, Thrash e Hard Riffs"),
        GenreCategory("mpb", "MPB", "mpb", "🇧🇷", "Música Popular Brasileira e Autoral"),
        GenreCategory("musica_religiosa", "Música Religiosa", "musica_religiosa", "🕊️", "Mensagens espirituais, orações e fé"),
        GenreCategory("new_wave_synth", "New Wave & Synthwave", "new_wave_synth", "🕹️", "Synthpop, New Wave e Vaporwave"),
        GenreCategory("noticias_talk", "Notícias & Talk", "noticias_talk", "📰", "Jornalismo, notícias ao vivo e debates"),
        GenreCategory("pisadinha", "Pisadinha", "pisadinha", "👢", "Pisadinha e Piseiro do Brasil"),
        GenreCategory("pop", "Pop & Hits", "pop", "✨", "Grandes paradas pop mundiais e nacionais"),
        GenreCategory("reggae", "Reggae & Dub", "reggae", "🌴", "Roots Reggae, Dub e Dancehall"),
        GenreCategory("regional", "Regional & Gaúcha", "regional", "🧉", "Música Gaúcha, Nativista e Campeira"),
        GenreCategory("rock", "Rock & Classic Rock", "rock", "🎸", "Classic Rock, Hard Rock e Indie"),
        GenreCategory("romantica", "Romântica", "romantica", "❤️", "Baladas românticas e Love Songs"),
        GenreCategory("samba_pagode", "Samba & Pagode", "samba_pagode", "🪘", "Samba de raiz, Pagode e Roda de samba"),
        GenreCategory("sertanejo", "Sertanejo & Raiz", "sertanejo", "🤠", "Modões sertanejos, viola e universitário"),
        GenreCategory("soul_rnb", "Soul & R&B", "soul_rnb", "🎷", "Soul clássico, Motown e R&B"),
        GenreCategory("variedades_musicais", "Variedades Musicais", "variedades_musicais", "📻", "Programação eclética e comunitária"),
        GenreCategory("world", "World Music", "world", "🌍", "Músicas e ritmos do mundo")
    )

    val COUNTRIES: List<CountryCategory> = run {
        val collator = java.text.Collator.getInstance(java.util.Locale("pt", "BR")).apply {
            strength = java.text.Collator.PRIMARY
        }
        val brasil = CountryCategory("BR", "Brasil", "[BR]", "América do Sul")
        val todos = CountryCategory("ALL", "Todos os Países", "[ALL]", "Global")
        val others = listOf(
            CountryCategory("DE", "Alemanha", "[DE]", "Europa"),
            CountryCategory("AR", "Argentina", "[AR]", "América do Sul"),
            CountryCategory("AU", "Austrália", "[AU]", "Oceania"),
            CountryCategory("CA", "Canadá", "[CA]", "América do Norte"),
            CountryCategory("ES", "Espanha", "[ES]", "Europa"),
            CountryCategory("US", "Estados Unidos", "[US]", "América do Norte"),
            CountryCategory("FR", "França", "[FR]", "Europa"),
            CountryCategory("NL", "Holanda", "[NL]", "Europa"),
            CountryCategory("IE", "Irlanda", "[IE]", "Europa"),
            CountryCategory("IT", "Itália", "[IT]", "Europa"),
            CountryCategory("JP", "Japão", "[JP]", "Ásia"),
            CountryCategory("MX", "México", "[MX]", "América do Norte"),
            CountryCategory("PT", "Portugal", "[PT]", "Europa"),
            CountryCategory("GB", "Reino Unido", "[GB]", "Europa"),
            CountryCategory("CH", "Suíça", "[CH]", "Europa")
        ).sortedWith { a, b -> collator.compare(a.name, b.name) }

        listOf(brasil, todos) + others
    }

    val BRAZILIAN_CITIES = listOf(
        "São Paulo",
        "Rio de Janeiro",
        "Araras",
        "Campinas",
        "Curitiba",
        "Belo Horizonte",
        "Porto Alegre",
        "Brasília",
        "Salvador",
        "Fortaleza",
        "Recife",
        "Goiânia",
        "Florianópolis",
        "Ribeirão Preto",
        "Santos",
        "Manaus",
        "Belém",
        "Sorocaba",
        "São José dos Campos",
        "Vitória",
        "Maceió",
        "Natal"
    )

    val GLOBAL_CITIES_BY_COUNTRY = mapOf(
        "BR" to BRAZILIAN_CITIES,
        "US" to listOf("Nova York", "Los Angeles", "Chicago", "Miami", "San Francisco", "Nashville", "Austin", "Seattle"),
        "PT" to listOf("Lisboa", "Porto", "Coimbra", "Braga", "Faro", "Funchal"),
        "GB" to listOf("Londres", "Manchester", "Liverpool", "Edimburgo", "Birmingham"),
        "DE" to listOf("Berlim", "Munique", "Hamburgo", "Colônia", "Frankfurt"),
        "FR" to listOf("Paris", "Lyon", "Marselha", "Nice", "Bordeaux"),
        "IT" to listOf("Roma", "Milão", "Nápoles", "Florença", "Turim"),
        "ES" to listOf("Madri", "Barcelona", "Valência", "Sevilha", "Málaga"),
        "AR" to listOf("Buenos Aires", "Córdoba", "Rosário", "Mendoza"),
        "JP" to listOf("Tóquio", "Osaka", "Quioto", "Yokohama")
    )

    val BRAZILIAN_STATES = listOf(
        "SP" to "São Paulo (SP)",
        "" to "Todos os Estados",
        "AC" to "Acre (AC)",
        "AL" to "Alagoas (AL)",
        "AP" to "Amapá (AP)",
        "AM" to "Amazonas (AM)",
        "BA" to "Bahia (BA)",
        "CE" to "Ceará (CE)",
        "DF" to "Distrito Federal (DF)",
        "ES" to "Espírito Santo (ES)",
        "GO" to "Goiás (GO)",
        "MA" to "Maranhão (MA)",
        "MT" to "Mato Grosso (MT)",
        "MS" to "Mato Grosso do Sul (MS)",
        "MG" to "Minas Gerais (MG)",
        "PA" to "Pará (PA)",
        "PB" to "Paraíba (PB)",
        "PR" to "Paraná (PR)",
        "PE" to "Pernambuco (PE)",
        "PI" to "Piauí (PI)",
        "RJ" to "Rio de Janeiro (RJ)",
        "RN" to "Rio Grande do Norte (RN)",
        "RS" to "Rio Grande do Sul (RS)",
        "RO" to "Rondônia (RO)",
        "RR" to "Roraima (RR)",
        "SC" to "Santa Catarina (SC)",
        "SE" to "Sergipe (SE)",
        "TO" to "Tocantins (TO)"
    )

    fun getStateFullName(uf: String?): String {
        return when (uf?.trim()?.uppercase()) {
            "AC" -> "Acre"
            "AL" -> "Alagoas"
            "AP" -> "Amapá"
            "AM" -> "Amazonas"
            "BA" -> "Bahia"
            "CE" -> "Ceará"
            "DF" -> "Distrito Federal"
            "ES" -> "Espírito Santo"
            "GO" -> "Goiás"
            "MA" -> "Maranhão"
            "MT" -> "Mato Grosso"
            "MS" -> "Mato Grosso do Sul"
            "MG" -> "Minas Gerais"
            "PA" -> "Pará"
            "PB" -> "Paraíba"
            "PR" -> "Paraná"
            "PE" -> "Pernambuco"
            "PI" -> "Piauí"
            "RJ" -> "Rio de Janeiro"
            "RN" -> "Rio Grande do Norte"
            "RS" -> "Rio Grande do Sul"
            "RO" -> "Rondônia"
            "RR" -> "Roraima"
            "SC" -> "Santa Catarina"
            "SP" -> "São Paulo"
            "SE" -> "Sergipe"
            "TO" -> "Tocantins"
            else -> uf ?: "Brasil"
        }
    }

    val CITIES_BY_BRAZILIAN_STATE: Map<String, List<String>> = mapOf(
        "AC" to listOf("Todas as Cidades"),
        "AL" to listOf("Todas as Cidades"),
        "AM" to listOf("Todas as Cidades"),
        "AP" to listOf("Todas as Cidades"),
        "BA" to listOf("Todas as Cidades"),
        "CE" to listOf("Todas as Cidades", "Fortaleza, Ceará"),
        "DF" to listOf("Todas as Cidades"),
        "ES" to listOf("Todas as Cidades"),
        "GO" to listOf("Todas as Cidades"),
        "MA" to listOf("Todas as Cidades"),
        "MG" to listOf("Todas as Cidades", "Minas Gerais Brasil", "Patos de Minas , Minas Gerais"),
        "MT" to listOf("Todas as Cidades"),
        "PA" to listOf("Todas as Cidades", "Curitiba Paraná", "Londrina, Paraná", "ParanáBrazil"),
        "PB" to listOf("Todas as Cidades"),
        "PE" to listOf("Todas as Cidades"),
        "PI" to listOf("Todas as Cidades", "Brasil ,Piaui ,Brasil", "Piauí ,Teresina"),
        "PR" to listOf("Todas as Cidades", "ParanáBrazil"),
        "RJ" to listOf("Todas as Cidades"),
        "RN" to listOf("Todas as Cidades", "Curitiba", "Porto Alegre", "Santa Catarina, SC", "Sao Paulo Brazil", "Sorocaba", "São Miguel", "Web"),
        "RR" to listOf("Todas as Cidades"),
        "RS" to listOf("Todas as Cidades"),
        "SC" to listOf("Todas as Cidades", "Joinville", "Santa Catarina, SC"),
        "SE" to listOf("Todas as Cidades"),
        "SP" to listOf("São Paulo", "Campinas", "Santos", "Ribeirão Preto", "São José dos Campos", "Sorocaba", "Araras", "Taubaté", "Todas as Cidades"),
        "TO" to listOf("Todas as Cidades")
    )

    fun getCitiesForState(stateCode: String?): List<String> {
        val cities = CURATED_GLOBAL_STATIONS.asSequence().filter {
            it.countryCode == "BR" && (stateCode.isNullOrBlank() || stateCode == "ALL" || it.state.equals(stateCode, true))
        }.map { it.city.trim() }.filter { it.isNotBlank() }.distinct().sorted().toList()
        return listOf("Todas as Cidades") + cities
    }

    fun getCitiesForCountry(countryCode: String?): List<String> = CURATED_GLOBAL_STATIONS.asSequence()
        .filter { countryCode.isNullOrBlank() || countryCode == "ALL" || it.countryCode.equals(countryCode, true) }
        .map { it.city.trim() }.filter { it.isNotBlank() }.distinct().sorted().toList()

    val CURATED_GLOBAL_STATIONS: List<RadioStation> by lazy {
        CuratedStations.stations
    }
}

/** Compatibility facade; radio_catalog.json is the sole runtime catalog. */
object CuratedStations {
    val stations: List<RadioStation> get() = RadioCatalog.stations
    val stationsCount: Int get() = stations.size
}
