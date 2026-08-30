package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.model.RadioStation
import com.example.data.repository.CuratedData
import com.example.player.RadioPlayerManager
import com.example.service.RadioMediaService

class RadioAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_clock_full_screen_channel"
        const val ALARM_NOTIFICATION_ID = 90211
    }

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

        // 1. Aciona imediatamente o player oficial com todas as funções existentes (ExoPlayer, ICY, RDS, multi-stream, audio focus)
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

        // 2. Prepara o intent para abrir a tela de alarme
        val alarmIntent = Intent(context, RadioAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtras(intent)
        }

        // 3. Dispara Notificação com Full-Screen Intent para acordar a tela e sobrepor a tela de bloqueio
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Despertador MediaPod",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Canal de notificação em tela cheia do despertador"
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                ALARM_NOTIFICATION_ID,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pear_logo)
                .setContentTitle("DESPERTADOR: $stationName")
                .setContentText("A rádio está tocando. Toque para soneca ou desligar.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            try {
                notificationManager.notify(ALARM_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w("RadioAlarmReceiver", "Falha ao enviar notificação de tela cheia", e)
            }
        }

        // 4. Também inicia a activity diretamente
        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            Log.e("RadioAlarmReceiver", "Erro ao iniciar RadioAlarmActivity diretamente", e)
        } finally {
            wakeLock?.let {
                if (it.isHeld) {
                    try { it.release() } catch (_: Exception) {}
                }
            }
        }
    }
}
