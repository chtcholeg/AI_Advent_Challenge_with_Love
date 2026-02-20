package ru.chtcholeg.agent.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific scrollable box with vertical scrollbar.
 * Desktop version shows scrollbar, Android version doesn't.
 */
@Composable
expect fun ScrollableBox(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
