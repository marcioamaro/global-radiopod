package com.example.data.remote

import android.util.Log
import com.example.data.repository.CuratedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object IbgeLocationService {

    private const val TAG = "IbgeLocationService"
    private const val BASE_URL = "https://servicodados.ibge.gov.br/api/v1/localidades/estados"

    // Cache em memória: chave é UF (ex: "SP", "RJ", "MG") -> lista de nomes de municípios
    private val memoryCache = ConcurrentHashMap<String, List<String>>()

    suspend fun getCitiesForState(uf: String): List<String> = withContext(Dispatchers.IO) {
        val cleanUf = uf.trim().uppercase()
        if (cleanUf.isBlank() || cleanUf == "ALL") {
            return@withContext emptyList()
        }

        // 1. Verifica cache local em memória
        memoryCache[cleanUf]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }

        // 2. Tenta obter da API pública oficial do IBGE
        try {
            val url = URL("$BASE_URL/$cleanUf/municipios")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GlobalRadioPod/1.0 (Android)")
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                val responseStr = reader.use { it.readText() }
                connection.disconnect()

                val jsonArray = JSONArray(responseStr)
                val cities = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val name = item.optString("nome", "").trim()
                    if (name.isNotEmpty()) {
                        cities.add(name)
                    }
                }

                if (cities.isNotEmpty()) {
                    // Ordena respeitando acentuação da língua portuguesa
                    val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
                    cities.sortWith(collator)

                    // Garante São Paulo em PRIMEIRO lugar na lista de cidades
                    val spIndex = cities.indexOfFirst { it.equals("São Paulo", ignoreCase = true) }
                    if (spIndex > 0) {
                        val spCity = cities.removeAt(spIndex)
                        cities.add(0, spCity)
                    }

                    memoryCache[cleanUf] = cities
                    Log.d(TAG, "IBGE: Carregados ${cities.size} municípios para $cleanUf com sucesso (São Paulo no topo)")
                    return@withContext cities
                }
            } else {
                Log.w(TAG, "IBGE API retornou status HTTP ${connection.responseCode} para UF $cleanUf")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao conectar na API do IBGE para UF $cleanUf: ${e.message}")
        }

        // 3. Fallback inteligente: base pré-curada local caso esteja sem internet
        val fallback = CuratedData.getCitiesForState(cleanUf).filter { it != "Todas as Cidades" }.toMutableList()
        val spIndexFallback = fallback.indexOfFirst { it.equals("São Paulo", ignoreCase = true) }
        if (spIndexFallback > 0) {
            val spCity = fallback.removeAt(spIndexFallback)
            fallback.add(0, spCity)
        }
        if (fallback.isNotEmpty()) {
            memoryCache[cleanUf] = fallback
        }
        fallback
    }

    fun clearCache() {
        memoryCache.clear()
    }
}
