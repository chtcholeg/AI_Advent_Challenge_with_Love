package ru.chtcholeg.agent

import androidx.compose.runtime.*
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import ru.chtcholeg.agent.presentation.agent.AgentIntent
import ru.chtcholeg.agent.presentation.agent.AgentScreen
import ru.chtcholeg.agent.presentation.agent.AgentStore
import ru.chtcholeg.agent.presentation.session.SessionListScreen
import ru.chtcholeg.agent.presentation.settings.SettingsScreen
import ru.chtcholeg.agent.presentation.theme.AgentTheme

enum class Screen {
    AGENT,
    SETTINGS,
    SESSION_LIST
}

@Composable
fun App() {
    KoinContext {
        AgentTheme {
            var currentScreen by remember { mutableStateOf(Screen.AGENT) }
            val agentStore: AgentStore = koinInject()

            when (currentScreen) {
                Screen.AGENT -> {
                    AgentScreen(
                        onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                        onNavigateToSessions = { currentScreen = Screen.SESSION_LIST }
                    )
                }

                Screen.SETTINGS -> {
                    SettingsScreen(
                        onNavigateBack = { currentScreen = Screen.AGENT }
                    )
                }

                Screen.SESSION_LIST -> {
                    SessionListScreen(
                        onNavigateBack = { currentScreen = Screen.AGENT },
                        onSessionSelected = { sessionId ->
                            agentStore.dispatch(AgentIntent.LoadSession(sessionId))
                            currentScreen = Screen.AGENT
                        },
                        onNewChat = {
                            agentStore.dispatch(AgentIntent.NewChat)
                            currentScreen = Screen.AGENT
                        }
                    )
                }
            }
        }
    }
}
