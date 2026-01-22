package ru.chtcholeg.app.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.domain.model.MessageType
import ru.chtcholeg.app.presentation.components.MessageInput
import ru.chtcholeg.app.presentation.components.MessageList
import ru.chtcholeg.app.presentation.theme.chatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    val colors = chatColors()
    var showSummarizeDialog by remember { mutableStateOf(false) }

    if (showSummarizeDialog) {
        AlertDialog(
            onDismissRequest = { showSummarizeDialog = false },
            title = {
                Text(
                    text = "Summarize Conversation",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose how to summarize:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Add Summary — keeps chat and adds summary at the end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Replace — replaces entire chat with summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSummarizeDialog = false
                        store.dispatch(ChatIntent.SummarizeAndReplaceChat)
                    }
                ) {
                    Text(
                        text = "Replace",
                        color = colors.primaryAccent
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSummarizeDialog = false
                        store.dispatch(ChatIntent.SummarizeChat)
                    }
                ) {
                    Text(
                        text = "Add Summary",
                        color = colors.primaryAccent
                    )
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.headerText,
            textContentColor = colors.headerText
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Chat",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (state.currentModelName.isNotEmpty()) {
                            Text(
                                text = state.currentModelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.headerText.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat"
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Chat History"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(
                        onClick = { showSummarizeDialog = true },
                        enabled = state.messages.filter { it.messageType != MessageType.SYSTEM }.size >= 2 && !state.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Summarize conversation"
                        )
                    }
                    IconButton(
                        onClick = { store.dispatch(ChatIntent.CopyAllMessages) },
                        enabled = state.messages.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy all messages"
                        )
                    }
                    IconButton(
                        onClick = { store.dispatch(ChatIntent.ClearChat) },
                        enabled = state.messages.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear chat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.headerBackground,
                    titleContentColor = colors.headerText,
                    actionIconContentColor = colors.headerText
                )
            )
        },
        bottomBar = {
            Column {
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.primaryAccent,
                        trackColor = colors.divider
                    )
                }

                state.error?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { store.dispatch(ChatIntent.RetryLastMessage) }
                            ) {
                                Text(
                                    "Retry",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                MessageInput(
                    onSendMessage = { message ->
                        store.dispatch(ChatIntent.SendMessage(message))
                    },
                    isLoading = state.isLoading
                )
            }
        }
    ) { paddingValues ->
        MessageList(
            messages = state.messages,
            onCopyMessage = { messageId ->
                store.dispatch(ChatIntent.CopyMessage(messageId))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
