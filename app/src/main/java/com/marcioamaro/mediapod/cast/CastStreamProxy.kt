package com.marcioamaro.mediapod.cast

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.marcioamaro.mediapod.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder

/**
 * Servidor Proxy HTTP local para o Google Cast (Chromecast / Google Home / Nest Mini).
 *
 * O Default Media Receiver do Cast exige que os streams de áudio possuam cabeçalhos
 * CORS (Access-Control-Allow-Origin: *), não contenham frames intercalados ICY e aceitem
 * conexões sem bloqueio de User-Agent.
 *
 * Como quase todas as emissoras Icecast/Shoutcast não fornecem CORS ou usam HTTP simples,
 * este proxy local no aparelho faz o download limpo da rádio e serve via LAN para o Cast
 * com todos os cabeçalhos ideais (CORS, áudio contínuo, keep-alive).
 */
class CastStreamProxy private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverPort: Int = 0
    private var serverJob: Job? = null
    private var activeStreamJob: Job? = null
    private var appIconPngCached: ByteArray? = null

    companion object {
        private const val TAG = "CastStreamProxy"

        @Volatile
        private var instance: CastStreamProxy? = null

        fun getInstance(context: Context): CastStreamProxy {
            return instance ?: synchronized(this) {
                instance ?: CastStreamProxy(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        startServer()
    }

    @Synchronized
    fun startServer() {
        if (serverSocket != null && !serverSocket!!.isClosed) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(0)
                serverPort = serverSocket!!.localPort
                Log.i(TAG, "CastStreamProxy iniciado na porta $serverPort")

                while (isActive && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar ServerSocket do CastStreamProxy", e)
            }
        }
    }

    @Synchronized
    fun stopServer() {
        try {
            activeStreamJob?.cancel()
            serverJob?.cancel()
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob = null
    }

    fun getLocalWifiIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Retorna a URL proxied que o Chromecast deve carregar.
     * Se não for possível determinar o IP local da Wi-Fi, retorna a URL original como fallback.
     */
    fun getProxyStreamUrl(originalStreamUrl: String): String {
        startServer()
        val wifiIp = getLocalWifiIp()
        if (wifiIp.isNullOrBlank() || serverPort == 0) {
            Log.w(TAG, "Não foi possível obter IP Wi-Fi local para proxy do Cast, usando URL direta")
            return originalStreamUrl
        }
        val encoded = Uri.encode(originalStreamUrl)
        val proxyUrl = "http://$wifiIp:$serverPort/stream.mp3?src=$encoded"
        Log.d(TAG, "Gerada URL proxy para Cast: $proxyUrl (original: $originalStreamUrl)")
        return proxyUrl
    }

    fun getAppIconUrl(): String? {
        startServer()
        val wifiIp = getLocalWifiIp()
        return if (!wifiIp.isNullOrBlank() && serverPort > 0) {
            "http://$wifiIp:$serverPort/app_icon.png"
        } else {
            null
        }
    }

    /**
     * Retorna a URL HTTP local que serve um arquivo local (MP3 ou Vídeo) via LAN para o Chromecast,
     * com suporte completo a requisições de Range (Seek) e cabeçalhos CORS.
     */
    fun getLocalMediaProxyUrl(contentUri: Uri, mimeType: String): String {
        startServer()
        val wifiIp = getLocalWifiIp()
        if (wifiIp.isNullOrBlank() || serverPort == 0) {
            Log.w(TAG, "Não foi possível obter IP Wi-Fi local para media local no Cast")
            return contentUri.toString()
        }
        val encodedUri = Uri.encode(contentUri.toString())
        val encodedMime = Uri.encode(mimeType)
        val ext = if (mimeType.startsWith("video")) ".mp4" else ".mp3"
        val proxyUrl = "http://$wifiIp:$serverPort/local_media$ext?uri=$encodedUri&mime=$encodedMime"
        Log.d(TAG, "Gerada URL proxy de mídia local para Cast: $proxyUrl (contentUri: $contentUri)")
        return proxyUrl
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val inStream = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(inStream))
            val requestLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            Log.d(TAG, "Cast cliente conectado: $requestLine")

            val headers = mutableMapOf<String, String>()
            var hLine: String?
            while (true) {
                hLine = reader.readLine()
                if (hLine.isNullOrBlank()) break
                val colonIdx = hLine.indexOf(':')
                if (colonIdx > 0) {
                    val key = hLine.substring(0, colonIdx).trim().lowercase()
                    val value = hLine.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            if (requestLine.startsWith("OPTIONS")) {
                val out = socket.getOutputStream()
                val response = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, OPTIONS, HEAD\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(response.toByteArray())
                out.flush()
                socket.close()
                return
            }

            if (requestLine.startsWith("GET /app_icon.png") || requestLine.startsWith("HEAD /app_icon.png")) {
                val bytes = getAppIconPngBytes()
                val out = socket.getOutputStream()
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/png\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(header.toByteArray())
                if (requestLine.startsWith("GET")) {
                    out.write(bytes)
                }
                out.flush()
                socket.close()
                return
            }

            if (requestLine.startsWith("GET /local_media") || requestLine.startsWith("HEAD /local_media")) {
                val parts = requestLine.split(" ")
                val path = if (parts.size > 1) parts[1] else ""
                val uri = Uri.parse(path)
                val targetUriEncoded = uri.getQueryParameter("uri")
                val mimeType = uri.getQueryParameter("mime") ?: if (path.contains(".mp4")) "video/mp4" else "audio/mpeg"

                if (targetUriEncoded.isNullOrBlank()) {
                    send404(socket)
                    return
                }

                val targetContentUri = try {
                    Uri.parse(URLDecoder.decode(targetUriEncoded, "UTF-8"))
                } catch (_: Exception) {
                    Uri.parse(targetUriEncoded)
                }

                streamLocalMediaToCast(
                    contentUri = targetContentUri,
                    mimeType = mimeType,
                    rangeHeader = headers["range"],
                    isHead = requestLine.startsWith("HEAD"),
                    clientSocket = socket
                )
                return
            }

            if (requestLine.startsWith("GET /stream") || requestLine.startsWith("HEAD /stream")) {
                val parts = requestLine.split(" ")
                val path = if (parts.size > 1) parts[1] else ""
                val uri = Uri.parse(path)
                val targetUrlEncoded = uri.getQueryParameter("src")

                if (targetUrlEncoded.isNullOrBlank()) {
                    send404(socket)
                    return
                }

                val originalUrl = try {
                    URLDecoder.decode(targetUrlEncoded, "UTF-8")
                } catch (_: Exception) {
                    targetUrlEncoded
                }

                if (requestLine.startsWith("HEAD")) {
                    val out = socket.getOutputStream()
                    val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: audio/mpeg\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Cache-Control: no-cache, no-store\r\n" +
                        "Connection: close\r\n\r\n"
                    out.write(header.toByteArray())
                    out.flush()
                    socket.close()
                    return
                }

                // Faz streaming dos bytes da emissora para o Chromecast
                activeStreamJob?.cancel()
                activeStreamJob = scope.launch {
                    streamRadioToCast(originalUrl, socket)
                }
            } else {
                send404(socket)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exceção em handleClient: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun streamLocalMediaToCast(
        contentUri: Uri,
        mimeType: String,
        rangeHeader: String?,
        isHead: Boolean,
        clientSocket: Socket
    ) {
        var afd: android.content.res.AssetFileDescriptor? = null
        try {
            clientSocket.soTimeout = 0
            afd = context.contentResolver.openAssetFileDescriptor(contentUri, "r")
            if (afd == null) {
                Log.w(TAG, "Falha ao abrir AssetFileDescriptor para $contentUri")
                send404(clientSocket)
                return
            }

            val totalLength = if (afd.length > 0) afd.length else afd.parcelFileDescriptor.statSize
            var start = 0L
            var end = if (totalLength > 0) totalLength - 1L else -1L
            var isRange = false

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                val rangeValue = rangeHeader.removePrefix("bytes=").trim()
                val dashIdx = rangeValue.indexOf('-')
                if (dashIdx != -1) {
                    val startStr = rangeValue.substring(0, dashIdx).trim()
                    val endStr = rangeValue.substring(dashIdx + 1).trim()
                    if (startStr.isNotEmpty()) {
                        start = startStr.toLongOrNull() ?: 0L
                    }
                    if (endStr.isNotEmpty()) {
                        end = endStr.toLongOrNull() ?: end
                    }
                    if (totalLength > 0 && end >= totalLength) {
                        end = totalLength - 1L
                    }
                    isRange = true
                }
            }

            val outStream = clientSocket.getOutputStream()
            if (isRange && totalLength > 0) {
                val contentLength = (end - start + 1L).coerceAtLeast(0L)
                val header = "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Type: $mimeType\r\n" +
                    "Content-Range: bytes $start-$end/$totalLength\r\n" +
                    "Content-Length: $contentLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                    "Connection: close\r\n\r\n"
                outStream.write(header.toByteArray())
            } else {
                val lenHeader = if (totalLength > 0) "Content-Length: $totalLength\r\n" else ""
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: $mimeType\r\n" +
                    lenHeader +
                    "Accept-Ranges: bytes\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                    "Connection: close\r\n\r\n"
                outStream.write(header.toByteArray())
            }
            outStream.flush()

            if (isHead) {
                return
            }

            val fis = afd.createInputStream()
            val baseOffset = afd.startOffset
            if (baseOffset + start > 0) {
                fis.channel.position(baseOffset + start)
            }

            var bytesRemaining = if (totalLength > 0) (end - start + 1L).coerceAtLeast(0L) else Long.MAX_VALUE
            val buffer = ByteArray(16384)
            while (scope.isActive && !clientSocket.isClosed && bytesRemaining > 0) {
                val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                val read = fis.read(buffer, 0, toRead)
                if (read == -1) break
                outStream.write(buffer, 0, read)
                bytesRemaining -= read
            }
            outStream.flush()
            fis.close()
            Log.d(TAG, "Envio de mídia local para Cast concluído com sucesso ($contentUri, bytes: ${totalLength - bytesRemaining})")
        } catch (e: Exception) {
            Log.d(TAG, "Exceção em streamLocalMediaToCast: ${e.message}")
        } finally {
            try { afd?.close() } catch (_: Exception) {}
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 6) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MediaPod/1.0")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Icy-MetaData", "0")

            conn.connect()
            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                val newLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (!newLocation.isNullOrBlank()) {
                    currentUrl = if (newLocation.startsWith("http://") || newLocation.startsWith("https://")) {
                        newLocation
                    } else {
                        URL(url, newLocation).toString()
                    }
                    Log.d(TAG, "Seguindo redirect para o Cast: $currentUrl")
                    redirects++
                    continue
                }
            }
            return conn
        }
        val finalConn = URL(currentUrl).openConnection() as HttpURLConnection
        finalConn.connect()
        return finalConn
    }

    private fun streamRadioToCast(streamUrl: String, clientSocket: Socket) {
        var conn: HttpURLConnection? = null
        try {
            clientSocket.soTimeout = 0 // Streaming contínuo sem timeout de socket de saída
            Log.i(TAG, "Iniciando encaminhamento de stream para o Cast: $streamUrl")

            conn = openConnectionWithRedirects(streamUrl)
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                Log.e(TAG, "Emissora retornou HTTP $responseCode ao conectar para o Cast")
                sendError(clientSocket, responseCode)
                return
            }

            val contentType = conn.contentType?.ifBlank { "audio/mpeg" } ?: "audio/mpeg"
            val outStream = clientSocket.getOutputStream()

            val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS, HEAD\r\n" +
                "Cache-Control: no-cache, no-store\r\n" +
                "Pragma: no-cache\r\n" +
                "Connection: close\r\n\r\n"

            outStream.write(responseHeaders.toByteArray())
            outStream.flush()

            val remoteInputStream = conn.inputStream
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (scope.isActive && !clientSocket.isClosed) {
                bytesRead = remoteInputStream.read(buffer)
                if (bytesRead == -1) break
                outStream.write(buffer, 0, bytesRead)
            }
            outStream.flush()
            Log.i(TAG, "Streaming para o Cast finalizado normalmente")
        } catch (e: Exception) {
            Log.d(TAG, "Stream encerrado ou interrompido pelo Cast: ${e.message}")
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun send404(socket: Socket) {
        try {
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            socket.close()
        } catch (_: Exception) {}
    }

    private fun sendError(socket: Socket, code: Int) {
        try {
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 $code Error\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            socket.close()
        } catch (_: Exception) {}
    }

    private fun getAppIconPngBytes(): ByteArray {
        appIconPngCached?.let { return it }
        try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.playstore_icon)
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    b
                }
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                appIconPngCached = bytes
                return bytes
            }
        } catch (_: Exception) {}
        return ByteArray(0)
    }
}
