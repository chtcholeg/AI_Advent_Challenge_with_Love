package ru.chtcholeg.agent.domain.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents a task in the task management system.
 */
@Serializable
data class Task(
    val id: String,
    val subject: String,
    val description: String,
    val activeForm: String? = null,  // Present continuous form for spinner (e.g., "Running tests")
    val status: TaskStatus = TaskStatus.PENDING,
    val owner: String? = null,  // Agent ID that owns this task
    val blocks: List<String> = emptyList(),  // Task IDs that this task blocks
    val blockedBy: List<String> = emptyList(),  // Task IDs that block this task
    val metadata: Map<String, String> = emptyMap(),  // Arbitrary metadata
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val completedAt: Long? = null
)

/**
 * Status of a task.
 */
@Serializable
enum class TaskStatus {
    PENDING,      // Not started yet
    IN_PROGRESS,  // Currently being worked on
    COMPLETED,    // Successfully completed
    DELETED       // Soft-deleted (removed from task list)
}

/**
 * Summary view of a task for TaskList.
 */
@Serializable
data class TaskSummary(
    val id: String,
    val subject: String,
    val status: TaskStatus,
    val owner: String?,
    val blockedBy: List<String>,
    val activeForm: String?
)
