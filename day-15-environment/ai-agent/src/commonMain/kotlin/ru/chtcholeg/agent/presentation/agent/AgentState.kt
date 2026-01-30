package ru.chtcholeg.agent.presentation.agent

import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.shared.domain.model.McpTool

/**
 * UI state for the agent screen.
 */
data class AgentState(
    val messages: List<AgentMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUserMessage: String? = null,
    val availableTools: List<McpTool> = emptyList(),
    val toolsLoading: Boolean = false
)
