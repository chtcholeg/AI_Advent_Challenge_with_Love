package ru.chtcholeg.shared.data.api

import ru.chtcholeg.shared.data.model.ChatResponse

interface HuggingFaceApi {
    suspend fun sendMessage(
        accessToken: String,
        messages: List<ru.chtcholeg.shared.data.model.Message>,
        model: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?
    ): ChatResponse
}
