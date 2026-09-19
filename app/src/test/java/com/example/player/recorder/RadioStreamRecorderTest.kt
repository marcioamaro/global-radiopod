package com.example.player.recorder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Testes unitários para o gravador de streams de rádio ao vivo (Item 11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RadioStreamRecorderTest {

    private lateinit var context: Context
    private lateinit var recorder: RadioStreamRecorder

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        RadioStreamRecorder.clearInstanceForTesting()
        recorder = RadioStreamRecorder.getInstance(context)

        // Limpa arquivos de testes anteriores
        recorder.recordingsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun testInitialStateAndDirectoryCreation() {
        assertTrue(recorder.recordingsDir.exists())
        assertEquals(RecordingState.Idle, recorder.state.value)
        assertFalse(recorder.isRecording)
        assertTrue(recorder.getRecordings().isEmpty())
    }

    @Test
    fun testRecordingsListingAndDelete() {
        val dummyMp3 = File(recorder.recordingsDir, "rec_Antena1_20260918.mp3")
        dummyMp3.writeText("fake mp3 stream content")
        assertTrue(dummyMp3.exists())

        val recordings = recorder.getRecordings()
        assertEquals(1, recordings.size)
        assertEquals(dummyMp3.name, recordings.first().name)

        val deleted = recorder.deleteRecording(dummyMp3)
        assertTrue(deleted)
        assertFalse(dummyMp3.exists())
        assertTrue(recorder.getRecordings().isEmpty())
    }

    @Test
    fun testStopWhenNotRecordingReturnsNull() {
        val file = recorder.stopRecording()
        assertEquals(null, file)
    }
}
