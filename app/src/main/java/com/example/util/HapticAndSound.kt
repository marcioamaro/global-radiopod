package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

import android.util.Log
import android.view.SoundEffectConstants

class IpodSoundAndHaptics(private val context: Context) {

    private val prefs = context.getSharedPreferences("radiopod_haptics", Context.MODE_PRIVATE)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean("sound_enabled", true)
        set(value) {
            prefs.edit().putBoolean("sound_enabled", value).commit()
        }

    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) {
            prefs.edit().putBoolean("haptics_enabled", value).commit()
        }

    fun playClickSound(view: View? = null) {
        if (!isSoundEnabled) return
        try {
            view?.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (e: Exception) {
            Log.d("IpodSoundAndHaptics", "Sound effect unavailable: ${e.message}")
        }
    }

    fun performClickHaptic(view: View? = null) {
        if (!isHapticsEnabled) return
        try {
            if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10L)
            }
        } catch (e: Exception) {
            Log.d("IpodSoundAndHaptics", "Click haptic failed: ${e.message}")
        }
    }

    fun performHeavyHaptic(view: View? = null) {
        if (!isHapticsEnabled) return
        try {
            if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30L)
            }
        } catch (e: Exception) {
            Log.d("IpodSoundAndHaptics", "Heavy haptic failed: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: IpodSoundAndHaptics? = null

        fun getInstance(context: Context): IpodSoundAndHaptics {
            return INSTANCE ?: synchronized(this) {
                val instance = IpodSoundAndHaptics(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
