package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Glob tool - fast file pattern matching.
 */
class GlobTool(private val fileSystem: FileSystem) : LocalTool {

    override val name = "glob"

    override val description = """
        Fast file pattern matching tool that works with any codebase size.
        - Supports glob patterns like "**/*.kt", "src/**/*.ts"
        - Returns matching file paths sorted by modification time (most recent first)
        - Use this when you need to find files by name patterns
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("pattern", "Glob pattern to match files against (e.g., '**/*.kt', 'src/**/*.java')", required = true)
        string("path", "Directory to search in. If not specified, current working directory is used.")
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val pattern = args["pattern"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: pattern", isError = true)

            val path = args["path"]?.jsonPrimitive?.contentOrNull

            val files = fileSystem.glob(pattern, path)

            if (files.isEmpty()) {
                McpToolResult("No files found matching pattern: $pattern")
            } else {
                McpToolResult(files.joinToString("\n"))
            }
        } catch (e: Exception) {
            McpToolResult("Error executing glob: ${e.message}", isError = true)
        }
    }
}
