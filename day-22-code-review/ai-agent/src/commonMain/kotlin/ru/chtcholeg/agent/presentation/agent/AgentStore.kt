package ru.chtcholeg.agent.presentation.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.chtcholeg.agent.data.local.ChatHistoryRepository
import ru.chtcholeg.agent.data.repository.AgentRepository
import ru.chtcholeg.agent.data.repository.McpRepository
import ru.chtcholeg.agent.data.repository.RagRepository
import ru.chtcholeg.agent.data.repository.SettingsRepository
import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.agent.domain.model.CommandResult
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.agent.domain.model.RagMode
import ru.chtcholeg.agent.domain.model.SourceReference
import ru.chtcholeg.agent.domain.service.CommandHandler

/**
 * MVI Store for agent screen.
 */
class AgentStore(
    private val agentRepository: AgentRepository,
    private val mcpRepository: McpRepository,
    private val ragRepository: RagRepository,
    private val settingsRepository: SettingsRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val commandHandler: CommandHandler,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    init {
        // Restore the most recent session and initialize MCP
        coroutineScope.launch {
            try {
                val latestSessionId = chatHistoryRepository.getLatestSessionId()
                if (latestSessionId != null) {
                    val savedMessages = chatHistoryRepository.loadMessages(latestSessionId)
                    if (savedMessages.isNotEmpty()) {
                        agentRepository.restoreHistory(savedMessages)
                        _state.update {
                            it.copy(
                                messages = savedMessages,
                                currentSessionId = latestSessionId
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                println("[AgentStore] Failed to restore chat history: ${e.message}")
            }

            mcpRepository.initialize()
            loadToolsInternal()
        }
    }

    /**
     * Dispatch an intent to the store.
     */
    fun dispatch(intent: AgentIntent) {
        when (intent) {
            is AgentIntent.SendMessage -> sendMessage(intent.content)
            is AgentIntent.NewChat -> newChat()
            is AgentIntent.LoadSession -> loadSession(intent.sessionId)
            is AgentIntent.RetryLastMessage -> retryLastMessage()
            is AgentIntent.ReloadTools -> loadTools()
        }
    }

    private fun sendMessage(content: String) {
        if (content.isBlank()) return

        // Check if this is a command
        if (commandHandler.isCommand(content)) {
            handleCommand(content)
            return
        }

        // Add user message to UI
        val userMessage = AgentMessage(
            content = content,
            type = MessageType.USER
        )

        _state.update { currentState ->
            currentState.copy(
                messages = currentState.messages + userMessage,
                isLoading = true,
                error = null,
                lastUserMessage = content
            )
        }

        coroutineScope.launch {
            try {
                // Ensure session exists before any persistence
                val sessionId = getOrCreateSessionId(content)

                chatHistoryRepository.saveMessage(sessionId, userMessage)
                chatHistoryRepository.updateSessionTimestamp(sessionId)

                // Load RAG context if enabled
                val (ragContext, currentSources) = loadRagContext(content, sessionId)

                val responses = agentRepository.sendMessage(content, ragContext)

                // Attach source references with sequential renumbering
                val responsesWithSources = attachSources(responses, currentSources)

                _state.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + responsesWithSources,
                        isLoading = false,
                        error = null
                    )
                }

                // Persist response messages
                chatHistoryRepository.saveMessages(sessionId, responsesWithSources)
                chatHistoryRepository.updateSessionTimestamp(sessionId)
            } catch (e: Exception) {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    /**
     * Handle slash command execution.
     */
    private fun handleCommand(content: String) {
        // Add user message to show what command was entered
        val userMessage = AgentMessage(
            content = content,
            type = MessageType.USER
        )

        _state.update { currentState ->
            currentState.copy(
                messages = currentState.messages + userMessage,
                isLoading = true,
                error = null
            )
        }

        coroutineScope.launch {
            try {
                val result = commandHandler.handleCommand(content)

                // If the command needs LLM processing, send context + query to the model
                if (result is CommandResult.NeedsLlmProcessing) {
                    handleLlmCommand(userMessage, result)
                    return@launch
                }

                val commandResultMessage = when (result) {
                    is CommandResult.Success -> AgentMessage(
                        content = result.response,
                        type = MessageType.COMMAND
                    )
                    is CommandResult.Error -> AgentMessage(
                        content = result.message,
                        type = MessageType.ERROR
                    )
                    null -> AgentMessage(
                        content = "Unknown command. Type /help for available commands.",
                        type = MessageType.ERROR
                    )
                    else -> AgentMessage(
                        content = "Unexpected command result.",
                        type = MessageType.ERROR
                    )
                }

                _state.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + commandResultMessage,
                        isLoading = false,
                        error = null
                    )
                }

                // Save to history if session exists
                _state.value.currentSessionId?.let { sessionId ->
                    chatHistoryRepository.saveMessage(sessionId, userMessage)
                    chatHistoryRepository.saveMessage(sessionId, commandResultMessage)
                    chatHistoryRepository.updateSessionTimestamp(sessionId)
                }
            } catch (e: Exception) {
                val errorMessage = AgentMessage(
                    content = "Command execution failed: ${e.message}",
                    type = MessageType.ERROR
                )

                _state.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + errorMessage,
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * Handle a command that requires LLM processing.
     * Sends the command context along with the user's query to the AI model.
     *
     * When [CommandResult.NeedsLlmProcessing.enableTools] is true (e.g. /review-pr),
     * tools remain available (ragCitations=true) and RAG context is loaded if enabled.
     * When false (e.g. /help), tools are disabled and only the command context is used.
     */
    private suspend fun handleLlmCommand(
        userMessage: AgentMessage,
        result: CommandResult.NeedsLlmProcessing
    ) {
        try {
            val sessionId = getOrCreateSessionId(result.query)
            chatHistoryRepository.saveMessage(sessionId, userMessage)
            chatHistoryRepository.updateSessionTimestamp(sessionId)

            if (result.enableTools) {
                // === PHASE 1: Data gathering + review WITHOUT RAG ===
                // Use clean MCP-only mode so the model focuses on calling tools.
                // RAG context in this phase confuses the model into describing
                // steps instead of actually invoking function calls.
                val statusMessage = AgentMessage(
                    content = "Analyzing code changes with MCP tools...",
                    type = MessageType.RAG_CONTEXT
                )
                _state.update { it.copy(messages = it.messages + statusMessage) }
                chatHistoryRepository.saveMessage(sessionId, statusMessage)

                val toolResponses = agentRepository.sendMessage(
                    userMessage = result.query,
                    ragContext = null,
                    ragCitations = true,
                    excludeTools = result.excludeTools,
                    commandContext = result.context
                )

                _state.update { it.copy(messages = it.messages + toolResponses) }
                chatHistoryRepository.saveMessages(sessionId, toolResponses)

                // === PHASE 2: Enhance review with RAG documentation (if available) ===
                val (ragContext, currentSources) = loadRagContext(result.query, sessionId)
                if (ragContext != null) {
                    val ragStatusMessage = AgentMessage(
                        content = "Enhancing review with project documentation...",
                        type = MessageType.RAG_CONTEXT
                    )
                    _state.update { it.copy(messages = it.messages + ragStatusMessage) }
                    chatHistoryRepository.saveMessage(sessionId, ragStatusMessage)

                    val ragResponses = agentRepository.sendMessage(
                        userMessage = "Используя документацию проекта, дополни свой code review выше:\n" +
                            "- Проверь соответствие изменений архитектурным паттернам проекта\n" +
                            "- Укажи нарушения правил и рекомендаций из документации\n" +
                            "- Добавь рекомендации на основе документации, если они релевантны\n" +
                            "Если документация не содержит релевантной информации — так и скажи.",
                        ragContext = ragContext,
                        ragCitations = false,
                        excludeTools = null
                    )

                    _state.update { currentState ->
                        currentState.copy(
                            messages = currentState.messages + ragResponses,
                            isLoading = false,
                            error = null
                        )
                    }
                    chatHistoryRepository.saveMessages(sessionId, ragResponses)
                } else {
                    _state.update { it.copy(isLoading = false, error = null) }
                }

                chatHistoryRepository.updateSessionTimestamp(sessionId)
            } else {
                // Tools-disabled mode (e.g. /help): simple context prompt, no tools
                val contextMessage = AgentMessage(
                    content = "Analyzing project documentation to answer: ${result.query}",
                    type = MessageType.RAG_CONTEXT
                )
                _state.update { it.copy(messages = it.messages + contextMessage) }
                chatHistoryRepository.saveMessage(sessionId, contextMessage)

                val responses = agentRepository.sendMessage(
                    userMessage = result.query,
                    ragContext = result.context,
                    ragCitations = false
                )

                _state.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + responses,
                        isLoading = false,
                        error = null
                    )
                }

                chatHistoryRepository.saveMessages(sessionId, responses)
                chatHistoryRepository.updateSessionTimestamp(sessionId)
            }
        } catch (e: Exception) {
            val errorMessage = AgentMessage(
                content = "LLM processing failed: ${e.message}",
                type = MessageType.ERROR
            )
            _state.update { currentState ->
                currentState.copy(
                    messages = currentState.messages + errorMessage,
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun newChat() {
        agentRepository.clearHistory()
        _state.update {
            AgentState(
                availableTools = it.availableTools,
                currentSessionId = null,
                currentSessionTitle = null
            )
        }
    }

    private fun loadSession(sessionId: String) {
        coroutineScope.launch {
            try {
                val messages = chatHistoryRepository.loadMessages(sessionId)
                agentRepository.clearHistory()
                agentRepository.restoreHistory(messages)
                _state.update {
                    it.copy(
                        messages = messages,
                        currentSessionId = sessionId,
                        currentSessionTitle = null,
                        error = null,
                        isLoading = false,
                        lastUserMessage = messages.lastOrNull { msg -> msg.type == MessageType.USER }?.content
                    )
                }
            } catch (e: Exception) {
                println("[AgentStore] Failed to load session: ${e.message}")
                _state.update {
                    it.copy(error = "Failed to load session: ${e.message}")
                }
            }
        }
    }

    private fun retryLastMessage() {
        val lastMessage = _state.value.lastUserMessage ?: return
        sendMessage(lastMessage)
    }

    private fun loadTools() {
        _state.update { it.copy(toolsLoading = true) }

        coroutineScope.launch {
            loadToolsInternal()
        }
    }

    private suspend fun loadToolsInternal() {
        _state.update { it.copy(toolsLoading = true) }

        try {
            val tools = mcpRepository.getAllTools()
            _state.update { currentState ->
                currentState.copy(
                    availableTools = tools,
                    toolsLoading = false
                )
            }
        } catch (e: Exception) {
            _state.update { currentState ->
                currentState.copy(
                    toolsLoading = false,
                    error = "Failed to load tools: ${e.message}"
                )
            }
        }
    }

    /**
     * Attach source references to AI responses — renumber [Источник N] tags sequentially.
     * Removes the "📚 Источники:" footer section and only keeps referenced sources.
     */
    private fun attachSources(
        responses: List<AgentMessage>,
        sources: Map<Int, SourceReference>?
    ): List<AgentMessage> {
        if (sources == null) return responses

        val sourcePattern = Regex("""\[Источник\s+(\d+)]""")
        val sourceSectionPattern = Regex("""\n*📚\s*Источники:[\s\S]*$""")

        return responses.map { msg ->
            if (msg.type != MessageType.AI) return@map msg

            val mainText = sourceSectionPattern.replace(msg.content, "")
            val referencedNums = sourcePattern.findAll(mainText)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in sources }
                .distinct()
                .toList()

            if (referencedNums.isEmpty()) {
                msg.copy(sources = null)
            } else {
                val renumberMap = referencedNums
                    .sorted()
                    .mapIndexed { index, oldNum -> oldNum to (index + 1) }
                    .toMap()
                val renumberedContent = sourcePattern.replace(msg.content) { match ->
                    val oldNum = match.groupValues[1].toIntOrNull()
                    val newNum = oldNum?.let { renumberMap[it] }
                    if (newNum != null) "[Источник $newNum]" else match.value
                }
                val renumberedSources = renumberMap.mapNotNull { (oldNum, newNum) ->
                    sources[oldNum]?.let { newNum to it }
                }.toMap()
                msg.copy(
                    content = renumberedContent,
                    sources = renumberedSources.ifEmpty { null }
                )
            }
        }
    }

    /**
     * Load RAG context for a query. Returns (ragContext, sources) pair.
     */
    private suspend fun loadRagContext(query: String, sessionId: String): Pair<String?, Map<Int, SourceReference>?> {
        val settings = settingsRepository.settings.value

        if (settings.ragMode != RagMode.ON) return null to null
        if (settings.indexPath.isBlank()) {
            val errorMessage = AgentMessage(
                content = "RAG index path not configured. Set the path in Settings → RAG section.",
                type = MessageType.ERROR
            )
            _state.update { it.copy(messages = it.messages + errorMessage) }
            chatHistoryRepository.saveMessage(sessionId, errorMessage)
            return null to null
        }

        return try {
            ragRepository.loadIndex(settings.indexPath)

            val chunksForContext: List<ru.chtcholeg.shared.domain.service.SearchResult>
            val summary: String

            if (settings.rerankerEnabled) {
                val rerankerResult = ragRepository.getRelevantChunksWithReranking(
                    query = query,
                    initialTopK = settings.ragInitialTopK,
                    finalTopK = settings.ragFinalTopK,
                    rerankerThreshold = settings.rerankerThreshold,
                    scoreGapThreshold = settings.scoreGapThreshold
                )
                chunksForContext = rerankerResult.rerankedResults
                summary = if (rerankerResult.initialResults.isEmpty()) {
                    "No relevant chunks found. Answering without document context."
                } else {
                    ragRepository.formatRerankerReport(rerankerResult)
                }
            } else {
                val chunks = ragRepository.getRelevantChunks(query)
                chunksForContext = chunks
                summary = if (chunks.isEmpty()) {
                    "No relevant chunks found. Answering without document context."
                } else {
                    "Found ${chunks.size} relevant chunk(s):\n${ragRepository.formatChunksSummary(chunks)}"
                }
            }

            val sources = if (chunksForContext.isNotEmpty()) {
                ragRepository.buildSourceReferences(chunksForContext)
            } else null

            val ragMessage = AgentMessage(content = summary, type = MessageType.RAG_CONTEXT)
            _state.update { it.copy(messages = it.messages + ragMessage) }
            chatHistoryRepository.saveMessage(sessionId, ragMessage)

            val ragContext = ragRepository.formatContext(chunksForContext).takeIf { it.isNotEmpty() }
            ragContext to sources
        } catch (e: Exception) {
            val errorMessage = AgentMessage(content = "RAG error: ${e.message}", type = MessageType.ERROR)
            _state.update { it.copy(messages = it.messages + errorMessage) }
            chatHistoryRepository.saveMessage(sessionId, errorMessage)
            null to null
        }
    }

    private suspend fun getOrCreateSessionId(firstMessageContent: String): String {
        _state.value.currentSessionId?.let { return it }
        val title = firstMessageContent.take(50).replace("\n", " ")
        val session = chatHistoryRepository.createSession(title)
        _state.update {
            it.copy(
                currentSessionId = session.id,
                currentSessionTitle = session.title
            )
        }
        return session.id
    }
}
