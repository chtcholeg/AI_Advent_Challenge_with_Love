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
import ru.chtcholeg.agent.domain.service.TicketSourceParser

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

                // Load RAG context if enabled and not in simple chat mode
                val settings = settingsRepository.settings.value
                val (ragContext, currentSources) = if (settings.simpleChatMode) {
                    Pair(null, emptyMap<Int, SourceReference>())
                } else {
                    loadRagContext(content, sessionId)
                }

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
                // ═══════════════════════════════════════════════════════════════════════
                // TWO-PHASE ARCHITECTURE
                // ═══════════════════════════════════════════════════════════════════════
                // Phase 1: MCP tools + optional RAG (for /support or /review-pr)
                // Phase 2: RAG documentation validation (for /review-pr only)
                //
                // WHY TWO PHASES FOR CODE REVIEW?
                // - Phase 1 with RAG: model describes steps instead of calling tools
                // - Phase 1 without RAG: model correctly invokes function calls
                // - Phase 2: documentation context enhances review quality
                //
                // FOR SUPPORT MODE:
                // - Phase 1 WITH RAG: knowledge base search + CRM tools
                // - Phase 2: skipped (no code review validation needed)
                // ═══════════════════════════════════════════════════════════════════════

                // === PHASE 1: Data gathering with optional RAG ===
                // Load RAG context if requested (e.g., /support needs knowledge base)
                val (phase1RagContext, phase1Sources) = if (result.enableRagContext) {
                    loadRagContext(result.query, sessionId)
                } else {
                    null to null
                }

                val statusMessage = AgentMessage(
                    content = if (result.enableRagContext) {
                        "Searching knowledge base and analyzing context..."
                    } else {
                        "Phase 1/2: Analyzing code changes with MCP tools..."
                    },
                    type = MessageType.RAG_CONTEXT
                )
                _state.update { it.copy(messages = it.messages + statusMessage) }
                chatHistoryRepository.saveMessage(sessionId, statusMessage)

                val toolResponses = try {
                    agentRepository.sendMessage(
                        userMessage = result.query,
                        ragContext = phase1RagContext,  // Include RAG if requested
                        ragCitations = true,
                        excludeTools = result.excludeTools,
                        includeTools = result.includeTools,
                        commandContext = result.context
                    )
                } catch (e: Exception) {
                    println("[AgentStore] PHASE 1 failed: ${e.message}")
                    throw e
                }

                // Extract ticket sources from CRM tool results (search_tickets, get_ticket, etc.)
                println("[AgentStore] Extracting ticket sources from ${toolResponses.size} responses")
                val ticketSources = extractTicketSources(
                    toolResponses,
                    startIndex = (phase1Sources?.size ?: 0) + 1
                )
                println("[AgentStore] Extracted ${ticketSources.size} ticket sources")

                // Combine RAG sources (documentation) and CRM sources (tickets)
                val allSources = when {
                    phase1Sources != null && ticketSources.isNotEmpty() -> phase1Sources + ticketSources
                    phase1Sources != null -> phase1Sources
                    ticketSources.isNotEmpty() -> ticketSources
                    else -> null
                }

                // Log combined sources for debugging
                println("[AgentStore] RAG sources: ${phase1Sources?.size ?: 0}")
                println("[AgentStore] CRM sources: ${ticketSources.size}")
                println("[AgentStore] Total sources: ${allSources?.size ?: 0}")

                if (allSources != null) {
                    allSources.forEach { (idx, src) ->
                        println("[AgentStore] Source [$idx]: ${src.filePath} (similarity=${src.similarity})")
                    }
                }

                // Attach all source references (RAG + CRM)
                val toolResponsesWithSources = attachSources(toolResponses, allSources)
                println("[AgentStore] Attached sources to ${toolResponsesWithSources.size} responses")

                _state.update { it.copy(messages = it.messages + toolResponsesWithSources) }
                chatHistoryRepository.saveMessages(sessionId, toolResponsesWithSources)

                // === PHASE 2: Enhance review with RAG documentation (if requested and available) ===
                // If command requires doc validation (e.g., /review-pr) and RAG index is configured,
                // load project documentation and validate code changes against standards.
                if (result.requiresDocValidation) {
                    val (ragContext, currentSources) = loadRagContext(result.query, sessionId)
                    if (ragContext != null) {
                    val ragStatusMessage = AgentMessage(
                        content = "Phase 2/2: Enhancing review with project documentation...",
                        type = MessageType.RAG_CONTEXT
                    )
                    _state.update { it.copy(messages = it.messages + ragStatusMessage) }
                    chatHistoryRepository.saveMessage(sessionId, ragStatusMessage)

                    val ragResponses = agentRepository.sendMessage(
                        userMessage = "Выше ты уже написал code review изменённого кода.\n" +
                            "Теперь тебе предоставлена ДОКУМЕНТАЦИЯ проекта (в разделе <context>).\n\n" +
                            "Твоя задача: найти в документации КОНКРЕТНЫЕ правила, паттерны или соглашения, " +
                            "которые НАРУШЕНЫ в изменённом коде из review выше.\n\n" +
                            "Для каждого найденного нарушения покажи:\n" +
                            "1. Какое правило/паттерн из документации нарушено (ПРОЦИТИРУЙ точную фразу из документации)\n" +
                            "2. Какой именно код нарушает это правило (покажи строки кода из review)\n" +
                            "3. Как исправить (покажи исправленный код)\n\n" +
                            "ВАЖНО:\n" +
                            "- Ищи ТОЛЬКО в предоставленной документации, НЕ выдумывай правила\n" +
                            "- НЕ повторяй замечания из основного review выше\n" +
                            "- НЕ перечисляй общие принципы (SOLID, null safety и т.д.)\n" +
                            "- Если документация НЕ содержит релевантных правил для данных изменений, " +
                            "напиши ТОЛЬКО: «Документация проекта не содержит дополнительных требований к этим изменениям.»",
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
                } else {
                    _state.update { it.copy(isLoading = false, error = null) }
                }

                chatHistoryRepository.updateSessionTimestamp(sessionId)
            } else {
                // Tools-disabled mode: /help uses ragContext, /review pre-fetched uses commandContext
                val isReview = result.context.contains("Code Review Instructions")
                val contextMessage = AgentMessage(
                    content = if (isReview) "Analyzing code changes..." else "Analyzing project documentation to answer: ${result.query}",
                    type = MessageType.RAG_CONTEXT
                )
                _state.update { it.copy(messages = it.messages + contextMessage) }
                chatHistoryRepository.saveMessage(sessionId, contextMessage)

                val responses = if (isReview) {
                    // Pre-fetched review: pass as commandContext so it becomes
                    // part of the system prompt (not buried inside <context> tags)
                    agentRepository.sendMessage(
                        userMessage = result.query,
                        ragContext = null,
                        ragCitations = false,
                        commandContext = result.context
                    )
                } else {
                    // /help: pass as ragContext for simple document Q&A
                    agentRepository.sendMessage(
                        userMessage = result.query,
                        ragContext = result.context,
                        ragCitations = false
                    )
                }

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
        coroutineScope.launch {
            agentRepository.clearHistory()
            _state.update {
                AgentState(
                    availableTools = it.availableTools,
                    currentSessionId = null,
                    currentSessionTitle = null
                )
            }
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
        println("[AgentStore.attachSources] Called with ${responses.size} responses and ${sources?.size ?: 0} sources")

        if (sources == null) {
            println("[AgentStore.attachSources] No sources provided, returning original responses")
            return responses
        }

        val sourcePattern = Regex("""\[Источник\s+(\d+)]""")
        val sourceSectionPattern = Regex("""\n*📚\s*Источники:[\s\S]*$""")

        return responses.map { msg ->
            if (msg.type != MessageType.AI) {
                println("[AgentStore.attachSources] Message type ${msg.type}, skipping")
                return@map msg
            }

            println("[AgentStore.attachSources] Processing AI message (${msg.content.length} chars)")
            println("[AgentStore.attachSources] First 200 chars: ${msg.content.take(200)}")

            val mainText = sourceSectionPattern.replace(msg.content, "")
            val referencedNums = sourcePattern.findAll(mainText)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in sources }
                .distinct()
                .toList()

            println("[AgentStore.attachSources] Found ${referencedNums.size} referenced source numbers: $referencedNums")

            if (referencedNums.isEmpty()) {
                println("[AgentStore.attachSources] No source references found in AI message, returning without sources")
                msg.copy(sources = null)
            } else {
                println("[AgentStore.attachSources] Renumbering and attaching sources")
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

    /**
     * Extract ticket sources from CRM tool results (search_tickets, get_ticket, get_user_tickets).
     * Parses TOOL_RESULT messages and creates SourceReference objects for tickets.
     *
     * @param messages List of messages including TOOL_CALL and TOOL_RESULT pairs
     * @param startIndex Starting index for source numbering (to append after RAG sources)
     * @return Map of source index -> SourceReference
     */
    private fun extractTicketSources(
        messages: List<AgentMessage>,
        startIndex: Int = 1
    ): Map<Int, SourceReference> {
        val sources = mutableMapOf<Int, SourceReference>()
        var currentIndex = startIndex

        println("[AgentStore.extractTicketSources] Processing ${messages.size} messages, startIndex=$startIndex")

        // Find TOOL_CALL and corresponding TOOL_RESULT pairs
        for (i in messages.indices) {
            val msg = messages[i]

            // Look for tool calls
            if (msg.type == MessageType.TOOL_CALL) {
                // Parse tool name from "tool_name(args)" format
                val toolName = msg.content.substringBefore("(")
                println("[AgentStore.extractTicketSources] Found TOOL_CALL: $toolName")

                // Find corresponding tool result (next TOOL_RESULT message)
                val resultMsg = messages.getOrNull(i + 1)
                if (resultMsg?.type == MessageType.TOOL_RESULT) {
                    println("[AgentStore.extractTicketSources] Found TOOL_RESULT for $toolName (${resultMsg.content.length} chars)")

                    val ticketSources = when (toolName) {
                        "search_tickets" -> {
                            println("[AgentStore.extractTicketSources] Parsing search_tickets result")
                            TicketSourceParser.parseSearchTicketsSources(
                                resultMsg.content,
                                currentIndex
                            )
                        }
                        "get_ticket" -> {
                            println("[AgentStore.extractTicketSources] Parsing get_ticket result")
                            TicketSourceParser.parseGetTicketSource(
                                resultMsg.content,
                                currentIndex
                            )
                        }
                        "get_user_tickets" -> {
                            println("[AgentStore.extractTicketSources] Parsing get_user_tickets result")
                            TicketSourceParser.parseUserTicketsSources(
                                resultMsg.content,
                                currentIndex
                            )
                        }
                        else -> {
                            println("[AgentStore.extractTicketSources] Tool $toolName not a ticket tool, skipping")
                            emptyMap()
                        }
                    }

                    println("[AgentStore.extractTicketSources] Parsed ${ticketSources.size} sources from $toolName")
                    sources.putAll(ticketSources)
                    currentIndex += ticketSources.size
                } else {
                    println("[AgentStore.extractTicketSources] WARNING: No TOOL_RESULT found after TOOL_CALL $toolName")
                }
            }
        }

        println("[AgentStore.extractTicketSources] Total extracted sources: ${sources.size}")
        return sources
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
