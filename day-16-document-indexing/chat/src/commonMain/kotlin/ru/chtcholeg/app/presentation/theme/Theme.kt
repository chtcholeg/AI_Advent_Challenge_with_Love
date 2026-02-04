package ru.chtcholeg.app.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Minimalist Light Color Palette with Purple Accent
private val md_theme_light_primary = Color(0xFF7C4DFF) // Purple accent
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFE9DDFF)
private val md_theme_light_onPrimaryContainer = Color(0xFF22005D)

private val md_theme_light_secondary = Color(0xFF7C4DFF)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFE9DDFF)
private val md_theme_light_onSecondaryContainer = Color(0xFF22005D)

private val md_theme_light_tertiary = Color(0xFF7C4DFF)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFE9DDFF)
private val md_theme_light_onTertiaryContainer = Color(0xFF22005D)

private val md_theme_light_error = Color(0xFFBA1A1A)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_errorContainer = Color(0xFFFFDAD6)
private val md_theme_light_onErrorContainer = Color(0xFF410002)

private val md_theme_light_background = Color(0xFFFFFFFF) // Pure white
private val md_theme_light_onBackground = Color(0xFF202124) // Dark gray text
private val md_theme_light_surface = Color(0xFFF8F9FA) // Light gray surface
private val md_theme_light_onSurface = Color(0xFF202124) // Dark gray text

private val md_theme_light_surfaceVariant = Color(0xFFF1F3F4) // AI bubble color
private val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
private val md_theme_light_outline = Color(0xFFE8EAED) // Dividers

// Minimalist Dark Color Palette with Purple Accent
private val md_theme_dark_primary = Color(0xFFB388FF) // Light purple accent
private val md_theme_dark_onPrimary = Color(0xFF121212)
private val md_theme_dark_primaryContainer = Color(0xFF4F378A)
private val md_theme_dark_onPrimaryContainer = Color(0xFFE9DDFF)

private val md_theme_dark_secondary = Color(0xFFB388FF)
private val md_theme_dark_onSecondary = Color(0xFF121212)
private val md_theme_dark_secondaryContainer = Color(0xFF4F378A)
private val md_theme_dark_onSecondaryContainer = Color(0xFFE9DDFF)

private val md_theme_dark_tertiary = Color(0xFFB388FF)
private val md_theme_dark_onTertiary = Color(0xFF121212)
private val md_theme_dark_tertiaryContainer = Color(0xFF4F378A)
private val md_theme_dark_onTertiaryContainer = Color(0xFFE9DDFF)

private val md_theme_dark_error = Color(0xFFFFB4AB)
private val md_theme_dark_onError = Color(0xFF690005)
private val md_theme_dark_errorContainer = Color(0xFF93000A)
private val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

private val md_theme_dark_background = Color(0xFF121212) // Deep dark
private val md_theme_dark_onBackground = Color(0xFFE8EAED) // Light text
private val md_theme_dark_surface = Color(0xFF1E1E1E) // Dark gray surface
private val md_theme_dark_onSurface = Color(0xFFE8EAED) // Light text

private val md_theme_dark_surfaceVariant = Color(0xFF2D2D2D) // AI bubble color
private val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
private val md_theme_dark_outline = Color(0xFF3C3C3C) // Dividers

val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
)

val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
)
