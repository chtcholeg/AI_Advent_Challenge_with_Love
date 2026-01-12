package ru.chtcholeg.app.data.repository

interface ChatRepository {
    /**
     * Send a message to the AI and get a response
     * @param userMessage The message from the user
     * @return The AI's response
     */
    suspend fun sendMessage(userMessage: String): String

    /**
     * Clear conversation history
     */
    fun clearHistory()
}
