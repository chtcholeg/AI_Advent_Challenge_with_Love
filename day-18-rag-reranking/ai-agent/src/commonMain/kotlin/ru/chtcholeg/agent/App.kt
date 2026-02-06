package ru.chtcholeg.agent

import androidx.compose.runtime.*
import org.koin.compose.KoinContext
import ru.chtcholeg.agent.presentation.agent.AgentScreen
import ru.chtcholeg.agent.presentation.settings.SettingsScreen
import ru.chtcholeg.agent.presentation.theme.AgentTheme

enum class Screen {
    AGENT,
    SETTINGS
}

@Composable
fun App() {
    KoinContext {
        AgentTheme {
            var currentScreen by remember { mutableStateOf(Screen.AGENT) }

            when (currentScreen) {
                Screen.AGENT -> {
                    AgentScreen(
                        onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                    )
                }

                Screen.SETTINGS -> {
                    SettingsScreen(
                        onNavigateBack = { currentScreen = Screen.AGENT }
                    )
                }
            }
        }
    }
}
