package com.marcioamaro.mediapod.util

import android.content.Context
import android.net.Uri
import com.marcioamaro.mediapod.data.db.FavoriteStationEntity
import com.marcioamaro.mediapod.data.db.RadioDatabase
import com.marcioamaro.mediapod.data.model.PodcastShow
import com.marcioamaro.mediapod.data.model.RadioStation
import com.marcioamaro.mediapod.data.model.YouTubeVideo
import com.marcioamaro.mediapod.data.preferences.BrickHighScore
import com.marcioamaro.mediapod.data.preferences.IpodFontType
import com.marcioamaro.mediapod.data.preferences.IpodFontSizeScale
import com.marcioamaro.mediapod.data.preferences.IpodPreferencesManager
import com.marcioamaro.mediapod.data.preferences.IpodWheelPreset
import com.marcioamaro.mediapod.data.repository.PodcastRepository
import com.marcioamaro.mediapod.ui.IpodChassisTheme
import com.marcioamaro.mediapod.ui.LcdBacklight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupRestoreManager {

    suspend fun generateBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val prefs = IpodPreferencesManager.getInstance(context)
        val podcastRepo = PodcastRepository.getInstance(context)
        val db = RadioDatabase.getDatabase(context)

        root.put("version", 29)
        root.put("app", "MediaPod + Radio / Podcast")
        root.put("timestamp", System.currentTimeMillis())

        // 1. Visual & Themes
        val visualObj = JSONObject().apply {
            put("chassisTheme", prefs.chassisTheme.name)
            put("customBodyColor", prefs.customBodyColor)
            put("lcdBacklight", prefs.lcdBacklight.name)
            put("wheelPreset", prefs.wheelPreset.name)
            put("customWheelColor", prefs.customWheelColor)
            put("customWheelTextColor", prefs.customWheelTextColor)
            put("customCenterButtonColor", prefs.customCenterButtonColor)
            put("fontType", prefs.fontType.name)
            put("fontSizeScale", prefs.fontSizeScale.name)
            put("isFontBold", prefs.isFontBold)
            put("dockClockScale", prefs.dockClockScale.name)
            put("dockShowSeconds", prefs.dockShowSeconds)
            put("randomHardwareColorsEnabled", prefs.randomHardwareColorsEnabled)
            put("is24HourClock", prefs.is24HourClock)
        }
        root.put("visualPreferences", visualObj)

        // 2. Audio & Haptics
        val audioObj = JSONObject().apply {
            put("isSoundEnabled", prefs.isSoundEnabled)
            put("isHapticsEnabled", prefs.isHapticsEnabled)
            put("volumeLevel", prefs.volumeLevel.toDouble())
            put("autoPlayOnLaunch", prefs.isAutoPlayOnLaunch)
            put("remoteArtwork", DataUsagePolicy(context).remoteArtwork)
            put("preferredBitrate", DataUsagePolicy(context).preferredBitrate)
        }
        root.put("audioPreferences", audioObj)

        // 3. Radio Data (Room Favorites, Recents, Custom Radios)
        val radioObj = JSONObject()

        val roomFavs = db.favoriteStationDao().getAllFavoritesDirect()
        val favArray = JSONArray()
        for (f in roomFavs) {
            favArray.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("streamUrl", f.streamUrl)
                put("favicon", f.favicon)
                put("homepage", f.homepage)
                put("tags", f.tags)
                put("country", f.country)
                put("countryCode", f.countryCode)
                put("bitrate", f.bitrate)
                put("codec", f.codec)
                put("votes", f.votes)
            })
        }
        radioObj.put("favorites", favArray)

        val recents = prefs.getRecentStations()
        val recentsArray = JSONArray()
        for (r in recents) {
            recentsArray.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("streamUrl", r.streamUrl)
                put("favicon", r.favicon)
                put("homepage", r.homepage)
                put("tags", r.tags)
                put("country", r.country)
                put("countryCode", r.countryCode)
                put("bitrate", r.bitrate)
                put("codec", r.codec)
                put("votes", r.votes)
            })
        }
        radioObj.put("recents", recentsArray)

        val customs = prefs.getCustomStations()
        val customArray = JSONArray()
        for (c in customs) {
            customArray.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("streamUrl", c.streamUrl)
                put("favicon", c.favicon)
                put("homepage", c.homepage)
                put("tags", c.tags)
                put("country", c.country)
                put("countryCode", c.countryCode)
                put("bitrate", c.bitrate)
                put("codec", c.codec)
                put("votes", c.votes)
            })
        }
        radioObj.put("customStations", customArray)
        root.put("radioData", radioObj)

        // 4. Podcasts (Favorites, Custom Podcasts)
        val podcastObj = JSONObject()
        val podFavs = podcastRepo.favoritesFlow.value
        val podFavArray = JSONArray()
        for (p in podFavs) {
            podFavArray.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("author", p.author)
                put("feedUrl", p.feedUrl)
                put("artworkUrl", p.artworkUrl)
                put("category", p.category)
                put("description", p.description)
                put("isCustom", p.isCustom)
            })
        }
        podcastObj.put("favorites", podFavArray)

        val podCustoms = podcastRepo.customShowsFlow.value
        val podCustomArray = JSONArray()
        for (p in podCustoms) {
            podCustomArray.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("author", p.author)
                put("feedUrl", p.feedUrl)
                put("artworkUrl", p.artworkUrl)
                put("category", p.category)
                put("description", p.description)
                put("isCustom", p.isCustom)
            })
        }
        podcastObj.put("customPodcasts", podCustomArray)
        podcastObj.put("playedEpisodeIds", JSONArray(podcastRepo.playedEpisodeIds.value.toList()))
        podcastObj.put("library", PodcastLibraryBackup.export(context))
        root.put("podcastData", podcastObj)

        // 5. YouTube Videos
        val ytVideos = prefs.getYouTubeVideos()
        val ytArray = JSONArray()
        for (v in ytVideos) {
            ytArray.put(JSONObject().apply {
                put("id", v.id)
                put("title", v.title)
                put("url", v.url)
                put("thumbnailUrl", v.thumbnailUrl)
                put("addedAt", v.addedAt)
            })
        }
        root.put("youtubeVideos", ytArray)
        root.put("mediaLibrary", com.marcioamaro.mediapod.data.repository.MediaLibraryRepository.getInstance(context).exportJson())

        // 6. Jogo Brick High Scores (Arcade Ranking)
        val brickScores = prefs.getBrickHighScores()
        val brickArray = JSONArray()
        for (s in brickScores) {
            brickArray.put(JSONObject().apply {
                put("initials", s.initials)
                put("score", s.score)
                put("timestamp", s.timestamp)
            })
        }
        root.put("brickHighScores", brickArray)

        root.toString(2)
    }

    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    suspend fun exportBackupToUri(context: Context, uri: Uri, password: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = generateBackupJson(context)
            val encryptedBytes = BackupCryptoHelper.encryptBackupPayload(jsonString, password)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(encryptedBytes)
                outputStream.flush()
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupRestoreManager", "Falha ao exportar backup criptografado", e)
            false
        }
    }

    suspend fun restoreBackupFromUri(context: Context, uri: Uri, password: CharArray? = null): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = inputStream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 16 * 1024 * 1024) { "Backup muito grande" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return@withContext RestoreResult.Error("Arquivo de backup inválido ou incompatível")

            if (rawBytes.isEmpty()) {
                return@withContext RestoreResult.Error("Arquivo de backup inválido ou incompatível")
            }

            // Descriptografia obrigatória AES-256-GCM com validação de assinatura e integridade
            val jsonStr = try {
                BackupCryptoHelper.decryptBackupPayload(rawBytes, password)
            } catch (secEx: Exception) {
                android.util.Log.w("BackupRestoreManager", "Falha de criptografia / integridade", secEx)
                return@withContext RestoreResult.Error("Arquivo de backup inválido ou incompatível")
            }

            if (jsonStr.isBlank()) {
                return@withContext RestoreResult.Error("Arquivo de backup inválido ou incompatível")
            }

            val success = restoreFromJson(context, jsonStr)
            if (success) {
                RestoreResult.Success
            } else {
                RestoreResult.Error("Arquivo de backup inválido ou incompatível")
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupRestoreManager", "Falha ao restaurar backup", e)
            RestoreResult.Error("Arquivo de backup inválido ou incompatível")
        }
    }

    suspend fun restoreFromJson(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            require(jsonString.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024)
            val root = JSONObject(jsonString)
            BackupSchema.validate(root)
            RestoreJournal.apply(context) { applyRestoreJson(context, jsonString) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    private suspend fun applyRestoreJson(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val prefs = IpodPreferencesManager.getInstance(context)
            val podcastRepo = PodcastRepository.getInstance(context)
            val db = RadioDatabase.getDatabase(context)

            // Restore Visual Preferences
            if (root.has("visualPreferences")) {
                val v = root.getJSONObject("visualPreferences")
                try { prefs.chassisTheme = IpodChassisTheme.valueOf(v.optString("chassisTheme")) } catch (_: Exception) {}
                if (v.has("customBodyColor")) prefs.customBodyColor = v.getLong("customBodyColor")
                try { prefs.lcdBacklight = LcdBacklight.valueOf(v.optString("lcdBacklight")) } catch (_: Exception) {}
                try { prefs.wheelPreset = IpodWheelPreset.valueOf(v.optString("wheelPreset")) } catch (_: Exception) {}
                if (v.has("customWheelColor")) prefs.customWheelColor = v.getLong("customWheelColor")
                if (v.has("customWheelTextColor")) prefs.customWheelTextColor = v.getLong("customWheelTextColor")
                if (v.has("customCenterButtonColor")) prefs.customCenterButtonColor = v.getLong("customCenterButtonColor")
                try { prefs.fontType = IpodFontType.valueOf(v.optString("fontType")) } catch (_: Exception) {}
                try { prefs.fontSizeScale = IpodFontSizeScale.valueOf(v.optString("fontSizeScale")) } catch (_: Exception) {}
                if (v.has("isFontBold")) prefs.isFontBold = v.getBoolean("isFontBold")
                try {
                    if (v.has("dockClockScale")) {
                        prefs.dockClockScale = com.marcioamaro.mediapod.data.preferences.DockClockScale.valueOf(v.getString("dockClockScale"))
                    }
                } catch (_: Exception) {}
                if (v.has("dockShowSeconds")) {
                    prefs.dockShowSeconds = v.getBoolean("dockShowSeconds")
                }
                if (v.has("randomHardwareColorsEnabled")) {
                    prefs.randomHardwareColorsEnabled = v.getBoolean("randomHardwareColorsEnabled")
                }
                if (v.has("is24HourClock")) {
                    prefs.is24HourClock = v.getBoolean("is24HourClock")
                }
            }

            // Restore Audio Preferences
            if (root.has("audioPreferences")) {
                val a = root.getJSONObject("audioPreferences")
                if (a.has("isSoundEnabled")) prefs.isSoundEnabled = a.getBoolean("isSoundEnabled")
                if (a.has("isHapticsEnabled")) prefs.isHapticsEnabled = a.getBoolean("isHapticsEnabled")
                if (a.has("volumeLevel")) prefs.volumeLevel = a.getDouble("volumeLevel").toFloat()
                if (a.has("autoPlayOnLaunch")) prefs.isAutoPlayOnLaunch = a.getBoolean("autoPlayOnLaunch")
                if (a.has("remoteArtwork")) DataUsagePolicy(context).remoteArtwork = a.getBoolean("remoteArtwork")
                if (a.has("preferredBitrate")) DataUsagePolicy(context).preferredBitrate = a.getInt("preferredBitrate")
            }

            // Restore Radio Data
            if (root.has("radioData")) {
                val r = root.getJSONObject("radioData")
                if (r.has("favorites")) {
                    val favs = r.getJSONArray("favorites")
                    for (i in 0 until favs.length()) {
                        val f = favs.getJSONObject(i)
                        val entity = FavoriteStationEntity(
                            id = f.optString("id"),
                            name = f.optString("name"),
                            streamUrl = f.optString("streamUrl"),
                            favicon = f.optString("favicon"),
                            homepage = f.optString("homepage"),
                            tags = f.optString("tags"),
                            country = f.optString("country"),
                            countryCode = f.optString("countryCode"),
                            codec = f.optString("codec", "MP3"),
                            bitrate = f.optInt("bitrate", 128),
                            votes = f.optInt("votes", 0)
                        )
                        db.favoriteStationDao().insertFavorite(entity)
                    }
                }

                if (r.has("recents")) {
                    val recents = r.getJSONArray("recents")
                    val recentList = mutableListOf<RadioStation>()
                    for (i in 0 until recents.length()) {
                        val rc = recents.getJSONObject(i)
                        val station = RadioStation(
                            id = rc.optString("id"),
                            name = rc.optString("name"),
                            streamUrl = rc.optString("streamUrl"),
                            favicon = rc.optString("favicon"),
                            homepage = rc.optString("homepage"),
                            tags = rc.optString("tags"),
                            country = rc.optString("country"),
                            countryCode = rc.optString("countryCode"),
                            codec = rc.optString("codec", "MP3"),
                            bitrate = rc.optInt("bitrate", 128),
                            votes = rc.optInt("votes", 0)
                        )
                        recentList.add(station)
                    }
                    prefs.setRecentStations(recentList)
                }

                if (r.has("customStations")) {
                    val customs = r.getJSONArray("customStations")
                    for (i in 0 until customs.length()) {
                        val c = customs.getJSONObject(i)
                        val station = RadioStation(
                            id = c.optString("id"),
                            name = c.optString("name"),
                            streamUrl = c.optString("streamUrl"),
                            favicon = c.optString("favicon"),
                            homepage = c.optString("homepage"),
                            tags = c.optString("tags"),
                            country = c.optString("country"),
                            countryCode = c.optString("countryCode"),
                            codec = c.optString("codec", "MP3"),
                            bitrate = c.optInt("bitrate", 128),
                            votes = c.optInt("votes", 0)
                        )
                        prefs.addCustomStation(station)
                    }
                }
            }

            // Restore Podcast Data
            if (root.has("podcastData")) {
                val p = root.getJSONObject("podcastData")
                p.optJSONObject("library")?.let { PodcastLibraryBackup.restore(context, it) }
                p.optJSONArray("playedEpisodeIds")?.let { ids ->
                    podcastRepo.restorePlayedEpisodes((0 until ids.length()).map { ids.getString(it) }.toSet())
                }
                if (p.has("favorites")) {
                    val favs = p.getJSONArray("favorites")
                    for (i in 0 until favs.length()) {
                        val pf = favs.getJSONObject(i)
                        val show = PodcastShow(
                            id = pf.optString("id"),
                            title = pf.optString("title"),
                            author = pf.optString("author"),
                            feedUrl = pf.optString("feedUrl"),
                            artworkUrl = pf.optString("artworkUrl"),
                            category = pf.optString("category"),
                            description = pf.optString("description"),
                            isCustom = pf.optBoolean("isCustom", false),
                            isFavorite = true
                        )
                        if (podcastRepo.favoritesFlow.value.none { it.id == show.id || it.feedUrl == show.feedUrl }) {
                            podcastRepo.toggleFavorite(show)
                        }
                    }
                }

                if (p.has("customPodcasts")) {
                    val customs = p.getJSONArray("customPodcasts")
                    for (i in 0 until customs.length()) {
                        val pc = customs.getJSONObject(i)
                        val show = PodcastShow(
                            id = pc.optString("id"),
                            title = pc.optString("title"),
                            author = pc.optString("author"),
                            feedUrl = pc.optString("feedUrl"),
                            artworkUrl = pc.optString("artworkUrl"),
                            category = pc.optString("category"),
                            description = pc.optString("description"),
                            isCustom = true
                        )
                        podcastRepo.addCustomPodcast(show)
                    }
                }
            }

            // Restore YouTube Videos
            root.optJSONObject("mediaLibrary")?.let {
                com.marcioamaro.mediapod.data.repository.MediaLibraryRepository.getInstance(context).restoreJson(it)
            }
            if (root.has("youtubeVideos")) {
                val yt = root.getJSONArray("youtubeVideos")
                for (i in yt.length() - 1 downTo 0) {
                    val y = yt.getJSONObject(i)
                    val video = YouTubeVideo(
                        id = y.optString("id"),
                        title = y.optString("title"),
                        url = y.optString("url"),
                        thumbnailUrl = y.optString("thumbnailUrl"),
                        addedAt = y.optLong("addedAt", System.currentTimeMillis())
                    )
                    prefs.addYouTubeVideo(video)
                }
            }

            // Restore Brick High Scores (Arcade Ranking)
            if (root.has("brickHighScores")) {
                val brickArray = root.getJSONArray("brickHighScores")
                val scoresList = mutableListOf<BrickHighScore>()
                for (i in 0 until brickArray.length()) {
                    val b = brickArray.getJSONObject(i)
                    scoresList.add(
                        BrickHighScore(
                            initials = b.optString("initials", "AAA"),
                            score = b.optInt("score", 0),
                            timestamp = b.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                prefs.setBrickHighScores(scoresList)
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("BackupRestoreManager", "Falha ao processar restauração JSON", e)
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }
}
