package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.PlanModeRepository
import ru.chtcholeg.agent.domain.model.AllowedPrompt
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * ExitPlanMode tool - exits planning mode and requests user approval.
 */
class ExitPlanModeTool(
    private val planModeRepository: PlanModeRepository
) : LocalTool {

    override val name = "exit_plan_mode"

    override val description = """
        Exits planning mode and requests user approval for the plan.

        WHEN TO USE:
        - You have finished writing your plan to the plan file
        - Your plan is complete and unambiguous
        - You're ready for user to review and approve

        BEFORE USING:
        - Ensure your plan is written to the plan file
        - If you have unresolved questions, use ask_user_question first
        - Once finalized, use THIS tool to request approval

        IMPORTANT:
        - Do NOT use ask_user_question to ask "Is this plan okay?"
        - exit_plan_mode inherently requests user approval
        - This tool reads the plan from the file you wrote
        - Does NOT take plan content as parameter

        ALLOWED PROMPTS:
        - Specify prompt-based permissions needed for implementation
        - These describe categories of actions, not specific commands
        - Examples: "run tests", "install dependencies", "start dev server"
    """.trimIndent()

    override val inputSchema = toolSchema {
        raw("allowedPrompts", buildJsonObject {
            put("type", "array")
            put("description", "Prompt-based permissions needed to implement the plan")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("tool") {
                        put("type", "string")
                        put("description", "Tool name (e.g., 'Bash')")
                    }
                    putJsonObject("prompt") {
                        put("type", "string")
                        put("description", "Semantic description (e.g., 'run tests', 'install dependencies')")
                    }
                }
                putJsonArray("required") {
                    add("tool")
                    add("prompt")
                }
            }
        })
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            // Check if in plan mode
            if (!planModeRepository.planModeState.value.isActive) {
                return McpToolResult("Not in plan mode", isError = true)
            }

            // Parse allowed prompts
            val args = arguments.jsonObject
            val allowedPrompts = args["allowedPrompts"]?.jsonArray?.map { promptJson ->
                val promptObj = promptJson.jsonObject
                val tool = promptObj["tool"]?.jsonPrimitive?.content
                    ?: return McpToolResult("Missing 'tool' field in allowedPrompt", isError = true)
                val prompt = promptObj["prompt"]?.jsonPrimitive?.content
                    ?: return McpToolResult("Missing 'prompt' field in allowedPrompt", isError = true)

                AllowedPrompt(tool = tool, prompt = prompt)
            } ?: emptyList()

            // Read plan file content
            val planContent = planModeRepository.readPlanFile()
                ?: return McpToolResult("Plan file not found", isError = true)

            // Exit plan mode (but don't complete yet - wait for user approval)
            val planFilePath = planModeRepository.exitPlanMode(allowedPrompts)

            val output = buildString {
                appendLine("✓ Exiting planning mode")
                appendLine()
                appendLine("Plan file: $planFilePath")
                appendLine()
                appendLine("Plan Summary:")
                appendLine("─".repeat(50))
                // Show first 20 lines of plan
                val lines = planContent.split("\n")
                lines.take(20).forEach { appendLine(it) }
                if (lines.size > 20) {
                    appendLine("... (${lines.size - 20} more lines)")
                }
                appendLine("─".repeat(50))
                appendLine()
                appendLine("Allowed prompts for implementation:")
                if (allowedPrompts.isEmpty()) {
                    appendLine("  (none specified)")
                } else {
                    allowedPrompts.forEach { prompt ->
                        appendLine("  - ${prompt.tool}: ${prompt.prompt}")
                    }
                }
                appendLine()
                appendLine("⏳ Waiting for user approval...")
                appendLine()
                appendLine("User will review the plan and either:")
                appendLine("  ✓ Approve → proceed with implementation")
                appendLine("  ✗ Reject → provide feedback and revise plan")
            }

            McpToolResult(
                content = output,
                metadata = mapOf(
                    "plan_file" to (planFilePath ?: ""),
                    "mode" to "awaiting_approval",
                    "allowed_prompts_count" to allowedPrompts.size.toString()
                )
            )
        } catch (e: Exception) {
            McpToolResult("Error exiting plan mode: ${e.message}", isError = true)
        }
    }
}
