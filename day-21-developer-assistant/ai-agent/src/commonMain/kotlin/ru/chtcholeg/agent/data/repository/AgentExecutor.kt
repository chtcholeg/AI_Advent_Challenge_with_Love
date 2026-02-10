package ru.chtcholeg.agent.data.repository

import kotlinx.datetime.Clock
import ru.chtcholeg.agent.data.agent.AgentRegistry
import ru.chtcholeg.agent.domain.model.AgentResult
import ru.chtcholeg.agent.domain.model.AgentTask
import ru.chtcholeg.agent.domain.model.AgentTaskStatus
import kotlin.random.Random
import kotlin.time.measureTimedValue

/**
 * Executes specialized agents.
 *
 * NOTE: This is a simplified implementation that delegates to the main AI.
 * In production, this would spawn separate AI instances with specialized prompts.
 */
class AgentExecutor(
    private val agentRepository: AgentRepository
) {

    /**
     * Execute an agent task.
     *
     * For now, this delegates to the main AI with a specialized prompt.
     * In production, this would spawn a separate AI instance.
     */
    suspend fun execute(task: AgentTask): AgentResult {
        val agentDef = AgentRegistry.getAgent(task.agentType)
            ?: return AgentResult(
                agentId = task.id,
                output = "Unknown agent type: ${task.agentType}",
                success = false,
                executionTimeMs = 0,
                error = "Agent type not found"
            )

        // Build specialized prompt for this agent
        val specializedPrompt = buildAgentPrompt(agentDef, task.prompt)

        // Measure execution time
        val (messages, duration) = measureTimedValue {
            try {
                // Execute using main agent repository
                // This will use the agent's specialized system prompt
                agentRepository.sendMessage(specializedPrompt)
            } catch (e: Exception) {
                listOf()
            }
        }

        // Extract result from AI messages
        val output = messages.lastOrNull()?.content ?: "No output"
        val success = messages.isNotEmpty() && messages.none { it.type == ru.chtcholeg.agent.domain.model.MessageType.ERROR }

        return AgentResult(
            agentId = task.id,
            output = output,
            success = success,
            executionTimeMs = duration.inWholeMilliseconds,
            error = if (!success) "Agent execution failed" else null
        )
    }

    /**
     * Build specialized prompt for agent.
     */
    private fun buildAgentPrompt(agentDef: ru.chtcholeg.agent.domain.model.AgentDefinition, userPrompt: String): String {
        return buildString {
            appendLine("You are a ${agentDef.name}.")
            appendLine()
            appendLine("Description: ${agentDef.description}")
            appendLine()
            appendLine("Your capabilities:")
            agentDef.capabilities.forEach { capability ->
                appendLine("- $capability")
            }
            appendLine()
            appendLine("Task:")
            appendLine(userPrompt)
            appendLine()
            appendLine("Provide a detailed, professional response using your specialized expertise.")
        }
    }

    /**
     * Execute agent in background.
     * Returns immediately with task ID.
     */
    suspend fun executeInBackground(task: AgentTask): String {
        // TODO: Implement background execution with coroutines
        // For now, return task ID
        return task.id
    }

    /**
     * Resume agent from previous execution.
     */
    suspend fun resume(taskId: String, additionalPrompt: String): AgentResult {
        // TODO: Implement resume mechanism
        // For now, treat as new execution
        return AgentResult(
            agentId = taskId,
            output = "Resume not implemented yet",
            success = false,
            executionTimeMs = 0,
            error = "Resume feature coming soon"
        )
    }
}
