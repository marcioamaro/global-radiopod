package com.example.util

import com.example.data.model.PodcastShow

object PodcastIdentity {
    private fun canonical(value: String) = RadioSearchEngine.normalize(value).filter { it.isLetterOrDigit() }
    fun samePublisherAndTitle(original: PodcastShow, candidate: PodcastShow): Boolean {
        val author = canonical(original.author)
        val title = canonical(original.title)
        return author.isNotEmpty() && title.isNotEmpty() && author == canonical(candidate.author) &&
            title == canonical(candidate.title)
    }
}
