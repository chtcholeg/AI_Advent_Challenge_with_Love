package ru.chtcholeg.agent.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a specialized agent that can be invoked.
 */
@Serializable
data class AgentDefinition(
    val type: String,  // e.g., "Explore", "Plan", "python-pro"
    val name: String,
    val description: String,
    val capabilities: List<String>,
    val model: String = "sonnet",  // default model: sonnet, opus, haiku
    val maxTurns: Int? = null  // max API round-trips
)

/**
 * Agent execution task.
 */
@Serializable
data class AgentTask(
    val id: String,
    val agentType: String,
    val prompt: String,
    val description: String,  // 3-5 word description
    val model: String = "sonnet",
    val maxTurns: Int? = null,
    val runInBackground: Boolean = false,
    val resumeFromId: String? = null,  // Resume from previous execution
    val status: AgentTaskStatus = AgentTaskStatus.PENDING,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val result: String? = null,
    val error: String? = null
)

/**
 * Agent task status.
 */
@Serializable
enum class AgentTaskStatus {
    PENDING,     // Not started yet
    RUNNING,     // Currently executing
    COMPLETED,   // Successfully completed
    FAILED,      // Failed with error
    CANCELLED    // Cancelled by user
}

/**
 * Agent execution result.
 */
@Serializable
data class AgentResult(
    val agentId: String,
    val output: String,
    val success: Boolean,
    val executionTimeMs: Long,
    val error: String? = null
)
