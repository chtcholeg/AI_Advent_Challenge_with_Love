package ru.chtcholeg.agent.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android implementation without scrollbar (LazyColumn handles scrolling natively).
 */
@Composable
actual fun ScrollableBox(
    listState: LazyListState,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}
