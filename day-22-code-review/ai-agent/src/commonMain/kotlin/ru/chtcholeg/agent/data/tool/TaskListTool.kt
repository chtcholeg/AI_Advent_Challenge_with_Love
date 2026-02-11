package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.TaskRepository
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * TaskList tool - lists all tasks.
 */
class TaskListTool(
    private val taskRepository: TaskRepository
) : LocalTool {

    override val name = "task_list"

    override val description = """
        Lists all tasks in the task list.

        Use this tool when:
        - See what tasks are available to work on
        - Check overall progress
        - Find tasks that are blocked
        - After completing a task to check for newly unblocked work
        - Find next task to claim

        Returns summary of each task:
        - id: Task identifier
        - subject: Brief description
        - status: pending, in_progress, or completed
        - owner: Agent ID if assigned, empty if available
        - blockedBy: List of task IDs that must be resolved first
        - activeForm: Present continuous form for display

        Prefer working on tasks in ID order (lowest first) when multiple available.
    """.trimIndent()

    override val inputSchema = toolSchema { /* No parameters */ }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val summaries = taskRepository.getTaskSummaries()

            if (summaries.isEmpty()) {
                return McpToolResult("No tasks in the task list")
            }

            val output = buildString {
                appendLine("Task List (${summaries.size} tasks):")
                appendLine()
                summaries.forEach { summary ->
                    appendLine("ID: ${summary.id}")
                    appendLine("  Subject: ${summary.subject}")
                    appendLine("  Status: ${summary.status}")
                    if (summary.owner != null) {
                        appendLine("  Owner: ${summary.owner}")
                    }
                    if (summary.blockedBy.isNotEmpty()) {
                        appendLine("  Blocked by: ${summary.blockedBy.joinToString(", ")}")
                    }
                    if (summary.activeForm != null) {
                        appendLine("  Active form: ${summary.activeForm}")
                    }
                    appendLine()
                }
            }

            McpToolResult(output)
        } catch (e: Exception) {
            McpToolResult("Error listing tasks: ${e.message}", isError = true)
        }
    }
}
