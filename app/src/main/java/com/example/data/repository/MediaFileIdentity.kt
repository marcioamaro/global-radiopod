package com.example.data.repository

import java.io.InputStream
import java.security.MessageDigest

data class MediaFileReference(val key: String, val size: Long, val modified: Long, val open: () -> InputStream)

internal object MediaFileIdentity {
    fun digest(file: MediaFileReference): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.open().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
