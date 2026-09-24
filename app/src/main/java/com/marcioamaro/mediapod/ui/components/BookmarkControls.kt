package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.R
import com.marcioamaro.mediapod.data.model.PodcastEpisode
import com.marcioamaro.mediapod.data.repository.EpisodeBookmarks

/** Formata tempo em milissegundos para MM:SS ou HH:MM:SS */
private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Diálogo LCD retrô para criação e edição de marcações.
 * Contido 100% no visor LCD, com cores do tema ativo e total conformidade com acessibilidade.
 */
@Composable
fun BookmarkEditor(
    position: Long,
    note: String,
    onDismiss: () -> Unit,
    onSave: (Long, String) -> Unit,
    backlightBg: Color = Color(0xFFC0CAD0),
    backlightTextPrimary: Color = Color(0xFF1E293B),
    backlightTextSecondary: Color = Color(0xFF64748B),
    backlightHighlight: Color = Color(0xFF0F172A),
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    episodeTitle: String = ""
) {
    val context = LocalContext.current
    var seconds by remember { mutableStateOf((position / 1000).toString()) }
    var text by remember { mutableStateOf(note) }
    val valid = seconds.toLongOrNull()?.let { it in 0..604800 } == true && text.length <= 2000

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(6.dp))
                .background(backlightBg)
                .border(1.5.dp, backlightHighlight, RoundedCornerShape(6.dp))
                .clickable(enabled = false) {}
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cabeçalho LCD
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = backlightHighlight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = context.getString(R.string.bookmark_title).uppercase(),
                    color = backlightTextPrimary,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (12f * fontScale).sp
                )
            }

            if (episodeTitle.isNotBlank()) {
                Text(
                    text = episodeTitle,
                    color = backlightTextSecondary,
                    fontFamily = fontFamily,
                    fontSize = (9.5f * fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = backlightTextSecondary.copy(alpha = 0.25f), thickness = 1.dp)

            // Campo de segundos com label acessível oficial
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it },
                label = { Text(context.getString(R.string.bookmark_seconds), color = backlightTextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedBorderColor = backlightHighlight,
                    unfocusedBorderColor = backlightTextSecondary.copy(alpha = 0.6f),
                    cursorColor = backlightHighlight,
                    focusedContainerColor = backlightTextPrimary.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Campo de anotação
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                label = { Text(context.getString(R.string.bookmark_note), color = backlightTextSecondary) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedBorderColor = backlightHighlight,
                    unfocusedBorderColor = backlightTextSecondary.copy(alpha = 0.6f),
                    cursorColor = backlightHighlight,
                    focusedContainerColor = backlightTextPrimary.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Botões de ação estilizados com estilo retrô LCD e semântica acessível
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancelar
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .border(1.dp, backlightTextSecondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .background(backlightTextSecondary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = context.getString(R.string.feature_cancel),
                        color = backlightTextSecondary,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (11f * fontScale).sp
                    )
                }

                // Salvar
                TextButton(
                    enabled = valid,
                    onClick = { onSave(seconds.toLong() * 1000, text) },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .border(
                            1.dp,
                            if (valid) backlightHighlight else backlightTextSecondary.copy(alpha = 0.25f),
                            RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (valid) backlightHighlight.copy(alpha = 0.22f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    Text(
                        text = context.getString(R.string.feature_save),
                        color = if (valid) backlightTextPrimary else backlightTextSecondary.copy(alpha = 0.4f),
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (11f * fontScale).sp
                    )
                }
            }
        }
    }
}

/**
 * Botão LCD de largura total posicionado acima da barra de progresso do podcast,
 * e gerenciador do diálogo de marcação.
 */
@Composable
fun AddBookmarkControl(
    episode: PodcastEpisode,
    positionMs: Long,
    backlightBg: Color = Color(0xFFC0CAD0),
    backlightTextPrimary: Color = Color(0xFF1E293B),
    backlightTextSecondary: Color = Color(0xFF64748B),
    backlightHighlight: Color = Color(0xFF0F172A),
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true
) {
    val context = LocalContext.current
    val bookmarks = remember(context) { EpisodeBookmarks(context) }
    var isOpen by remember { mutableStateOf(false) }

    // Botão LCD de largura total
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(backlightTextPrimary.copy(alpha = 0.08f))
            .border(1.dp, backlightTextPrimary.copy(alpha = 0.28f), RoundedCornerShape(3.dp))
            .clickable { isOpen = true }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.BookmarkAdd,
                contentDescription = null,
                tint = backlightTextPrimary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "MARCAR NESTE PONTO (${formatTime(positionMs)})",
                color = backlightTextPrimary,
                fontSize = (10f * fontScale).sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                fontFamily = fontFamily
            )
        }
    }

    if (isOpen) {
        BookmarkEditor(
            position = positionMs,
            note = "",
            episodeTitle = episode.title,
            backlightBg = backlightBg,
            backlightTextPrimary = backlightTextPrimary,
            backlightTextSecondary = backlightTextSecondary,
            backlightHighlight = backlightHighlight,
            fontFamily = fontFamily,
            fontScale = fontScale,
            onDismiss = { isOpen = false },
            onSave = { time, note ->
                bookmarks.reload()
                bookmarks.save(episode, time, note)
                isOpen = false
            }
        )
    }
}
