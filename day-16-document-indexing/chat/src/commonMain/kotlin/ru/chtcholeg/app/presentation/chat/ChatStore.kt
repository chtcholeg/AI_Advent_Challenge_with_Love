package ru.chtcholeg.app.presentation.chat

import ru.chtcholeg.app.data.local.ChatLocalRepository
import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.MessageType
import ru.chtcholeg.shared.domain.model.Model
import ru.chtcholeg.app.domain.model.ResponseMode
import ru.chtcholeg.app.domain.usecase.SendMessageUseCase
import ru.chtcholeg.app.presentation.reminder.ReminderIntent
import ru.chtcholeg.app.presentation.reminder.ReminderStore
import ru.chtcholeg.app.util.ClipboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ChatStore(
    private val sendMessageUseCase: SendMessageUseCase,
    private val chatRepository: ChatRepository,
    private val chatLocalRepository: ChatLocalRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderStore: ReminderStore,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var lastUserMessage: String? = null
    private var currentResponseMode: ResponseMode = ResponseMode.NORMAL
    private var messagesSinceLastSummarization: Int = 0
    private var isSummarizing: Boolean = false
    private var currentSessionId: String? = null

    init {
        // Watch for settings changes
        coroutineScope.launch {
            settingsRepository.settings.collect { settings ->
                handleResponseModeChange(settings.responseMode)
                val model = Model.fromId(settings.model) ?: Model.GigaChat
                _state.update { it.copy(currentModelName = model.displayName) }
            }
        }

        // Watch for reminder summaries and inject them as REMINDER messages
        coroutineScope.launch {
            reminderStore.state.collect { reminderState ->
                _state.update { it.copy(activeReminder = reminderState.activeConfig) }

                if (reminderState.lastSummary != null && reminderState.lastSummaryChannel != null) {
                    val reminderMessage = ChatMessage(
                        content = reminderState.lastSummary,
                        isFromUser = false,
                        messageType = MessageType.REMINDER
                    )
                    _state.update { it.copy(messages = it.messages + reminderMessage) }

                    // Save reminder message to local storage if session is active
                    currentSessionId?.let { sessionId ->
                        chatLocalRepository.saveMessage(sessionId, reminderMessage)
                    }
                }
            }
        }
    }

    private fun handleResponseModeChange(newMode: ResponseMode) {
        if (newMode != currentResponseMode) {
            currentResponseMode = newMode

            // Unlock system prompt to allow updates from new UI settings
            // This overrides any system prompt locked from loaded session
            chatRepository.unlockSystemPrompt()

            val settings = settingsRepository.settings.value
            val preserveHistory = settings.preserveHistoryOnSystemPromptChange

            // Determine system message text based on mode
            val systemMessageText = when (newMode) {
                ResponseMode.STRUCTURED_JSON -> "Structured JSON response mode enabled. All AI responses will be in JSON format."
                ResponseMode.STRUCTURED_XML -> "Structured XML response mode enabled. All AI responses will be in XML format."
                ResponseMode.DIALOG -> "Dialog mode enabled. AI will ask clarifying questions one at a time to gather all necessary information before providing final result."
                ResponseMode.STEP_BY_STEP -> "Step-by-Step reasoning mode enabled. AI will solve problems by breaking them down into clear, logical steps."
                ResponseMode.EXPERT_PANEL -> "Expert Panel mode enabled. AI will simulate a panel of experts discussing the topic from different perspectives."
                ResponseMode.NORMAL -> null
            }

            if (systemMessageText != null) {
                val systemMessage = ChatMessage(
                    content = systemMessageText,
                    isFromUser = false,
                    messageType = MessageType.SYSTEM
                )

                if (preserveHistory) {
                    // Keep existing messages, just update/add system message
                    _state.update {
                        it.copy(messages = it.messages.filter { msg -> msg.messageType != MessageType.SYSTEM } + systemMessage)
                    }
                } else {
                    // Clear chat history and add new system message
                    chatRepository.clearHistory()
                    lastUserMessage = null
                    _state.update {
                        it.copy(
                            messages = listOf(systemMessage),
                            currentSessionId = null,
                            currentSessionTitle = "New Chat"
                        )
                    }
                    currentSessionId = null
                }
            } else {
                // Normal mode - remove system messages
                if (preserveHistory) {
                    // Just remove system message, keep history
                    _state.update {
                        it.copy(messages = it.messages.filter { msg -> msg.messageType != MessageType.SYSTEM })
                    }
                } else {
                    // Clear everything
                    chatRepository.clearHistory()
                    lastUserMessage = null
                    _state.update {
                        it.copy(
                            messages = emptyList(),
                            currentSessionId = null,
                            currentSessionTitle = "New Chat"
                        )
                    }
                    currentSessionId = null
                }
            }
        }
    }

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.RetryLastMessage -> retryLastMessage()
            is ChatIntent.ClearChat -> clearChat()
            is ChatIntent.CopyMessage -> copyMessage(intent.messageId)
            is ChatIntent.CopyAllMessages -> copyAllMessages()
            is ChatIntent.SummarizeChat -> summarizeChat()
            is ChatIntent.SummarizeAndReplaceChat -> summarizeAndReplaceChat()
            is ChatIntent.CreateNewSession -> createNewSession()
            is ChatIntent.LoadSession -> loadSession(intent.sessionId)
            is ChatIntent.UpdateSessionTitle -> updateSessionTitle(intent.title)
            is ChatIntent.UndoCompression -> undoCompression()
            is ChatIntent.ToggleCompressedHistory -> toggleCompressedHistory()
        }
    }

    private fun sendMessage(text: String) {
        if (text.isBlank()) return

        lastUserMessage = text
        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Create session if needed (first message)
                if (currentSessionId == null) {
                    val settings = settingsRepository.settings.value
                    val model = Model.fromId(settings.model)?.displayName ?: "Unknown"
                    val title = text.take(50).let { if (text.length > 50) "$it..." else it }

                    // Determine current system prompt based on response mode
                    val systemPrompt = when (settings.responseMode) {
                        ResponseMode.DIALOG -> AiSettings.DIALOG_SYSTEM_PROMPT
                        ResponseMode.STRUCTURED_JSON -> settings.systemPrompt
                        ResponseMode.STRUCTURED_XML -> AiSettings.STRUCTURED_XML_SYSTEM_PROMPT
                        ResponseMode.STEP_BY_STEP -> AiSettings.STEP_BY_STEP_SYSTEM_PROMPT
                        ResponseMode.EXPERT_PANEL -> AiSettings.EXPERT_PANEL_SYSTEM_PROMPT
                        ResponseMode.NORMAL -> null
                    }

                    val session = chatLocalRepository.createSession(
                        title = title,
                        modelName = model,
                        systemPrompt = systemPrompt
                    )
                    currentSessionId = session.id
                    _state.update {
                        it.copy(
                            currentSessionId = session.id,
                            currentSessionTitle = session.title
                        )
                    }
                }

                // Add user message to state immediately
                val userMessage = ChatMessage(
                    content = text,
                    isFromUser = true
                )
                _state.update { it.copy(messages = it.messages + userMessage) }

                // Save user message to local storage
                currentSessionId?.let { sessionId ->
                    chatLocalRepository.saveMessage(sessionId, userMessage)
                }

                // Get AI response
                val response = sendMessageUseCase(text)

                // Add AI response to state
                val aiMessage = ChatMessage(
                    content = response.content,
                    isFromUser = false,
                    executionTimeMs = response.executionTimeMs,
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens
                )
                _state.update {
                    it.copy(
                        messages = it.messages + aiMessage,
                        isLoading = false
                    )
                }

                // Save AI message to local storage
                currentSessionId?.let { sessionId ->
                    chatLocalRepository.saveMessage(sessionId, aiMessage)
                }

                // Increment message counter (counts user+AI as 2 messages)
                messagesSinceLastSummarization += 2

                // Check if auto-summarization is needed
                checkAutoSummarization()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    private fun retryLastMessage() {
        lastUserMessage?.let { message ->
            // Remove the last user message and any partial response
            val currentMessages = _state.value.messages
            val messagesToKeep = currentMessages.dropLast(1)
            _state.update { it.copy(messages = messagesToKeep, error = null) }

            // Resend the message
            sendMessage(message)
        }
    }

    private fun clearChat() {
        // Stop any active reminder when clearing chat
        reminderStore.dispatch(ReminderIntent.Stop)

        // Keep current session in history, just start fresh UI
        _state.update {
            ChatState(
                currentModelName = it.currentModelName
            )
        }
        chatRepository.clearHistory()
        lastUserMessage = null
        currentSessionId = null
        messagesSinceLastSummarization = 0
    }

    private fun createNewSession() {
        clearChat()
    }

    private fun loadSession(sessionId: String) {
        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val session = chatLocalRepository.getSessionById(sessionId)
                if (session == null) {
                    _state.update { it.copy(isLoading = false, error = "Session not found") }
                    return@launch
                }

                // Load messages from database
                val messages = chatLocalRepository.getMessagesForSession(sessionId).first()

                // Update state
                currentSessionId = sessionId
                _state.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        currentSessionId = sessionId,
                        currentSessionTitle = session.title,
                        currentModelName = session.modelName,
                        compressionPoint = if (session.isCompressed) session.compressionPoint else null,
                        originalSessionId = if (session.isCompressed) session.originalSessionId else null,
                        compressedMessagesCount = if (session.isCompressed) {
                            messages.count { it.messageType != MessageType.SYSTEM && !it.isSummary }
                        } else 0,
                        showCompressedHistory = false
                    )
                }

                // Restore conversation history in repository to preserve context
                // First, set the system prompt from the session
                chatRepository.setSystemPrompt(session.systemPrompt)

                if (session.isCompressed) {
                    // For compressed sessions, use summary as context and restore only new messages
                    val summaryMessage = messages.find { it.isSummary }
                    val newMessages = messages.filter { it.messageType != MessageType.SYSTEM && !it.isSummary }

                    if (summaryMessage != null) {
                        // Initialize with summary context
                        chatRepository.initializeWithContext(summaryMessage.content)

                        // Restore new messages while preserving summary context
                        chatRepository.restoreHistory(newMessages, preserveSummaryContext = true)
                    } else {
                        // No summary found, restore all messages
                        chatRepository.restoreHistory(messages, preserveSummaryContext = false)
                    }
                } else {
                    // Regular session - restore full history
                    chatRepository.restoreHistory(messages, preserveSummaryContext = false)
                }

                lastUserMessage = messages.lastOrNull { it.isFromUser }?.content
                messagesSinceLastSummarization = messages.count { it.messageType != MessageType.SYSTEM }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load session: ${e.message}"
                    )
                }
            }
        }
    }

    private fun updateSessionTitle(title: String) {
        coroutineScope.launch {
            currentSessionId?.let { sessionId ->
                chatLocalRepository.updateSessionTitle(sessionId, title)
                _state.update { it.copy(currentSessionTitle = title) }
            }
        }
    }

    private fun copyMessage(messageId: String) {
        val message = _state.value.messages.find { it.id == messageId }
        message?.let {
            ClipboardManager.copyToClipboard(it.content)
        }
    }

    private fun copyAllMessages() {
        val allMessages = _state.value.messages
            .filter { it.messageType != MessageType.SYSTEM }
            .joinToString("\n\n") { message ->
                val sender = when (message.messageType) {
                    MessageType.USER -> "User"
                    MessageType.AI -> "AI"
                    MessageType.SYSTEM -> "System"
                    MessageType.REMINDER -> "Reminder"
                }
                "$sender: ${message.content}"
            }
        ClipboardManager.copyToClipboard(allMessages)
    }

    private fun checkAutoSummarization() {
        val settings = settingsRepository.settings.value
        if (settings.summarizationEnabled &&
            messagesSinceLastSummarization >= settings.summarizationMessageThreshold &&
            !isSummarizing) {
            // Use compress mode (replace history) or append mode based on settings
            if (settings.summarizationReplaceHistory) {
                summarizeAndReplaceChat()
            } else {
                summarizeChat()
            }
        }
    }

    private fun summarizeChat() {
        val currentMessages = _state.value.messages
            .filter { it.messageType != MessageType.SYSTEM }

        // Need at least 2 messages (1 user + 1 AI) to summarize
        if (currentMessages.size < 2) return
        if (isSummarizing) return

        isSummarizing = true
        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Format conversation for summarization
                val conversationText = currentMessages.joinToString("\n\n") { message ->
                    val sender = when (message.messageType) {
                        MessageType.USER -> "User"
                        MessageType.AI -> "AI"
                        MessageType.SYSTEM -> "System"
                        MessageType.REMINDER -> "Reminder"
                    }
                    "$sender: ${message.content}"
                }

                // Create summarization request
                val summarizationRequest = """Please summarize the following conversation:

---
$conversationText
---

Provide a concise summary following the format specified in your instructions."""

                // Temporarily set system prompt for summarization
                val response = chatRepository.sendMessageWithCustomSystemPrompt(
                    userMessage = summarizationRequest,
                    systemPrompt = AiSettings.SUMMARIZATION_SYSTEM_PROMPT
                )

                // Add summary as a system message
                val summaryMessage = ChatMessage(
                    content = "**Conversation Summary**\n\n${response.content}",
                    isFromUser = false,
                    messageType = MessageType.SYSTEM,
                    executionTimeMs = response.executionTimeMs,
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens
                )

                _state.update {
                    it.copy(
                        messages = it.messages + summaryMessage,
                        isLoading = false
                    )
                }

                // Save summary to local storage
                currentSessionId?.let { sessionId ->
                    chatLocalRepository.saveMessage(sessionId, summaryMessage)
                }

                // Reset counter after summarization
                messagesSinceLastSummarization = 0
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to summarize: ${e.message ?: "Unknown error"}"
                    )
                }
            } finally {
                isSummarizing = false
            }
        }
    }

    private fun summarizeAndReplaceChat() {
        val currentMessages = _state.value.messages
            .filter { it.messageType != MessageType.SYSTEM }

        // Need at least 2 messages (1 user + 1 AI) to summarize
        if (currentMessages.size < 2) return
        if (isSummarizing) return

        isSummarizing = true
        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Format conversation for summarization
                val conversationText = currentMessages.joinToString("\n\n") { message ->
                    val sender = when (message.messageType) {
                        MessageType.USER -> "User"
                        MessageType.AI -> "AI"
                        MessageType.SYSTEM -> "System"
                        MessageType.REMINDER -> "Reminder"
                    }
                    "$sender: ${message.content}"
                }

                // Create summarization request
                val summarizationRequest = """Please summarize the following conversation:

---
$conversationText
---

Provide a concise summary following the format specified in your instructions."""

                // Get summary using custom system prompt
                val response = chatRepository.sendMessageWithCustomSystemPrompt(
                    userMessage = summarizationRequest,
                    systemPrompt = AiSettings.SUMMARIZATION_SYSTEM_PROMPT
                )

                val summaryContent = response.content
                val oldSessionId = currentSessionId
                val compressionPointTime = Clock.System.now().toEpochMilliseconds()

                // Archive the old session
                oldSessionId?.let { sessionId ->
                    chatLocalRepository.archiveSession(sessionId)
                }

                // Clear the chat history in repository
                chatRepository.clearHistory()

                // Initialize repository with summary as context for continuation
                val contextMessage = """Previous conversation summary (use this as context for continuing the conversation):

$summaryContent

---
The user may now continue the conversation based on this context."""

                chatRepository.initializeWithContext(contextMessage)

                // Create system message for UI showing the summary
                val summaryMessage = ChatMessage(
                    content = "**Conversation Summary**\n\n$summaryContent\n\n---\n*Continuing conversation based on this summary.*",
                    isFromUser = false,
                    messageType = MessageType.SYSTEM,
                    executionTimeMs = response.executionTimeMs,
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    isSummary = true,
                    compressedMessageCount = currentMessages.size
                )

                // Create new session with summary as starting point
                val oldSessionTitle = _state.value.currentSessionTitle
                val model = settingsRepository.settings.value.model
                val modelName = Model.fromId(model)?.displayName ?: "Unknown"
                val newSessionTitle = "Continue: $oldSessionTitle"

                // Get system prompt from old session if available
                val oldSession = oldSessionId?.let { chatLocalRepository.getSessionById(it) }
                val systemPrompt = oldSession?.systemPrompt

                val newSession = chatLocalRepository.createSession(
                    title = newSessionTitle,
                    modelName = modelName,
                    systemPrompt = systemPrompt
                )

                // Save summary to new session
                chatLocalRepository.saveMessage(newSession.id, summaryMessage)

                // Update new session with compression info
                if (oldSessionId != null) {
                    chatLocalRepository.updateSessionCompressionInfo(
                        sessionId = newSession.id,
                        isCompressed = true,
                        originalSessionId = oldSessionId,
                        compressionPoint = compressionPointTime
                    )
                }

                // Update state with new session
                currentSessionId = newSession.id
                _state.update {
                    it.copy(
                        messages = listOf(summaryMessage),
                        isLoading = false,
                        currentSessionId = newSession.id,
                        currentSessionTitle = newSession.title,
                        compressionPoint = compressionPointTime,
                        originalSessionId = oldSessionId,
                        compressedMessagesCount = currentMessages.size,
                        showCompressedHistory = false
                    )
                }

                // Reset counters
                messagesSinceLastSummarization = 0
                lastUserMessage = null
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to summarize and create new session: ${e.message ?: "Unknown error"}"
                    )
                }
            } finally {
                isSummarizing = false
            }
        }
    }

    private fun undoCompression() {
        val state = _state.value
        val originalSessionId = state.originalSessionId ?: return

        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Get the original session
                val originalSession = chatLocalRepository.getOriginalSession(originalSessionId)
                if (originalSession == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Original session not found. Cannot undo compression."
                        )
                    }
                    return@launch
                }

                // Unarchive the original session
                chatLocalRepository.unarchiveSession(originalSessionId)

                // Delete the compressed session
                currentSessionId?.let { sessionId ->
                    chatLocalRepository.deleteSession(sessionId)
                }

                // Load the original session
                loadSession(originalSessionId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to undo compression: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private fun toggleCompressedHistory() {
        _state.update {
            it.copy(showCompressedHistory = !it.showCompressedHistory)
        }
    }
}
