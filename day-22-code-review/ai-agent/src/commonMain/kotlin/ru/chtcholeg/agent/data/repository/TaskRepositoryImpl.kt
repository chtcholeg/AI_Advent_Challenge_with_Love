package ru.chtcholeg.agent.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import ru.chtcholeg.agent.data.local.McpDatabase
import ru.chtcholeg.agent.domain.model.Task
import ru.chtcholeg.agent.domain.model.TaskStatus
import ru.chtcholeg.agent.domain.model.TaskSummary

class TaskRepositoryImpl(
    private val database: McpDatabase
) : TaskRepository {

    private val queries = database.taskQueries

    override fun getAllTasks(): Flow<List<Task>> {
        return queries.getAllTasks()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getTaskById(taskId: String): Task? = withContext(Dispatchers.Default) {
        queries.getTaskById(taskId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getPendingTasks(): List<Task> = withContext(Dispatchers.Default) {
        queries.getPendingTasks().executeAsList().map { it.toDomain() }
    }

    override suspend fun getTasksByStatus(status: TaskStatus): List<Task> = withContext(Dispatchers.Default) {
        queries.getTasksByStatus(status.name).executeAsList().map { it.toDomain() }
    }

    override suspend fun getTasksByOwner(owner: String): List<Task> = withContext(Dispatchers.Default) {
        queries.getTasksByOwner(owner).executeAsList().map { it.toDomain() }
    }

    override suspend fun createTask(task: Task) = withContext(Dispatchers.Default) {
        queries.insertTask(
            id = task.id,
            subject = task.subject,
            description = task.description,
            activeForm = task.activeForm,
            status = task.status.name,
            owner = task.owner,
            blocks = task.blocks.joinToString(","),
            blockedBy = task.blockedBy.joinToString(","),
            metadata = Json.encodeToString(task.metadata),
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            completedAt = task.completedAt
        )
    }

    override suspend fun updateTask(task: Task) = withContext(Dispatchers.Default) {
        queries.updateTask(
            subject = task.subject,
            description = task.description,
            activeForm = task.activeForm,
            status = task.status.name,
            owner = task.owner,
            blocks = task.blocks.joinToString(","),
            blockedBy = task.blockedBy.joinToString(","),
            metadata = Json.encodeToString(task.metadata),
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            completedAt = task.completedAt,
            id = task.id
        )
    }

    override suspend fun deleteTask(taskId: String) = withContext(Dispatchers.Default) {
        queries.deleteTask(
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            id = taskId
        )
    }

    override suspend fun getTaskSummaries(): List<TaskSummary> = withContext(Dispatchers.Default) {
        queries.getAllTasks().executeAsList().map { entity ->
            TaskSummary(
                id = entity.id,
                subject = entity.subject,
                status = TaskStatus.valueOf(entity.status),
                owner = entity.owner,
                blockedBy = entity.blockedBy.split(",").filter { it.isNotBlank() },
                activeForm = entity.activeForm
            )
        }
    }

    override suspend fun clearAllTasks() = withContext(Dispatchers.Default) {
        queries.clearAllTasks()
    }

    private fun ru.chtcholeg.agent.data.local.Task.toDomain(): Task {
        return Task(
            id = id,
            subject = subject,
            description = description,
            activeForm = activeForm,
            status = TaskStatus.valueOf(status),
            owner = owner,
            blocks = blocks.split(",").filter { it.isNotBlank() },
            blockedBy = blockedBy.split(",").filter { it.isNotBlank() },
            metadata = try {
                Json.decodeFromString<Map<String, String>>(metadata)
            } catch (e: Exception) {
                emptyMap()
            },
            createdAt = createdAt,
            updatedAt = updatedAt,
            completedAt = completedAt
        )
    }
}
