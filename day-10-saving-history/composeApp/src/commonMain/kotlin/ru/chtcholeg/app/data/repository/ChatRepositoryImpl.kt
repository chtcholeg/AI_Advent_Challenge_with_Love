package ru.chtcholeg.app.data.repository

import kotlinx.datetime.Clock
import ru.chtcholeg.app.data.api.GigaChatApi
import ru.chtcholeg.app.data.api.HuggingFaceApi
import ru.chtcholeg.app.data.model.Message
import ru.chtcholeg.app.domain.model.AiResponse
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.app.domain.model.Model
import ru.chtcholeg.app.domain.model.ResponseMode
import kotlin.time.measureTimedValue

class ChatRepositoryImpl(
    private val gigaChatApi: GigaChatApi,
    private val huggingFaceApi: HuggingFaceApi,
    private val gigaChatClientId: String,
    private val gigaChatClientSecret: String,
    private val huggingFaceToken: String,
    private val settingsRepository: SettingsRepository
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
                        repetitionPenalty = settings.repetitionPenalty
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

        return AiResponse(
            content = aiMessage.content.trim(),
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
            content = aiMessage.content.trim(),
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
}

