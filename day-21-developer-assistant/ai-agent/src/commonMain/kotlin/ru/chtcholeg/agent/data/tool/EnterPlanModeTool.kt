package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.PlanModeRepository
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * EnterPlanMode tool - enters planning mode for complex implementation tasks.
 */
class EnterPlanModeTool(
    private val planModeRepository: PlanModeRepository
) : LocalTool {

    override val name = "enter_plan_mode"

    override val description = """
        Enters planning mode for complex implementation tasks requiring code changes.

        WHEN TO USE:
        - New feature implementation with multiple valid approaches
        - Code modifications affecting existing behavior or structure
        - Architectural decisions (choosing patterns, technologies)
        - Multi-file changes (3+ files)
        - Unclear requirements needing exploration
        - User preferences matter for implementation direction

        WHEN NOT TO USE:
        - Single-line or few-line fixes (typos, obvious bugs)
        - Adding single function with clear requirements
        - User gave very specific, detailed instructions
        - Pure research/exploration tasks (use grep/read instead)

        IN PLAN MODE YOU CAN:
        - Explore codebase with read, glob, grep tools
        - Understand existing patterns and architecture
        - Use ask_user_question to clarify approaches
        - Design implementation approach
        - Write plan to plan file
        - Exit with exit_plan_mode when ready

        IN PLAN MODE YOU CANNOT:
        - Write or edit files (no code changes)
        - Execute bash commands
        - Run tests or build commands

        IMPORTANT: This tool requires user approval to enter plan mode.
    """.trimIndent()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("task") {
                put("type", "string")
                put("description", "The task description that requires planning")
            }
        }
        putJsonArray("required") {
            add("task")
        }
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val task = args["task"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: task", isError = true)

            // Check if already in plan mode
            if (planModeRepository.planModeState.value.isActive) {
                return McpToolResult("Already in plan mode", isError = true)
            }

            // Enter plan mode
            val planFilePath = planModeRepository.enterPlanMode(task)

            val output = buildString {
                appendLine("✓ Entered planning mode")
                appendLine()
                appendLine("Task: $task")
                appendLine("Plan file: $planFilePath")
                appendLine()
                appendLine("You are now in PLAN MODE:")
                appendLine("- ✓ Can: read, glob, grep, ask_user_question")
                appendLine("- ✗ Cannot: write, edit, bash")
                appendLine()
                appendLine("Steps:")
                appendLine("1. Explore codebase to understand context")
                appendLine("2. Design implementation approach")
                appendLine("3. Write plan to plan file using write tool")
                appendLine("4. Use exit_plan_mode when ready for approval")
                appendLine()
                appendLine("The plan file has been created with a template.")
                appendLine("Update it with your implementation plan.")
            }

            McpToolResult(
                content = output,
                metadata = mapOf(
                    "plan_file" to planFilePath,
                    "mode" to "planning"
                )
            )
        } catch (e: Exception) {
            McpToolResult("Error entering plan mode: ${e.message}", isError = true)
        }
    }
}
