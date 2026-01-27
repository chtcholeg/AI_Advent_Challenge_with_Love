package ru.chtcholeg.app.data.api

import ru.chtcholeg.app.data.model.ChatResponse

interface HuggingFaceApi {
    suspend fun sendMessage(
        accessToken: String,
        messages: List<ru.chtcholeg.app.data.model.Message>,
        model: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?
    ): ChatResponse
}
