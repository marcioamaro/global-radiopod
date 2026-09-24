package com.marcioamaro.mediapod.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.util.MediaNameResolver
import com.marcioamaro.mediapod.util.UrlStreamValidator
import com.marcioamaro.mediapod.util.ValidationResult
import kotlinx.coroutines.launch

enum class CustomUrlTarget {
    RADIO,
    PODCAST
}

@Composable
fun IpodAddCustomUrlScreen(
    target: CustomUrlTarget,
    onSaveSuccess: (name: String, url: String) -> Unit,
    onCancel: () -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }

    var isResolvingName by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    fun triggerNameResolution(url: String) {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
        coroutineScope.launch {
            isResolvingName = true
            val resolved = if (target == CustomUrlTarget.RADIO) {
                MediaNameResolver.resolveRadioName(url)
            } else {
                MediaNameResolver.resolvePodcastName(url)
            }
            isResolvingName = false
            if (!resolved.isNullOrBlank()) {
                if (nameInput.isBlank() ||
                    nameInput.startsWith("Minha Rádio", ignoreCase = true) ||
                    nameInput.startsWith("Rádio Personalizada", ignoreCase = true) ||
                    nameInput.startsWith("Meu Podcast", ignoreCase = true) ||
                    nameInput.startsWith("Podcast Personalizado", ignoreCase = true)) {
                    nameInput = resolved
                }
            }
        }
    }

    fun pasteNameFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val rawText = clip.getItemAt(0).coerceToText(context).toString().trim()
                    if (rawText.isNotBlank()) {
                        nameInput = rawText
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun pasteUrlFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val rawText = clip.getItemAt(0).coerceToText(context).toString().trim()
                    val cleanUrl = MediaNameResolver.extractUrlFromText(rawText)
                    val urlToSet = if (cleanUrl.isNotBlank()) cleanUrl else rawText
                    if (urlToSet.isNotBlank()) {
                        urlInput = urlToSet
                        validationMessage = null
                        triggerNameResolution(urlToSet)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun startValidation() {
        val trimmedUrl = urlInput.trim()
        val trimmedName = nameInput.trim().ifBlank {
            if (target == CustomUrlTarget.RADIO) "Rádio Personalizada" else "Podcast Personalizado"
        }

        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true))) {
            validationMessage = "URL inválida. Comece com http:// ou https://"
            isSuccess = false
            return
        }

        isValidating = true
        validationMessage = "Testando conexão e fluxo de áudio..."
        isSuccess = false

        coroutineScope.launch {
            val result = if (target == CustomUrlTarget.RADIO) {
                UrlStreamValidator.validateRadioUrl(trimmedName, trimmedUrl)
            } else {
                UrlStreamValidator.validatePodcastUrl(trimmedName, trimmedUrl)
            }

            isValidating = false
            when (result) {
                is ValidationResult.RadioSuccess -> {
                    isSuccess = true
                    val finalName = if (nameInput.isNotBlank() &&
                        !nameInput.startsWith("Minha Rádio", ignoreCase = true) &&
                        !nameInput.startsWith("Rádio Personalizada", ignoreCase = true)) {
                        nameInput.trim()
                    } else {
                        result.station.name
                    }
                    validationMessage = "URL validada com sucesso! (${result.station.codec} ${result.station.bitrate}kbps)"
                    kotlinx.coroutines.delay(650L)
                    onSaveSuccess(finalName, result.station.streamUrl)
                }
                is ValidationResult.PodcastSuccess -> {
                    isSuccess = true
                    val finalTitle = if (nameInput.isNotBlank() &&
                        !nameInput.startsWith("Meu Podcast", ignoreCase = true) &&
                        !nameInput.startsWith("Podcast Personalizado", ignoreCase = true)) {
                        nameInput.trim()
                    } else {
                        result.show.title
                    }
                    validationMessage = "Feed validado com sucesso! ($finalTitle)"
                    kotlinx.coroutines.delay(650L)
                    onSaveSuccess(finalTitle, result.show.feedUrl)
                }
                is ValidationResult.Error -> {
                    isSuccess = false
                    validationMessage = result.message
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (target == CustomUrlTarget.RADIO) "ADICIONAR RÁDIO PERSONALIZADA" else "ADICIONAR PODCAST PERSONALIZADO",
                color = backlightTextPrimary,
                fontSize = (11.5f * fontScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (target == CustomUrlTarget.RADIO)
                    "Insira o link direto de transmissão (MP3, AAC ou HLS)."
                else
                    "Insira o link do Feed RSS oficial do Podcast.",
                color = backlightTextSecondary,
                fontSize = (9f * fontScale).sp,
                fontFamily = fontFamily
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Campo Nome / Descrição
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NOME DE EXIBIÇÃO",
                        color = backlightTextPrimary,
                        fontSize = (9f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                    if (isResolvingName) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(Buscando nome...)",
                            color = backlightHighlight,
                            fontSize = (8f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                }

                // Botão Colar Nome
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightHighlight.copy(alpha = 0.18f))
                        .border(1.dp, backlightHighlight, RoundedCornerShape(3.dp))
                        .clickable { pasteNameFromClipboard() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = "Colar Nome",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "COLAR",
                        color = backlightTextPrimary,
                        fontSize = (8.5f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = (11f * fontScale).sp,
                    lineHeight = 15.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                placeholder = {
                    Text(
                        text = if (target == CustomUrlTarget.RADIO) "Ex: Minha Rádio Rock" else "Ex: Meu Podcast Tech",
                        fontSize = (10f * fontScale).sp,
                        color = backlightTextSecondary.copy(alpha = 0.6f)
                    )
                },
                trailingIcon = {
                    if (isResolvingName) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = backlightHighlight
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = backlightHighlight,
                    unfocusedBorderColor = backlightHighlight.copy(alpha = 0.45f),
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedContainerColor = Color(0x22000000),
                    unfocusedContainerColor = Color(0x15000000)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Campo URL / Endereço
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "URL / LINK DO FLUXO",
                    color = backlightTextPrimary,
                    fontSize = (9f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )

                // Botão Colar URL
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightHighlight.copy(alpha = 0.18f))
                        .border(1.dp, backlightHighlight, RoundedCornerShape(3.dp))
                        .clickable { pasteUrlFromClipboard() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = "Colar URL",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "COLAR",
                        color = backlightTextPrimary,
                        fontSize = (8.5f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    validationMessage = null
                    if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) {
                        if (nameInput.isBlank()) {
                            triggerNameResolution(it)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = (10.5f * fontScale).sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                placeholder = {
                    Text(
                        text = if (target == CustomUrlTarget.RADIO) "https://servidor.com/stream.mp3" else "https://servidor.com/feed.xml",
                        fontSize = (9.5f * fontScale).sp,
                        color = backlightTextSecondary.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = backlightHighlight,
                    unfocusedBorderColor = backlightHighlight.copy(alpha = 0.45f),
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedContainerColor = Color(0x22000000),
                    unfocusedContainerColor = Color(0x15000000)
                )
            )

            // Feedback de Validação
            validationMessage?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSuccess) backlightHighlight.copy(alpha = 0.25f)
                            else Color(0x33DC2626)
                        )
                        .border(
                            1.dp,
                            if (isSuccess) backlightHighlight else Color(0x88DC2626),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = backlightTextPrimary
                        )
                    } else if (isSuccess) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = backlightTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg,
                        color = backlightTextPrimary,
                        fontSize = (9.5f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }
            }
        }

        // Action Buttons at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x22000000))
                    .border(1.dp, backlightTextSecondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable { onCancel() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCELAR",
                    color = backlightTextSecondary,
                    fontSize = (10f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isValidating) backlightHighlight.copy(alpha = 0.5f) else backlightHighlight)
                    .clickable(enabled = !isValidating) { startValidation() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isValidating) "VALIDANDO..." else "VALIDAR E INCLUIR",
                    color = Color.White,
                    fontSize = (10f * fontScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily
                )
            }
        }
    }
}
