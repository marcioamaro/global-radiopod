package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class NetworkStatus {
    ONLINE,
    OFFLINE
}

object NetworkConnectivityValidator {

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _networkStatus = MutableStateFlow(NetworkStatus.ONLINE)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private var isMonitoring = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Servidores de validação confiáveis (mínimo de 3 testados)
    private val VALIDATION_ENDPOINTS = listOf(
        "https://example.com",
        "https://www.google.com/generate_204",
        "https://cloudflare.com/cdn-cgi/trace",
        "https://www.apple.com/library/test/success.html"
    )

    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                monitorScope.launch {
                    val capabilities = cm.getNetworkCapabilities(network)
                    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    if (hasInternet) {
                        val validated = checkInternetAccess(appContext)
                        _networkStatus.value = if (validated) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
                    } else {
                        _networkStatus.value = NetworkStatus.OFFLINE
                    }
                }
            }

            override fun onLost(network: Network) {
                _networkStatus.value = NetworkStatus.OFFLINE
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet && isValidated) {
                    _networkStatus.value = NetworkStatus.ONLINE
                } else if (!hasInternet) {
                    _networkStatus.value = NetworkStatus.OFFLINE
                }
            }

            override fun onUnavailable() {
                _networkStatus.value = NetworkStatus.OFFLINE
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
            isMonitoring = true

            // Verificação inicial rápida sem travar a Main Thread
            monitorScope.launch {
                val initialAccess = checkInternetAccess(appContext)
                _networkStatus.value = if (initialAccess) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
            }
        } catch (e: Exception) {
            android.util.Log.w("NetworkValidator", "Failed to register network callback", e)
        }
    }

    /**
     * Valida se o dispositivo tem acesso real à internet.
     * Consulta no mínimo 3 servidores independentes e confiáveis antes de declarar offline.
     * Retorna true se qualquer um dos servidores responder com sucesso (200..399 ou 204).
     */
    suspend fun checkInternetAccess(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(activeNetwork)
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                // Sem interface de rede (nem Wi-Fi nem dados móveis ativos)
                return@withContext false
            }
        } catch (_: Exception) {}

        var testedServers = 0
        for (endpoint in VALIDATION_ENDPOINTS) {
            testedServers++
            if (pingServer(endpoint)) {
                return@withContext true // Conexão confirmada com sucesso!
            }
            if (testedServers >= 3) {
                // Já testou 3 servidores distintos e todos falharam
                break
            }
        }

        return@withContext false
    }

    private fun pingServer(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..399 || code == 204
        } catch (_: Exception) {
            // Alguns proxies barram HEAD, tenta GET rápido
            try {
                val url = URL(urlString)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    instanceFollowRedirects = true
                }
                val code = connection.responseCode
                connection.disconnect()
                code in 200..399 || code == 204
            } catch (_: Exception) {
                false
            }
        }
    }
}
