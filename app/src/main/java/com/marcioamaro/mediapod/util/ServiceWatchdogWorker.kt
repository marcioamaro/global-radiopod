package com.marcioamaro.mediapod.util

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.marcioamaro.mediapod.data.preferences.PlaybackStateDataStore
import com.marcioamaro.mediapod.player.RadioPlaybackStatus
import com.marcioamaro.mediapod.player.RadioPlayerManager
import com.marcioamaro.mediapod.service.RadioMediaService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * WorkManager Watchdog para o [RadioMediaService].
 *
 * O sistema Android pode matar o ForegroundService em cenários de pressão de memória,
 * ou fabricantes problemáticos (Samsung/Xiaomi/Huawei) podem bloquear o restart automático.
 *
 * Este Worker:
 * 1. Executa a cada [INTERVAL_MINUTES] minutos em background
 * 2. Verifica se havia reprodução ativa (via [PlaybackStateDataStore])
 * 3. Se houver stream ativo mas o serviço não estiver rodando → reinicia silenciosamente
 * 4. Em caso de falha, registra e não propaga exceções (Worker é tolerante a falhas)
 *
 * ## Garantias
 * - WorkManager garante execução mesmo após reboot (com REQUIRES_NETWORK constraint)
 * - Não consome bateria significativa: executa em intervalos amplos (15min)
 * - Idempotente: não faz nada se o serviço já estiver rodando normalmente
 */
class ServiceWatchdogWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val WORK_NAME = "radiopod_service_watchdog"
        private const val INTERVAL_MINUTES = 15L

        /**
         * Agenda o watchdog como um PeriodicWorkRequest singleton.
         * Seguro chamar múltiplas vezes — usa [ExistingPeriodicWorkPolicy.KEEP].
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                    INTERVAL_MINUTES, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Não substitui se já agendado
                    request
                )

                android.util.Log.d("ServiceWatchdog", "Watchdog agendado a cada ${INTERVAL_MINUTES}min")
            } catch (e: IllegalStateException) {
                // Em ambiente de teste (Robolectric/Unit) WorkManager pode não estar inicializado
                android.util.Log.w("ServiceWatchdog", "WorkManager não inicializado neste ambiente: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.w("ServiceWatchdog", "Falha ao agendar WorkManager: ${e.message}")
            }
        }

        /**
         * Cancela o watchdog (ex: ao desinstalar, limpar dados, ou logout).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }

    override fun doWork(): Result {
        return try {
            checkAndRestorePlayback()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ServiceWatchdog", "Watchdog encontrou erro (não-fatal): ${e.message}")
            Result.retry() // WorkManager vai tentar novamente no próximo ciclo
        }
    }

    private fun checkAndRestorePlayback() {
        // 1. Verifica se havia estado de playback persistido
        val dataStore = PlaybackStateDataStore.getInstance(context)
        val lastMediaType = runBlocking { dataStore.lastMediaType.firstOrNull() }
        val lastUrl = runBlocking { dataStore.lastStationUrl.firstOrNull() }
        val lastId = runBlocking { dataStore.lastStationId.firstOrNull() }

        if (lastUrl.isNullOrBlank() || lastId.isNullOrBlank()) {
            android.util.Log.d("ServiceWatchdog", "Nenhum estado de playback salvo. Nada a restaurar.")
            return
        }

        // 2. Verifica o estado atual do PlayerManager (se o processo ainda está vivo)
        val playerManager = try {
            RadioPlayerManager.getInstance(context)
        } catch (e: Exception) {
            android.util.Log.w("ServiceWatchdog", "PlayerManager inacessível: ${e.message}")
            null
        }

        val currentStatus = playerManager?.playbackStatus?.value
        android.util.Log.d("ServiceWatchdog", "Estado atual do playback: $currentStatus | Tipo: $lastMediaType")

        // 3. Se o player está em PLAYING ou BUFFERING, tudo está bem
        if (currentStatus == RadioPlaybackStatus.PLAYING ||
            currentStatus == RadioPlaybackStatus.BUFFERING) {
            android.util.Log.d("ServiceWatchdog", "Playback ativo detectado. Watchdog OK.")
            return
        }

        // 4. Se estava pausado pelo usuário, não interfere
        // (RadioPlayerManager.userInitiatedPause não é acessível diretamente aqui —
        // usamos o MediaType como heurística: se há RADIO salvo e status é IDLE/ERROR,
        // provavelmente o serviço morreu inesperadamente)
        if (currentStatus == RadioPlaybackStatus.PAUSED) {
            android.util.Log.d("ServiceWatchdog", "Player pausado pelo usuário. Watchdog não interfere.")
            return
        }

        // 5. Serviço parece morto/inativo com estado pendente → reinicia o serviço
        android.util.Log.w(
            "ServiceWatchdog",
            "Serviço de mídia inativo com estado '$lastMediaType' salvo. Reiniciando RadioMediaService..."
        )
        restartMediaService()
    }

    private fun restartMediaService() {
        try {
            val intent = Intent(context, RadioMediaService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.util.Log.i("ServiceWatchdog", "RadioMediaService reiniciado com sucesso via watchdog.")
        } catch (e: Exception) {
            android.util.Log.e("ServiceWatchdog", "Falha ao reiniciar RadioMediaService: ${e.message}")
        }
    }
}
