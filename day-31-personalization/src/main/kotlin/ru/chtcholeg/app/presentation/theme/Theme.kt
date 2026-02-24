package ru.chtcholeg.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkBluePurple = Color(0xFF22223B)
private val MediumBlueGray = Color(0xFF4A4E69)
private val Mauve = Color(0xFF9A8C98)
private val LightMauve = Color(0xFFC9ADA7)
private val LightCream = Color(0xFFF2E9E4)

val AppLightColors = lightColorScheme(
    primary = DarkBluePurple,
    onPrimary = LightCream,
    secondary = MediumBlueGray,
    onSecondary = LightCream,
    background = LightCream,
    onBackground = DarkBluePurple,
    surface = LightCream,
    onSurface = DarkBluePurple,
    surfaceVariant = LightMauve,
    onSurfaceVariant = DarkBluePurple,
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Mauve
)

val AppDarkColors = darkColorScheme(
    primary = LightMauve,
    onPrimary = DarkBluePurple,
    secondary = Mauve,
    onSecondary = DarkBluePurple,
    background = DarkBluePurple,
    onBackground = LightCream,
    surface = MediumBlueGray,
    onSurface = LightCream,
    surfaceVariant = Color(0xFF2E2E4A),
    onSurfaceVariant = LightMauve,
    error = Color(0xFFF2B8B8),
    onError = Color(0xFF601410),
    outline = Mauve
)

@Composable
fun AppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColors else AppLightColors,
        content = content
    )
}
