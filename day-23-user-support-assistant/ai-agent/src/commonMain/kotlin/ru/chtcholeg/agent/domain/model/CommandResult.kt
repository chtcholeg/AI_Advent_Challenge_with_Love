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
     * When [enableTools] is true, MCP and local tools remain available.
     * [excludeTools] optionally filters out tools by name.
     * [includeTools] optionally restricts to ONLY these tools (whitelist, takes priority over excludeTools).
     * [requiresDocValidation] when true, enables Phase 2 RAG documentation validation (for /review-pr).
     */
    data class NeedsLlmProcessing(
        val context: String,
        val query: String,
        val enableTools: Boolean = false,
        val excludeTools: List<String>? = null,
        val includeTools: List<String>? = null,
        val requiresDocValidation: Boolean = false
    ) : CommandResult()

    /**
     * Command failed with an error message.
     */
    data class Error(val message: String) : CommandResult()
}
