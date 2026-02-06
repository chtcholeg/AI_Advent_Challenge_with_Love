package ru.chtcholeg.agent.presentation.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import ru.chtcholeg.agent.data.repository.SettingsRepository
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.agent.domain.model.RagMode
import ru.chtcholeg.agent.presentation.components.MessageInput
import ru.chtcholeg.agent.presentation.components.MessageList
import ru.chtcholeg.agent.util.ClipboardManager
import ru.chtcholeg.agent.util.FileOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    onNavigateToSettings: () -> Unit,
    store: AgentStore = koinInject(),
    settingsRepository: SettingsRepository = koinInject()
) {
    val state by store.state.collectAsState()
    val settings by settingsRepository.settings.collectAsState()

    // Filter messages based on showSystemMessages setting
    val visibleMessages = if (settings.showSystemMessages) {
        state.messages
    } else {
        state.messages.filter { message ->
            message.type !in listOf(MessageType.TOOL_CALL, MessageType.TOOL_RESULT, MessageType.SYSTEM, MessageType.RAG_CONTEXT)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Minimal toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252525))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title with tools count
            Text(
                text = buildString {
                    append("AI Agent")
                    if (state.availableTools.isNotEmpty()) {
                        append(" · ${state.availableTools.size} tools")
                    }
                    if (settings.ragMode == RagMode.ON) {
                        append(" · RAG")
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFFB0B0B0)
            )

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Reload tools
                IconButton(
                    onClick = { store.dispatch(AgentIntent.ReloadTools) },
                    enabled = !state.toolsLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload tools",
                        tint = Color(0xFF6CB6FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy all messages
                IconButton(
                    onClick = {
                        val allText = state.messages.joinToString("\n\n") { message ->
                            val prefix = when (message.type) {
                                MessageType.USER -> "> "
                                MessageType.AI -> ""
                                MessageType.TOOL_CALL -> "[tool] "
                                MessageType.TOOL_RESULT -> "[result] "
                                MessageType.SCREENSHOT -> "[screenshot] "
                                MessageType.SYSTEM -> "[system] "
                                MessageType.ERROR -> "[error] "
                                MessageType.RAG_CONTEXT -> "[rag] "
                            }
                            prefix + message.content
                        }
                        ClipboardManager.copyToClipboard(allText)
                    },
                    enabled = state.messages.isNotEmpty(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy all",
                        tint = if (state.messages.isNotEmpty())
                            Color(0xFFA9DC76) else Color(0xFF404040),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear chat
                IconButton(
                    onClick = { store.dispatch(AgentIntent.ClearChat) },
                    enabled = state.messages.isNotEmpty() && !state.isLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = if (state.messages.isNotEmpty() && !state.isLoading)
                            Color(0xFFFF6188) else Color(0xFF404040),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFB0B0B0),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Error message (console style)
        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3D2020))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[error] $error",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFFF6188),
                    modifier = Modifier.weight(1f)
                )
                Row {
                    TextButton(
                        onClick = { ClipboardManager.copyToClipboard(error) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "copy",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0B0)
                        )
                    }
                    TextButton(
                        onClick = { store.dispatch(AgentIntent.RetryLastMessage) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "retry",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF6CB6FF)
                        )
                    }
                }
            }
        }

        // Messages list (filtered based on showSystemMessages setting)
        MessageList(
            messages = visibleMessages,
            isLoading = state.isLoading,
            onSourceClick = { path -> FileOpener.openFile(path) },
            modifier = Modifier.weight(1f)
        )

        // Input field
        MessageInput(
            onSendMessage = { message ->
                store.dispatch(AgentIntent.SendMessage(message))
            },
            enabled = !state.isLoading
        )
    }
}
