package com.example.player

import android.content.Context
import com.example.data.repository.libraryKey
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.LocalVideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class LocalVideoPlayerManager private constructor(private val context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentVideo = MutableStateFlow<LocalVideoTrack?>(null)
    val currentVideo: StateFlow<LocalVideoTrack?> = _currentVideo.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val ipodPrefs = com.example.data.preferences.IpodPreferencesManager.getInstance(context)
    private val _playbackSpeed = MutableStateFlow(ipodPrefs.localMediaPlaybackSpeed)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    var onVideoEnded: (() -> Unit)? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    stopProgressTracker()
                    onVideoEnded?.invoke()
                }
            }
        })
    }

    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = safeSpeed
        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(safeSpeed, 1.0f)
        ipodPrefs.localMediaPlaybackSpeed = safeSpeed
    }

    fun seekToPosition(posMs: Long) {
        val target = posMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        _currentPositionMs.value = target
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = exoPlayer.currentPosition.coerceAtLeast(0L)
                _currentVideo.value?.let { video ->
                    com.example.data.repository.MediaLibraryRepository.getInstance(context)
                        .savePosition(video.libraryKey(), _currentPositionMs.value, video.durationMs)
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playVideo(video: LocalVideoTrack) {
        com.example.data.repository.MediaLibraryRepository.getInstance(context).recordRecent(video.libraryKey())
        // Se já for o mesmo vídeo carregado, mantenha a posição e retome se necessário
        if (_currentVideo.value?.id == video.id && exoPlayer.playbackState != Player.STATE_IDLE) {
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
            return
        }

        // Pausar rádio ou MP3 em execução
        RadioPlayerManager.getInstance(context).pause()

        _currentVideo.value = video
        _durationMs.value = video.durationMs
        _currentPositionMs.value = 0L

        val mediaItem = MediaItem.fromUri(video.contentUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.seekTo(com.example.data.repository.MediaLibraryRepository.getInstance(context).position(video.libraryKey()))
        exoPlayer.prepare()
        val speed = ipodPrefs.localMediaPlaybackSpeed
        _playbackSpeed.value = speed
        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed, 1.0f)
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            RadioPlayerManager.getInstance(context).pause()
            exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        RadioPlayerManager.getInstance(context).pause()
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, _durationMs.value))
        _currentPositionMs.value = exoPlayer.currentPosition
    }

    fun forward10s() {
        seekTo(exoPlayer.currentPosition + 10000L)
    }

    fun rewind10s() {
        seekTo(exoPlayer.currentPosition - 10000L)
    }

    fun stop() {
        exoPlayer.stop()
        _isPlaying.value = false
        _currentVideo.value = null
        stopProgressTracker()
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.release()
    }

    companion object {
        @Volatile
        private var instance: LocalVideoPlayerManager? = null

        fun getInstance(context: Context): LocalVideoPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: LocalVideoPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
