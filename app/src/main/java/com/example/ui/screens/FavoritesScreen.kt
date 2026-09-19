package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RadioStation
import com.example.ui.components.StationLargeCard

@Composable
fun FavoritesScreen(
    favorites: List<RadioStation>,
    currentStationId: String?,
    selectedIndex: Int,
    onSelectStation: (RadioStation) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    backlightBg: Color,
    backlightTextPrimary: Color,
    backlightTextSecondary: Color,
    backlightHighlight: Color,
    fontFamily: FontFamily = FontFamily.Monospace,
    fontScale: Float = 1.0f,
    isBold: Boolean = true,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val displayedFavorites = remember(favorites, searchQuery) {
        if (searchQuery.isBlank()) {
            favorites
        } else {
            val q = searchQuery.trim().lowercase()
            favorites.filter {
                it.name.lowercase().contains(q) ||
                it.country.lowercase().contains(q) ||
                it.primaryGenre.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backlightBg)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("favorites_screen")
    ) {
        // Favorites Header Banner with LCD badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x22000000))
                .border(1.dp, backlightTextPrimary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = backlightTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ESTAÇÕES FAVORITAS",
                    color = backlightTextPrimary,
                    fontSize = (11 * fontScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Text(
                text = "${displayedFavorites.size}/${favorites.size} SALVAS",
                color = backlightTextPrimary.copy(alpha = 0.85f),
                fontSize = (10 * fontScale).sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Black
            )
        }

        if (favorites.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .testTag("favorites_search_input"),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    fontSize = (11.5f * fontScale).sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                ),
                placeholder = {
                    Text(
                        text = "Filtrar favoritos...",
                        fontSize = (11 * fontScale).sp,
                        color = backlightTextPrimary.copy(alpha = 0.65f),
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = backlightTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar",
                                tint = backlightTextPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = backlightTextPrimary,
                    unfocusedBorderColor = backlightTextPrimary.copy(alpha = 0.55f),
                    focusedTextColor = backlightTextPrimary,
                    unfocusedTextColor = backlightTextPrimary,
                    focusedContainerColor = Color(0x26000000),
                    unfocusedContainerColor = Color(0x18000000)
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = backlightHighlight.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhuma Rádio Favorita",
                        color = backlightTextPrimary,
                        fontSize = (14 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Navegue pelo Top Mundial, Gêneros ou Países e clique no coração para salvar suas rádios prediletas com ícones grandes aqui!",
                        color = backlightTextSecondary,
                        fontSize = (11 * fontScale).sp,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        lineHeight = (15 * fontScale).sp
                    )
                }
            }
        } else if (displayedFavorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Nenhum favorito para \"$searchQuery\"",
                        color = backlightTextPrimary,
                        fontSize = (12 * fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            com.example.ui.components.SelectableLazyColumn(
                items = displayedFavorites,
                selectedIndex = selectedIndex,
                key = { _, item -> item.id },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { index, station, isSelected ->
                StationLargeCard(
                    station = station,
                    isSelected = isSelected,
                    isPlaying = station.id == currentStationId,
                    onClick = { onSelectStation(station) },
                    onDelete = { onDeleteFavorite(station.id) },
                    backlightTextPrimary = backlightTextPrimary,
                    backlightTextSecondary = backlightTextSecondary,
                    backlightHighlight = backlightHighlight,
                    fontFamily = fontFamily,
                    fontScale = fontScale,
                    isBold = isBold
                )
            }
        }
    }
}
