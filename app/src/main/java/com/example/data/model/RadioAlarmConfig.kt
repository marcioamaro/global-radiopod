package com.example.data.model

import java.util.Calendar

data class RadioAlarmConfig(
    val isEnabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = emptySet(), // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7). Vazio = tocar uma vez.
    val stationId: String = "",
    val stationName: String = "",
    val stationStreamUrl: String = "",
    val stationFavicon: String = "",
    val volume: Float = 0.85f,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10
) {
    val formattedTime: String
        get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    fun calculateNextAlarmTimeMs(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (daysOfWeek.isEmpty()) {
            // Tocar na próxima ocorrência (hoje se ainda não passou, ou amanhã)
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        } else {
            // Encontrar o dia da semana mais próximo correspondente
            for (dayOffset in 0..7) {
                val candidate = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                if (daysOfWeek.contains(dayOfWeek) && candidate.timeInMillis > now.timeInMillis) {
                    return candidate.timeInMillis
                }
            }
            // Fallback de segurança: amanhã
            target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis
        }
    }

    fun getRemainingTimeString(): String {
        val nextMs = calculateNextAlarmTimeMs()
        val diffMs = nextMs - System.currentTimeMillis()
        if (diffMs <= 0) return "Agora"

        val totalMinutes = diffMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L

        return when {
            hours > 0 && minutes > 0 -> "em ${hours}h ${minutes}m"
            hours > 0 -> "em ${hours}h"
            else -> "em ${minutes.coerceAtLeast(1)}m"
        }
    }
}
