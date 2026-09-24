package com.marcioamaro.mediapod.ui.onboarding

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.R
import com.marcioamaro.mediapod.data.prefs.ClickWheelMode
import com.marcioamaro.mediapod.data.prefs.FixedSpeed
import com.marcioamaro.mediapod.ui.IpodChassisTheme
import com.marcioamaro.mediapod.ui.LcdBacklight
import com.marcioamaro.mediapod.ui.theme.IpodColorContrastUtil
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import java.util.Locale
import com.marcioamaro.mediapod.ui.components.ClickWheel
import com.marcioamaro.mediapod.util.AppLocaleManager
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2

private val BgDark = Color(0xFF0F172A)
private val CardBgDark = Color(0xFF1E293B)
private val CardBorderDark = Color(0xFF334155)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentBlue = Color(0xFF0284C7)
private val TextWhite = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingWizardScreen(
    viewModel: OnboardingWizardViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val currentLocale = remember(state.languageTag) {
        val parts = state.languageTag.split("-")
        if (parts.size > 1) Locale(parts[0], parts[1]) else Locale(parts[0])
    }
    val localizedConfig = remember(currentLocale) {
        Configuration(context.resources.configuration).apply {
            setLocale(currentLocale)
            setLayoutDirection(currentLocale)
        }
    }
    val localizedContext = remember(localizedConfig) {
        context.createConfigurationContext(localizedConfig)
    }

    if (!state.isReady) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BgDark),
            contentAlignment = Alignment.Center
        ) {
            LinearProgressIndicator(color = AccentCyan)
        }
        return
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag("onboarding_wizard_screen"),
            color = BgDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
            // Top Header: Step Indicator & Adaptive Bitten Pear Logo
            WizardTopBar(
                currentStep = state.currentStep,
                totalSteps = state.totalSteps,
                onStepClick = { step -> viewModel.goToStep(step) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Step Container (<300ms transition)
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(240)) { width -> width } + fadeIn(animationSpec = tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> -width } + fadeOut(animationSpec = tween(240)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(240)) { width -> -width } + fadeIn(animationSpec = tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { width -> width } + fadeOut(animationSpec = tween(240)))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "onboarding_step_transition"
            ) { targetStep ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (targetStep) {
                        0 -> StepLanguage(
                            currentTag = state.languageTag,
                            onSelect = { tag ->
                                triggerHapticTick(context)
                                viewModel.selectLanguage(tag)
                            }
                        )
                        1 -> StepClock(
                            is24H = state.is24HourClock,
                            onToggle = { is24 ->
                                triggerHapticTick(context)
                                viewModel.setClock24H(is24)
                            },
                            formattedTime = viewModel.getFormattedTimePreview()
                        )
                        2 -> StepThemeColors(
                            selectedChassis = state.chassisTheme,
                            selectedBacklight = state.lcdBacklight,
                            highContrast = state.highContrast,
                            onSelectChassis = {
                                triggerHapticTick(context)
                                viewModel.setChassisTheme(it)
                            },
                            onSelectBacklight = {
                                triggerHapticTick(context)
                                viewModel.setLcdBacklight(it)
                            },
                            onSelectU2 = {
                                triggerHapticTick(context)
                                viewModel.applyU2SpecialEdition()
                            },
                            onRandomColors = {
                                triggerHapticTick(context)
                                viewModel.applyRandomColors()
                            }
                        )
                        3 -> StepTypographyA11y(
                            fontSizeScale = state.fontSizeScale,
                            highContrast = state.highContrast,
                            onScaleChange = { viewModel.setFontSizeScale(it) },
                            onContrastChange = {
                                triggerHapticTick(context)
                                viewModel.setHighContrast(it)
                            }
                        )
                        4 -> StepClickWheelCalibration(
                            mode = state.clickWheelMode,
                            fixedSpeed = state.clickWheelFixedSpeed,
                            selectedBacklight = state.lcdBacklight,
                            selectedChassis = state.chassisTheme,
                            onModeChange = {
                                triggerHapticTick(context)
                                viewModel.setClickWheelMode(it)
                            },
                            onSpeedChange = {
                                triggerHapticTick(context)
                                viewModel.setClickWheelFixedSpeed(it)
                            }
                        )
                        5 -> StepSummary(
                            state = state,
                            onFinish = {
                                triggerHapticSuccess(context)
                                viewModel.completeOnboarding(onFinish)
                            },
                            onSkip = {
                                triggerHapticTick(context)
                                viewModel.completeOnboarding(onFinish)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Bottom Bar
            WizardBottomNav(
                currentStep = state.currentStep,
                totalSteps = state.totalSteps,
                onBack = {
                    triggerHapticTick(context)
                    viewModel.previousStep()
                },
                onNext = {
                    triggerHapticTick(context)
                    viewModel.nextStep()
                },
                onFinish = {
                    triggerHapticSuccess(context)
                    viewModel.completeOnboarding(onFinish)
                }
            )
        }
    }
}
}

@Composable
private fun WizardTopBar(
    currentStep: Int,
    totalSteps: Int,
    onStepClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Adaptive Bitten Pear Logo
                val pearColor = IpodColorContrastUtil.getAdaptivePearLogoColor(BgDark)
                Icon(
                    painter = painterResource(id = R.drawable.ic_pear_logo),
                    contentDescription = "Logo",
                    tint = pearColor,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("wizard_pear_logo")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Text(
                text = "${currentStep + 1} / $totalSteps",
                color = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-segment animated progress indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (step in 0 until totalSteps) {
                val isCompleted = step < currentStep
                val isCurrent = step == currentStep
                val barColor = when {
                    isCurrent -> AccentCyan
                    isCompleted -> AccentBlue
                    else -> CardBorderDark
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                        .clickable(enabled = isCompleted) { onStepClick(step) }
                )
            }
        }
    }
}

@Composable
private fun StepHeader(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 26.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Step 0: Language Selection
// ---------------------------------------------------------------------------
@Composable
private fun StepLanguage(
    currentTag: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepHeader(
            title = stringResource(id = R.string.onboarding_step_language_title),
            description = stringResource(id = R.string.onboarding_step_language_desc)
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppLocaleManager.SUPPORTED_LOCALES.forEach { loc ->
            val isSelected = currentTag.equals(loc.tag, ignoreCase = true)
            val borderCol = if (isSelected) AccentCyan else CardBorderDark
            val bgCol = if (isSelected) CardBorderDark else CardBgDark

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(loc.tag) }
                    .testTag("lang_card_${loc.tag}"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bgCol),
                border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderCol)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = loc.flagEmoji,
                            fontSize = 26.sp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = loc.nativeName,
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = loc.displayName,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = AccentCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Clock & Region Format
// ---------------------------------------------------------------------------
@Composable
private fun StepClock(
    is24H: Boolean,
    onToggle: (Boolean) -> Unit,
    formattedTime: String
) {
    var liveTime by remember { mutableStateOf(formattedTime) }

    LaunchedEffect(is24H) {
        while (true) {
            val pattern = if (is24H) "HH:mm:ss" else "hh:mm:ss a"
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            liveTime = sdf.format(java.util.Date())
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        StepHeader(
            title = stringResource(id = R.string.onboarding_step_clock_title),
            description = stringResource(id = R.string.onboarding_step_clock_desc)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live Clock Display Card (Simulating LCD Clock)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF021B2B)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentCyan.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_clock_preview_label),
                    color = AccentCyan.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = liveTime,
                    color = AccentCyan,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Options: 24h vs 12h
        ClockOptionCard(
            title = stringResource(id = R.string.onboarding_clock_24h),
            isSelected = is24H,
            onClick = { onToggle(true) }
        )

        ClockOptionCard(
            title = stringResource(id = R.string.onboarding_clock_12h),
            isSelected = !is24H,
            onClick = { onToggle(false) }
        )
    }
}

@Composable
private fun ClockOptionCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardBorderDark else CardBgDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) AccentCyan else CardBorderDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2: Theme & Backlight Colors
// ---------------------------------------------------------------------------
@Composable
private fun IpodMiniaturePreview(
    chassisTheme: IpodChassisTheme,
    lcdBacklight: LcdBacklight,
    highContrast: Boolean
) {
    val animatedBodyColor by animateColorAsState(Color(chassisTheme.bodyColor), label = "bodyColor")
    val animatedWheelColor by animateColorAsState(
        if (chassisTheme == IpodChassisTheme.U2_SPECIAL) Color(0xFFDC2626) else Color(chassisTheme.wheelColor),
        label = "wheelColor"
    )
    val animatedLcdBg by animateColorAsState(Color(lcdBacklight.background), label = "lcdBg")
    val animatedLcdText by animateColorAsState(Color(lcdBacklight.textPrimary), label = "lcdText")
    val animatedLcdSec by animateColorAsState(Color(lcdBacklight.textSecondary), label = "lcdSec")
    val animatedLcdHighlight by animateColorAsState(Color(lcdBacklight.highlight), label = "lcdHighlight")

    val centerButtonBg = if (chassisTheme == IpodChassisTheme.U2_SPECIAL) Color(0xFF111111) else animatedBodyColor
    val pearLogoColor = IpodColorContrastUtil.getAdaptivePearLogoColor(centerButtonBg)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Gabinete em miniatura do iPod Classic
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(240.dp)
                .shadow(14.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedBodyColor,
                            animatedBodyColor.copy(alpha = 0.90f)
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Tela LCD retrô
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(animatedLcdBg)
                        .border(1.2.dp, animatedLcdHighlight.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header do LCD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MediaPod + Radio / Podcast",
                                color = animatedLcdText,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "12:00",
                                color = animatedLcdSec,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Tocando Agora
                        Column {
                            Text(
                                text = "▶ MPB FM 90.3",
                                color = animatedLcdText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Rio de Janeiro • Ao Vivo",
                                color = animatedLcdSec,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Rodapé do LCD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lcdBacklight.displayName.take(14),
                                color = animatedLcdHighlight,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "●●●○",
                                color = animatedLcdSec,
                                fontSize = 7.sp
                            )
                        }
                    }
                }

                // ClickWheel miniatura proporcional
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(animatedWheelColor)
                        .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val wheelLabelColor = if (chassisTheme == IpodChassisTheme.U2_SPECIAL) {
                        Color.White
                    } else if (animatedWheelColor.luminance() > 0.5f) {
                        Color(0xFF334155)
                    } else {
                        Color(0xFFF1F5F9)
                    }

                    Text(
                        text = "MENU",
                        color = wheelLabelColor,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                    )
                    Text(
                        text = "|◀◀",
                        color = wheelLabelColor,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)
                    )
                    Text(
                        text = "▶▶|",
                        color = wheelLabelColor,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                    )
                    Text(
                        text = "▶❚❚",
                        color = wheelLabelColor,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    )

                    // Botão Central
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(centerButtonBg)
                            .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pear_logo),
                            contentDescription = "Logo",
                            tint = pearLogoColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepThemeColors(
    selectedChassis: IpodChassisTheme,
    selectedBacklight: LcdBacklight,
    highContrast: Boolean,
    onSelectChassis: (IpodChassisTheme) -> Unit,
    onSelectBacklight: (LcdBacklight) -> Unit,
    onSelectU2: () -> Unit,
    onRandomColors: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepHeader(
            title = stringResource(id = R.string.onboarding_step_theme_title),
            description = stringResource(id = R.string.onboarding_step_theme_desc)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Live iPod Classic Miniature Mockup Preview
        IpodMiniaturePreview(
            chassisTheme = selectedChassis,
            lcdBacklight = selectedBacklight,
            highContrast = highContrast
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Acabamento do Gabinete
        Text(
            text = stringResource(id = R.string.onboarding_theme_chassis),
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Botão Especial: 🎲 Modo Aleatório de Cores
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable { onRandomColors() },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = CardBgDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎲", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.onboarding_theme_random_colors),
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "SORTEAR",
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Botão Especial: U2 Edition (Red/Black)
        val isU2Selected = selectedChassis == IpodChassisTheme.U2_SPECIAL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable { onSelectU2() },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isU2Selected) Color(0xFF1F1212) else CardBgDark
            ),
            border = androidx.compose.foundation.BorderStroke(
                if (isU2Selected) 1.8.dp else 1.dp,
                if (isU2Selected) Color(0xFFDC2626) else CardBorderDark
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .border(1.dp, Color(0xFF111111), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.onboarding_theme_u2_special),
                            color = if (isU2Selected) Color(0xFFEF4444) else TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.onboarding_theme_u2_desc),
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                if (isU2Selected) {
                    Text(
                        text = stringResource(id = R.string.onboarding_active_tag),
                        color = Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Acabamentos de Linha Normal
        IpodChassisTheme.values().filter { it != IpodChassisTheme.U2_SPECIAL }.forEach { theme ->
            val isSelected = theme == selectedChassis
            ThemeSelectionRow(
                title = theme.displayName,
                indicatorColor = Color(theme.bodyColor),
                isSelected = isSelected,
                onClick = { onSelectChassis(theme) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Iluminação LCD
        Text(
            text = stringResource(id = R.string.onboarding_theme_backlight),
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        LcdBacklight.values().forEach { backlight ->
            val isSelected = backlight == selectedBacklight
            ThemeSelectionRow(
                title = backlight.displayName,
                indicatorColor = Color(backlight.background),
                isSelected = isSelected,
                onClick = { onSelectBacklight(backlight) }
            )
        }
    }
}

@Composable
private fun ThemeSelectionRow(
    title: String,
    indicatorColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardBorderDark else CardBgDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) AccentCyan else CardBorderDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3: Typography & Accessibility (WCAG 1.4.3)
// ---------------------------------------------------------------------------
@Composable
private fun StepTypographyA11y(
    fontSizeScale: Float,
    highContrast: Boolean,
    onScaleChange: (Float) -> Unit,
    onContrastChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepHeader(
            title = stringResource(id = R.string.onboarding_step_a11y_title),
            description = stringResource(id = R.string.onboarding_step_a11y_desc)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic Live Text Sample
        val effectiveScale = fontSizeScale.coerceIn(1.0f, 2.0f)
        val testTextColor = if (highContrast) Color(0xFFFFFFFF) else Color(0xFFCBD5E1)
        val testBgColor = if (highContrast) Color(0xFF000000) else Color(0xFF1E293B)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = testBgColor),
            border = androidx.compose.foundation.BorderStroke(
                if (highContrast) 2.dp else 1.dp,
                if (highContrast) AccentCyan else CardBorderDark
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WCAG 1.4.3 ✓",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${(effectiveScale * 100).toInt()}%",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Antena 1 FM - 94.7 MHz",
                    color = testTextColor,
                    fontSize = (15f * effectiveScale).sp,
                    fontWeight = if (highContrast) FontWeight.Black else FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Texto com contraste garantido de no mínimo 4.5:1",
                    color = if (highContrast) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                    fontSize = (12f * effectiveScale).sp,
                    lineHeight = (16f * effectiveScale).sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Font Size Scale Slider
        Text(
            text = "${stringResource(id = R.string.onboarding_font_size_label)}: ${(fontSizeScale * 100).toInt()}%",
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = fontSizeScale,
            onValueChange = { onScaleChange(it) },
            valueRange = 1.0f..2.0f,
            steps = 3, // 100%, 125%, 150%, 175%, 200%
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = CardBorderDark
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // High Contrast Mode Toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBgDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.onboarding_high_contrast_label),
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.onboarding_high_contrast_desc),
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = highContrast,
                    onCheckedChange = { onContrastChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CardBorderDark
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 4: Click Wheel Calibration (Identical to iPod Classic Settings)
// ---------------------------------------------------------------------------
@Composable
private fun StepClickWheelCalibration(
    mode: ClickWheelMode,
    fixedSpeed: FixedSpeed,
    selectedBacklight: LcdBacklight,
    selectedChassis: IpodChassisTheme,
    onModeChange: (ClickWheelMode) -> Unit,
    onSpeedChange: (FixedSpeed) -> Unit
) {
    val context = LocalContext.current
    val testItems = remember {
        listOf(
            "1. Antena 1 FM (94.7)",
            "2. Jovem Pan FM (100.9)",
            "3. MPB FM Rio (90.3)",
            "4. Alpha FM (101.7)",
            "5. BandNews FM (96.9)",
            "6. Rádio Gaúcha (93.7)",
            "7. CBN São Paulo (90.5)",
            "8. Kiss FM Classic Rock",
            "9. NovaBrasil FM (89.7)",
            "10. Transamérica Hits"
        )
    }
    var testIndex by remember { mutableStateOf(2) }
    val listState = rememberLazyListState()

    LaunchedEffect(testIndex) {
        listState.animateScrollToItem((testIndex - 1).coerceAtLeast(0))
    }

    val isProgressive = mode == ClickWheelMode.PROGRESSIVE
    val isFixed = mode == ClickWheelMode.FIXED

    val backlightHighlight = Color(selectedBacklight.highlight)
    val backlightBg = Color(selectedBacklight.background)
    val backlightTextPrimary = Color(selectedBacklight.textPrimary)
    val backlightTextSecondary = Color(selectedBacklight.textSecondary)

    Column(modifier = Modifier.fillMaxWidth()) {
        StepHeader(
            title = stringResource(id = R.string.onboarding_step_wheel_title),
            description = stringResource(id = R.string.onboarding_step_wheel_desc)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Modo Progressivo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isProgressive) CardBorderDark else CardBgDark)
                .border(1.dp, if (isProgressive) AccentCyan else CardBorderDark, RoundedCornerShape(8.dp))
                .clickable { onModeChange(ClickWheelMode.PROGRESSIVE) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isProgressive) "●" else "○",
                color = if (isProgressive) AccentCyan else TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.onboarding_wheel_mode_progressive),
                    color = if (isProgressive) TextWhite else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.onboarding_wheel_progressive_desc),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Modo Fixa
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFixed) CardBorderDark else CardBgDark)
                .border(1.dp, if (isFixed) AccentCyan else CardBorderDark, RoundedCornerShape(8.dp))
                .clickable { onModeChange(ClickWheelMode.FIXED) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFixed) "●" else "○",
                color = if (isFixed) AccentCyan else TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.onboarding_wheel_mode_fixed),
                    color = if (isFixed) TextWhite else TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.onboarding_wheel_fixed_desc),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Níveis de Velocidade Fixa (Animado quando Fixo)
        AnimatedVisibility(visible = isFixed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_wheel_fixed_speed_label),
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FixedSpeed.values().forEach { speed ->
                        val isSpeedSelected = fixedSpeed == speed
                        val label = when (speed) {
                            FixedSpeed.SLOW -> stringResource(id = R.string.onboarding_speed_slow)
                            FixedSpeed.STANDARD -> stringResource(id = R.string.onboarding_speed_standard)
                            FixedSpeed.FAST -> stringResource(id = R.string.onboarding_speed_fast)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSpeedSelected) AccentCyan.copy(alpha = 0.25f) else CardBgDark)
                                .border(
                                    1.2.dp,
                                    if (isSpeedSelected) AccentCyan else CardBorderDark,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSpeedChange(speed) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSpeedSelected) AccentCyan else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Área de Teste Interativa (Split Preview)
        Text(
            text = stringResource(id = R.string.onboarding_wheel_interactive_test),
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .height(175.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(backlightBg.copy(alpha = 0.95f))
                .border(1.5.dp, backlightHighlight.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lado Esquerdo: Lista de estações roláveis
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x18000000))
                    .padding(3.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(testItems) { index, item ->
                        val isSelected = index == testIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) backlightHighlight else Color.Transparent)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                color = if (isSelected) Color.White else backlightTextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Lado Direito: Mini ClickWheel funcional
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                val wheelCol = if (selectedChassis == IpodChassisTheme.U2_SPECIAL) {
                    Color(0xFFDC2626)
                } else {
                    Color(selectedChassis.wheelColor)
                }
                val centerBtnCol = if (selectedChassis == IpodChassisTheme.U2_SPECIAL) {
                    Color(0xFF111111)
                } else {
                    Color(selectedChassis.bodyColor)
                }
                val wheelTextCol = if (selectedChassis == IpodChassisTheme.U2_SPECIAL) {
                    Color.White
                } else if (wheelCol.luminance() > 0.5f) {
                    Color(0xFF334155)
                } else {
                    Color(0xFFF1F5F9)
                }

                ClickWheel(
                    onRotaryScroll = { steps ->
                        triggerHapticTick(context)
                        testIndex = (testIndex + steps).coerceIn(0, testItems.lastIndex)
                    },
                    onCenterClick = {
                        triggerHapticTick(context)
                        testIndex = 0
                    },
                    onMenuClick = {
                        triggerHapticTick(context)
                        testIndex = 0
                    },
                    onPlayPauseClick = {
                        triggerHapticTick(context)
                        testIndex = testItems.lastIndex
                    },
                    onPrevClick = {
                        triggerHapticTick(context)
                        testIndex = (testIndex - 1).coerceAtLeast(0)
                    },
                    onNextClick = {
                        triggerHapticTick(context)
                        testIndex = (testIndex + 1).coerceAtMost(testItems.lastIndex)
                    },
                    wheelColor = wheelCol,
                    textColor = wheelTextCol,
                    centerButtonColor = centerBtnCol,
                    wheelSize = 125.dp
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 5: Summary & Confirmation
// ---------------------------------------------------------------------------
@Composable
private fun StepSummary(
    state: OnboardingUiState,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    val currentLocaleObj = AppLocaleManager.getLocaleByTag(state.languageTag)

    Column(modifier = Modifier.fillMaxWidth()) {
        StepHeader(
            title = stringResource(id = R.string.onboarding_step_summary_title),
            description = stringResource(id = R.string.onboarding_step_summary_desc)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardBgDark),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentCyan.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryItem(
                    label = stringResource(
                        id = R.string.onboarding_summary_language,
                        "${currentLocaleObj.flagEmoji} ${currentLocaleObj.nativeName}"
                    )
                )
                SummaryItem(
                    label = stringResource(
                        id = R.string.onboarding_summary_clock,
                        if (state.is24HourClock) "24h" else "12h AM/PM"
                    )
                )
                SummaryItem(
                    label = stringResource(
                        id = R.string.onboarding_summary_chassis,
                        state.chassisTheme.displayName
                    )
                )
                SummaryItem(
                    label = stringResource(
                        id = R.string.onboarding_summary_backlight,
                        state.lcdBacklight.displayName
                    )
                )
                SummaryItem(
                    label = stringResource(
                        id = R.string.onboarding_summary_contrast,
                        if (state.highContrast) "WCAG 1.4.3 (Ativo)" else "Padrão"
                    )
                )
                SummaryItem(
                    label = stringResource(
                        id = R.string.onboarding_summary_wheel,
                        "${(state.clickWheelSensitivity * 100).toInt()}% • ${state.clickWheelMode.name}"
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Button: "Começar a Ouvir"
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_onboarding_finish"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan,
                contentColor = Color(0xFF0F172A)
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.onboarding_btn_finish),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Text Button: "Reconfigurar depois"
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("btn_onboarding_skip")
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_btn_skip),
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(AccentCyan)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom Navigation Bar
// ---------------------------------------------------------------------------
@Composable
private fun WizardBottomNav(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (currentStep > 0) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_wizard_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.onboarding_btn_back),
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.width(60.dp))
        }

        if (currentStep < totalSteps - 1) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("btn_wizard_next"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF0F172A)
                )
            ) {
                Text(
                    text = stringResource(id = R.string.onboarding_btn_next),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Haptic Helpers
// ---------------------------------------------------------------------------
private fun triggerHapticTick(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(15)
        }
    } catch (_: Exception) {
    }
}

private fun triggerHapticSuccess(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    } catch (_: Exception) {
    }
}
