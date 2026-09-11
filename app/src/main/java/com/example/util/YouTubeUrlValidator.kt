package com.example.util

import java.util.regex.Pattern

sealed class YouTubeValidationResult {
    data class Success(val videoId: String, val cleanUrl: String) : YouTubeValidationResult()
    data class Error(val message: String) : YouTubeValidationResult()
}

object YouTubeUrlValidator {

    private val EXACT_PATTERNS = listOf(
        Pattern.compile("^https?://(?:www\\.)?youtu\\.be/([a-zA-Z0-9_-]{11})(?:\\?.*)?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^https?://(?:[a-zA-Z0-9-]+\\.)?youtube\\.com/watch\\?(?:.*&)?v=([a-zA-Z0-9_-]{11})(?:&.*)?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^https?://(?:[a-zA-Z0-9-]+\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})(?:\\?.*)?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^https?://(?:[a-zA-Z0-9-]+\\.)?youtube\\.com/embed/([a-zA-Z0-9_-]{11})(?:\\?.*)?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^https?://(?:[a-zA-Z0-9-]+\\.)?youtube\\.com/v/([a-zA-Z0-9_-]{11})(?:\\?.*)?$", Pattern.CASE_INSENSITIVE)
    )

    private val EMBEDDED_PATTERNS = listOf(
        Pattern.compile("(?:youtu\\.be/|v/|u/\\w/|embed/|shorts/|watch\\?(?:.*&)?v=)([a-zA-Z0-9_-]{11})", Pattern.CASE_INSENSITIVE)
    )

    fun extractVideoId(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }
        val cleanUrl = MediaNameResolver.extractUrlFromText(trimmed)
        for (pattern in EXACT_PATTERNS) {
            val matcher = pattern.matcher(cleanUrl)
            if (matcher.find()) {
                val id = matcher.group(1)
                if (!id.isNullOrBlank()) return id
            }
        }
        for (pattern in EMBEDDED_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val id = matcher.group(1)
                if (!id.isNullOrBlank()) return id
            }
        }
        return null
    }

    fun validateUrl(rawUrl: String): YouTubeValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return YouTubeValidationResult.Error("Por favor, informe a URL do vídeo do YouTube.")
        }
        val videoId = extractVideoId(trimmed)
            ?: return YouTubeValidationResult.Error("URL do YouTube inválida. Formatos aceitos: youtube.com/watch?v=..., youtu.be/... ou youtube.com/shorts/...")

        val cleanUrl = "https://www.youtube.com/watch?v=$videoId"
        return YouTubeValidationResult.Success(videoId = videoId, cleanUrl = cleanUrl)
    }
}
