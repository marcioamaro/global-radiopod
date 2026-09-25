package com.marcioamaro.mediapod.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.marcioamaro.mediapod.R
import com.marcioamaro.mediapod.player.RadioPlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DeviceBatteryStatus(
    val percent: Int,
    val isCharging: Boolean
)

private fun Intent.toDeviceBatteryStatus(): DeviceBatteryStatus {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val percent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val batteryState = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    return DeviceBatteryStatus(
        percent = percent,
        isCharging = batteryState == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryState == BatteryManager.BATTERY_STATUS_FULL
    )
}

/** Observa o broadcast do Android para mostrar nível e carregamento reais. */
@Composable
fun BatteryStatusIndicator(
    tint: Color,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = false
) {
    val context = LocalContext.current
    var batteryStatus by remember { mutableStateOf<DeviceBatteryStatus?>(null) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                batteryStatus = intent.toDeviceBatteryStatus()
            }
        }
        val stickyIntent = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        stickyIntent?.let { batteryStatus = it.toDeviceBatteryStatus() }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val percent = batteryStatus?.percent ?: 0
    val isCharging = batteryStatus?.isCharging == true
    val description = if (isCharging) "Bateria $percent%, carregando" else "Bateria $percent%"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (showPercentage) {
            Text(
                text = "$percent%",
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint.copy(alpha = 0.25f))
                .padding(1.5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
            if (isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = description,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(0.5.dp))
                .background(tint.copy(alpha = 0.75f))
        )
    }
}

@Composable
fun IpodHeader(
    title: String,
    status: RadioPlaybackStatus,
    isHoldLocked: Boolean,
    sleepTimerMinutes: Int,
    backlightTextPrimary: Color,
    backlightHighlight: Color,
    backlightBg: Color = Color.Transparent,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    showAudioOutputIcon: Boolean = false,
    onAudioOutputClick: (() -> Unit)? = null,
    onNowPlayingClick: (() -> Unit)? = null,
    playbackSpeed: Float = 1.0f,
    nowPlayingTicker: String? = null,
    is24HourClock: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTime by remember(is24HourClock) {
        mutableStateOf(
            if (is24HourClock) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            } else {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            }
        )
    }

    LaunchedEffect(is24HourClock) {
        while (isActive) {
            val now = System.currentTimeMillis()
            val nextMinute = 60_000L - (now % 60_000L)
            delay(nextMinute.coerceAtLeast(1000L))
            currentTime = if (is24HourClock) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            } else {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "buffering_pulse")
    val bufferAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buffer_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(backlightHighlight.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Playback indicator icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (status) {
                    RadioPlaybackStatus.PLAYING -> {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Tocando",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    RadioPlaybackStatus.BUFFERING -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_hourglass_flat),
                            contentDescription = "Bufferizando",
                            tint = backlightTextPrimary.copy(alpha = bufferAlpha),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    RadioPlaybackStatus.PAUSED -> {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pausado",
                            tint = backlightTextPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    RadioPlaybackStatus.NO_INTERNET -> {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pausado (Sem Conexão)",
                            tint = backlightTextPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    else -> {
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                }

                if (isHoldLocked) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Hold Bloqueado",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Center: Screen Title or Animated Marquee Ticker
            if (!nowPlayingTicker.isNullOrBlank()) {
                Text(
                    text = nowPlayingTicker,
                    color = backlightTextPrimary,
                    fontSize = (12f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = 6.dp)
                        .then(
                            if (onNowPlayingClick != null) Modifier.clickable { onNowPlayingClick() }
                            else Modifier
                        )
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1200,
                            velocity = 35.dp
                        )
                )
            } else {
                Text(
                    text = title,
                    color = backlightTextPrimary,
                    fontSize = (13.5f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
                )
            }

            // Right: Audio Output Switcher, Battery & Sleep timer
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAudioOutputIcon || onAudioOutputClick != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .clickable(enabled = onAudioOutputClick != null) { onAudioOutputClick?.invoke() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_audio_output_classic),
                            contentDescription = "Saída de Áudio",
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                }

                if (sleepTimerMinutes != 0) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = if (sleepTimerMinutes == -1) "Timer ao Fim do Episódio" else "Timer $sleepTimerMinutes min",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (sleepTimerMinutes == -1) "Fim" else "${sleepTimerMinutes}m",
                        color = backlightTextPrimary,
                        fontSize = (10 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                    )
                }

                // Ícone de status de conexão OBRIGATORIAMENTE ao lado ESQUERDO do relógio LCD
                if (status == RadioPlaybackStatus.NO_INTERNET) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi_off_alert),
                        contentDescription = "Sem Conexão com a Internet",
                        tint = backlightTextPrimary,
                        modifier = Modifier
                            .size(15.dp)
                            .padding(end = 4.dp)
                    )
                }

                // Relógio LCD retrô em tempo real
                Text(
                    text = currentTime,
                    color = backlightTextPrimary,
                    fontSize = (10f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(end = 5.dp)
                )

                BatteryStatusIndicator(tint = backlightTextPrimary)
            }
        }
    }
}
