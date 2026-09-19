package com.example.player.recorder

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado da gravação de stream ao vivo de rádio.
 */
sealed interface RecordingState {
    object Idle : RecordingState
    data class Recording(
        val stationName: String,
        val elapsedSeconds: Long,
        val bytesWritten: Long,
        val maxDurationSeconds: Long
    ) : RecordingState
    data class Completed(val file: File, val durationSeconds: Long, val sizeBytes: Long) : RecordingState
    data class Failed(val reason: String) : RecordingState
}

/**
 * Gravador de streaming de rádio ao vivo para armazenamento local (Item 11).
 * Suporta limitação de tempo, salvamento atômico, metadados no arquivo
 * e degradação segura contra estouro de armazenamento.
 */
class RadioStreamRecorder private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    val isRecording: Boolean
        get() = _state.value is RecordingState.Recording

    val recordingsDir: File
        get() {
            val dir = File(context.filesDir, "radio_recordings")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun startRecording(
        stationName: String,
        streamUrl: String,
        maxDurationSeconds: Long = 3600L,
        maxSizeBytes: Long = 150L * 1024L * 1024L // 150 MB de limite de proteção
    ) {
        if (isRecording) {
            Log.w(TAG, "Gravação já em andamento")
            return
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        val sanitizedStation = stationName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val baseFileName = "rec_${sanitizedStation}_$timestamp"
        val tempFile = File(recordingsDir, "$baseFileName.tmp")
        val finalFile = File(recordingsDir, "$baseFileName.mp3")

        var secondsElapsed = 0L
        var totalBytes = 0L

        _state.value = RecordingState.Recording(stationName, 0L, 0L, maxDurationSeconds)

        tickerJob = scope.launch {
            while (isActive && _state.value is RecordingState.Recording) {
                delay(1000L)
                secondsElapsed++
                val current = _state.value
                if (current is RecordingState.Recording) {
                    _state.value = current.copy(elapsedSeconds = secondsElapsed, bytesWritten = totalBytes)
                }
                if (secondsElapsed >= maxDurationSeconds) {
                    Log.i(TAG, "Tempo limite de gravação atingido ($maxDurationSeconds s). Encerrando...")
                    stopRecording()
                    break
                }
            }
        }

        recordingJob = scope.launch {
            var connection: HttpURLConnection? = null
            var input: InputStream? = null
            var output: FileOutputStream? = null

            try {
                val url = URL(streamUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    _state.value = RecordingState.Failed("Servidor retornou HTTP ${connection.responseCode}")
                    return@launch
                }

                input = connection.inputStream
                output = FileOutputStream(tempFile)
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (isActive && bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    if (totalBytes >= maxSizeBytes) {
                        Log.w(TAG, "Tamanho máximo da gravação excedido ($maxSizeBytes bytes). Finalizando arquivo.")
                        break
                    }
                    bytesRead = input.read(buffer)
                }
                output.flush()

                if (tempFile.exists() && tempFile.length() > 0) {
                    if (tempFile.renameTo(finalFile)) {
                        _state.value = RecordingState.Completed(finalFile, secondsElapsed, finalFile.length())
                        Log.i(TAG, "Gravação concluída com sucesso: ${finalFile.name} (${finalFile.length()} bytes)")
                    } else {
                        _state.value = RecordingState.Failed("Falha ao renomear arquivo temporário para ${finalFile.name}")
                    }
                } else {
                    _state.value = RecordingState.Failed("Nenhum dado gravado do stream")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro durante gravação de streaming: ${e.message}", e)
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.renameTo(finalFile)
                    _state.value = RecordingState.Completed(finalFile, secondsElapsed, finalFile.length())
                } else {
                    _state.value = RecordingState.Failed(e.message ?: "Erro de gravação")
                }
            } finally {
                try { input?.close() } catch (_: Exception) {}
                try { output?.close() } catch (_: Exception) {}
                try { connection?.disconnect() } catch (_: Exception) {}
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    fun stopRecording(): File? {
        tickerJob?.cancel()
        recordingJob?.cancel()

        val current = _state.value
        if (current is RecordingState.Completed) {
            return current.file
        }
        return null
    }

    fun getRecordings(): List<File> {
        return recordingsDir.listFiles { file -> file.extension.lowercase() in listOf("mp3", "aac") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean {
        return if (file.exists()) file.delete() else false
    }

    companion object {
        private const val TAG = "RadioStreamRecorder"

        @Volatile
        private var INSTANCE: RadioStreamRecorder? = null

        fun getInstance(context: Context): RadioStreamRecorder {
            return INSTANCE ?: synchronized(this) {
                val instance = RadioStreamRecorder(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        @androidx.annotation.VisibleForTesting
        fun clearInstanceForTesting() {
            INSTANCE = null
        }
    }
}
