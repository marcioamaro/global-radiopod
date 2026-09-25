package com.marcioamaro.mediapod.util

import org.json.JSONArray
import org.json.JSONObject

/** Validates collection shapes before any preference or database write. */
internal object BackupSchema {
    fun validate(root: JSONObject) {
        require(root.get("version") is Number && root.getDouble("version") == root.getInt("version").toDouble())
        require(root.getInt("version") in 1..30)
        val objects = listOf("visualPreferences", "audioPreferences", "radioData", "podcastData", "mediaLibrary")
        val arrays = listOf("youtubeVideos", "brickHighScores")
        require((objects + arrays).any(root::has)) { "Empty backup" }
        objects.filter(root::has).forEach { require(root.get(it) is JSONObject) { "Invalid $it" } }
        fun collection(parent: JSONObject, key: String, strings: Boolean = false) {
            if (!parent.has(key)) return
            val array = parent.get(key)
            require(array is JSONArray && array.length() <= 100_000) { "Invalid $key" }
            for (i in 0 until array.length()) {
                require(if (strings) array.get(i) is String else array.get(i) is JSONObject) { "Invalid $key entry" }
            }
        }
        arrays.forEach { collection(root, it) }
        fun entries(parent: JSONObject, key: String, required: List<String>, url: String) {
            parent.optJSONArray(key)?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    required.forEach { require(item.get(it) is String && item.getString(it).isNotBlank()) }
                    val uri = java.net.URI(item.getString(url))
                    require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
                }
            }
        }
        entries(root, "youtubeVideos", listOf("id", "title", "url"), "url")
        root.optJSONObject("radioData")?.let { data ->
            listOf("favorites", "recents", "customStations").forEach {
                collection(data, it)
                data.optJSONArray(it)?.let { array ->
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        listOf("id", "name").forEach { key ->
                            require(item.get(key) is String && item.getString(key).isNotBlank())
                        }
                        // Somente backups legados trazem streamUrl; aceite-os para restauração compatível.
                        item.optString("streamUrl").takeIf { url -> url.isNotBlank() }?.let { url ->
                            val uri = java.net.URI(url)
                            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
                        }
                    }
                }
            }
        }
        root.optJSONObject("podcastData")?.let { data ->
            if (data.has("library")) PodcastLibraryBackup.validate(data.getJSONObject("library"))
            listOf("favorites", "customPodcasts").forEach {
                collection(data, it)
                entries(data, it, listOf("id", "title", "feedUrl"), "feedUrl")
            }
            collection(data, "playedEpisodeIds", strings = true)
        }
        root.optJSONObject("mediaLibrary")?.let { data ->
            collection(data, "favorites", strings = true)
            collection(data, "recents", strings = true)
            collection(data, "playlists")
            data.optJSONArray("playlists")?.let { lists ->
                val ids = mutableSetOf<String>()
                for (i in 0 until lists.length()) {
                    val list = lists.getJSONObject(i)
                    require(list.getString("id").isNotBlank() && ids.add(list.getString("id")))
                    require(list.getString("name").isNotBlank())
                    require(list.getString("kind") in setOf("AUDIO", "VIDEO"))
                    collection(list, "keys", strings = true)
                }
            }
        }
    }
}
