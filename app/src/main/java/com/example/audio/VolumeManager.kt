package com.example.audio

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VolumeManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _localVolume = MutableStateFlow(getSystemVolume())
    val localVolume: StateFlow<Float> = _localVolume.asStateFlow()

    private val _castVolume = MutableStateFlow<Float?>(null)
    val castVolume: StateFlow<Float?> = _castVolume.asStateFlow()

    private val _activeRoute = MutableStateFlow(AudioRoute.LOCAL)
    val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

    private val _activeVolume = MutableStateFlow(getSystemVolume())
    val activeVolume: StateFlow<Float> = _activeVolume.asStateFlow()

    enum class AudioRoute { LOCAL, CAST }

    var onCastVolumeChange: ((Float) -> Unit)? = null

    fun getSystemVolume(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max == 0) return 0f
        return (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    fun setSystemVolume(volume: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (volume * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val updated = getSystemVolume()
        _localVolume.value = updated
        if (_activeRoute.value == AudioRoute.LOCAL) {
            _activeVolume.value = updated
        }
    }

    val castVolumeController = CastVolumeController(
        onSendVolumeCommand = { vol -> onCastVolumeChange?.invoke(vol) }
    )

    fun switchToCast(castCurrentVolume: Float) {
        _activeRoute.value = AudioRoute.CAST
        castVolumeController.setInitialVolume(castCurrentVolume)
        _castVolume.value = castCurrentVolume
        _activeVolume.value = castCurrentVolume
    }

    fun switchToLocal() {
        _activeRoute.value = AudioRoute.LOCAL
        _castVolume.value = null
        val updated = getSystemVolume()
        _localVolume.value = updated
        _activeVolume.value = updated
    }

    fun updateCastVolume(volume: Float) {
        castVolumeController.onCastStatusVolumeReported(volume.toDouble())
        val updated = castVolumeController.castVolume.value
        _castVolume.value = updated
        if (_activeRoute.value == AudioRoute.CAST) {
            _activeVolume.value = updated
        }
    }

    fun updateCastVolumeFromStatus(volume: Double) {
        castVolumeController.onCastStatusVolumeReported(volume)
        val updated = castVolumeController.castVolume.value
        _castVolume.value = updated
        if (_activeRoute.value == AudioRoute.CAST) {
            _activeVolume.value = updated
        }
    }

    fun getActiveVolume(): Float {
        return if (_activeRoute.value == AudioRoute.CAST) {
            _castVolume.value ?: 0f
        } else {
            _localVolume.value
        }
    }

    fun setActiveVolume(volume: Float) {
        if (_activeRoute.value == AudioRoute.CAST) {
            castVolumeController.onUserVolumeChange(volume)
            val updated = castVolumeController.castVolume.value
            _castVolume.value = updated
            _activeVolume.value = updated
        } else {
            setSystemVolume(volume)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VolumeManager? = null

        fun getInstance(context: Context): VolumeManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VolumeManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
