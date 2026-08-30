package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.data.model.RadioStation
import com.example.data.repository.CuratedData
import com.example.player.RadioPlayerManager
import com.example.service.RadioMediaService

class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("RadioAlarmReceiver", "Disparo do Alarme recebido!")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "radiopod:AlarmWakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 minutos de garantia

        val stationId = intent.getStringExtra("stationId") ?: ""
        val stationName = intent.getStringExtra("stationName") ?: "Rádio Despertador"
        val stationStreamUrl = intent.getStringExtra("stationStreamUrl") ?: ""
        val stationFavicon = intent.getStringExtra("stationFavicon") ?: ""
        val volume = intent.getFloatExtra("volume", 0.85f)

        // Aciona imediatamente o player oficial com todas as funções existentes (ExoPlayer, ICY, RDS, multi-stream, audio focus)
        if (stationStreamUrl.isNotBlank()) {
            try {
                val curated = CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull {
                    (stationId.isNotBlank() && it.id == stationId) ||
                    (stationStreamUrl.isNotBlank() && it.streamUrl == stationStreamUrl)
                }
                val station = curated ?: RadioStation(
                    id = stationId.ifBlank { "alarm_station" },
                    name = stationName,
                    streamUrl = stationStreamUrl,
                    alternativeStreamUrls = emptyList(),
                    favicon = stationFavicon,
                    country = "Brasil",
                    countryCode = "BR",
                    codec = "MP3",
                    bitrate = 128
                )

                val playerManager = RadioPlayerManager.getInstance(context.applicationContext)
                playerManager.setVolumeLevel(volume)
                playerManager.playStation(station)

                val serviceIntent = Intent(context.applicationContext, RadioMediaService::class.java).apply {
                    action = RadioMediaService.ACTION_PLAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("RadioAlarmReceiver", "Erro ao acionar RadioPlayerManager no alarme", e)
            }
        }

        val alarmIntent = Intent(context, RadioAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtras(intent)
        }

        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            Log.e("RadioAlarmReceiver", "Erro ao iniciar RadioAlarmActivity", e)
        } finally {
            wakeLock?.let {
                if (it.isHeld) {
                    try { it.release() } catch (_: Exception) {}
                }
            }
        }
    }
}
