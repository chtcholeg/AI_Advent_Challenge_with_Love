package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.TaskRepository
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * TaskGet tool - retrieves a specific task by ID.
 */
class TaskGetTool(
    private val taskRepository: TaskRepository
) : LocalTool {

    override val name = "task_get"

    override val description = """
        Retrieves a task by its ID from the task list.

        Use this tool when:
        - Need full description and context before starting work
        - Understand task dependencies (what it blocks, what blocks it)
        - After being assigned a task to get complete requirements

        Returns full task details:
        - subject: Task title
        - description: Detailed requirements and context
        - status: pending, in_progress, or completed
        - blocks: Tasks waiting on this one to complete
        - blockedBy: Tasks that must complete before this one can start
        - metadata: Additional task metadata

        Before starting work, verify blockedBy list is empty.
    """.trimIndent()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("taskId") {
                put("type", "string")
                put("description", "ID of the task to retrieve")
            }
        }
        putJsonArray("required") {
            add("taskId")
        }
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val taskId = args["taskId"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: taskId", isError = true)

            val task = taskRepository.getTaskById(taskId)
                ?: return McpToolResult("Task not found: $taskId", isError = true)

            val output = buildString {
                appendLine("Task Details:")
                appendLine()
                appendLine("ID: ${task.id}")
                appendLine("Subject: ${task.subject}")
                appendLine("Description: ${task.description}")
                appendLine("Status: ${task.status}")

                if (task.activeForm != null) {
                    appendLine("Active Form: ${task.activeForm}")
                }

                if (task.owner != null) {
                    appendLine("Owner: ${task.owner}")
                }

                if (task.blocks.isNotEmpty()) {
                    appendLine("Blocks: ${task.blocks.joinToString(", ")}")
                }

                if (task.blockedBy.isNotEmpty()) {
                    appendLine("Blocked By: ${task.blockedBy.joinToString(", ")}")
                }

                if (task.metadata.isNotEmpty()) {
                    appendLine("Metadata:")
                    task.metadata.forEach { (key, value) ->
                        appendLine("  $key: $value")
                    }
                }

                appendLine()
                appendLine("Created: ${task.createdAt}")
                appendLine("Updated: ${task.updatedAt}")
                if (task.completedAt != null) {
                    appendLine("Completed: ${task.completedAt}")
                }
            }

            McpToolResult(output)
        } catch (e: Exception) {
            McpToolResult("Error getting task: ${e.message}", isError = true)
        }
    }
}
