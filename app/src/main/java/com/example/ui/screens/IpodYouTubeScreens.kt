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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
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
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float = 1.5f,
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
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ADICIONAR VÍDEO DO YOUTUBE",
                color = backlightTextPrimary,
                fontSize = (11f * fontScale).coerceIn(11f, 15f).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily
            )

            IconButton(onClick = onCancel, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Cancelar",
                    tint = backlightTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "URL do Vídeo (youtube.com, youtu.be, shorts):",
                color = backlightTextSecondary,
                fontSize = (9f * fontScale).sp,
                fontFamily = fontFamily
            )

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
                    imageVector = Icons.Default.ContentPaste,
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

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                if (it.isNotBlank()) {
                    val res = YouTubeUrlValidator.validateUrl(it)
                    if (res is YouTubeValidationResult.Success) {
                        isError = false
                        validationMessage = "URL válida! ID: ${res.videoId}"
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
                .defaultMinSize(minHeight = 46.dp),
            placeholder = { Text("https://www.youtube.com/watch?v=...", fontSize = (9.5f * fontScale).sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = (10f * fontScale).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            trailingIcon = {
                IconButton(onClick = { handlePasteFromClipboard() }) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Colar URL",
                        tint = backlightHighlight
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = backlightHighlight,
                unfocusedBorderColor = backlightTextPrimary.copy(alpha = 0.4f),
                focusedTextColor = backlightTextPrimary,
                unfocusedTextColor = backlightTextPrimary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Título Personalizado (Opcional):",
                    color = backlightTextSecondary,
                    fontSize = (9f * fontScale).sp,
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
                    imageVector = Icons.Default.ContentPaste,
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

        OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp),
            placeholder = { Text("Nome do clipe ou apresentação", fontSize = (9.5f * fontScale).sp) },
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
            textStyle = LocalTextStyle.current.copy(
                fontSize = (10f * fontScale).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = backlightHighlight,
                unfocusedBorderColor = backlightTextPrimary.copy(alpha = 0.4f),
                focusedTextColor = backlightTextPrimary,
                unfocusedTextColor = backlightTextPrimary
            )
        )

        if (validationMessage != null) {
            Text(
                text = validationMessage!!,
                color = if (isError) Color(0xFFDC2626) else backlightHighlight,
                fontSize = (9f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0x33000000))
            ) {
                Text("Cancelar", color = backlightTextPrimary, fontSize = 11.sp, fontFamily = fontFamily)
            }

            ElevatedButton(
                onClick = { handleSave() },
                modifier = Modifier.weight(1.3f),
                colors = ButtonDefaults.elevatedButtonColors(containerColor = backlightHighlight)
            ) {
                Text("Salvar Vídeo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
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
class YouTubeJsBridge(private val onTimeUpdate: (Int) -> Unit) {
    @android.webkit.JavascriptInterface
    fun onTimeUpdate(seconds: Int) {
        onTimeUpdate(seconds)
    }
}

private fun buildYouTubePlayerHtml(videoId: String, startSeconds: Int): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                #player { width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        height: '100%',
                        width: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1,
                            'enablejsapi': 1,
                            'playsinline': 1,
                            'fs': 1,
                            'rel': 0,
                            'modestbranding': 1,
                            'start': $startSeconds
                        },
                        events: {
                            'onReady': onPlayerReady
                        }
                    });
                }
                function onPlayerReady(event) {
                    event.target.playVideo();
                    setInterval(function() {
                        if (player && player.getCurrentTime) {
                            var t = Math.floor(player.getCurrentTime());
                            if (window.AndroidBridge && t >= 0) {
                                window.AndroidBridge.onTimeUpdate(t);
                            }
                        }
                    }, 1000);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
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
    isBold: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Compliance Google Play / YouTube: Pausar vídeo ao sair da tela ou minimizar app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                webViewRef?.evaluateJavascript("if (typeof player !== 'undefined') { player.pauseVideo(); } else { document.querySelector('video')?.pause(); }", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    fun openNetworkCastChooser() {
        try {
            val router = MediaRouter.getInstance(context)
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()
            val dialog = MediaRouteDialogFactory.getDefault().onCreateChooserDialogFragment()
            dialog.routeSelector = selector
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null) {
                dialog.show(activity.supportFragmentManager, "MediaRouteChooserDialog")
            }
        } catch (_: Exception) {}
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
                        }
                        addJavascriptInterface(YouTubeJsBridge(onTimeUpdate), "AndroidBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false // Permite navegação interna no player do YouTube
                            }
                        }
                        webChromeClient = WebChromeClient()

                        loadDataWithBaseURL("https://www.youtube-nocookie.com", buildYouTubePlayerHtml(video.id, initialStartSeconds), "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                }
            )

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
    onTimeUpdate: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                webViewRef?.evaluateJavascript("if (typeof player !== 'undefined') { player.pauseVideo(); } else { document.querySelector('video')?.pause(); }", null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
                    }
                    addJavascriptInterface(YouTubeJsBridge(onTimeUpdate), "AndroidBridge")
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()

                    loadDataWithBaseURL("https://www.youtube-nocookie.com", buildYouTubePlayerHtml(video.id, initialStartSeconds), "text/html", "UTF-8", null)
                    webViewRef = this
                }
            }
        )

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
