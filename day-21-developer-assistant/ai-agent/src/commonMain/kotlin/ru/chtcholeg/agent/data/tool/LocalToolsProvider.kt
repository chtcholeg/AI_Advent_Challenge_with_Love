package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.JsonElement
import ru.chtcholeg.agent.data.repository.AgentExecutor
import ru.chtcholeg.agent.data.repository.PlanModeRepository
import ru.chtcholeg.agent.data.repository.TaskRepository
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpTool
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Provides local tools that execute directly in the agent without external MCP servers.
 * These tools implement core Claude Code functionality: file operations, search, bash execution,
 * task management, planning mode, autonomous agents, and user interaction.
 */
class LocalToolsProvider(
    private val fileSystem: FileSystem,
    private val taskRepository: TaskRepository,
    private val planModeRepository: PlanModeRepository,
    private val agentExecutor: Lazy<AgentExecutor>
) {

    companion object {
        const val LOCAL_SERVER_ID = "local"
        const val LOCAL_SERVER_NAME = "Local Tools"
    }

    private val tools: Map<String, LocalTool> by lazy {
        mapOf(
            // File operations
            "read" to ReadTool(fileSystem),
            "write" to WriteTool(fileSystem),
            "edit" to EditTool(fileSystem),
            "glob" to GlobTool(fileSystem),
            "grep" to GrepTool(fileSystem),
            "bash" to BashTool(fileSystem),

            // Task management
            "task_create" to TaskCreateTool(taskRepository),
            "task_update" to TaskUpdateTool(taskRepository),
            "task_list" to TaskListTool(taskRepository),
            "task_get" to TaskGetTool(taskRepository),

            // Planning mode
            "enter_plan_mode" to EnterPlanModeTool(planModeRepository),
            "exit_plan_mode" to ExitPlanModeTool(planModeRepository),

            // Autonomous agents
            "task" to TaskTool(agentExecutor.value),

            // User interaction
            "ask_user_question" to AskUserQuestionTool()
        )
    }

    /**
     * Get all local tools as MCP tools for AI to use.
     */
    fun getTools(): List<McpTool> {
        return tools.values.map { tool ->
            McpTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema,
                serverId = LOCAL_SERVER_ID
            )
        }
    }

    /**
     * Execute a local tool by name.
     */
    suspend fun executeTool(toolName: String, arguments: JsonElement): McpToolResult {
        val tool = tools[toolName]
            ?: return McpToolResult("Unknown local tool: $toolName", isError = true)

        return tool.execute(arguments)
    }

    /**
     * Check if a tool name is a local tool.
     */
    fun isLocalTool(toolName: String): Boolean {
        return tools.containsKey(toolName)
    }

    /**
     * Get list of all local tool names.
     */
    fun getToolNames(): List<String> {
        return tools.keys.toList()
    }
}
