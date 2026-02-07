package ru.chtcholeg.app.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android implementation - empty as Android handles scrollbars natively
 */
@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    // Android shows scrollbars natively when using verticalScroll
    // No need to add explicit scrollbar component
}
