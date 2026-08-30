package com.example.data.model

data class YouTubeVideo(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String = "https://img.youtube.com/vi/$id/hqdefault.jpg",
    val addedAt: Long = System.currentTimeMillis()
)
