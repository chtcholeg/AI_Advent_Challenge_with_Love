package ru.chtcholeg.agent.presentation.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.chtcholeg.agent.data.repository.AgentRepository
import ru.chtcholeg.agent.data.repository.McpRepository
import ru.chtcholeg.agent.data.repository.RagRepository
import ru.chtcholeg.agent.data.repository.SettingsRepository
import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.agent.domain.model.RagMode
import ru.chtcholeg.agent.domain.model.SourceReference

/**
 * MVI Store for agent screen.
 */
class AgentStore(
    private val agentRepository: AgentRepository,
    private val mcpRepository: McpRepository,
    private val ragRepository: RagRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    init {
        // Initialize MCP repository - load saved servers and connect
        coroutineScope.launch {
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
            is AgentIntent.ClearChat -> clearChat()
            is AgentIntent.RetryLastMessage -> retryLastMessage()
            is AgentIntent.ReloadTools -> loadTools()
        }
    }

    private fun sendMessage(content: String) {
        if (content.isBlank()) return

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

        // Send to AI
        coroutineScope.launch {
            try {
                val settings = settingsRepository.settings.value
                var ragContext: String? = null
                var currentSources: Map<Int, SourceReference>? = null

                // RAG: retrieve relevant chunks if enabled
                if (settings.ragMode == RagMode.ON) {
                    try {
                        ragRepository.loadIndex(settings.indexPath)

                        val summary: String
                        val chunksForContext: List<ru.chtcholeg.shared.domain.service.SearchResult>

                        if (settings.rerankerEnabled) {
                            // Two-stage retrieval with reranking
                            val rerankerResult = ragRepository.getRelevantChunksWithReranking(
                                query = content,
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
                            // Single-stage vector search (original behavior)
                            val chunks = ragRepository.getRelevantChunks(content)
                            chunksForContext = chunks
                            summary = if (chunks.isEmpty()) {
                                "No relevant chunks found. Answering without document context."
                            } else {
                                "Found ${chunks.size} relevant chunk(s):\n${ragRepository.formatChunksSummary(chunks)}"
                            }
                        }

                        if (chunksForContext.isNotEmpty()) {
                            currentSources = ragRepository.buildSourceReferences(chunksForContext)
                        }

                        _state.update { it.copy(messages = it.messages + AgentMessage(content = summary, type = MessageType.RAG_CONTEXT)) }
                        ragContext = ragRepository.formatContext(chunksForContext).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        _state.update { it.copy(messages = it.messages + AgentMessage(content = "RAG error: ${e.message}", type = MessageType.ERROR)) }
                    }
                }

                val responses = agentRepository.sendMessage(content, ragContext)

                // Attach source references to AI messages
                val responsesWithSources = if (currentSources != null) {
                    responses.map { msg ->
                        if (msg.type == MessageType.AI) msg.copy(sources = currentSources) else msg
                    }
                } else {
                    responses
                }

                _state.update { currentState ->
                    currentState.copy(
                        messages = currentState.messages + responsesWithSources,
                        isLoading = false,
                        error = null
                    )
                }
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

    private fun clearChat() {
        agentRepository.clearHistory()
        _state.update { AgentState(availableTools = it.availableTools) }
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
}
