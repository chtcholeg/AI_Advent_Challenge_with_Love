package ru.chtcholeg.godagent.domain.model

sealed class AgentStep {
    data class ToolCall(val toolName: String, val args: String) : AgentStep()
    data class ToolResult(val toolName: String, val result: String, val isError: Boolean = false) : AgentStep()
    data class FinalAnswer(val content: String) : AgentStep()
    data class Error(val message: String) : AgentStep()
    data class StatusUpdate(val message: String) : AgentStep()
}
