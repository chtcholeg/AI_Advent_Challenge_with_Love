package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import ru.chtcholeg.agent.data.agent.AgentRegistry
import ru.chtcholeg.agent.domain.model.AgentDefinition
import ru.chtcholeg.agent.domain.model.AgentResult
import ru.chtcholeg.agent.domain.model.AgentTask
import ru.chtcholeg.agent.domain.model.AgentTaskStatus
import ru.chtcholeg.agent.config.AgentConfig
import kotlin.time.measureTimedValue

/**
 * Executes specialized agents with isolated context.
 *
 * Each subagent gets:
 * - Its own conversation history (no access to parent's context)
 * - A specialized system prompt based on agent type
 * - A filtered set of tools (defined by AgentDefinition.allowedTools)
 * - A maxTurns limit to prevent infinite loops
 *
 * Supports background execution with progress tracking.
 */
class AgentExecutor(
    private val agentRepository: AgentRepository
) {
    private val backgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Track running background agents: taskId → (job, result)
    private val backgroundJobs = mutableMapOf<String, BackgroundAgent>()

    /**
     * Execute an agent task with isolated context, filtered tools, and maxTurns limit.
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

        val systemPrompt = buildSystemPrompt(agentDef)
        val maxTurns = task.maxTurns ?: agentDef.maxTurns ?: DEFAULT_MAX_TURNS

        val (output, duration) = measureTimedValue {
            try {
                agentRepository.executeIsolated(
                    systemPrompt = systemPrompt,
                    userMessage = task.prompt,
                    allowedTools = agentDef.allowedTools,
                    maxTurns = maxTurns
                )
            } catch (e: Exception) {
                "Error during agent execution: ${e.message}"
            }
        }

        val success = !output.startsWith("Error:")
        return AgentResult(
            agentId = task.id,
            output = output,
            success = success,
            executionTimeMs = duration.inWholeMilliseconds,
            error = if (!success) output else null
        )
    }

    /**
     * Execute agent in background. Returns immediately with task ID.
     * Progress can be checked via getBackgroundStatus().
     */
    fun executeInBackground(task: AgentTask): String {
        // Check concurrent limit
        val runningCount = backgroundJobs.values.count { it.status == AgentTaskStatus.RUNNING }
        if (runningCount >= MAX_CONCURRENT_BACKGROUND) {
            // Return error — too many background agents
            backgroundJobs[task.id] = BackgroundAgent(
                taskId = task.id,
                status = AgentTaskStatus.FAILED,
                output = "Too many concurrent background agents (max $MAX_CONCURRENT_BACKGROUND). Wait for running agents to complete.",
                job = null,
                startedAt = Clock.System.now().toEpochMilliseconds()
            )
            return task.id
        }

        val agent = BackgroundAgent(
            taskId = task.id,
            status = AgentTaskStatus.RUNNING,
            output = "Agent starting...",
            job = null,
            startedAt = Clock.System.now().toEpochMilliseconds()
        )
        backgroundJobs[task.id] = agent

        val job = backgroundScope.launch {
            try {
                val result = execute(task)
                backgroundJobs[task.id] = agent.copy(
                    status = if (result.success) AgentTaskStatus.COMPLETED else AgentTaskStatus.FAILED,
                    output = result.output,
                    completedAt = Clock.System.now().toEpochMilliseconds()
                )
            } catch (e: Exception) {
                backgroundJobs[task.id] = agent.copy(
                    status = AgentTaskStatus.FAILED,
                    output = "Background agent failed: ${e.message}",
                    completedAt = Clock.System.now().toEpochMilliseconds()
                )
            }
        }

        backgroundJobs[task.id] = agent.copy(job = job)
        return task.id
    }

    /**
     * Get the status and output of a background agent.
     */
    fun getBackgroundStatus(taskId: String): BackgroundAgent? {
        return backgroundJobs[taskId]
    }

    /**
     * Cancel a running background agent.
     */
    fun cancelBackground(taskId: String): Boolean {
        val agent = backgroundJobs[taskId] ?: return false
        if (agent.status != AgentTaskStatus.RUNNING) return false

        agent.job?.cancel()
        backgroundJobs[taskId] = agent.copy(
            status = AgentTaskStatus.CANCELLED,
            output = agent.output + "\n[Cancelled by user]",
            completedAt = Clock.System.now().toEpochMilliseconds()
        )
        return true
    }

    /**
     * Resume agent from previous execution.
     */
    suspend fun resume(taskId: String, additionalPrompt: String): AgentResult {
        // Check if there's a completed background agent to resume from
        val bgAgent = backgroundJobs[taskId]
        if (bgAgent != null && bgAgent.status == AgentTaskStatus.COMPLETED) {
            // Re-run with the previous output as context
            val combinedPrompt = "Previous result:\n${bgAgent.output}\n\nAdditional request:\n$additionalPrompt"
            return execute(
                AgentTask(
                    id = taskId,
                    agentType = "general-purpose",
                    prompt = combinedPrompt,
                    description = "Resume from previous"
                )
            )
        }

        return AgentResult(
            agentId = taskId,
            output = "No previous execution found for task $taskId",
            success = false,
            executionTimeMs = 0,
            error = "Cannot resume: task not found"
        )
    }

    /**
     * Build system prompt for a subagent.
     */
    private fun buildSystemPrompt(agentDef: AgentDefinition): String {
        return buildString {
            appendLine("You are ${agentDef.name} — ${agentDef.description}")
            appendLine()
            appendLine("Your capabilities:")
            agentDef.capabilities.forEach { capability ->
                appendLine("- $capability")
            }
            appendLine()
            if (agentDef.allowedTools != null) {
                appendLine("Available tools: ${agentDef.allowedTools.joinToString(", ")}")
                appendLine()
            }
            appendLine("RULES:")
            appendLine("- Use available tools to accomplish the task")
            appendLine("- Be thorough and precise")
            appendLine("- Return a complete, actionable result")
            appendLine("- If a tool call fails, analyze the error and try an alternative approach")
        }
    }

    companion object {
        private const val DEFAULT_MAX_TURNS = AgentConfig.DEFAULT_MAX_TURNS
        private const val MAX_CONCURRENT_BACKGROUND = AgentConfig.MAX_CONCURRENT_BACKGROUND
    }
}

/**
 * Tracks the state of a background agent execution.
 */
data class BackgroundAgent(
    val taskId: String,
    val status: AgentTaskStatus,
    val output: String,
    val job: Job?,
    val startedAt: Long,
    val completedAt: Long? = null
)
