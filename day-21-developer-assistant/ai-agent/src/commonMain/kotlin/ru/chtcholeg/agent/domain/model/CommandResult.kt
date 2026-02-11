package ru.chtcholeg.agent.domain.model

/**
 * Result of a command execution.
 */
sealed class CommandResult {
    /**
     * Command executed successfully with a response.
     */
    data class Success(val response: String) : CommandResult()

    /**
     * Command produced context that needs additional LLM processing.
     * The [context] is sent as system context, and [query] is the user's question.
     */
    data class NeedsLlmProcessing(
        val context: String,
        val query: String,
        val enableTools: Boolean = false
    ) : CommandResult()

    /**
     * Command failed with an error message.
     */
    data class Error(val message: String) : CommandResult()
}
