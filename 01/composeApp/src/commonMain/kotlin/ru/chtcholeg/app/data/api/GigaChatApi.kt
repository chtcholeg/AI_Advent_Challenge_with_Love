package ru.chtcholeg.app.data.api

import ru.chtcholeg.app.data.model.AuthResponse
import ru.chtcholeg.app.data.model.ChatResponse
import ru.chtcholeg.app.data.model.Message

interface GigaChatApi {
    /**
     * Authenticate with GigaChat API using client credentials
     * @param clientId Client ID from GigaChat
     * @param clientSecret Client secret from GigaChat
     * @return AuthResponse containing access token and expiration
     */
    suspend fun authenticate(
        clientId: String,
        clientSecret: String
    ): AuthResponse

    /**
     * Send a message to GigaChat API
     * @param accessToken Valid access token
     * @param messages List of messages in the conversation
     * @param model Model name (default: "GigaChat")
     * @param temperature Controls randomness (0.0-2.0, higher = more random)
     * @param topP Nucleus sampling threshold (0.0-1.0)
     * @param maxTokens Maximum tokens in response
     * @param repetitionPenalty Penalty for repeating tokens (0.0-2.0)
     * @return ChatResponse containing AI response
     */
    suspend fun sendMessage(
        accessToken: String,
        messages: List<Message>,
        model: String = "GigaChat",
        temperature: Float? = null,
        topP: Float? = null,
        maxTokens: Int? = null,
        repetitionPenalty: Float? = null
    ): ChatResponse
}
