package ru.chtcholeg.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Chat color scheme supporting light and dark themes.
 * Minimalist design with purple accent inspired by Telegram/WhatsApp.
 */
data class ChatColorScheme(
    val background: Color,
    val surface: Color,
    val userBubbleBackground: Color,
    val userBubbleText: Color,
    val userBubbleTimestamp: Color,
    val aiBubbleBackground: Color,
    val aiBubbleText: Color,
    val aiBubbleTimestamp: Color,
    val systemBubbleBackground: Color,
    val systemBubbleText: Color,
    val systemBubbleTimestamp: Color,
    val headerBackground: Color,
    val headerText: Color,
    val primaryAccent: Color,
    val divider: Color,
    val inputBackground: Color,
    val inputText: Color,
    val inputPlaceholder: Color,
    val dropdownBackground: Color,
    val dropdownText: Color,
    val dropdownTextSecondary: Color
)

private val LightChatColorScheme = ChatColorScheme(
    background = Color(0xFFFFFFFF),          // Pure white
    surface = Color(0xFFF8F9FA),              // Light gray
    userBubbleBackground = Color(0xFF7C4DFF), // Purple
    userBubbleText = Color(0xFFFFFFFF),       // White
    userBubbleTimestamp = Color(0xFFFFFFFF).copy(alpha = 0.7f),
    aiBubbleBackground = Color(0xFFF1F3F4),   // Light gray
    aiBubbleText = Color(0xFF202124),         // Dark gray
    aiBubbleTimestamp = Color(0xFF202124).copy(alpha = 0.5f),
    systemBubbleBackground = Color(0xFFE8EAED).copy(alpha = 0.8f),
    systemBubbleText = Color(0xFF202124),
    systemBubbleTimestamp = Color(0xFF202124).copy(alpha = 0.5f),
    headerBackground = Color(0xFFFFFFFF),     // White
    headerText = Color(0xFF202124),           // Dark gray
    primaryAccent = Color(0xFF7C4DFF),        // Purple
    divider = Color(0xFFE8EAED),
    inputBackground = Color(0xFFF1F3F4),
    inputText = Color(0xFF202124),
    inputPlaceholder = Color(0xFF202124).copy(alpha = 0.5f),
    dropdownBackground = Color(0xFFFFFFFF),
    dropdownText = Color(0xFF202124),
    dropdownTextSecondary = Color(0xFF202124).copy(alpha = 0.6f)
)

private val DarkChatColorScheme = ChatColorScheme(
    background = Color(0xFF121212),           // Deep dark
    surface = Color(0xFF1E1E1E),              // Dark gray
    userBubbleBackground = Color(0xFFB388FF), // Light purple
    userBubbleText = Color(0xFF121212),       // Dark
    userBubbleTimestamp = Color(0xFF121212).copy(alpha = 0.7f),
    aiBubbleBackground = Color(0xFF2D2D2D),   // Gray
    aiBubbleText = Color(0xFFE8EAED),         // Light
    aiBubbleTimestamp = Color(0xFFE8EAED).copy(alpha = 0.5f),
    systemBubbleBackground = Color(0xFF3C3C3C).copy(alpha = 0.8f),
    systemBubbleText = Color(0xFFE8EAED),
    systemBubbleTimestamp = Color(0xFFE8EAED).copy(alpha = 0.5f),
    headerBackground = Color(0xFF1E1E1E),     // Dark
    headerText = Color(0xFFE8EAED),           // Light
    primaryAccent = Color(0xFFB388FF),        // Light purple
    divider = Color(0xFF3C3C3C),
    inputBackground = Color(0xFF2D2D2D),
    inputText = Color(0xFFE8EAED),
    inputPlaceholder = Color(0xFFE8EAED).copy(alpha = 0.5f),
    dropdownBackground = Color(0xFF2D2D2D),
    dropdownText = Color(0xFFE8EAED),
    dropdownTextSecondary = Color(0xFFE8EAED).copy(alpha = 0.6f)
)

/**
 * Provides the appropriate ChatColorScheme based on the current system theme.
 */
object ChatColors {
    val colors: ChatColorScheme
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) DarkChatColorScheme else LightChatColorScheme
}

/**
 * Helper function to get chat colors in composable context.
 */
@Composable
@ReadOnlyComposable
fun chatColors(): ChatColorScheme = ChatColors.colors
