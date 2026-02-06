package ru.chtcholeg.app.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific vertical scrollbar for LazyColumn/LazyRow.
 * On Desktop/Web: Shows visible scrollbar
 * On Android: Empty (Android handles scrollbars natively)
 */
@Composable
expect fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
)
