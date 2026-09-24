package com.marcioamaro.mediapod.util

import android.content.Context

class DataUsagePolicy(context: Context) {
    private val prefs = context.getSharedPreferences("ipod_radio_user_preferences", Context.MODE_PRIVATE)
    var remoteArtwork: Boolean
        get() = prefs.getBoolean("data_remote_artwork", true)
        set(value) { prefs.edit().putBoolean("data_remote_artwork", value).apply() }
    var preferredBitrate: Int
        get() = prefs.getInt("data_audio_bitrate", 0).takeIf { it in setOf(0, 64000, 128000) } ?: 0
        set(value) { require(value in setOf(0, 64000, 128000)); prefs.edit().putInt("data_audio_bitrate", value).apply() }
}
