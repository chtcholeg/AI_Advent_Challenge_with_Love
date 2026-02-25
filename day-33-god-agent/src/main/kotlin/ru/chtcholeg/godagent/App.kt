package ru.chtcholeg.godagent

import androidx.compose.runtime.*
import org.koin.compose.koinInject
import ru.chtcholeg.godagent.presentation.chat.ChatScreen
import ru.chtcholeg.godagent.presentation.chat.ChatStore
import ru.chtcholeg.godagent.presentation.settings.SettingsScreen
import ru.chtcholeg.godagent.presentation.theme.AppTheme

enum class Screen { CHAT, SETTINGS }

@Composable
fun App() {
    AppTheme {
        var screen by remember { mutableStateOf(Screen.CHAT) }
        val store: ChatStore = koinInject()

        when (screen) {
            Screen.CHAT -> ChatScreen(
                store = store,
                onNavigateToSettings = { screen = Screen.SETTINGS }
            )
            Screen.SETTINGS -> SettingsScreen(
                store = store,
                onNavigateBack = { screen = Screen.CHAT }
            )
        }
    }
}
