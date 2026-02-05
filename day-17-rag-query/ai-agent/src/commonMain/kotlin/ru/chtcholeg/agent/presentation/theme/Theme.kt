package ru.chtcholeg.agent.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Console-style dark theme for AI Agent.
 */
private val ConsoleColorScheme = darkColorScheme(
    primary = Color(0xFF6CB6FF),
    onPrimary = Color(0xFF1E1E1E),
    primaryContainer = Color(0xFF2D2D2D),
    onPrimaryContainer = Color(0xFFE6E6E6),
    secondary = Color(0xFFA9DC76),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF2D2D2D),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFFFFD866),
    onTertiary = Color(0xFF1E1E1E),
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFFF6188),
    onError = Color(0xFF1E1E1E),
    errorContainer = Color(0xFF3D2D2D),
    onErrorContainer = Color(0xFFFF6188),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF303030)
)

@Composable
fun AgentTheme(
    darkTheme: Boolean = true,  // Always use dark theme for console style
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ConsoleColorScheme,
        content = content
    )
}
