package ru.chtcholeg.agent.presentation.agent

/**
 * User actions in the agent screen.
 */
sealed interface AgentIntent {
    /**
     * Send a message to the AI agent.
     */
    data class SendMessage(val content: String) : AgentIntent

    /**
     * Start a new chat session (preserves old session in DB).
     */
    data object NewChat : AgentIntent

    /**
     * Load a saved session by ID.
     */
    data class LoadSession(val sessionId: String) : AgentIntent

    /**
     * Retry the last failed message.
     */
    data object RetryLastMessage : AgentIntent

    /**
     * Reload available tools from MCP servers.
     */
    data object ReloadTools : AgentIntent
}
