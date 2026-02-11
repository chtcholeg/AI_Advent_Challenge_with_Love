package ru.chtcholeg.agent.domain.service

import ru.chtcholeg.agent.data.repository.McpRepository
import ru.chtcholeg.agent.data.repository.PlanModeRepository
import ru.chtcholeg.shared.data.model.FunctionCall
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Executes tool calls with permission checking, hook integration, and retry logic.
 * Extracted from AgentRepository to improve testability and separation of concerns.
 */
class ToolExecutor(
    private val mcpRepository: McpRepository,
    private val planModeRepository: PlanModeRepository,
    private val permissionManager: PermissionManager,
    private val hookManager: HookManager?,
) {
    suspend fun executeTool(functionCall: FunctionCall): McpToolResult {
        // Check permissions before executing
        val inPlanMode = planModeRepository.planModeState.value.isActive
        val permission = permissionManager.checkPermission(
            toolName = functionCall.name,
            arguments = functionCall.arguments,
            inPlanMode = inPlanMode
        )

        when (permission) {
            is PermissionResult.Denied -> {
                return McpToolResult(
                    content = "Permission denied: ${permission.reason}",
                    isError = true
                )
            }
            is PermissionResult.AllowedWithWarning -> {
                println("[PermissionManager] Warning: ${permission.warning}")
            }
            is PermissionResult.Allowed -> { /* proceed */ }
        }

        // Run pre-tool hooks
        val argsJson = functionCall.arguments.toString()
        if (hookManager != null) {
            val hookResult = hookManager.runPreToolHooks(functionCall.name, argsJson)
            if (hookResult is HookResult.Deny) {
                return McpToolResult(
                    content = "Blocked by hook '${hookResult.hookDescription}': ${hookResult.reason}",
                    isError = true
                )
            }
        }

        val result = try {
            RetryPolicy.withToolRetry(functionCall.name) {
                val result = mcpRepository.executeTool(
                    toolName = functionCall.name,
                    arguments = functionCall.arguments
                )
                result.getOrThrow()
            }
        } catch (e: Exception) {
            McpToolResult(
                content = "Error executing tool '${functionCall.name}': ${e.message}",
                isError = true
            )
        }

        // Run post-tool hooks (non-blocking)
        hookManager?.runPostToolHooks(functionCall.name, argsJson, result.content)

        return result
    }
}
