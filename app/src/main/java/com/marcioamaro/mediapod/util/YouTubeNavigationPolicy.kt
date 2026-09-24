package com.marcioamaro.mediapod.util

import java.net.URI

object YouTubeNavigationPolicy {
    fun isPlayerOrigin(url: String?): Boolean = host(url)?.let {
        it == "youtube.com" || it.endsWith(".youtube.com") ||
            it == "youtube-nocookie.com" || it.endsWith(".youtube-nocookie.com") || it == "youtu.be"
    } ?: false

    fun allowsNavigation(url: String?): Boolean = isPlayerOrigin(url) ||
        host(url) in setOf("accounts.google.com", "consent.google.com")

    private fun host(url: String?): String? = runCatching {
        val uri = URI(url ?: return null)
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return null
        uri.host?.lowercase(java.util.Locale.ROOT)
    }.getOrNull()
}
