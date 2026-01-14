package ru.chtcholeg.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import ru.chtcholeg.app.presentation.chat.ChatScreen
import ru.chtcholeg.app.presentation.chat.ChatStore
import ru.chtcholeg.app.presentation.settings.SettingsScreen
import ru.chtcholeg.app.presentation.theme.DarkColorScheme
import ru.chtcholeg.app.presentation.theme.LightColorScheme
import org.koin.compose.koinInject

enum class Screen {
    CHAT,
    SETTINGS
}

@Composable
fun App() {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme) {
        var currentScreen by remember { mutableStateOf(Screen.CHAT) }
        val store: ChatStore = koinInject()

        when (currentScreen) {
            Screen.CHAT -> ChatScreen(
                store = store,
                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
            )
            Screen.SETTINGS -> SettingsScreen(
                onNavigateBack = { currentScreen = Screen.CHAT }
            )
        }
    }
}
