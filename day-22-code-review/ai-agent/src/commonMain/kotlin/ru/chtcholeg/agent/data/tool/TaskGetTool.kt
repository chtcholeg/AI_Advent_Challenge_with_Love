package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.AgentExecutor
import ru.chtcholeg.agent.data.repository.TaskRepository
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * TaskGet tool - retrieves a specific task by ID.
 * Also supports checking background agent status for agent_* task IDs.
 */
class TaskGetTool(
    private val taskRepository: TaskRepository,
    private val agentExecutor: Lazy<AgentExecutor>? = null
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

    override val inputSchema = toolSchema {
        string("taskId", "ID of the task to retrieve", required = true)
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val taskId = args["taskId"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: taskId", isError = true)

            // Check for background agent task
            if (taskId.startsWith("agent_") && agentExecutor != null) {
                val bgAgent = agentExecutor.value.getBackgroundStatus(taskId)
                if (bgAgent != null) {
                    val output = buildString {
                        appendLine("Background Agent Status:")
                        appendLine()
                        appendLine("Task ID: ${bgAgent.taskId}")
                        appendLine("Status: ${bgAgent.status}")
                        appendLine("Started: ${bgAgent.startedAt}")
                        if (bgAgent.completedAt != null) {
                            appendLine("Completed: ${bgAgent.completedAt}")
                        }
                        appendLine()
                        appendLine("Output:")
                        appendLine("─".repeat(50))
                        appendLine(bgAgent.output)
                        appendLine("─".repeat(50))
                    }
                    return McpToolResult(output)
                }
            }

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
