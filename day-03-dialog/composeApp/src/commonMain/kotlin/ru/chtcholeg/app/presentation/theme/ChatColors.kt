package ru.chtcholeg.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Color constants for the chat UI
 * Elegant muted color scheme inspired by https://coolors.co/palette/22223b-4a4e69-9a8c98-c9ada7-f2e9e4
 */
object ChatColors {
    // Base palette colors
    private val DarkBluePurple = Color(0xFF22223B) // Dark blue-purple
    private val MediumBlueGray = Color(0xFF4A4E69) // Medium blue-gray
    private val Mauve = Color(0xFF9A8C98) // Mauve
    private val LightMauve = Color(0xFFC9ADA7) // Light mauve/beige
    private val LightCream = Color(0xFFF2E9E4) // Light cream

    // Background gradient colors (light to medium tones)
    val BackgroundGradientTop = LightCream
    val BackgroundGradientMiddle = LightMauve
    val BackgroundGradientBottom = Mauve

    // User message bubble (right side) - Dark blue-purple
    val UserBubbleBackground = DarkBluePurple
    val UserBubbleText = LightCream
    val UserBubbleTimestamp = LightCream.copy(alpha = 0.8f)

    // AI message bubble (left side) - Medium blue-gray
    val AiBubbleBackground = MediumBlueGray
    val AiBubbleText = LightCream
    val AiBubbleTimestamp = LightCream.copy(alpha = 0.8f)

    // System message bubble (center) - Mauve with slight transparency
    val SystemBubbleBackground = Mauve.copy(alpha = 0.7f)
    val SystemBubbleText = DarkBluePurple
    val SystemBubbleTimestamp = DarkBluePurple.copy(alpha = 0.7f)

    // Header colors - Dark blue-purple
    val HeaderBackground = DarkBluePurple
    val HeaderText = LightCream

    // Input area colors
    val InputBackground = LightCream
    val InputBorder = Mauve
}
