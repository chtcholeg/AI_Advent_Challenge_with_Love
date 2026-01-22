package ru.chtcholeg.app.data.repository

import ru.chtcholeg.app.domain.model.AiResponse

interface ChatRepository {
    /**
     * Send a message to the AI and get a response
     * @param userMessage The message from the user
     * @return The AI's response with metadata (execution time, tokens)
     */
    suspend fun sendMessage(userMessage: String): AiResponse

    /**
     * Send a message with a custom system prompt (for summarization, etc.)
     * This does NOT affect the main conversation history.
     * @param userMessage The message from the user
     * @param systemPrompt Custom system prompt to use
     * @return The AI's response with metadata
     */
    suspend fun sendMessageWithCustomSystemPrompt(userMessage: String, systemPrompt: String): AiResponse

    /**
     * Clear conversation history
     */
    fun clearHistory()

    /**
     * Initialize conversation history with a context message (e.g., summary).
     * This sets up the conversation with prior context for continuation.
     * @param context The context/summary to use as conversation foundation
     */
    fun initializeWithContext(context: String)
}
