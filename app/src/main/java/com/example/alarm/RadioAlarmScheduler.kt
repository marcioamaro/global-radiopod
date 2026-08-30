package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.RadioAlarmConfig
import com.example.data.preferences.IpodPreferencesManager

object RadioAlarmScheduler {
    private const val TAG = "RadioAlarmScheduler"
    const val ACTION_TRIGGER_ALARM = "com.example.action.TRIGGER_ALARM"
    private const val ALARM_REQUEST_CODE = 90210

    fun scheduleAlarm(context: Context, config: RadioAlarmConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (!config.isEnabled) {
            cancelAlarm(context)
            return
        }

        val triggerTimeMs = config.calculateNextAlarmTimeMs()
        Log.i(TAG, "Agendando despertador para: $triggerTimeMs (${config.formattedTime})")

        val intent = Intent(context, RadioAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra("stationName", config.stationName)
            putExtra("stationStreamUrl", config.stationStreamUrl)
            putExtra("volume", config.volume)
            putExtra("vibrate", config.vibrate)
            putExtra("snoozeMinutes", config.snoozeMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val showIntent = Intent(context, RadioAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE + 1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.i(TAG, "setAlarmClock definido com sucesso.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão de alarme exato não concedida, usando setExactAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Falha ao definir alarme", ex)
            }
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RadioAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        alarmManager.cancel(pendingIntent)
        Log.i(TAG, "Alarme cancelado com sucesso.")
    }
}
