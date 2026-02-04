package ru.chtcholeg.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import ru.chtcholeg.app.presentation.chat.ChatIntent
import ru.chtcholeg.app.presentation.chat.ChatScreen
import ru.chtcholeg.app.presentation.chat.ChatStore
import ru.chtcholeg.app.presentation.session.SessionListScreen
import ru.chtcholeg.app.presentation.session.SessionListStore
import ru.chtcholeg.app.presentation.settings.SettingsScreen
import ru.chtcholeg.app.presentation.settings.mcp.McpManagementScreen
import ru.chtcholeg.app.presentation.theme.DarkColorScheme
import ru.chtcholeg.app.presentation.theme.LightColorScheme
import org.koin.compose.koinInject

enum class Screen {
    CHAT,
    SETTINGS,
    SESSION_LIST,
    MCP_MANAGEMENT
}

@Composable
fun App() {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(colorScheme = colorScheme) {
        var currentScreen by remember { mutableStateOf(Screen.CHAT) }
        val chatStore: ChatStore = koinInject()
        val sessionListStore: SessionListStore = koinInject()

        when (currentScreen) {
            Screen.CHAT -> ChatScreen(
                store = chatStore,
                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                onNavigateToHistory = { currentScreen = Screen.SESSION_LIST },
                onNewChat = { chatStore.dispatch(ChatIntent.CreateNewSession) }
            )
            Screen.SETTINGS -> SettingsScreen(
                onNavigateBack = { currentScreen = Screen.CHAT },
                onNavigateToMcpManagement = { currentScreen = Screen.MCP_MANAGEMENT }
            )
            Screen.MCP_MANAGEMENT -> McpManagementScreen(
                onNavigateBack = { currentScreen = Screen.SETTINGS }
            )
            Screen.SESSION_LIST -> SessionListScreen(
                store = sessionListStore,
                onNavigateBack = { currentScreen = Screen.CHAT },
                onSessionSelected = { sessionId ->
                    chatStore.dispatch(ChatIntent.LoadSession(sessionId))
                    currentScreen = Screen.CHAT
                },
                onNewChat = {
                    chatStore.dispatch(ChatIntent.CreateNewSession)
                    currentScreen = Screen.CHAT
                }
            )
        }
    }
}
