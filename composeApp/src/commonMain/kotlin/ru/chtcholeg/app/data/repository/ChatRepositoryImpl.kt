package ru.chtcholeg.app.data.repository

import kotlinx.datetime.Clock
import ru.chtcholeg.app.data.api.GigaChatApi
import ru.chtcholeg.app.data.api.HuggingFaceApi
import ru.chtcholeg.app.data.model.Message
import ru.chtcholeg.app.domain.model.Model

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

    override suspend fun sendMessage(userMessage: String): String {
        // Add user message to history
        val userMsg = Message(role = "user", content = userMessage)
        conversationHistory.add(userMsg)

        // Get current AI settings
        val settings = settingsRepository.settings.value

        // Determine which API to use based on model
        val model = Model.fromId(settings.model) ?: Model.GigaChat

        val response = when (model.api) {
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

        // Extract AI response
        val aiMessage = response.choices.firstOrNull()?.message
            ?: throw IllegalStateException("No response from AI")

        // Add AI response to history
        conversationHistory.add(aiMessage)

        return aiMessage.content
    }

    override fun clearHistory() {
        conversationHistory.clear()
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

