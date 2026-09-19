package com.example.audio

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VolumeManagerTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val half = (max * 0.5f).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, half, 0)
    }

    @Test
    fun `local volume should reflect system volume`() {
        val volumeManager = VolumeManager(context)
        
        // Simula volume do sistema em 50%
        assertEquals(0.5f, volumeManager.localVolume.value, 0.05f)
    }

    @Test
    fun `setting local volume should update system`() {
        val volumeManager = VolumeManager(context)
        
        volumeManager.setSystemVolume(0.7f)
        
        assertEquals(0.7f, volumeManager.getSystemVolume(), 0.05f)
    }

    @Test
    fun `switching to cast should change active route`() {
        val volumeManager = VolumeManager(context)
        
        volumeManager.switchToCast(0.8f)
        
        assertEquals(VolumeManager.AudioRoute.CAST, volumeManager.activeRoute.value)
        assertEquals(0.8f, volumeManager.castVolume.value)
    }

    @Test
    fun `switching back to local should restore local volume`() {
        val volumeManager = VolumeManager(context)
        val originalLocalVolume = volumeManager.localVolume.value
        
        volumeManager.switchToCast(0.8f)
        volumeManager.switchToLocal()
        
        assertEquals(VolumeManager.AudioRoute.LOCAL, volumeManager.activeRoute.value)
        assertEquals(originalLocalVolume, volumeManager.localVolume.value, 0.01f)
    }

    @Test
    fun `getActiveVolume should return cast volume when on cast`() {
        val volumeManager = VolumeManager(context)
        
        volumeManager.switchToCast(0.6f)
        
        assertEquals(0.6f, volumeManager.getActiveVolume(), 0.01f)
    }

    @Test
    fun `getActiveVolume should return local volume when on local`() {
        val volumeManager = VolumeManager(context)
        val localVolume = volumeManager.localVolume.value
        
        assertEquals(localVolume, volumeManager.getActiveVolume(), 0.01f)
    }
}
