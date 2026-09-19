package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.RadioStation
import com.example.ui.IpodChassisTheme
import com.example.ui.LcdBacklight
import org.json.JSONObject

enum class IpodFontType(val displayName: String) {
    MONOSPACE("Monospace Retrô LCD"),
    SANS_SERIF("Sans-Serif Clássica"),
    SERIF("Serif Elegante"),
    DEFAULT("Padrão do Sistema")
}

fun IpodFontType.toFontFamily(): androidx.compose.ui.text.font.FontFamily {
    return when (this) {
        IpodFontType.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
        IpodFontType.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.SansSerif
        IpodFontType.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
        IpodFontType.DEFAULT -> androidx.compose.ui.text.font.FontFamily.Default
    }
}

enum class IpodFontSizeScale(val displayName: String, val scale: Float) {
    SCALE_100("100%", 1.0f),
    SCALE_150("150%", 1.5f),
    SCALE_200("200%", 2.0f),
    SCALE_250("250%", 2.5f)
}

enum class DockClockScale(val displayName: String, val multiplier: Float) {
    SCALE_100("100% (Padrão)", 1.0f),
    SCALE_150("150%", 1.5f),
    SCALE_200("200%", 2.0f),
    SCALE_250("250%", 2.5f)
}

enum class IpodWheelPreset(
    val displayName: String,
    val wheelColor: Long,
    val textColor: Long,
    val centerButtonColor: Long
) {
    CLASSIC_GREY("Cinza Clássico (Original)", 0xFFE2E4E8, 0xFF475569, 0xFFFFFFFF),
    PURE_WHITE("Branco Neve (Clean Look)", 0xFFFFFFFF, 0xFF64748B, 0xFFF1F5F9),
    STEALTH_BLACK("Preto Fosco (Stealth)", 0xFF1E293B, 0xFFFFFFFF, 0xFF0F172A),
    U2_RED("Vermelho Edição U2", 0xFFDC2626, 0xFFFFFFFF, 0xFF111111),
    CHAMPAGNE_GOLD("Ouro Champagne", 0xFFFDE68A, 0xFF78350F, 0xFFFEF3C7),
    ICE_BLUE("Azul Mini Classic", 0xFF0284C7, 0xFFFFFFFF, 0xFFE0F2FE),
    EMERALD_GREEN("Verde Esmeralda", 0xFF059669, 0xFFFFFFFF, 0xFFD1FAE5),
    CUSTOM("Personalizado", 0xFFE2E4E8, 0xFF475569, 0xFFFFFFFF)
}

class IpodPreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ipod_radio_user_preferences"

        private const val KEY_LAST_STATION_JSON = "key_last_station_json"
        private const val KEY_AUTO_PLAY_ON_LAUNCH = "key_auto_play_on_launch"
        private const val KEY_CHASSIS_THEME = "key_chassis_theme"
        private const val KEY_CUSTOM_BODY_COLOR = "key_custom_body_color"
        private const val KEY_LCD_BACKLIGHT = "key_lcd_backlight"
        private const val KEY_WHEEL_PRESET = "key_wheel_preset"
        private const val KEY_CUSTOM_WHEEL_COLOR = "key_custom_wheel_color"
        private const val KEY_CUSTOM_WHEEL_TEXT_COLOR = "key_custom_wheel_text_color"
        private const val KEY_CUSTOM_CENTER_BUTTON_COLOR = "key_custom_center_button_color"
        private const val KEY_FONT_TYPE = "key_font_type"
        private const val KEY_FONT_SIZE_SCALE = "key_font_size_scale"
        private const val KEY_FONT_BOLD = "key_font_bold"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_HAPTICS_ENABLED = "key_haptics_enabled"
        private const val KEY_RECENTS_JSON = "key_recents_json"
        private const val KEY_VOLUME = "key_volume"
        private const val KEY_LAST_MEDIA_TYPE = "key_last_media_type"
        private const val KEY_LAST_PODCAST_EPISODE_JSON = "key_last_podcast_episode_json"
        private const val KEY_LAST_PODCAST_SHOW_JSON = "key_last_podcast_show_json"
        private const val KEY_DISPLAY_MODE = "key_display_mode"
        private const val KEY_DOCK_CLOCK_SCALE = "key_dock_clock_scale"
        private const val KEY_DOCK_SHOW_SECONDS = "key_dock_show_seconds"
        private const val KEY_CHASSIS_BACK_ANIMATION_ENABLED = "key_chassis_back_animation_enabled"
        private const val KEY_BRICK_HIGH_SCORES_JSON = "key_brick_high_scores_json"
        private const val KEY_PURE_AUDIO_MODE = "key_pure_audio_mode"
        private const val KEY_RANDOM_HARDWARE_COLORS_ENABLED = "key_random_hardware_colors_enabled"
        private const val MAX_RECENTS = 20

        @Volatile
        private var instance: IpodPreferencesManager? = null

        fun getInstance(context: Context): IpodPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: IpodPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Modo Streaming Puro (Apenas Áudio): Quando ativado, desativa permanentemente a extração
     * e atualização de metadados ICY/ID3 de músicas/artistas do stream para eliminar sobrecarga e travamentos.
     */
    fun isPureAudioModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_PURE_AUDIO_MODE, false)
    }

    fun setPureAudioModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_AUDIO_MODE, enabled).apply()
    }

    fun isChassisBackAnimationEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHASSIS_BACK_ANIMATION_ENABLED, false)
    }

    fun setChassisBackAnimationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHASSIS_BACK_ANIMATION_ENABLED, enabled).apply()
    }

    // Media Type persistence (RADIO or PODCAST)
    fun saveLastMediaType(type: String) {
        prefs.edit().putString(KEY_LAST_MEDIA_TYPE, type).apply()
    }

    fun getLastMediaType(): String? {
        return prefs.getString(KEY_LAST_MEDIA_TYPE, null)
    }

    // Display mode persistence
    fun saveDisplayMode(mode: String) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode).apply()
    }

    fun getDisplayMode(): String {
        return prefs.getString(KEY_DISPLAY_MODE, "IPOD_CLASSIC") ?: "IPOD_CLASSIC"
    }

    // Last station persistence
    fun saveLastPlayedStation(station: RadioStation) {
        try {
            saveLastMediaType("RADIO")
            val json = JSONObject().apply {
                put("id", station.id)
                put("name", station.name)
                put("streamUrl", station.streamUrl)
                put("favicon", station.favicon)
                put("country", station.country)
                put("countryCode", station.countryCode)
                put("state", station.state)
                put("city", station.city)
                put("tags", station.tags)
                put("bitrate", station.bitrate)
                put("codec", station.codec)
                put("votes", station.votes)
            }
            prefs.edit().putString(KEY_LAST_STATION_JSON, json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getLastPlayedStation(): RadioStation? {
        val jsonStr = prefs.getString(KEY_LAST_STATION_JSON, null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            RadioStation(
                id = obj.optString("id", ""),
                name = obj.optString("name", "Estação de Rádio"),
                streamUrl = obj.optString("streamUrl", ""),
                favicon = obj.optString("favicon", ""),
                country = obj.optString("country", "Mundial"),
                countryCode = obj.optString("countryCode", "XX"),
                state = obj.optString("state", ""),
                city = obj.optString("city", ""),
                tags = obj.optString("tags", ""),
                bitrate = obj.optInt("bitrate", 128),
                codec = obj.optString("codec", "MP3"),
                votes = obj.optInt("votes", 0)
            )
        } catch (_: Exception) {
            null
        }
    }

    // Last podcast persistence
    fun saveLastPlayedPodcast(episode: com.example.data.model.PodcastEpisode, show: com.example.data.model.PodcastShow?) {
        try {
            saveLastMediaType("PODCAST")
            val epJson = JSONObject().apply {
                put("id", episode.id)
                put("showId", episode.showId)
                put("showTitle", episode.showTitle)
                put("title", episode.title)
                put("description", episode.description)
                put("audioUrl", episode.audioUrl)
                put("durationMs", episode.durationMs)
                put("publishDate", episode.publishDate)
                put("artworkUrl", episode.artworkUrl)
            }
            val editor = prefs.edit().putString(KEY_LAST_PODCAST_EPISODE_JSON, epJson.toString())
            if (show != null) {
                val showJson = JSONObject().apply {
                    put("id", show.id)
                    put("title", show.title)
                    put("author", show.author)
                    put("description", show.description)
                    put("feedUrl", show.feedUrl)
                    put("artworkUrl", show.artworkUrl)
                    put("country", show.country)
                    put("category", show.category)
                }
                editor.putString(KEY_LAST_PODCAST_SHOW_JSON, showJson.toString())
            } else {
                editor.remove(KEY_LAST_PODCAST_SHOW_JSON)
            }
            editor.apply()
        } catch (_: Exception) {}
    }

    fun getLastPlayedPodcast(): Pair<com.example.data.model.PodcastEpisode, com.example.data.model.PodcastShow?>? {
        val epStr = prefs.getString(KEY_LAST_PODCAST_EPISODE_JSON, null) ?: return null
        return try {
            val obj = JSONObject(epStr)
            val ep = com.example.data.model.PodcastEpisode(
                id = obj.getString("id"),
                showId = obj.optString("showId", ""),
                showTitle = obj.optString("showTitle", ""),
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                audioUrl = obj.getString("audioUrl"),
                durationMs = obj.optLong("durationMs", 0L),
                publishDate = obj.optString("publishDate", ""),
                artworkUrl = obj.optString("artworkUrl", "")
            )
            val showStr = prefs.getString(KEY_LAST_PODCAST_SHOW_JSON, null)
            val show = showStr?.let {
                val sObj = JSONObject(it)
                com.example.data.model.PodcastShow(
                    id = sObj.getString("id"),
                    title = sObj.getString("title"),
                    author = sObj.optString("author", ""),
                    description = sObj.optString("description", ""),
                    feedUrl = sObj.getString("feedUrl"),
                    artworkUrl = sObj.optString("artworkUrl", ""),
                    country = sObj.optString("country", "BR"),
                    category = sObj.optString("category", "Geral")
                )
            }
            ep to show
        } catch (_: Exception) {
            null
        }
    }

    // Recent stations history
    fun addRecentStation(station: RadioStation) {
        try {
            val currentList = getRecentStations().toMutableList()
            currentList.removeAll { it.id == station.id }
            currentList.add(0, station)
            val trimmed = currentList.take(MAX_RECENTS)

            val array = org.json.JSONArray()
            for (st in trimmed) {
                val obj = JSONObject().apply {
                    put("id", st.id)
                    put("name", st.name)
                    put("streamUrl", st.streamUrl)
                    put("favicon", st.favicon)
                    put("country", st.country)
                    put("countryCode", st.countryCode)
                    put("state", st.state)
                    put("city", st.city)
                    put("tags", st.tags)
                    put("bitrate", st.bitrate)
                    put("codec", st.codec)
                    put("votes", st.votes)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_RECENTS_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRecentStations(): List<RadioStation> {
        val jsonStr = prefs.getString(KEY_RECENTS_JSON, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<RadioStation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RadioStation(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", "Estação"),
                        streamUrl = obj.optString("streamUrl", ""),
                        favicon = obj.optString("favicon", ""),
                        country = obj.optString("country", "Mundial"),
                        countryCode = obj.optString("countryCode", "XX"),
                        state = obj.optString("state", ""),
                        city = obj.optString("city", ""),
                        tags = obj.optString("tags", ""),
                        bitrate = obj.optInt("bitrate", 128),
                        codec = obj.optString("codec", "MP3"),
                        votes = obj.optInt("votes", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearRecentStations() {
        prefs.edit().remove(KEY_RECENTS_JSON).apply()
    }

    // Custom Radio Stations (Minhas Rádios)
    fun addCustomStation(station: RadioStation) {
        try {
            val list = getCustomStations().toMutableList()
            list.removeAll { it.id == station.id || it.streamUrl.equals(station.streamUrl, ignoreCase = true) }
            list.add(0, station)
            val array = org.json.JSONArray()
            for (st in list) {
                val obj = JSONObject().apply {
                    put("id", st.id)
                    put("name", st.name)
                    put("streamUrl", st.streamUrl)
                    put("favicon", st.favicon)
                    put("country", st.country)
                    put("countryCode", st.countryCode)
                    put("state", st.state)
                    put("city", st.city)
                    put("tags", st.tags)
                    put("bitrate", st.bitrate)
                    put("codec", st.codec)
                    put("votes", st.votes)
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_stations_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun removeCustomStation(stationId: String) {
        try {
            val list = getCustomStations().toMutableList()
            list.removeAll { it.id == stationId }
            val array = org.json.JSONArray()
            for (st in list) {
                val obj = JSONObject().apply {
                    put("id", st.id)
                    put("name", st.name)
                    put("streamUrl", st.streamUrl)
                    put("favicon", st.favicon)
                    put("country", st.country)
                    put("countryCode", st.countryCode)
                    put("state", st.state)
                    put("city", st.city)
                    put("tags", st.tags)
                    put("bitrate", st.bitrate)
                    put("codec", st.codec)
                    put("votes", st.votes)
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_stations_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getCustomStations(): List<RadioStation> {
        val jsonStr = prefs.getString("custom_stations_json", null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<RadioStation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RadioStation(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", "Rádio Personalizada"),
                        streamUrl = obj.optString("streamUrl", ""),
                        favicon = obj.optString("favicon", ""),
                        country = obj.optString("country", "Personalizada"),
                        countryCode = obj.optString("countryCode", "BR"),
                        state = obj.optString("state", ""),
                        city = obj.optString("city", "Custom"),
                        tags = obj.optString("tags", "personalizada"),
                        bitrate = obj.optInt("bitrate", 128),
                        codec = obj.optString("codec", "MP3"),
                        votes = obj.optInt("votes", 999)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Auto-play on startup
    var isAutoPlayOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_ON_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PLAY_ON_LAUNCH, value).apply()

    // Chassis theme
    var chassisTheme: IpodChassisTheme
        get() {
            val name = prefs.getString(KEY_CHASSIS_THEME, IpodChassisTheme.CLASSIC_SILVER.name)
            return try {
                IpodChassisTheme.valueOf(name ?: IpodChassisTheme.CLASSIC_SILVER.name)
            } catch (_: Exception) {
                IpodChassisTheme.CLASSIC_SILVER
            }
        }
        set(value) {
            prefs.edit()
                .putString(KEY_CHASSIS_THEME, value.name)
                .putLong(KEY_CUSTOM_BODY_COLOR, value.bodyColor)
                .apply()
        }

    var customBodyColor: Long
        get() = prefs.getLong(KEY_CUSTOM_BODY_COLOR, chassisTheme.bodyColor)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_BODY_COLOR, value).apply()

    // LCD Backlight
    var lcdBacklight: LcdBacklight
        get() {
            val name = prefs.getString(KEY_LCD_BACKLIGHT, LcdBacklight.RETRO_IPOD_LCD.name)
            return try {
                LcdBacklight.valueOf(name ?: LcdBacklight.RETRO_IPOD_LCD.name)
            } catch (_: Exception) {
                LcdBacklight.RETRO_IPOD_LCD
            }
        }
        set(value) = prefs.edit().putString(KEY_LCD_BACKLIGHT, value.name).apply()

    // Click Wheel Customization
    var wheelPreset: IpodWheelPreset
        get() {
            val name = prefs.getString(KEY_WHEEL_PRESET, IpodWheelPreset.CLASSIC_GREY.name)
            return try {
                IpodWheelPreset.valueOf(name ?: IpodWheelPreset.CLASSIC_GREY.name)
            } catch (_: Exception) {
                IpodWheelPreset.CLASSIC_GREY
            }
        }
        set(value) = prefs.edit().putString(KEY_WHEEL_PRESET, value.name).apply()

    var customWheelColor: Long
        get() = prefs.getLong(KEY_CUSTOM_WHEEL_COLOR, IpodWheelPreset.CLASSIC_GREY.wheelColor)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_WHEEL_COLOR, value).apply()

    var customWheelTextColor: Long
        get() = prefs.getLong(KEY_CUSTOM_WHEEL_TEXT_COLOR, IpodWheelPreset.CLASSIC_GREY.textColor)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_WHEEL_TEXT_COLOR, value).apply()

    var customCenterButtonColor: Long
        get() = prefs.getLong(KEY_CUSTOM_CENTER_BUTTON_COLOR, IpodWheelPreset.CLASSIC_GREY.centerButtonColor)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_CENTER_BUTTON_COLOR, value).apply()

    // Modo Aleatório de Cores do Hardware (Carcaça, Click Wheel e Botão Central)
    var randomHardwareColorsEnabled: Boolean
        get() = prefs.getBoolean(KEY_RANDOM_HARDWARE_COLORS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RANDOM_HARDWARE_COLORS_ENABLED, value).apply()

    // Typography customization
    var fontType: IpodFontType
        get() {
            val name = prefs.getString(KEY_FONT_TYPE, IpodFontType.MONOSPACE.name)
            return try {
                IpodFontType.valueOf(name ?: IpodFontType.MONOSPACE.name)
            } catch (_: Exception) {
                IpodFontType.MONOSPACE
            }
        }
        set(value) = prefs.edit().putString(KEY_FONT_TYPE, value.name).apply()

    var fontSizeScale: IpodFontSizeScale
        get() {
            val name = prefs.getString(KEY_FONT_SIZE_SCALE, IpodFontSizeScale.SCALE_100.name)
            return try {
                when (name) {
                    "COMPACT", "NORMAL", "SCALE_100" -> IpodFontSizeScale.SCALE_100
                    "LARGE", "EXTRA_LARGE", "SCALE_150" -> IpodFontSizeScale.SCALE_150
                    "SCALE_200" -> IpodFontSizeScale.SCALE_200
                    "SCALE_250" -> IpodFontSizeScale.SCALE_250
                    else -> IpodFontSizeScale.valueOf(name ?: IpodFontSizeScale.SCALE_100.name)
                }
            } catch (_: Exception) {
                IpodFontSizeScale.SCALE_100
            }
        }
        set(value) = prefs.edit().putString(KEY_FONT_SIZE_SCALE, value.name).apply()

    var isFontBold: Boolean
        get() = prefs.getBoolean(KEY_FONT_BOLD, true) // Default bold as requested
        set(value) = prefs.edit().putBoolean(KEY_FONT_BOLD, value).apply()

    // Sound and Haptics
    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    var volumeLevel: Float
        get() = prefs.getFloat(KEY_VOLUME, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_VOLUME, value).apply()

    var dockClockScale: DockClockScale
        get() {
            val name = prefs.getString(KEY_DOCK_CLOCK_SCALE, DockClockScale.SCALE_100.name)
            return try {
                DockClockScale.valueOf(name ?: DockClockScale.SCALE_100.name)
            } catch (_: Exception) {
                DockClockScale.SCALE_100
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DOCK_CLOCK_SCALE, value.name).commit()
        }

    var dockShowSeconds: Boolean
        get() = prefs.getBoolean(KEY_DOCK_SHOW_SECONDS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DOCK_SHOW_SECONDS, value).commit()
        }

    // Velocidade de reprodução (separada para Podcasts e Mídias Locais MP3/Vídeo)
    var podcastPlaybackSpeed: Float
        get() = prefs.getFloat("key_podcast_playback_speed", 1.0f)
        set(value) = prefs.edit().putFloat("key_podcast_playback_speed", value).apply()

    var localMediaPlaybackSpeed: Float
        get() = prefs.getFloat("key_local_media_playback_speed", 1.0f)
        set(value) = prefs.edit().putFloat("key_local_media_playback_speed", value).apply()

    // YouTube Videos (Vídeos no YouTube)
    fun getYouTubeVideos(): List<com.example.data.model.YouTubeVideo> {
        val jsonStr = prefs.getString("custom_youtube_videos_json", null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.data.model.YouTubeVideo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.data.model.YouTubeVideo(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", "Vídeo"),
                        url = obj.optString("url", ""),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addYouTubeVideo(video: com.example.data.model.YouTubeVideo) {
        try {
            val list = getYouTubeVideos().toMutableList()
            list.removeAll { it.id == video.id }
            list.add(0, video)
            val array = org.json.JSONArray()
            for (v in list) {
                val obj = org.json.JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("url", v.url)
                    put("thumbnailUrl", v.thumbnailUrl)
                    put("addedAt", v.addedAt)
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_youtube_videos_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun removeYouTubeVideo(videoId: String) {
        try {
            val list = getYouTubeVideos().toMutableList()
            list.removeAll { it.id == videoId }
            val array = org.json.JSONArray()
            for (v in list) {
                val obj = org.json.JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("url", v.url)
                    put("thumbnailUrl", v.thumbnailUrl)
                    put("addedAt", v.addedAt)
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_youtube_videos_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    // ==========================================
    // Jogo Brick - Memória Top 10 High Scores (Arcade)
    // ==========================================

    fun getBrickHighScores(): List<BrickHighScore> {
        val jsonStr = prefs.getString(KEY_BRICK_HIGH_SCORES_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            return getDefaultBrickHighScores()
        }
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<BrickHighScore>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BrickHighScore(
                        initials = obj.optString("initials", "AAA"),
                        score = obj.optInt("score", 0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            if (list.isEmpty()) getDefaultBrickHighScores() else list.sortedByDescending { it.score }.take(10)
        } catch (_: Exception) {
            getDefaultBrickHighScores()
        }
    }

    fun getDefaultBrickHighScores(): List<BrickHighScore> {
        return listOf(
            BrickHighScore("IPD", 1500),
            BrickHighScore("MAC", 1200),
            BrickHighScore("RET", 1000),
            BrickHighScore("BRK", 850),
            BrickHighScore("APL", 700),
            BrickHighScore("GEO", 600),
            BrickHighScore("CLW", 500),
            BrickHighScore("RAD", 400),
            BrickHighScore("LCD", 300),
            BrickHighScore("MIN", 200)
        )
    }

    fun saveBrickHighScore(initials: String, score: Int): List<BrickHighScore> {
        val cleanInitials = initials.trim().uppercase().take(3).ifBlank { "AAA" }
        val current = getBrickHighScores().toMutableList()
        current.add(BrickHighScore(cleanInitials, score, System.currentTimeMillis()))
        val updated = current.sortedByDescending { it.score }.take(10)
        try {
            val array = org.json.JSONArray()
            for (item in updated) {
                val obj = org.json.JSONObject().apply {
                    put("initials", item.initials)
                    put("score", item.score)
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_BRICK_HIGH_SCORES_JSON, array.toString()).apply()
        } catch (_: Exception) {}
        return updated
    }
}

data class BrickHighScore(
    val initials: String,
    val score: Int,
    val timestamp: Long = System.currentTimeMillis()
)

