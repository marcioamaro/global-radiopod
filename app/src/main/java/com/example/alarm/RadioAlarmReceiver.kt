package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("RadioAlarmReceiver", "Disparo do Alarme recebido!")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "radiopod:AlarmWakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 minutos de garantia

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
