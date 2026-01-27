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

    /**
     * Restore conversation history from a list of messages.
     * This is used when loading a saved session to preserve context.
     * @param messages The list of messages to restore (ChatMessage objects)
     * @param preserveSummaryContext If true, keeps existing summary context when restoring
     */
    fun restoreHistory(messages: List<ru.chtcholeg.app.domain.model.ChatMessage>, preserveSummaryContext: Boolean = false)

    /**
     * Set system prompt for the current session.
     * This is used when loading a saved session to restore the correct system prompt.
     * @param systemPrompt The system prompt to use, or null to clear it
     */
    fun setSystemPrompt(systemPrompt: String?)

    /**
     * Unlock system prompt to allow it to be updated from UI settings.
     * This is called when user explicitly changes response mode in UI.
     */
    fun unlockSystemPrompt()
}
