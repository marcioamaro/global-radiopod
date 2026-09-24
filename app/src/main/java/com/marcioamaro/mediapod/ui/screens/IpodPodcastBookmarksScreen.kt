package com.marcioamaro.mediapod.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcioamaro.mediapod.data.repository.EpisodeBookmark
import com.marcioamaro.mediapod.ui.components.SelectableLazyColumn

/** Formata tempo em milissegundos para MM:SS ou HH:MM:SS */
private fun formatBookmarkTime(ms: Long): String {
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
 * NÍVEL 1: Lista de Pastas/Programas de Podcasts que possuem marcações salvas.
 * Navegável 100% via Click Wheel (SelectableLazyColumn) e LCD retrô.
 */
@Composable
fun IpodPodcastBookmarkFoldersScreen(
    folders: List<Pair<String, List<EpisodeBookmark>>>,
    selectedIndex: Int,
    onSelectFolder: (Int) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    if (folders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backlightBg)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = backlightTextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "NENHUMA MARCAÇÃO",
                    color = backlightTextPrimary,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (12f * fontScale).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Marque momentos durante a reprodução do podcast.",
                    color = backlightTextSecondary,
                    fontFamily = fontFamily,
                    fontSize = (10f * fontScale).sp,
                    lineHeight = (13f * fontScale).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    SelectableLazyColumn(
        items = folders,
        selectedIndex = selectedIndex,
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp)
    ) { index, (folderName, marks), isSelected ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) backlightHighlight else Color.Transparent)
                .clickable { onSelectFolder(index) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else backlightTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        color = if (isSelected) Color.White else backlightTextPrimary,
                        fontSize = (11f * fontScale).sp,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${marks.size} ${if (marks.size == 1) "marcação" else "marcações"}",
                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else backlightTextSecondary,
                        fontSize = (9f * fontScale).sp,
                        fontFamily = fontFamily
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

/**
 * NÍVEL 2: Lista de Marcações de um Podcast específico.
 * Navegável via Click Wheel, exibe timestamp, título do episódio e anotação.
 */
@Composable
fun IpodPodcastBookmarkListScreen(
    bookmarks: List<EpisodeBookmark>,
    selectedIndex: Int,
    onSelectBookmark: (EpisodeBookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily,
    fontScale: Float,
    isBold: Boolean
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backlightBg)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PASTA VAZIA",
                color = backlightTextSecondary,
                fontFamily = fontFamily,
                fontSize = (11f * fontScale).sp
            )
        }
        return
    }

    SelectableLazyColumn(
        items = bookmarks,
        selectedIndex = selectedIndex,
        modifier = Modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { index, mark, isSelected ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) backlightHighlight else Color.Transparent)
                .clickable { onSelectBookmark(mark) }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else backlightHighlight,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatBookmarkTime(mark.positionMs),
                            color = if (isSelected) Color.White else backlightHighlight,
                            fontSize = (10f * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily
                        )
                        if (mark.note.isNotBlank()) {
                            Text(
                                text = " • ${mark.note}",
                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else backlightTextPrimary,
                                fontSize = (10f * fontScale).sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = mark.episode.title,
                        color = if (isSelected) Color.White.copy(alpha = 0.8f) else backlightTextSecondary,
                        fontSize = (9f * fontScale).sp,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = { onDeleteBookmark(mark.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir marcação",
                    tint = if (isSelected) Color.White else backlightTextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
