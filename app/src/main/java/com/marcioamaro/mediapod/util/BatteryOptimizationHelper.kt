package com.marcioamaro.mediapod.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun checkAndRequest(context: Context, activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                showBatteryDialog(context, activity)
            } else if (isXiaomiDevice()) {
                checkXiaomiBackgroundRestriction(context, activity)
            }
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }
        return true
    }

    fun requestDisableBatteryOptimization(context: Context) {
        if (context is Activity) {
            checkAndRequest(context, context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun showBatteryDialog(context: Context, activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Garantir Reprodução Contínua")
            .setMessage("Para evitar que a rádio pare de tocar quando você sair do app ou desligar a tela, precisamos que você desative a 'Otimização de Bateria' para este aplicativo. Isso garante que o serviço de áudio permaneça ativo em segundo plano.")
            .setPositiveButton("Configurar Agora") { _, _ ->
                requestIgnoreBatteryOptimizations(activity)
            }
            .setNegativeButton("Entendi", null)
            .show()
    }

    private fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
                openAppDetailsSettings(activity)
            }
        }
    }

    private fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ||
                brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")
    }

    private fun checkXiaomiBackgroundRestriction(context: Context, activity: Activity) {
        val prefs = activity.getSharedPreferences("xiaomi_opt_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("xiaomi_checked", false)) return

        try {
            val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", context.packageName)
            }
            val canResolve = miuiIntent.resolveActivity(context.packageManager) != null

            AlertDialog.Builder(activity)
                .setTitle("Configuração Xiaomi (MIUI / HyperOS)")
                .setMessage("Dispositivos Xiaomi possuem uma configuração extra que pausa apps em segundo plano. Por favor, vá em 'Permissões' e defina 'Executar em segundo plano' como 'Sem Restrições' ou desative 'Pausar atividades'.")
                .setPositiveButton("Abrir Configurações") { _, _ ->
                    prefs.edit().putBoolean("xiaomi_checked", true).apply()
                    try {
                        if (canResolve) {
                            context.startActivity(miuiIntent)
                        } else {
                            openAppDetailsSettings(activity)
                        }
                    } catch (_: Exception) {
                        openAppDetailsSettings(activity)
                    }
                }
                .setNegativeButton("Agora Não") { _, _ ->
                    prefs.edit().putBoolean("xiaomi_checked", true).apply()
                }
                .show()
        } catch (_: Exception) {
            openAppDetailsSettings(activity)
        }
    }

    fun openAppDetailsSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {}
    }
}
