package ru.chtcholeg.app.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ru.chtcholeg.app.data.api.GigaChatApi
import ru.chtcholeg.app.data.api.HuggingFaceApi
import ru.chtcholeg.app.data.model.FewShotExample
import ru.chtcholeg.app.data.model.GigaChatFunction
import ru.chtcholeg.app.data.model.Message
import ru.chtcholeg.app.domain.model.AiResponse
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.app.domain.model.McpTool
import ru.chtcholeg.app.domain.model.Model
import ru.chtcholeg.app.domain.model.ResponseMode
import kotlin.time.measureTimedValue

class ChatRepositoryImpl(
    private val gigaChatApi: GigaChatApi,
    private val huggingFaceApi: HuggingFaceApi,
    private val gigaChatClientId: String,
    private val gigaChatClientSecret: String,
    private val huggingFaceToken: String,
    private val settingsRepository: SettingsRepository,
    private val mcpRepository: McpRepository
) : ChatRepository {

    private var gigaChatAccessToken: String? = null
    private var gigaChatTokenExpirationTime: Long = 0
    private val conversationHistory = mutableListOf<Message>()
    private var currentSystemPrompt: String? = null
    private var summaryContext: String? = null  // Stores summary context separately
    private var isSystemPromptLockedFromSession: Boolean = false  // True if system prompt was loaded from a saved session

    override suspend fun sendMessage(userMessage: String): AiResponse {
        // Get current AI settings
        val settings = settingsRepository.settings.value

        // Determine which system prompt to use
        val modeSystemPrompt = if (isSystemPromptLockedFromSession) {
            // Use system prompt from loaded session, ignoring current UI settings
            currentSystemPrompt
        } else {
            // Use system prompt based on current response mode from UI settings
            when (settings.responseMode) {
                ResponseMode.DIALOG -> AiSettings.DIALOG_SYSTEM_PROMPT
                ResponseMode.STRUCTURED_JSON -> settings.systemPrompt
                ResponseMode.STRUCTURED_XML -> AiSettings.STRUCTURED_XML_SYSTEM_PROMPT
                ResponseMode.STEP_BY_STEP -> AiSettings.STEP_BY_STEP_SYSTEM_PROMPT
                ResponseMode.EXPERT_PANEL -> AiSettings.EXPERT_PANEL_SYSTEM_PROMPT
                ResponseMode.NORMAL -> null
            }
        }

        // Combine mode system prompt with summary context if available
        val systemPrompt = when {
            modeSystemPrompt != null && summaryContext != null -> {
                // Combine both: summary context first, then mode instructions
                """$summaryContext

---
$modeSystemPrompt"""
            }
            modeSystemPrompt != null -> modeSystemPrompt
            summaryContext != null -> summaryContext
            else -> null
        }

        // Update system prompt if needed
        if (systemPrompt != null) {
            if (currentSystemPrompt != systemPrompt) {
                // System prompt changed
                if (settings.preserveHistoryOnSystemPromptChange) {
                    // Preserve history: just update/add system message at the beginning
                    val systemMessage = Message(role = "system", content = systemPrompt)
                    if (currentSystemPrompt != null) {
                        // Replace existing system message
                        if (conversationHistory.isNotEmpty() && conversationHistory[0].role == "system") {
                            conversationHistory[0] = systemMessage
                        } else {
                            conversationHistory.add(0, systemMessage)
                        }
                    } else {
                        // Add system message at the beginning
                        conversationHistory.add(0, systemMessage)
                    }
                } else {
                    // Clear history and add new system message
                    conversationHistory.clear()
                    conversationHistory.add(Message(role = "system", content = systemPrompt))
                }
                currentSystemPrompt = systemPrompt
            }
        } else {
            // No system prompt needed, remove it if present
            if (currentSystemPrompt != null) {
                if (settings.preserveHistoryOnSystemPromptChange) {
                    // Just remove system message, keep history
                    if (conversationHistory.isNotEmpty() && conversationHistory[0].role == "system") {
                        conversationHistory.removeAt(0)
                    }
                } else {
                    // Clear all history
                    conversationHistory.clear()
                }
                currentSystemPrompt = null
            }
        }

        // Add user message to history
        val userMsg = Message(role = "user", content = userMessage)
        conversationHistory.add(userMsg)

        // Determine which API to use based on model
        val model = Model.fromId(settings.model) ?: Model.GigaChat

        // Get available MCP tools for function calling (only for GigaChat)
        val mcpTools = if (model.api == Model.Api.GIGACHAT) {
            getAvailableMcpTools()
        } else {
            emptyList()
        }
        val functions = if (mcpTools.isNotEmpty()) {
            convertMcpToolsToFunctions(mcpTools)
        } else {
            null
        }

        // Measure execution time
        val timedResult = measureTimedValue {
            when (model.api) {
                Model.Api.GIGACHAT -> {
                    // Ensure we have a valid token for GigaChat
                    ensureGigaChatAuthenticated()

                    gigaChatApi.sendMessage(
                        accessToken = gigaChatAccessToken!!,
                        messages = conversationHistory,
                        model = model.id,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        maxTokens = settings.maxTokens,
                        repetitionPenalty = settings.repetitionPenalty,
                        functions = functions
                    )
                }
                Model.Api.HUGGINGFACE -> {
                    huggingFaceApi.sendMessage(
                        accessToken = huggingFaceToken,
                        messages = conversationHistory,
                        model = model.id,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        maxTokens = settings.maxTokens,
                        repetitionPenalty = settings.repetitionPenalty
                    )
                }
            }
        }

        val response = timedResult.value
        val executionTimeMs = timedResult.duration.inWholeMilliseconds

        // Extract AI response
        val aiMessage = response.choices.firstOrNull()?.message
            ?: throw IllegalStateException("No response from AI")

        // Add AI response to history
        conversationHistory.add(aiMessage)

        // Handle function calling
        if (aiMessage.functionCall != null && response.choices.firstOrNull()?.finishReason == "function_call") {
            return handleFunctionCall(aiMessage, settings, model, executionTimeMs, response.usage)
        }

        return AiResponse(
            content = aiMessage.content?.trim() ?: "",
            executionTimeMs = executionTimeMs,
            promptTokens = response.usage?.promptTokens,
            completionTokens = response.usage?.completionTokens,
            totalTokens = response.usage?.totalTokens
        )
    }

    override fun clearHistory() {
        conversationHistory.clear()
        summaryContext = null
        currentSystemPrompt = null
        isSystemPromptLockedFromSession = false
    }

    override suspend fun sendMessageWithCustomSystemPrompt(userMessage: String, systemPrompt: String): AiResponse {
        val settings = settingsRepository.settings.value
        val model = Model.fromId(settings.model) ?: Model.GigaChat

        // Create a temporary message list with custom system prompt
        val tempMessages = mutableListOf(
            Message(role = "system", content = systemPrompt),
            Message(role = "user", content = userMessage)
        )

        // Measure execution time
        val timedResult = measureTimedValue {
            when (model.api) {
                Model.Api.GIGACHAT -> {
                    ensureGigaChatAuthenticated()
                    gigaChatApi.sendMessage(
                        accessToken = gigaChatAccessToken!!,
                        messages = tempMessages,
                        model = model.id,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        maxTokens = settings.maxTokens,
                        repetitionPenalty = settings.repetitionPenalty
                    )
                }
                Model.Api.HUGGINGFACE -> {
                    huggingFaceApi.sendMessage(
                        accessToken = huggingFaceToken,
                        messages = tempMessages,
                        model = model.id,
                        temperature = settings.temperature,
                        topP = settings.topP,
                        maxTokens = settings.maxTokens,
                        repetitionPenalty = settings.repetitionPenalty
                    )
                }
            }
        }

        val response = timedResult.value
        val executionTimeMs = timedResult.duration.inWholeMilliseconds

        val aiMessage = response.choices.firstOrNull()?.message
            ?: throw IllegalStateException("No response from AI")

        return AiResponse(
            content = aiMessage.content?.trim() ?: "",
            executionTimeMs = executionTimeMs,
            promptTokens = response.usage?.promptTokens,
            completionTokens = response.usage?.completionTokens,
            totalTokens = response.usage?.totalTokens
        )
    }

    override fun initializeWithContext(context: String) {
        // Clear existing history but preserve summary context
        conversationHistory.clear()
        currentSystemPrompt = null
        isSystemPromptLockedFromSession = false  // Context is from compression, not from loaded session

        // Store the summary context separately so it persists across mode changes
        summaryContext = context

        // The context will be combined with any mode system prompt in sendMessage()
        // For now, just add it as a system message
        val contextSystemMessage = Message(
            role = "system",
            content = context
        )
        conversationHistory.add(contextSystemMessage)
        currentSystemPrompt = context
    }

    override fun restoreHistory(messages: List<ru.chtcholeg.app.domain.model.ChatMessage>, preserveSummaryContext: Boolean) {
        // Save current system prompt (set by setSystemPrompt before calling this)
        val savedSystemPrompt = currentSystemPrompt
        val savedSummaryContext = summaryContext

        // Clear existing history
        conversationHistory.clear()

        // Add system prompt if set (from loaded session)
        if (savedSystemPrompt != null && !preserveSummaryContext) {
            conversationHistory.add(Message(role = "system", content = savedSystemPrompt))
        }

        // Restore summary context if requested (for compressed sessions)
        if (preserveSummaryContext && savedSummaryContext != null) {
            summaryContext = savedSummaryContext
            conversationHistory.add(Message(role = "system", content = savedSummaryContext))
            currentSystemPrompt = savedSummaryContext
        }

        // Convert and add messages to history (filter out SYSTEM messages)
        messages.forEach { chatMessage ->
            when (chatMessage.messageType) {
                ru.chtcholeg.app.domain.model.MessageType.USER -> {
                    conversationHistory.add(
                        Message(role = "user", content = chatMessage.content)
                    )
                }
                ru.chtcholeg.app.domain.model.MessageType.AI -> {
                    conversationHistory.add(
                        Message(role = "assistant", content = chatMessage.content)
                    )
                }
                ru.chtcholeg.app.domain.model.MessageType.SYSTEM -> {
                    // Skip SYSTEM messages - they are UI-only informational messages
                    // not part of the conversation context
                }
            }
        }
    }

    override fun setSystemPrompt(systemPrompt: String?) {
        // Set the system prompt without clearing history
        // This is used before restoring history from a loaded session
        currentSystemPrompt = systemPrompt

        // Lock the system prompt from the loaded session
        // This prevents it from being overridden by UI settings changes
        isSystemPromptLockedFromSession = (systemPrompt != null)

        // Clear summary context as we're setting explicit system prompt
        summaryContext = null
    }

    override fun unlockSystemPrompt() {
        // Allow system prompt to be updated from UI settings again
        isSystemPromptLockedFromSession = false
    }

    /**
     * Handle function calling from AI response.
     * Executes the tool and makes a recursive API call to get the final answer.
     */
    private suspend fun handleFunctionCall(
        aiMessage: Message,
        settings: AiSettings,
        model: Model,
        initialExecutionTimeMs: Long,
        initialUsage: ru.chtcholeg.app.data.model.Usage?
    ): AiResponse {
        val functionCall = aiMessage.functionCall ?: return AiResponse(
            content = "Error: Function call information missing",
            executionTimeMs = initialExecutionTimeMs,
            isError = true
        )

        // Get function arguments as JsonElement (handle both object and string format)
        val parameters = try {
            when {
                // If arguments is already a JsonObject, use it directly
                functionCall.arguments is JsonObject -> {
                    functionCall.arguments
                }
                // If arguments is a string, parse it
                functionCall.arguments is JsonPrimitive -> {
                    Json.parseToJsonElement(functionCall.getArgumentsAsString())
                }
                // Fallback
                else -> {
                    Json.parseToJsonElement(functionCall.getArgumentsAsString())
                }
            }
        } catch (e: Exception) {
            // Create empty JSON object on parse failure
            buildJsonObject {}
        }

        // Execute the tool via MCP Repository
        val toolResult = try {
            mcpRepository.executeTool(functionCall.name, parameters)
        } catch (e: Exception) {
            ru.chtcholeg.app.domain.model.McpToolResult(
                content = "Tool execution error: ${e.message}",
                isError = true
            )
        }

        // Add function result to conversation history
        // GigaChat expects function results as valid JSON strings
        val functionResultJson = buildJsonObject {
            put("result", toolResult.content)
            put("is_error", toolResult.isError)
        }.toString()

        val functionResultMessage = Message(
            role = Message.FUNCTION,
            name = functionCall.name,
            content = functionResultJson
        )
        conversationHistory.add(functionResultMessage)

        // Make recursive API call to get final answer with tool results
        val finalTimedResult = measureTimedValue {
            ensureGigaChatAuthenticated()
            gigaChatApi.sendMessage(
                accessToken = gigaChatAccessToken!!,
                messages = conversationHistory,
                model = model.id,
                temperature = settings.temperature,
                topP = settings.topP,
                maxTokens = settings.maxTokens,
                repetitionPenalty = settings.repetitionPenalty,
                functions = null  // Don't offer functions in follow-up call
            )
        }

        val finalResponse = finalTimedResult.value
        val finalExecutionTimeMs = finalTimedResult.duration.inWholeMilliseconds
        val totalExecutionTimeMs = initialExecutionTimeMs + finalExecutionTimeMs

        // Extract final AI response
        val finalAiMessage = finalResponse.choices.firstOrNull()?.message
            ?: throw IllegalStateException("No response from AI after function call")

        // Add final response to history
        conversationHistory.add(finalAiMessage)

        // Calculate total token usage
        val totalPromptTokens = (initialUsage?.promptTokens ?: 0) + (finalResponse.usage?.promptTokens ?: 0)
        val totalCompletionTokens = (initialUsage?.completionTokens ?: 0) + (finalResponse.usage?.completionTokens ?: 0)
        val totalTokens = (initialUsage?.totalTokens ?: 0) + (finalResponse.usage?.totalTokens ?: 0)

        return AiResponse(
            content = finalAiMessage.content?.trim() ?: "",
            executionTimeMs = totalExecutionTimeMs,
            promptTokens = totalPromptTokens,
            completionTokens = totalCompletionTokens,
            totalTokens = totalTokens
        )
    }

    /**
     * Ensure we have a valid authentication token for GigaChat
     */
    private suspend fun ensureGigaChatAuthenticated() {
        val currentTime = Clock.System.now().toEpochMilliseconds()

        if (gigaChatAccessToken == null || currentTime >= gigaChatTokenExpirationTime) {
            val authResponse = gigaChatApi.authenticate(gigaChatClientId, gigaChatClientSecret)
            gigaChatAccessToken = authResponse.accessToken
            gigaChatTokenExpirationTime = authResponse.expiresAt
        }
    }

    /**
     * Convert MCP tools to GigaChat function format.
     */
    private fun convertMcpToolsToFunctions(tools: List<McpTool>): List<GigaChatFunction> {
        return tools.map { tool ->
            val examples = tool.fewShotExamples.takeIf { it.isNotEmpty() }?.map { ex ->
                FewShotExample(request = ex.request, params = ex.params)
            }
            GigaChatFunction(
                name = tool.name,
                description = tool.description,
                parameters = tool.inputSchema,
                fewShotExamples = examples
            )
        }
    }

    /**
     * Get available MCP tools for function calling.
     * Returns empty list if MCP is not available or if there are no connected servers.
     */
    private suspend fun getAvailableMcpTools(): List<McpTool> {
        return try {
            mcpRepository.getAvailableTools().first()
        } catch (e: Exception) {
            println("Failed to get MCP tools: ${e.message}")
            emptyList()
        }
    }
}

