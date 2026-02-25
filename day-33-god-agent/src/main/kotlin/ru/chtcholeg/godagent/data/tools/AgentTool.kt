package ru.chtcholeg.godagent.data.tools

interface AgentTool {
    val name: String
    val description: String
    val parametersDescription: String  // Human-readable parameters for prompt
    suspend fun execute(argsJson: String): String
}

data class ToolStatus(
    val name: String,
    val displayName: String,
    val isAvailable: Boolean,
    val errorMessage: String? = null
)
