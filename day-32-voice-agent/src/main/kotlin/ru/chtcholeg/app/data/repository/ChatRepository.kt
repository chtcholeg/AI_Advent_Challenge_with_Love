package ru.chtcholeg.app.data.repository

import ru.chtcholeg.app.data.api.ApiMessage
import ru.chtcholeg.app.data.api.ApiResponse
import ru.chtcholeg.app.data.api.GigaChatApi
import ru.chtcholeg.app.data.api.OllamaApi
import ru.chtcholeg.app.domain.model.AppSettings
import ru.chtcholeg.app.domain.model.ModelInfo
import ru.chtcholeg.app.domain.model.buildSystemPrompt

class ChatRepository(
    private val gigaChatApi: GigaChatApi,
    private val ollamaApi: OllamaApi
) {
    private val conversationHistory = mutableListOf<ApiMessage>()
    private var currentSystemPrompt: String? = null

    suspend fun sendMessage(userMessage: String, settings: AppSettings): ApiResponse {
        val systemPrompt = settings.userProfile.buildSystemPrompt().takeIf { it.isNotBlank() }

        // Rebuild history with updated system prompt if needed
        if (systemPrompt != currentSystemPrompt) {
            val userMessages = conversationHistory.filter { it.role != "system" }
            conversationHistory.clear()
            if (systemPrompt != null) {
                conversationHistory.add(ApiMessage("system", systemPrompt))
            }
            conversationHistory.addAll(userMessages)
            currentSystemPrompt = systemPrompt
        }

        conversationHistory.add(ApiMessage("user", userMessage))

        val response = callApi(settings)

        conversationHistory.add(ApiMessage("assistant", response.content))
        return response
    }

    private suspend fun callApi(settings: AppSettings): ApiResponse {
        val modelId = settings.selectedModelId
        val params = settings.modelParameters

        val isGigaChat = ModelInfo.GIGACHAT_MODELS.any { it.id == modelId }

        return if (isGigaChat) {
            gigaChatApi.ensureAuthenticated(
                clientId = settings.gigachatClientId,
                clientSecret = settings.gigachatClientSecret
            )
            gigaChatApi.sendMessage(
                messages = conversationHistory.toList(),
                modelId = modelId,
                temperature = params.temperature,
                topP = params.topP,
                maxTokens = params.maxTokens,
                repetitionPenalty = params.repetitionPenalty
            )
        } else {
            ollamaApi.sendMessage(
                messages = conversationHistory.toList(),
                modelId = modelId,
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                maxTokens = params.maxTokens,
                repetitionPenalty = params.repetitionPenalty
            )
        }
    }

    fun resetHistory() {
        conversationHistory.clear()
        currentSystemPrompt = null
    }

    fun loadHistory(messages: List<ApiMessage>) {
        conversationHistory.clear()
        currentSystemPrompt = null
        conversationHistory.addAll(messages)
    }
}
