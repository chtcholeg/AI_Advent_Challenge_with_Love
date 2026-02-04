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
     * Clear the conversation history.
     */
    data object ClearChat : AgentIntent

    /**
     * Retry the last failed message.
     */
    data object RetryLastMessage : AgentIntent

    /**
     * Reload available tools from MCP servers.
     */
    data object ReloadTools : AgentIntent
}
