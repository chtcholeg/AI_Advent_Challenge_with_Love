package ru.chtcholeg.agent.data.tool

import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.TaskRepository
import ru.chtcholeg.agent.domain.model.Task
import ru.chtcholeg.agent.domain.model.TaskStatus
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult
import kotlin.random.Random

/**
 * TaskCreate tool - creates a new task.
 */
class TaskCreateTool(
    private val taskRepository: TaskRepository
) : LocalTool {

    override val name = "task_create"

    override val description = """
        Creates a structured task list for tracking progress during coding sessions.

        ⚠️ IMPORTANT: This is a TODO-list tool for TRACKING work, NOT for EXECUTING it!
        Tasks created here won't be executed automatically - you must do the work yourself.

        Use this tool proactively when:
        - Complex multi-step tasks (3+ steps required) that YOU will implement
        - Non-trivial tasks requiring careful progress tracking
        - User explicitly requests todo list
        - User provides multiple tasks (numbered or comma-separated)

        DO NOT use when:
        - User asks to DO something NOW (execute the work directly instead)
        - Commands like /review-pr, /analyze, /fix (execute these directly)
        - Single, straightforward task that can be done immediately
        - Trivial task (less than 3 steps)
        - Purely conversational/informational task

        All tasks are created with status 'pending'.
        Always provide activeForm (present continuous) for spinner display.
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("subject", "Brief, actionable title in imperative form (e.g., 'Fix authentication bug')", required = true)
        string("description", "Detailed description with context and acceptance criteria", required = true)
        string("activeForm", "Present continuous form for spinner (e.g., 'Fixing authentication bug')")
        obj("metadata", "Optional arbitrary metadata to attach to the task", additionalProperties = true)
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val subject = args["subject"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: subject", isError = true)

            val description = args["description"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: description", isError = true)

            val activeForm = args["activeForm"]?.jsonPrimitive?.contentOrNull

            val metadata = args["metadata"]?.jsonObject?.let { metaObj ->
                metaObj.entries.associate { (key, value) ->
                    key to (value.jsonPrimitive.contentOrNull ?: value.toString())
                }
            } ?: emptyMap()

            val task = Task(
                id = generateTaskId(),
                subject = subject,
                description = description,
                activeForm = activeForm,
                status = TaskStatus.PENDING,
                metadata = metadata
            )

            taskRepository.createTask(task)

            McpToolResult("Task created successfully: ${task.id}")
        } catch (e: Exception) {
            McpToolResult("Error creating task: ${e.message}", isError = true)
        }
    }

    private fun generateTaskId(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = Random.nextInt(1000, 9999)
        return "task_${timestamp}_$random"
    }
}
