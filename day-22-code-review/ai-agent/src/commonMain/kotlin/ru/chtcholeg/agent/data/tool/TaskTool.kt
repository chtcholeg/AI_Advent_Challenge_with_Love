package ru.chtcholeg.agent.data.tool

import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.agent.AgentRegistry
import ru.chtcholeg.agent.data.repository.AgentExecutor
import ru.chtcholeg.agent.domain.model.AgentTask
import ru.chtcholeg.agent.domain.model.AgentTaskStatus
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult
import kotlin.random.Random

/**
 * Task tool - launches specialized agents for complex, multi-step tasks.
 */
class TaskTool(
    private val agentExecutor: AgentExecutor
) : LocalTool {

    override val name = "task"

    override val description = """
        Launches specialized agents (subprocesses) that autonomously handle complex tasks.

        WHEN TO USE:
        - Exploring codebase to gather context (use Explore agent)
        - Complex questions requiring research (use general-purpose agent)
        - Language-specific tasks (use python-pro, kotlin-specialist, etc.)
        - Architecture planning (use Plan agent)
        - Code review (use code-reviewer agent)

        WHEN NOT TO USE:
        - Simple file operations (use read, write, edit directly)
        - Known file/class location (use read/grep directly)
        - Task can be done in 1-2 tool calls

        AVAILABLE AGENTS:
        - Explore: Fast codebase exploration (quick, medium, thorough modes)
        - Plan: Software architect for implementation planning
        - general-purpose: Complex questions and multi-step tasks
        - python-pro, kotlin-specialist, typescript-pro, java-architect, rust-engineer
        - backend-developer, devops-engineer, database-optimizer
        - frontend-developer, react-specialist, mobile-app-developer
        - qa-expert, test-automator, code-reviewer, debugger
        - architect-reviewer, refactoring-specialist, api-designer
        - technical-writer, api-documenter

        TIPS:
        - Provide clear, detailed prompts for autonomous work
        - Use description parameter (3-5 words) for tracking
        - Agents return complete information - trust their outputs
        - For exploration, specify thoroughness: "quick", "medium", "very thorough"
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("subagent_type", "Type of specialized agent (e.g., 'Explore', 'python-pro', 'code-reviewer')", required = true)
        string("prompt", "The task for the agent to perform (detailed instructions)", required = true)
        string("description", "Short 3-5 word description of what agent will do", required = true)
        string("model", "Model to use: 'sonnet' (default), 'opus', 'haiku' (fast)", enum = listOf("sonnet", "opus", "haiku"))
        integer("max_turns", "Maximum agentic turns (API round-trips) before stopping")
        boolean("run_in_background", "Run agent in background (returns immediately)", default = false)
        string("resume", "Agent ID to resume from previous execution")
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject

            val subagentType = args["subagent_type"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: subagent_type", isError = true)

            val prompt = args["prompt"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: prompt", isError = true)

            val description = args["description"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: description", isError = true)

            val model = args["model"]?.jsonPrimitive?.contentOrNull ?: "sonnet"
            val maxTurns = args["max_turns"]?.jsonPrimitive?.intOrNull
            val runInBackground = args["run_in_background"]?.jsonPrimitive?.booleanOrNull ?: false
            val resumeId = args["resume"]?.jsonPrimitive?.contentOrNull

            // Validate agent type
            val agentDef = AgentRegistry.getAgent(subagentType)
                ?: return McpToolResult(
                    "Unknown agent type: $subagentType\n\nAvailable agents: ${AgentRegistry.getAllAgents().joinToString(", ") { it.type }}",
                    isError = true
                )

            // Create agent task
            val task = AgentTask(
                id = generateTaskId(),
                agentType = subagentType,
                prompt = prompt,
                description = description,
                model = model,
                maxTurns = maxTurns,
                runInBackground = runInBackground,
                resumeFromId = resumeId,
                status = AgentTaskStatus.RUNNING,
                startedAt = Clock.System.now().toEpochMilliseconds()
            )

            // Execute agent
            val output = if (runInBackground) {
                val taskId = agentExecutor.executeInBackground(task)
                buildString {
                    appendLine("✓ Agent launched in background")
                    appendLine()
                    appendLine("Agent: ${agentDef.name}")
                    appendLine("Task ID: $taskId")
                    appendLine("Description: $description")
                    appendLine()
                    appendLine("Agent is running in background.")
                    appendLine("Use task_get to check progress: task_get({\"taskId\": \"$taskId\"})")
                }
            } else {
                // Synchronous execution
                val result = if (resumeId != null) {
                    agentExecutor.resume(resumeId, prompt)
                } else {
                    agentExecutor.execute(task)
                }

                buildString {
                    appendLine("✓ Agent execution completed")
                    appendLine()
                    appendLine("Agent: ${agentDef.name}")
                    appendLine("Description: $description")
                    appendLine("Execution time: ${result.executionTimeMs}ms")
                    appendLine("Status: ${if (result.success) "Success" else "Failed"}")
                    appendLine()
                    appendLine("─".repeat(50))
                    appendLine("Result:")
                    appendLine("─".repeat(50))
                    appendLine(result.output)
                    appendLine("─".repeat(50))

                    if (result.error != null) {
                        appendLine()
                        appendLine("Error: ${result.error}")
                    }
                }
            }

            McpToolResult(
                content = output,
                metadata = mapOf(
                    "agent_type" to subagentType,
                    "agent_id" to task.id,
                    "background" to runInBackground.toString()
                )
            )
        } catch (e: Exception) {
            McpToolResult("Error executing agent: ${e.message}", isError = true)
        }
    }

    private fun generateTaskId(): String {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = Random.nextInt(1000, 9999)
        return "agent_${timestamp}_$random"
    }
}
