package ru.chtcholeg.app.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.presentation.components.MessageInput
import ru.chtcholeg.app.presentation.components.MessageList
import ru.chtcholeg.app.presentation.theme.ChatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
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
                    containerColor = ChatColors.HeaderBackground,
                    titleContentColor = ChatColors.HeaderText,
                    actionIconContentColor = ChatColors.HeaderText
                )
            )
        },
        bottomBar = {
            Column {
                // Show loading indicator
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Show error message with retry button
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
