package com.marcioamaro.mediapod.data.download

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal object ResumableTransfer {
    fun transfer(url: String, target: File, validatorFile: File,
                 checkActive: () -> Unit, progress: (Long, Long) -> Unit,
                 connectionChanged: (HttpURLConnection?) -> Unit = {}) {
        val validator = validatorFile.takeIf { it.isFile }?.readText().orEmpty()
        val offset = if (validator.isNotBlank() && target.exists()) target.length() else 0L
        val connection = URL(url).openConnection() as HttpURLConnection
        connectionChanged(connection)
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=$offset-")
                connection.setRequestProperty("If-Range", validator)
            }
            checkActive()
            val code = connection.responseCode
            if (code == 416 && offset > 0) {
                // A completed partial or a changed resource must not remain stuck forever.
                validatorFile.delete()
                FileOutputStream(target).use { it.fd.sync() }
                connection.disconnect()
                transfer(url, target, validatorFile, checkActive, progress, connectionChanged)
                return
            }
            require(code == 200 || code == 206) { "Servidor retornou HTTP $code" }
            require(!connection.contentType.orEmpty().startsWith("text/", true)) { "Servidor retornou uma página em vez de áudio" }
            val append = code == 206 && offset > 0
            val range = if (code == 206) Regex("bytes (\\d+)-(\\d+)/(\\d+)")
                .matchEntire(connection.getHeaderField("Content-Range").orEmpty()) else null
            if (code == 206) require(range != null && range.groupValues[1].toLong() == offset) { "Resposta de retomada inválida" }
            val total = range?.groupValues?.get(3)?.toLong() ?: connection.contentLengthLong
            val initial = if (append) offset else 0L
            require(total < 0 || total >= initial) { "Tamanho inválido" }
            require(target.parentFile!!.usableSpace > (if (total > 0) total - initial else 0L) + 8 * 1024 * 1024) { "Libere espaço para baixar" }
            val nextValidator = connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") }
                ?: connection.getHeaderField("Last-Modified").orEmpty()
            // Truncate before persisting the new validator, so an interrupted full response
            // cannot associate old partial bytes with a new representation.
            FileOutputStream(target, append).use { output ->
                validatorFile.writeText(nextValidator)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = initial
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        require(total < 0 || downloaded <= total) { "Tamanho inconsistente" }
                        progress(downloaded, total)
                    }
                    checkActive()
                    require(downloaded > 0 && (total < 0 || downloaded == total)) { "Download incompleto; tente retomar" }
                }
                output.fd.sync()
            }
        } finally { connectionChanged(null); connection.disconnect() }
    }
}
