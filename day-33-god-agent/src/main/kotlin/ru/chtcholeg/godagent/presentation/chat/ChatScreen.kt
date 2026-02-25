package ru.chtcholeg.godagent.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import ru.chtcholeg.godagent.data.repository.SettingsRepository
import ru.chtcholeg.godagent.domain.model.AgentStep
import ru.chtcholeg.godagent.domain.model.ChatMessage
import ru.chtcholeg.godagent.domain.model.ChatSession
import ru.chtcholeg.godagent.presentation.components.MessageInput
import ru.chtcholeg.godagent.presentation.components.MessageItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    store: ChatStore,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    val settingsRepository: SettingsRepository = koinInject()
    val settings by settingsRepository.settings.collectAsState()
    var showSidebar by remember { mutableStateOf(true) }

    Row(modifier = modifier.fillMaxSize()) {
        // Sidebar
        AnimatedVisibility(visible = showSidebar) {
            SessionSidebar(
                sessions = state.sessions,
                currentSessionId = state.currentSession?.id,
                onNewSession = { store.dispatch(ChatIntent.NewSession) },
                onSelectSession = { store.dispatch(ChatIntent.SelectSession(it)) },
                onDeleteSession = { store.dispatch(ChatIntent.DeleteSession(it)) },
                modifier = Modifier.width(260.dp).fillMaxHeight()
            )
        }

        VerticalDivider()

        // Main chat area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Top bar
            TopAppBar(
                title = {
                    Column {
                        Text("God Agent", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = settings.selectedModelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showSidebar = !showSidebar }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle sidebar"
                        )
                    }
                },
                actions = {
                    if (state.isAgentRunning) {
                        IconButton(onClick = { store.dispatch(ChatIntent.StopAgent) }) {
                            Icon(
                                Icons.Default.StopCircle,
                                contentDescription = "Stop agent",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    IconButton(onClick = { store.dispatch(ChatIntent.RefreshOllamaModels) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Ollama models")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            // Loading indicator
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Error banner
            state.error?.let { error ->
                val clipboardManager = LocalClipboardManager.current
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(error)) }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Копировать ошибку",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        TextButton(onClick = { store.dispatch(ChatIntent.RetryLastMessage) }) {
                            Text("Повтор", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Messages + ThinkingBlock
            if (state.currentMessages.isEmpty() && !state.isAgentRunning) {
                EmptyChat(
                    modifier = Modifier.weight(1f),
                    modelName = settings.selectedModelId,
                    isOllamaAvailable = state.isOllamaAvailable
                )
            } else {
                ChatMessageList(
                    messages = state.currentMessages,
                    agentSteps = state.agentSteps,
                    isAgentRunning = state.isAgentRunning,
                    statusMessage = state.currentStatusMessage,
                    modifier = Modifier.weight(1f)
                )
            }

            // STT error banner
            state.sttError?.let { sttError ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Голос: $sttError",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Input
            MessageInput(
                onSendMessage = { store.dispatch(ChatIntent.SendMessage(it)) },
                onStopAgent = { store.dispatch(ChatIntent.StopAgent) },
                isLoading = state.isLoading,
                isAgentRunning = state.isAgentRunning,
                isRecording = state.isRecording,
                isPassiveListening = state.isPassiveListening,
                isInitializingSTT = state.isInitializingSTT,
                keywordsEnabled = settings.voiceKeywords.enabled,
                onStartRecording = { store.dispatch(ChatIntent.StartVoiceRecording) },
                onStopRecording = { store.dispatch(ChatIntent.StopVoiceRecording) },
                onStartPassiveListening = { store.dispatch(ChatIntent.StartPassiveListening) },
                onStopPassiveListening = { store.dispatch(ChatIntent.StopPassiveListening) },
                recognizedText = state.recognizedText,
                partialText = state.partialRecognizedText,
                clearInputTrigger = state.clearInputTrigger,
                onRecognizedTextConsumed = { store.dispatch(ChatIntent.ConsumeRecognizedText) }
            )
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    agentSteps: List<AgentStep>,
    isAgentRunning: Boolean,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll when new messages arrive or agent is running
    LaunchedEffect(messages.size, isAgentRunning, agentSteps.size) {
        if (messages.isNotEmpty() || isAgentRunning) {
            // Scroll to end
            val target = maxOf(0, messages.size + if (isAgentRunning) 1 else 0)
            listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(vertical = 8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageItem(message = message)
        }

        // Show ThinkingBlock at the end when agent is running or has steps
        if (isAgentRunning || agentSteps.isNotEmpty()) {
            item(key = "thinking_block") {
                ThinkingBlock(
                    steps = agentSteps,
                    isRunning = isAgentRunning,
                    statusMessage = statusMessage,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SessionSidebar(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "История",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onNewSession,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New chat",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Нет сессий",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            val grouped = groupSessionsByDate(sessions)

            LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                grouped.forEach { (label, group) ->
                    item {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(group, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            isSelected = session.id == currentSessionId,
                            onSelect = { onSelectSession(session.id) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить сессию?") },
            text = { Text("Это действие необратимо.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }

    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${session.messages.size} сообщений",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun EmptyChat(
    modifier: Modifier = Modifier,
    modelName: String,
    isOllamaAvailable: Boolean
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "God Agent готов",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Модель: $modelName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        if (!isOllamaAvailable && !modelName.startsWith("GigaChat")) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ollama недоступна. Убедитесь, что ollama запущена.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}

private fun groupSessionsByDate(sessions: List<ChatSession>): List<Pair<String, List<ChatSession>>> {
    val now = Calendar.getInstance()
    val today = now.get(Calendar.DAY_OF_YEAR)
    val year = now.get(Calendar.YEAR)

    val todayGroup = mutableListOf<ChatSession>()
    val yesterdayGroup = mutableListOf<ChatSession>()
    val last7DaysGroup = mutableListOf<ChatSession>()
    val olderGroup = mutableListOf<ChatSession>()

    for (session in sessions) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = session.updatedAt
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val sessionYear = cal.get(Calendar.YEAR)
        val diff = if (sessionYear == year) today - dayOfYear else Int.MAX_VALUE

        when {
            diff == 0 -> todayGroup.add(session)
            diff == 1 -> yesterdayGroup.add(session)
            diff in 2..6 -> last7DaysGroup.add(session)
            else -> olderGroup.add(session)
        }
    }

    return buildList {
        if (todayGroup.isNotEmpty()) add("Сегодня" to todayGroup)
        if (yesterdayGroup.isNotEmpty()) add("Вчера" to yesterdayGroup)
        if (last7DaysGroup.isNotEmpty()) add("Последние 7 дней" to last7DaysGroup)
        if (olderGroup.isNotEmpty()) add("Ранее" to olderGroup)
    }
}
