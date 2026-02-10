package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Bash tool - executes bash commands.
 * IMPORTANT: This tool is for terminal operations like git, npm, docker, etc.
 * DO NOT use it for file operations - use specialized tools instead (read, write, edit, glob, grep).
 */
class BashTool(private val fileSystem: FileSystem) : LocalTool {

    override val name = "bash"

    override val description = """
        Executes a bash command with optional timeout.
        - Working directory persists between commands; shell state does not
        - Maximum timeout: 600000ms (10 minutes)
        - IMPORTANT: Use this for terminal operations (git, npm, docker, etc.)
        - DO NOT use for file operations - use specialized tools instead:
          * Read files → use 'read' tool
          * Write files → use 'write' tool
          * Edit files → use 'edit' tool
          * Find files → use 'glob' tool
          * Search content → use 'grep' tool
        - Always quote file paths with spaces using double quotes
        - Avoid find, cat, head, tail, sed, awk, echo for file operations
    """.trimIndent()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") {
                put("type", "string")
                put("description", "Bash command to execute (properly quote paths with spaces)")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Clear description of what this command does (5-10 words for simple commands)")
            }
            putJsonObject("timeout") {
                put("type", "integer")
                put("description", "Optional timeout in milliseconds (max 600000, default 120000)")
                put("default", 120000)
            }
        }
        putJsonArray("required") {
            add("command")
        }
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val command = args["command"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: command", isError = true)

            val timeout = args["timeout"]?.jsonPrimitive?.longOrNull ?: 120000L

            if (timeout > 600000) {
                return McpToolResult("Timeout exceeds maximum of 600000ms", isError = true)
            }

            val output = fileSystem.bash(command, timeout)

            McpToolResult(content = output)
        } catch (e: Exception) {
            McpToolResult("Command failed: ${e.message}", isError = true)
        }
    }
}
