package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.mediarouter.app.MediaRouteDialogFactory
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import coil.compose.AsyncImage
import com.example.data.model.YouTubeVideo
import com.example.util.MediaNameResolver
import com.example.util.YouTubeUrlValidator
import com.example.util.YouTubeValidationResult
import kotlinx.coroutines.launch

/**
 * 1. Lista de Vídeos do YouTube Salvos pelo Usuário (Estilo iPod Classic)
 */
@Composable
fun IpodYouTubeListScreen(
    videos: List<YouTubeVideo>,
    selectedIndex: Int,
    onSelectAddVideo: () -> Unit,
    onSelectVideo: (YouTubeVideo) -> Unit,
    onDeleteVideo: (String) -> Unit,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float = 1.5f,
    isBold: Boolean = true
) {
    val allEntries = remember(videos) {
        listOf<YouTubeVideo?>(null) + videos
    }

    com.example.ui.components.SelectableLazyColumn(
        items = allEntries,
        selectedIndex = selectedIndex,
        key = { idx, entry -> entry?.id ?: "add_yt_btn" },
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { index, entry, isSelected ->
        if (index == 0 || entry == null) {
            Column {
                // Item 0: Botão de Adicionar URL de Vídeo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) backlightHighlight else backlightTextPrimary.copy(alpha = 0.08f))
                        .clickable(onClick = onSelectAddVideo)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White.copy(alpha = 0.25f) else backlightHighlight.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Adicionar",
                            tint = if (isSelected) Color.White else backlightHighlight,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "[+ Adicionar Vídeo do YouTube]",
                            color = if (isSelected) Color.White else backlightTextPrimary,
                            fontSize = (11.5f * fontScale).coerceIn(10.5f, 15f).sp,
                            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = "Cole links de vídeos, shorts ou músicas",
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else backlightTextSecondary,
                            fontSize = (9f * fontScale).coerceIn(8.5f, 11f).sp,
                            fontFamily = fontFamily
                        )
                    }
                }

                if (videos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum vídeo adicionado ainda.\nClique acima para colar uma URL do YouTube.",
                            color = backlightTextSecondary,
                            fontSize = (10f * fontScale).sp,
                            fontFamily = fontFamily,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val video = entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) backlightHighlight else Color.Transparent)
                    .clickable { onSelectVideo(video) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33000000))
                        .border(0.8.dp, backlightTextPrimary.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = (11f * fontScale).coerceIn(10.5f, 14f).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.url,
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                        fontSize = (8.5f * fontScale).coerceIn(8f, 10.5f).sp,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Delete Button
                IconButton(
                    onClick = { onDeleteVideo(video.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir Vídeo",
                        tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 2. Tela de Adicionar URL de Vídeo do YouTube
 */
@Composable
fun IpodAddYouTubeUrlScreen(
    onSaveSuccess: (title: String, url: String) -> Unit,
    onCancel: () -> Unit,
    backlightBg: Color = Color.Transparent,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var isResolvingTitle by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun triggerTitleResolution(url: String) {
        coroutineScope.launch {
            isResolvingTitle = true
            val resolved = MediaNameResolver.resolveYouTubeTitle(url)
            isResolvingTitle = false
            if (!resolved.isNullOrBlank()) {
                if (titleInput.isBlank() || titleInput.startsWith("Vídeo YouTube", ignoreCase = true)) {
                    titleInput = resolved
                }
            }
        }
    }

    fun handlePasteFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val rawText = clip.getItemAt(0).coerceToText(context).toString().trim()
                val cleanUrl = MediaNameResolver.extractUrlFromText(rawText)
                val urlToSet = if (cleanUrl.isNotBlank()) cleanUrl else rawText
                if (urlToSet.isNotBlank()) {
                    urlInput = urlToSet
                    val result = YouTubeUrlValidator.validateUrl(urlToSet)
                    if (result is YouTubeValidationResult.Success) {
                        isError = false
                        validationMessage = "URL do YouTube válida! ID: ${result.videoId}"
                        triggerTitleResolution(result.cleanUrl)
                    } else if (result is YouTubeValidationResult.Error) {
                        isError = true
                        validationMessage = result.message
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun handlePasteTitleFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val rawText = clip.getItemAt(0).coerceToText(context).toString().trim()
                if (rawText.isNotBlank()) {
                    titleInput = rawText
                }
            }
        } catch (_: Exception) {}
    }

    fun handleSave() {
        val result = YouTubeUrlValidator.validateUrl(urlInput)
        when (result) {
            is YouTubeValidationResult.Success -> {
                isError = false
                val finalTitle = titleInput.trim().ifBlank { "Vídeo YouTube (${result.videoId})" }
                onSaveSuccess(finalTitle, result.cleanUrl)
            }
            is YouTubeValidationResult.Error -> {
                isError = true
                validationMessage = result.message
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ADICIONAR VÍDEO DO YOUTUBE",
                    color = backlightTextPrimary,
                    fontSize = (11.5f * fontScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily
                )

                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Cancelar",
                        tint = backlightTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Insira o link do vídeo do YouTube (youtube.com, youtu.be, shorts).",
                color = backlightTextSecondary,
                fontSize = (9f * fontScale).sp,
                fontFamily = fontFamily
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Campo URL / Link do Vídeo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "URL / LINK DO VÍDEO",
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
                        .clickable { handlePasteFromClipboard() }
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
                    if (it.isNotBlank()) {
                        val res = YouTubeUrlValidator.validateUrl(it)
                        if (res is YouTubeValidationResult.Success) {
                            isError = false
                            validationMessage = "URL do YouTube válida! ID: ${res.videoId}"
                            if (titleInput.isBlank()) {
                                triggerTitleResolution(res.cleanUrl)
                            }
                        } else if (res is YouTubeValidationResult.Error) {
                            isError = true
                            validationMessage = res.message
                        }
                    } else {
                        validationMessage = null
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
                        text = "https://www.youtube.com/watch?v=...",
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
                    focusedPlaceholderColor = backlightTextSecondary.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = backlightTextSecondary.copy(alpha = 0.6f),
                    focusedContainerColor = Color(0x22000000),
                    unfocusedContainerColor = Color(0x15000000),
                    cursorColor = backlightHighlight
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Campo Título
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TÍTULO (OPCIONAL)",
                        color = backlightTextPrimary,
                        fontSize = (9f * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                    if (isResolvingTitle) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(Buscando título...)",
                            color = backlightHighlight,
                            fontSize = (8f * fontScale).sp,
                            fontFamily = fontFamily
                        )
                    }
                }

                // Botão Colar Título
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(backlightHighlight.copy(alpha = 0.18f))
                        .border(1.dp, backlightHighlight, RoundedCornerShape(3.dp))
                        .clickable { handlePasteTitleFromClipboard() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = "Colar Título",
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
                value = titleInput,
                onValueChange = { titleInput = it },
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
                        text = "Ex: Nome do clipe ou apresentação",
                        fontSize = (10f * fontScale).sp,
                        color = backlightTextSecondary.copy(alpha = 0.6f),
                        fontFamily = fontFamily
                    )
                },
                trailingIcon = {
                    if (isResolvingTitle) {
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
                    focusedPlaceholderColor = backlightTextSecondary.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = backlightTextSecondary.copy(alpha = 0.6f),
                    focusedContainerColor = Color(0x22000000),
                    unfocusedContainerColor = Color(0x15000000),
                    cursorColor = backlightHighlight
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
                            if (!isError) backlightHighlight.copy(alpha = 0.25f)
                            else Color(0x33DC2626)
                        )
                        .border(
                            1.dp,
                            if (!isError) backlightHighlight else Color(0x88DC2626),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isResolvingTitle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = backlightTextPrimary
                        )
                    } else if (!isError) {
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
                    .background(backlightHighlight)
                    .clickable { handleSave() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SALVAR VÍDEO",
                    color = Color.White,
                    fontSize = (10f * fontScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

/**
 * 3. Player Oficial do YouTube (Modo iPod Classic)
 *
 * Em total compliance com as diretrizes do Google Play e YouTube:
 * - Utiliza YouTube IFrame Player API oficial
 * - Anti-Background Play estrito: pausa imediatamente em ON_PAUSE e ON_STOP
 * - Não remove nem oculta anúncios oficiais
 * - Permite botão de Cast para transmissão na rede local (Smart TVs, Chromecast)
 */
class YouTubeJsBridge(private val onPositionUpdate: (Int) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onTimeUpdate(seconds: Int) {
        onPositionUpdate(seconds)
    }
}

internal fun buildYouTubePlayerHtml(videoId: String, startSeconds: Int): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                #player { width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <iframe id="player"
                type="text/html"
                width="100%"
                height="100%"
                src="https://www.youtube.com/embed/$videoId?enablejsapi=1&autoplay=1&playsinline=1&fs=1&rel=0&start=$startSeconds&origin=https://www.youtube.com"
                frameborder="0"
                allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                referrerpolicy="strict-origin-when-cross-origin"
                allowfullscreen>
            </iframe>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
                var player;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        events: {
                            'onReady': onPlayerReady,
                            'onError': onPlayerError
                        }
                    });
                }
                function onPlayerReady(event) {
                    event.target.playVideo();
                    setInterval(function() {
                        try {
                            if (player && player.getCurrentTime) {
                                var t = Math.floor(player.getCurrentTime());
                                if (window.AndroidBridge && t >= 0) {
                                    window.AndroidBridge.onTimeUpdate(t);
                                }
                            }
                        } catch (e) {
                            console.error("Time update error:", e);
                        }
                    }, 1000);
                }
                function onPlayerError(event) {
                    console.error("YouTube Player Error code:", event.data);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

internal fun buildYouTubeWatchUrl(videoId: String, startSeconds: Int = 0): String {
    val baseUrl = "https://m.youtube.com/watch?v=$videoId&embeds_referring_euri=https%3A%2F%2Fwww.youtube.com%2F&embeds_referring_origin=https%3A%2F%2Fwww.youtube.com"
    return if (startSeconds > 0) "$baseUrl&t=${startSeconds}s" else baseUrl
}

@SuppressLint("SetJavaScriptEnabled")

@Composable
fun IpodYouTubePlayerScreen(
    video: YouTubeVideo,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit = {},
    initialStartSeconds: Int = 0,
    onTimeUpdate: (Int) -> Unit = {},
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float = 1.5f,
    isBold: Boolean = true,
    sharedWebView: WebView? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Usa o WebView compartilhado (passado de fora) ou cria um local como fallback
    var localWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(sharedWebView == null) }
    val activeWebView = sharedWebView ?: localWebViewRef

    // Compliance Google Play / YouTube: Pausar vídeo ao sair da tela ou minimizar app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                activeWebView?.evaluateJavascript("var v = document.querySelector('video'); if (v) { v.pause(); } else if (typeof player !== 'undefined' && player && typeof player.pauseVideo === 'function') { player.pauseVideo(); }", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Só destrói se for um WebView local (não compartilhado)
            if (sharedWebView == null) {
                localWebViewRef?.destroy()
                localWebViewRef = null
            }
        }
    }

    fun openNetworkCastChooser() {
        try {
            // Pausa a reprodução local no iPod antes de transmitir
            activeWebView?.evaluateJavascript("var v = document.querySelector('video'); if (v) { v.pause(); }", null)

            android.widget.Toast.makeText(
                context,
                "Abrindo no YouTube para transmitir para a TV...",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            val watchUrl = "https://www.youtube.com/watch?v=${video.id}"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(watchUrl)).apply {
                setPackage("com.google.android.youtube")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val genericIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(watchUrl)).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(genericIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("IpodYouTubeScreens", "Erro ao abrir para Cast/YouTube", e)
            try {
                val fallbackIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/watch?v=${video.id}")
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Top Player Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backlightTextPrimary.copy(alpha = 0.10f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Voltar",
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = video.title,
                    color = backlightTextPrimary,
                    fontSize = (11f * fontScale).sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Botão de Transmissão de Rede (Cast)
                IconButton(
                    onClick = { openNetworkCastChooser() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Transmitir",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Botão de Tela Cheia
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Tela Cheia",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // WebView IFrame Player Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .border(1.2.dp, backlightTextPrimary.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (sharedWebView != null) {
                // Reutiliza o WebView compartilhado sem recriar — continua de onde parou
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.widget.FrameLayout(ctx).apply {
                            (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
                            addView(sharedWebView, ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                    },
                    update = { container ->
                        if (sharedWebView.parent != container) {
                            (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
                            container.addView(sharedWebView, ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                        }
                    }
                )
            } else {
                // Fallback: cria WebView local (sem WebView compartilhado)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = false
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = userAgentString.replace("; wv", "")
                            }
                            addJavascriptInterface(YouTubeJsBridge(onTimeUpdate), "AndroidBridge")
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            function autoPlayVideo() {
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    if (v.paused) { v.play().catch(function(e){}); }
                                                } else {
                                                    setTimeout(autoPlayVideo, 400);
                                                }
                                            }
                                            autoPlayVideo();
                                            if (!window._ipodTimeInterval) {
                                                window._ipodTimeInterval = setInterval(function() {
                                                    try {
                                                        var v = document.querySelector('video');
                                                        if (v && !v.paused && window.AndroidBridge) {
                                                            window.AndroidBridge.onTimeUpdate(Math.floor(v.currentTime));
                                                        }
                                                    } catch(e) {}
                                                }, 1000);
                                            }
                                        })();
                                        """.trimIndent(), null
                                    )
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return !(url.startsWith("http://") || url.startsWith("https://"))
                                }
                            }
                            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            loadUrl(buildYouTubeWatchUrl(video.id, initialStartSeconds))
                            localWebViewRef = this
                        }
                    }
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = backlightHighlight,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * 4. Player de Tela Cheia para o Modo Paisagem (Landscape Fullscreen)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullscreenLandscapeYouTubePlayer(
    video: YouTubeVideo,
    onBack: () -> Unit,
    initialStartSeconds: Int = 0,
    onTimeUpdate: (Int) -> Unit = {},
    sharedWebView: WebView? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var localWebViewRef by remember { mutableStateOf<WebView?>(null) }
    val activeWebView = sharedWebView ?: localWebViewRef

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                activeWebView?.evaluateJavascript("var v = document.querySelector('video'); if (v) { v.pause(); } else if (typeof player !== 'undefined' && player && typeof player.pauseVideo === 'function') { player.pauseVideo(); }", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Só destrói se for um WebView local (não compartilhado)
            if (sharedWebView == null) {
                localWebViewRef?.destroy()
                localWebViewRef = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (sharedWebView != null) {
            // Reutiliza o WebView compartilhado sem recriar — continua de onde parou
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    android.widget.FrameLayout(ctx).apply {
                        (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
                        addView(sharedWebView, ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                    }
                },
                update = { container ->
                    if (sharedWebView.parent != container) {
                        (sharedWebView.parent as? ViewGroup)?.removeView(sharedWebView)
                        container.addView(sharedWebView, ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ))
                    }
                }
            )
        } else {
            // Fallback: cria WebView local
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = false
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = userAgentString.replace("; wv", "")
                        }
                        addJavascriptInterface(YouTubeJsBridge(onTimeUpdate), "AndroidBridge")
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return !(url.startsWith("http://") || url.startsWith("https://"))
                            }
                        }
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl(buildYouTubeWatchUrl(video.id, initialStartSeconds))
                        localWebViewRef = this
                    }
                }
            )
        }

        // Overlay Exit Button (Topo esquerdo)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x88000000))
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Sair da Tela Cheia",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
