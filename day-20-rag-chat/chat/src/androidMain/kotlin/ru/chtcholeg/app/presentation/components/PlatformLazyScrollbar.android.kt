package ru.chtcholeg.app.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android implementation - empty as Android handles scrollbars natively for LazyColumn
 */
@Composable
actual fun PlatformLazyVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier
) {
    // Android shows scrollbars natively for LazyColumn
    // No need to add explicit scrollbar component
}
