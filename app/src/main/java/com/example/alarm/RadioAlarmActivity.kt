package com.example.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.IpodPreferencesManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RadioAlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        // Ligar a tela e exibir sobre a tela de bloqueio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val stationId = intent.getStringExtra("stationId") ?: ""
        val stationName = intent.getStringExtra("stationName") ?: "Rádio Favorita"
        val stationStreamUrl = intent.getStringExtra("stationStreamUrl") ?: ""
        val stationFavicon = intent.getStringExtra("stationFavicon") ?: ""
        val volume = intent.getFloatExtra("volume", 0.85f)
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val snoozeMinutes = intent.getIntExtra("snoozeMinutes", 10)

        // Iniciar vibração
        if (vibrate) {
            startVibration()
        }

        val curated = com.example.data.repository.CuratedData.CURATED_GLOBAL_STATIONS.firstOrNull {
            (stationId.isNotBlank() && it.id == stationId) ||
            (stationStreamUrl.isNotBlank() && it.streamUrl == stationStreamUrl)
        }

        val station = curated ?: com.example.data.model.RadioStation(
            id = stationId.ifBlank { "alarm_station" },
            name = stationName,
            streamUrl = stationStreamUrl,
            alternativeStreamUrls = emptyList(),
            favicon = stationFavicon,
            country = "Brasil",
            countryCode = "BR",
            codec = "MP3",
            bitrate = 128
        )

        // Iniciar áudio: Aciona o player de rádio oficial usando toda a infraestrutura existente
        startAlarmAudio(station, volume)

        setContent {
            AlarmScreen(
                stationName = station.name,
                onSnooze = {
                    snoozeAlarm(snoozeMinutes)
                },
                onDismiss = {
                    dismissAlarm()
                }
            )
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private var fallbackWatchdogJob: Job? = null

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun startAlarmAudio(station: com.example.data.model.RadioStation, volume: Float) {
        val playerManager = com.example.player.RadioPlayerManager.getInstance(applicationContext)

        // Aplica o volume configurado no alarme
        playerManager.setVolumeLevel(volume)

        // Se offline ou sem URL de stream, aciona fallback sonoro imediatamente
        if (!isNetworkAvailable() || station.streamUrl.isBlank()) {
            android.util.Log.w("RadioAlarmActivity", "Sem conexão ou stream vazio no alarme. Acionando fallback sonoro.")
            playFallbackAlarmSound()
            return
        }

        // Aciona o player oficial com todas as funções existentes (ExoPlayer, ICY, RDS, multi-stream, buffer, wake lock)
        if (playerManager.currentStation.value?.id != station.id || playerManager.playbackStatus.value != com.example.player.RadioPlaybackStatus.PLAYING) {
            playerManager.playStation(station)
        }

        // Inicia o RadioMediaService para manter o foreground e notificação
        try {
            val serviceIntent = Intent(applicationContext, com.example.service.RadioMediaService::class.java).apply {
                action = com.example.service.RadioMediaService.ACTION_PLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.w("RadioAlarmActivity", "Não foi possível iniciar RadioMediaService diretamente", e)
        }

        // Watchdog de segurança: se em 15 segundos o streaming não estiver tocando ou der erro de rede, toca o ringtone de backup
        fallbackWatchdogJob = activityScope.launch {
            delay(15_000L)
            if (playerManager.playbackStatus.value != com.example.player.RadioPlaybackStatus.PLAYING && !isFinishing) {
                android.util.Log.w("RadioAlarmActivity", "RadioPlayerManager não iniciou reprodução em 15s. Acionando fallback de segurança.")
                playFallbackAlarmSound()
            }
        }
    }

    private fun playFallbackAlarmSound() {
        if (fallbackRingtone != null && fallbackRingtone!!.isPlaying) return
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            fallbackRingtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            fallbackRingtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            fallbackRingtone?.play()
        } catch (_: Exception) {}
    }

    private fun snoozeAlarm(snoozeMinutes: Int) {
        stopAllAudioAndVibration()
        val prefs = IpodPreferencesManager.getInstance(this)
        val currentConfig = prefs.getRadioAlarmConfig()
        val snoozeMs = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        // Reagenda alarme único para a soneca
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        val intent = android.content.Intent(this, RadioAlarmReceiver::class.java).apply {
            action = RadioAlarmScheduler.ACTION_TRIGGER_ALARM
            putExtra("stationId", currentConfig.stationId)
            putExtra("stationName", currentConfig.stationName)
            putExtra("stationStreamUrl", currentConfig.stationStreamUrl)
            putExtra("stationFavicon", currentConfig.stationFavicon)
            putExtra("volume", currentConfig.volume)
            putExtra("vibrate", currentConfig.vibrate)
            putExtra("snoozeMinutes", snoozeMinutes)
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this,
            90210,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, snoozeMs, pi)
        } else {
            alarmManager?.setExact(android.app.AlarmManager.RTC_WAKEUP, snoozeMs, pi)
        }

        finish()
    }

    private fun dismissAlarm() {
        stopAllAudioAndVibration()
        val prefs = IpodPreferencesManager.getInstance(this)
        val config = prefs.getRadioAlarmConfig()

        if (config.daysOfWeek.isNotEmpty()) {
            // Se repete em dias específicos, agenda a próxima ocorrência
            RadioAlarmScheduler.scheduleAlarm(this, config)
        } else {
            // Se era alarme de disparo único, desativa
            val updated = config.copy(isEnabled = false)
            prefs.saveRadioAlarmConfig(updated)
            RadioAlarmScheduler.cancelAlarm(this)
        }

        finish()
    }

    private fun stopAllAudioAndVibration() {
        fallbackWatchdogJob?.cancel()
        fallbackWatchdogJob = null

        try {
            val playerManager = com.example.player.RadioPlayerManager.getInstance(applicationContext)
            playerManager.pause()
        } catch (_: Exception) {}

        try {
            fallbackRingtone?.stop()
            fallbackRingtone = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllAudioAndVibration()
        activityScope.cancel()
    }
}

@Composable
fun AlarmScreen(
    stationName: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    var currentDate by remember {
        mutableStateOf(SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date()))
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            currentDate = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date())
            delay(1000L)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top: Alarme Icon & Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color(0xFFDC2626).copy(alpha = 0.2f))
                        .border(2.dp, Color(0xFFDC2626), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarme Tocando",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DESPERTADOR MEDIAPOD",
                    color = Color(0xFFDC2626),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = currentDate.replaceFirstChar { it.uppercase() },
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Center: Big Time & Radio Station
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "📻 $stationName",
                        color = Color(0xFFE2E8F0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom: Action Buttons (Soneca & Desligar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Botão Soneca
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.5.dp, Color(0xFF475569), RoundedCornerShape(16.dp))
                        .clickable(onClick = onSnooze),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = "Soneca",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SONECA (+10 MIN)",
                            color = Color(0xFFE2E8F0),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Botão Desligar Alarme
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFDC2626), Color(0xFFB91C1C))
                            )
                        )
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AlarmOff,
                            contentDescription = "Desligar Alarme",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DESLIGAR ALARME",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
