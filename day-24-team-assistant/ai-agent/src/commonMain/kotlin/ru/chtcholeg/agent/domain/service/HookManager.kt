package ru.chtcholeg.agent.domain.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.chtcholeg.agent.util.FileSystem

/**
 * Hook system for extending agent behavior with user-defined shell commands.
 *
 * Hooks are defined in `.agent-hooks.json` in the project root.
 * Each hook runs a shell command and can:
 * - ALLOW: let the tool call proceed
 * - DENY: block the tool call with a reason
 *
 * Example `.agent-hooks.json`:
 * ```json
 * {
 *   "hooks": [
 *     {
 *       "type": "pre_tool",
 *       "tool": "bash",
 *       "command": "echo 'Bash command intercepted'",
 *       "description": "Log bash commands"
 *     },
 *     {
 *       "type": "pre_tool",
 *       "tool": "write",
 *       "command": "echo 'File write intercepted'",
 *       "description": "Log file writes"
 *     },
 *     {
 *       "type": "post_tool",
 *       "tool": "*",
 *       "command": "echo 'Tool completed'",
 *       "description": "Log all tool completions"
 *     }
 *   ]
 * }
 * ```
 */
class HookManager(
    private val fileSystem: FileSystem
) {
    private var hooks: List<HookDefinition> = emptyList()
    private var loaded = false

    /**
     * Load hooks from .agent-hooks.json in the project root.
     */
    suspend fun loadHooks(projectRoot: String) {
        val hookFilePath = "$projectRoot/.agent-hooks.json"
        try {
            val content = fileSystem.readFile(hookFilePath)
            if (content.isNotBlank()) {
                val config = json.decodeFromString<HookConfig>(content)
                hooks = config.hooks
                loaded = true
                println("[HookManager] Loaded ${hooks.size} hooks from $hookFilePath")
            }
        } catch (e: Exception) {
            // No hooks file or invalid format — hooks are optional
            hooks = emptyList()
            loaded = true
        }
    }

    /**
     * Run pre-tool hooks for the given tool.
     * Returns HookResult.Allow if all hooks pass, or HookResult.Deny if any hook blocks.
     */
    suspend fun runPreToolHooks(toolName: String, argsJson: String): HookResult {
        if (!loaded) return HookResult.Allow

        val matchingHooks = hooks.filter {
            it.type == HookType.PRE_TOOL && (it.tool == toolName || it.tool == "*")
        }

        for (hook in matchingHooks) {
            val result = executeHook(hook, toolName, argsJson)
            if (result is HookResult.Deny) {
                return result
            }
        }

        return HookResult.Allow
    }

    /**
     * Run post-tool hooks for the given tool.
     * Post-tool hooks are informational — they can't block execution.
     */
    suspend fun runPostToolHooks(toolName: String, argsJson: String, resultContent: String) {
        if (!loaded) return

        val matchingHooks = hooks.filter {
            it.type == HookType.POST_TOOL && (it.tool == toolName || it.tool == "*")
        }

        for (hook in matchingHooks) {
            try {
                executeHook(hook, toolName, argsJson, resultContent)
            } catch (e: Exception) {
                println("[HookManager] Post-hook '${hook.description}' failed: ${e.message}")
            }
        }
    }

    private suspend fun executeHook(
        hook: HookDefinition,
        toolName: String,
        argsJson: String,
        resultContent: String? = null
    ): HookResult {
        return try {
            // Set environment variables for the hook command
            val envPrefix = buildString {
                append("AGENT_TOOL_NAME='$toolName' ")
                append("AGENT_TOOL_ARGS='${argsJson.take(1000).replace("'", "\\'")}' ")
                if (resultContent != null) {
                    append("AGENT_TOOL_RESULT='${resultContent.take(1000).replace("'", "\\'")}' ")
                }
            }

            val fullCommand = "$envPrefix${hook.command}"
            val output = fileSystem.bash(fullCommand, HOOK_TIMEOUT_MS)

            // Check output for "DENY:" prefix to block execution
            val outputTrimmed = output.trim()
            if (outputTrimmed.startsWith("DENY:")) {
                HookResult.Deny(
                    reason = outputTrimmed.removePrefix("DENY:").trim(),
                    hookDescription = hook.description
                )
            } else {
                HookResult.Allow
            }
        } catch (e: Exception) {
            println("[HookManager] Hook '${hook.description}' error: ${e.message}")
            // Hook errors don't block — fail open
            HookResult.Allow
        }
    }

    companion object {
        private const val HOOK_TIMEOUT_MS = 10000L
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Result of a hook execution.
 */
sealed class HookResult {
    data object Allow : HookResult()
    data class Deny(val reason: String, val hookDescription: String) : HookResult()
}

/**
 * Hook configuration file structure.
 */
@Serializable
data class HookConfig(
    val hooks: List<HookDefinition> = emptyList()
)

/**
 * A single hook definition.
 */
@Serializable
data class HookDefinition(
    val type: HookType,
    val tool: String,  // tool name or "*" for all tools
    val command: String,  // shell command to execute
    val description: String = ""
)

/**
 * Types of hooks.
 */
@Serializable
enum class HookType {
    PRE_TOOL,   // Before tool execution
    POST_TOOL   // After tool execution
}
