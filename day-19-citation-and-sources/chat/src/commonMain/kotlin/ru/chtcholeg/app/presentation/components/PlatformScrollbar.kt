package ru.chtcholeg.app.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific vertical scrollbar.
 * On Desktop/Web: Shows visible scrollbar
 * On Android: Empty (Android handles scrollbars natively)
 */
@Composable
expect fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
)
