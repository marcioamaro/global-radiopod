package com.marcioamaro.mediapod.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable Selectable LazyColumn with authentic iPod Classic anchored scroll mechanics.
 *
 * Mechanics:
 * - When scrolling down, the highlight travels freely across visible rows.
 * - When reaching the bottom threshold (e.g. last visible item), the selection anchors
 *   and items scroll upwards smoothly beneath it via [LazyListState.animateScrollToItem].
 * - When scrolling up, the selection anchors at the top threshold and items scroll downwards.
 * - If all items fit within the visible viewport, scrolling is skipped to prevent unwanted jitter.
 */
@Composable
fun <T> SelectableLazyColumn(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    anchorThresholdBottom: Int = 1,
    anchorThresholdTop: Int = 1,
    key: ((Int, T) -> Any)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    itemContent: @Composable (Int, T, Boolean) -> Unit
) {
    LaunchedEffect(selectedIndex, items.size) {
        if (items.isEmpty() || selectedIndex !in items.indices) return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@LaunchedEffect

        // If all items fit in the viewport, let selection travel without scrolling
        if (visibleItems.size >= items.size &&
            visibleItems.first().index == 0 &&
            visibleItems.last().index == items.lastIndex
        ) {
            return@LaunchedEffect
        }

        val visibleStart = visibleItems.first().index
        val visibleEnd = visibleItems.last().index
        val visibleCount = visibleItems.size

        try {
            when {
                // Passed bottom threshold -> anchor selection to bottom line and scroll down
                selectedIndex >= visibleEnd - anchorThresholdBottom -> {
                    val target = (selectedIndex - visibleCount + anchorThresholdBottom + 1)
                        .coerceIn(0, items.lastIndex)
                    listState.animateScrollToItem(target)
                }
                // Passed top threshold -> anchor selection to top line and scroll up
                selectedIndex <= visibleStart + anchorThresholdTop -> {
                    val target = (selectedIndex - anchorThresholdTop)
                        .coerceIn(0, items.lastIndex)
                    listState.animateScrollToItem(target)
                }
            }
        } catch (_: Exception) {
            // Catches any cancellation or layout animation exceptions gracefully
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment
    ) {
        if (key != null) {
            itemsIndexed(items, key = { index, item -> key(index, item) }) { index, item ->
                itemContent(index, item, index == selectedIndex)
            }
        } else {
            itemsIndexed(items) { index, item ->
                itemContent(index, item, index == selectedIndex)
            }
        }
    }
}
