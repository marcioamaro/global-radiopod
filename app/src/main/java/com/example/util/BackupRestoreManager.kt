package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.db.FavoriteStationEntity
import com.example.data.db.RadioDatabase
import com.example.data.model.PodcastShow
import com.example.data.model.RadioStation
import com.example.data.model.YouTubeVideo
import com.example.data.preferences.IpodFontType
import com.example.data.preferences.IpodFontSizeScale
import com.example.data.preferences.IpodPreferencesManager
import com.example.data.preferences.IpodWheelPreset
import com.example.data.repository.PodcastRepository
import com.example.ui.IpodChassisTheme
import com.example.ui.LcdBacklight
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

        root.put("version", 28)
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

        root.toString(2)
    }

    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    suspend fun exportBackupToUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = generateBackupJson(context)
            val encryptedBytes = BackupCryptoHelper.encryptBackupPayload(jsonString)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(encryptedBytes)
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupRestoreManager", "Falha ao exportar backup criptografado", e)
            false
        }
    }

    suspend fun restoreBackupFromUri(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: return@withContext RestoreResult.Error("Arquivo de backup inválido ou incompatível")

            if (rawBytes.isEmpty()) {
                return@withContext RestoreResult.Error("Arquivo de backup inválido ou incompatível")
            }

            // Descriptografia obrigatória AES-256-GCM com validação de assinatura e integridade
            val jsonStr = try {
                BackupCryptoHelper.decryptBackupPayload(rawBytes)
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
                        prefs.dockClockScale = com.example.data.preferences.DockClockScale.valueOf(v.getString("dockClockScale"))
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
                        podcastRepo.toggleFavorite(show)
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
            if (root.has("youtubeVideos")) {
                val yt = root.getJSONArray("youtubeVideos")
                for (i in 0 until yt.length()) {
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

            true
        } catch (e: Exception) {
            android.util.Log.e("BackupRestoreManager", "Falha ao processar restauração JSON", e)
            false
        }
    }
}
