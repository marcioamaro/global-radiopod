package com.marcioamaro.mediapod.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.marcioamaro.mediapod.MainActivity
import com.marcioamaro.mediapod.player.LocalVideoPlayerManager
import com.marcioamaro.mediapod.player.RadioPlayerManager
import com.marcioamaro.mediapod.service.RadioMediaService
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppRestartHelper {

    fun restartApp(context: Context) {
        try {
            // 1. Interromper áudio e vídeo ativos imediatamente
            RadioPlayerManager.getInstance(context).stop()
            LocalVideoPlayerManager.getInstance(context).release()
        } catch (_: Exception) {}

        try {
            // 2. Encerrar serviço de segundo plano e remover notificações
            val stopIntent = Intent(context, RadioMediaService::class.java).apply {
                action = RadioMediaService.ACTION_STOP
            }
            context.startService(stopIntent)
            context.stopService(Intent(context, RadioMediaService::class.java))
        } catch (_: Exception) {}

        try {
            // 3. Desconectar qualquer sessão ativa de Google Cast
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.endCurrentSession(true)
        } catch (_: Exception) {}

        // 4. Iniciar nova instância limpa da MainActivity desvinculada
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(restartIntent)

        // 5. Finalizar a Activity atual e liberar o processo de forma limpa
        // CORREÇÃO P3 (auditoria item 12 — 24/09/2026): Handler legado substituído por coroutine.
        (context as? Activity)?.finishAffinity()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            kotlinx.coroutines.delay(250L)
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(0)
        }
    }

    fun exitApp(context: Context) {
        try {
            RadioPlayerManager.getInstance(context).stop()
            LocalVideoPlayerManager.getInstance(context).release()
        } catch (_: Exception) {}

        try {
            val stopIntent = Intent(context, RadioMediaService::class.java).apply {
                action = RadioMediaService.ACTION_STOP
            }
            context.startService(stopIntent)
            context.stopService(Intent(context, RadioMediaService::class.java))
        } catch (_: Exception) {}

        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.endCurrentSession(true)
        } catch (_: Exception) {}

        (context as? Activity)?.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
