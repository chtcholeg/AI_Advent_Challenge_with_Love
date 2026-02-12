package ru.chtcholeg.shared.data.api

import ru.chtcholeg.shared.data.model.ChatResponse
import ru.chtcholeg.shared.data.model.GigaChatFunction
import ru.chtcholeg.shared.data.model.Message

interface HuggingFaceApi {
    suspend fun sendMessage(
        accessToken: String,
        messages: List<Message>,
        model: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?,
        functions: List<GigaChatFunction>? = null,
        systemPrompt: String? = null
    ): ChatResponse
}
