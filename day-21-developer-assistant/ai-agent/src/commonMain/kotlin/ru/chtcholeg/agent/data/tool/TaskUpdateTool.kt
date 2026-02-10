package ru.chtcholeg.agent.data.tool

import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.TaskRepository
import ru.chtcholeg.agent.domain.model.TaskStatus
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * TaskUpdate tool - updates an existing task.
 */
class TaskUpdateTool(
    private val taskRepository: TaskRepository
) : LocalTool {

    override val name = "task_update"

    override val description = """
        Updates a task in the task list.

        Use this tool when:
        - Mark task as in_progress when starting work
        - Mark task as completed when finished (ONLY if fully accomplished)
        - Mark task as deleted to permanently remove it
        - Change task details (subject, description, activeForm)
        - Set task owner (claim task)
        - Add dependencies (blocks, blockedBy)
        - Update metadata

        IMPORTANT: Only mark completed when task is FULLY done.
        If encountering errors/blockers, keep in_progress and create new task for blockers.

        Status workflow: pending → in_progress → completed
    """.trimIndent()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("taskId") {
                put("type", "string")
                put("description", "ID of the task to update")
            }
            putJsonObject("status") {
                put("type", "string")
                put("description", "New status: 'pending', 'in_progress', 'completed', or 'deleted'")
                putJsonArray("enum") {
                    add("pending")
                    add("in_progress")
                    add("completed")
                    add("deleted")
                }
            }
            putJsonObject("subject") {
                put("type", "string")
                put("description", "New subject (imperative form)")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "New description")
            }
            putJsonObject("activeForm") {
                put("type", "string")
                put("description", "New activeForm (present continuous)")
            }
            putJsonObject("owner") {
                put("type", "string")
                put("description", "New owner (agent name)")
            }
            putJsonObject("metadata") {
                put("type", "object")
                put("description", "Metadata keys to merge (set key to null to delete)")
                putJsonObject("properties") {}
                put("additionalProperties", true)
            }
            putJsonObject("addBlocks") {
                put("type", "array")
                put("description", "Task IDs that this task blocks")
                putJsonObject("items") {
                    put("type", "string")
                }
            }
            putJsonObject("addBlockedBy") {
                put("type", "array")
                put("description", "Task IDs that block this task")
                putJsonObject("items") {
                    put("type", "string")
                }
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

            // Parse updates
            val newStatus = args["status"]?.jsonPrimitive?.contentOrNull?.let { TaskStatus.valueOf(it.uppercase()) }
            val newSubject = args["subject"]?.jsonPrimitive?.contentOrNull
            val newDescription = args["description"]?.jsonPrimitive?.contentOrNull
            val newActiveForm = args["activeForm"]?.jsonPrimitive?.contentOrNull
            val newOwner = args["owner"]?.jsonPrimitive?.contentOrNull

            val newMetadata = args["metadata"]?.jsonObject?.let { metaObj ->
                val merged = task.metadata.toMutableMap()
                metaObj.entries.forEach { (key, value) ->
                    if (value is JsonNull) {
                        merged.remove(key)
                    } else {
                        merged[key] = value.jsonPrimitive.contentOrNull ?: value.toString()
                    }
                }
                merged
            }

            val addBlocks = args["addBlocks"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val addBlockedBy = args["addBlockedBy"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            // Update task
            val updatedTask = task.copy(
                subject = newSubject ?: task.subject,
                description = newDescription ?: task.description,
                activeForm = newActiveForm ?: task.activeForm,
                status = newStatus ?: task.status,
                owner = newOwner ?: task.owner,
                blocks = (task.blocks + addBlocks).distinct(),
                blockedBy = (task.blockedBy + addBlockedBy).distinct(),
                metadata = newMetadata ?: task.metadata,
                completedAt = if (newStatus == TaskStatus.COMPLETED && task.completedAt == null) {
                    Clock.System.now().toEpochMilliseconds()
                } else {
                    task.completedAt
                }
            )

            taskRepository.updateTask(updatedTask)

            McpToolResult("Task updated successfully: $taskId")
        } catch (e: Exception) {
            McpToolResult("Error updating task: ${e.message}", isError = true)
        }
    }
}
