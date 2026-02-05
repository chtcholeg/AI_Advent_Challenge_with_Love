package ru.chtcholeg.app.presentation.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import ru.chtcholeg.app.data.local.ChatLocalRepository
import ru.chtcholeg.app.domain.model.MessageType
import ru.chtcholeg.app.presentation.components.MessageInput
import ru.chtcholeg.app.presentation.components.MessageList
import ru.chtcholeg.app.presentation.theme.chatColors
import org.koin.compose.koinInject

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
    val chatLocalRepository: ChatLocalRepository = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val (originalMessages, setOriginalMessages) = remember { mutableStateOf<List<ru.chtcholeg.app.domain.model.ChatMessage>?>(null) }

    val handleLoadCompressedMessages: () -> Unit = {
        coroutineScope.launch {
            try {
                val origSessionId = state.originalSessionId
                val comprPoint = state.compressionPoint
                if (origSessionId != null && comprPoint != null) {
                    val messages = chatLocalRepository.getMessagesBeforeCompression(
                        origSessionId,
                        comprPoint
                    ).first()
                    setOriginalMessages(messages)
                }
            } catch (e: Exception) {
                // Silently fail to avoid disrupting UI - no original messages will be shown
            }
        }
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
                        // Reminder indicator chip
                        state.activeReminder?.let { reminder ->
                            if (reminder.enabled && reminder.channel.isNotBlank()) {
                                val pulseAlpha by animateFloatAsState(
                                    targetValue = 0.5f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 2000)
                                    ),
                                    label = "reminder_pulse"
                                )
                                Text(
                                    text = "\u23F0 @${reminder.channel} · ${reminder.interval.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primaryAccent,
                                    modifier = Modifier
                                        .background(
                                            color = colors.primaryAccent.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .alpha(0.6f + pulseAlpha * 0.4f)
                                )
                            }
                        }
                    }
                },
                actions = {
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
                        onClick = { store.dispatch(ChatIntent.SummarizeAndReplaceChat) },
                        enabled = state.messages.filter { it.messageType != MessageType.SYSTEM }.size >= 2 && !state.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Create,
                            contentDescription = "Compress conversation history"
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
                .padding(paddingValues),
            compressionPoint = state.compressionPoint,
            originalSessionId = state.originalSessionId,
            compressedMessagesCount = state.compressedMessagesCount,
            showCompressedHistory = state.showCompressedHistory,
            onToggleCompressed = {
                store.dispatch(ChatIntent.ToggleCompressedHistory)
            },
            onUndoCompression = {
                store.dispatch(ChatIntent.UndoCompression)
            },
            onLoadCompressedMessages = handleLoadCompressedMessages,
            compressedMessages = originalMessages
        )
    }
}
