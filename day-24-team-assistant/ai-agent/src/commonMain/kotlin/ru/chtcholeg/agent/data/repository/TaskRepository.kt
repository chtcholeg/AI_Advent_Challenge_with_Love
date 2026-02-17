package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.flow.Flow
import ru.chtcholeg.agent.domain.model.Task
import ru.chtcholeg.agent.domain.model.TaskStatus
import ru.chtcholeg.agent.domain.model.TaskSummary

/**
 * Repository for managing tasks.
 */
interface TaskRepository {
    /**
     * Get all tasks (excluding deleted).
     */
    fun getAllTasks(): Flow<List<Task>>

    /**
     * Get task by ID.
     */
    suspend fun getTaskById(taskId: String): Task?

    /**
     * Get pending tasks (available to claim).
     */
    suspend fun getPendingTasks(): List<Task>

    /**
     * Get tasks by status.
     */
    suspend fun getTasksByStatus(status: TaskStatus): List<Task>

    /**
     * Get tasks by owner.
     */
    suspend fun getTasksByOwner(owner: String): List<Task>

    /**
     * Create a new task.
     */
    suspend fun createTask(task: Task)

    /**
     * Update existing task.
     */
    suspend fun updateTask(task: Task)

    /**
     * Delete task (soft delete - sets status to DELETED).
     */
    suspend fun deleteTask(taskId: String)

    /**
     * Get task summaries for display.
     */
    suspend fun getTaskSummaries(): List<TaskSummary>

    /**
     * Clear all tasks.
     */
    suspend fun clearAllTasks()
}
