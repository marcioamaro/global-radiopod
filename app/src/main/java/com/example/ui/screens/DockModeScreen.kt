package com.example.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.data.model.LocalAudioTrack
import com.example.data.model.PodcastEpisode
import com.example.data.model.RadioStation
import com.example.player.RdsInfo
import com.example.player.RadioPlaybackStatus
import com.example.R
import com.example.ui.DockColorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockModeScreen(
    currentStation: RadioStation?,
    currentLocalAudio: LocalAudioTrack?,
    currentPodcastEpisode: PodcastEpisode?,
    rdsInfo: RdsInfo,
    playbackStatus: RadioPlaybackStatus,
    audioPositionMs: Long,
    audioDurationMs: Long,
    colorTheme: DockColorTheme,
    dockClockScale: com.example.data.preferences.DockClockScale = com.example.data.preferences.DockClockScale.SCALE_100,
    dockShowSeconds: Boolean = false,
    onCycleColorTheme: () -> Unit,
    onCycleClockScale: (() -> Unit)? = null,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onExitDockMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 1. Manter tela ligada (WAKELOCK) e ativar modo imersivo (ocultar Status Bar & Navigation Bar)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 2. Relógio em tempo real e data formatada por extenso
    var currentHourMinuteStr by remember { mutableStateOf("") }
    var currentSecondsStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val hourMinuteFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val secondsFormat = SimpleDateFormat("ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))

        while (isActive) {
            val now = Date()
            currentHourMinuteStr = hourMinuteFormat.format(now)
            currentSecondsStr = secondsFormat.format(now)
            val rawDate = dateFormat.format(now)
            currentDateStr = rawDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
            delay(1000L)
        }
    }

    // 3. Sistema Inteligente Anti Burn-In: Pixel Shifting a cada 60 segundos
    var shiftTargetX by remember { mutableStateOf(0f) }
    var shiftTargetY by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val random = kotlin.random.Random(System.currentTimeMillis())
        while (isActive) {
            delay(60_000L) // Deslocamento a cada 60s
            shiftTargetX = (random.nextInt(-12, 13)).toFloat()
            shiftTargetY = (random.nextInt(-8, 9)).toFloat()
        }
    }

    val animatedShiftX by animateDpAsState(
        targetValue = shiftTargetX.dp,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "pixel_shift_x"
    )
    val animatedShiftY by animateDpAsState(
        targetValue = shiftTargetY.dp,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "pixel_shift_y"
    )

    // 4. Dynamic Dimming & Extra Dim (Cabeceira Noturna)
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isDimmed by remember { mutableStateOf(false) }
    var isExtraDim by remember { mutableStateOf(false) }

    LaunchedEffect(lastInteractionTime) {
        isDimmed = false
        delay(30_000L)
        isDimmed = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = when {
            isExtraDim && isDimmed -> 0.15f
            isExtraDim -> 0.25f
            isDimmed -> 0.65f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "content_dimming_alpha"
    )

    val primaryColor = Color(colorTheme.primaryColor)
    val secondaryColor = Color(colorTheme.secondaryColor)
    val isPlaying = playbackStatus == RadioPlaybackStatus.PLAYING

    // Estrutura principal da tela com fundo preto puro AMOLED
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Qualquer toque na tela reinicia o temporizador de brilho
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastInteractionTime = System.currentTimeMillis()
                    }
                }
            }
    ) {
        // Container que aplica o deslocamento de Pixel Shifting e o Dimming
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = animatedShiftX, y = animatedShiftY)
                .alpha(contentAlpha)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Botões de Topo: Sair, Extra Dim e Alternar Paleta de Cores
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botão fechar / voltar ao iPod
                IconButton(
                    onClick = onExitDockMode,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Sair do Modo Dock",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botão Extra Dim (Escurecer Mais / Cabeceira)
                    IconButton(
                        onClick = { isExtraDim = !isExtraDim },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isExtraDim) primaryColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Extra Dim",
                            tint = if (isExtraDim) primaryColor else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Seletor discreto de cor do tema
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onCycleColorTheme() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = colorTheme.displayName,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Conteúdo Central: Relógio Gigante Proporcional e Data
            if (isLandscape) {
                // Layout Paisagem (lado a lado ou centralizado com mini-player à direita/abaixo)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 70.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxW = maxWidth.value
                        val baseScale = dockClockScale.multiplier
                        val baseSize = if (dockShowSeconds) 58f else 72f
                        val calculated = baseSize * baseScale
                        val maxAllowed = maxW / (if (dockShowSeconds) 4.2f else 3.0f)
                        val effectiveHourFontSize = calculated.coerceAtMost(maxAllowed)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onCycleClockScale?.invoke() ?: onCycleColorTheme() },
                                    onLongClick = { onCycleColorTheme() }
                                )
                            ) {
                                Text(
                                    text = currentHourMinuteStr.ifBlank { "--:--" },
                                    color = primaryColor,
                                    fontSize = effectiveHourFontSize.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = (-2).sp
                                )
                                if (dockShowSeconds) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ":${currentSecondsStr.ifBlank { "00" }}",
                                        color = primaryColor.copy(alpha = 0.85f),
                                        fontSize = (effectiveHourFontSize * 0.5f).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif,
                                        letterSpacing = (-1).sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentDateStr,
                                color = secondaryColor.copy(alpha = 0.9f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Layout Retrato (relógio gigante no centro superior)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val maxW = maxWidth.value
                    val baseScale = dockClockScale.multiplier
                    val baseSize = if (dockShowSeconds) 44f else 54f
                    val calculated = baseSize * baseScale
                    val maxAllowed = maxW / (if (dockShowSeconds) 3.6f else 2.6f)
                    val effectiveHourFontSize = calculated.coerceAtMost(maxAllowed)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onCycleClockScale?.invoke() ?: onCycleColorTheme() },
                                onLongClick = { onCycleColorTheme() }
                            )
                        ) {
                            Text(
                                text = currentHourMinuteStr.ifBlank { "--:--" },
                                color = primaryColor,
                                fontSize = effectiveHourFontSize.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-2).sp
                            )
                            if (dockShowSeconds) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = ":${currentSecondsStr.ifBlank { "00" }}",
                                    color = primaryColor.copy(alpha = 0.85f),
                                    fontSize = (effectiveHourFontSize * 0.5f).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    letterSpacing = (-1).sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentDateStr,
                            color = secondaryColor.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Barra Inferior (Now Playing Dock Mini-Player)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0D0D0D))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Informações de Mídia em Reprodução
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ícone Monocromático Oficial Retrô (Sem logotipos coloridos de terceiros nem arte da origem)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryColor.copy(alpha = 0.14f))
                                .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentPodcastEpisode != null) {
                                Icon(
                                    imageVector = Icons.Default.Podcasts,
                                    contentDescription = "Podcast",
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (currentLocalAudio != null) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Música",
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = "Rádio",
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val titleText = currentLocalAudio?.title
                                ?: currentPodcastEpisode?.title
                                ?: currentStation?.name
                                ?: "MediaPod Standby"

                            val subtitleText: String = when {
                                !currentLocalAudio?.artist.isNullOrBlank() -> currentLocalAudio!!.artist
                                !currentPodcastEpisode?.showTitle.isNullOrBlank() -> currentPodcastEpisode!!.showTitle
                                rdsInfo.radioText.isNotBlank() -> rdsInfo.radioText
                                currentStation != null && currentStation.country.isNotBlank() -> currentStation.country
                                else -> "Rádio Online"
                            }

                            Text(
                                text = titleText,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Badge de Status
                                if (currentStation != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFDC2626))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.status_live),
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (audioDurationMs > 0) {
                                    val posSecs = audioPositionMs / 1000
                                    val durSecs = audioDurationMs / 1000
                                    val posFormatted = String.format(Locale.US, "%02d:%02d", posSecs / 60, posSecs % 60)
                                    val durFormatted = String.format(Locale.US, "%02d:%02d", durSecs / 60, durSecs % 60)

                                    Text(
                                        text = "$posFormatted / $durFormatted",
                                        color = primaryColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Text(
                                    text = subtitleText,
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Controles de Toque Dock: Play/Pause, Próximo e Saída de Áudio
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Seletor de Saída de Áudio
                        IconButton(
                            onClick = onOpenAudioOutput,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_audio_output_classic),
                                contentDescription = "Saída de Áudio",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Botão Play / Pause
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Botão Próximo
                        IconButton(
                            onClick = onNextTrack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Próxima Faixa / Estação",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
