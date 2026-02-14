package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Grep tool - powerful search built on ripgrep.
 */
class GrepTool(private val fileSystem: FileSystem) : LocalTool {

    override val name = "grep"

    override val description = """
        Powerful search tool built on ripgrep (rg).
        - ALWAYS use Grep for search tasks, NEVER invoke 'rg' as a bash command
        - Supports full regex syntax (e.g., "log.*Error", "function\s+\w+")
        - Filter files with glob (e.g., "*.kt") or type (e.g., "kt", "py", "java")
        - Output modes: "content" (matching lines), "files_with_matches" (file paths), "count" (match counts)
        - Use for searching code, finding patterns, locating definitions
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("pattern", "Regular expression pattern to search for in file contents", required = true)
        string("path", "File or directory to search in (defaults to current working directory)")
        string("glob", "Glob pattern to filter files (e.g., '*.kt', '*.{ts,tsx}')")
        string("type", "File type to search (e.g., 'kt', 'py', 'rust', 'go', 'java')")
        boolean("case_insensitive", "Case insensitive search", default = false)
        string("output_mode", "Output mode: 'content' (matching lines), 'files_with_matches' (file paths), 'count' (match counts)", default = "files_with_matches", enum = listOf("content", "files_with_matches", "count"))
        integer("context_before", "Lines of context before match (requires output_mode: 'content')", default = 0)
        integer("context_after", "Lines of context after match (requires output_mode: 'content')", default = 0)
        boolean("show_line_numbers", "Show line numbers in content mode", default = true)
        integer("head_limit", "Limit output to first N entries", default = 0)
        integer("offset", "Skip first N entries", default = 0)
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val pattern = args["pattern"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: pattern", isError = true)

            val path = args["path"]?.jsonPrimitive?.contentOrNull
            val glob = args["glob"]?.jsonPrimitive?.contentOrNull
            val type = args["type"]?.jsonPrimitive?.contentOrNull
            val caseInsensitive = args["case_insensitive"]?.jsonPrimitive?.booleanOrNull ?: false
            val outputMode = args["output_mode"]?.jsonPrimitive?.contentOrNull ?: "files_with_matches"
            val contextBefore = args["context_before"]?.jsonPrimitive?.intOrNull ?: 0
            val contextAfter = args["context_after"]?.jsonPrimitive?.intOrNull ?: 0
            val showLineNumbers = args["show_line_numbers"]?.jsonPrimitive?.booleanOrNull ?: true
            val headLimit = args["head_limit"]?.jsonPrimitive?.intOrNull ?: 0
            val offset = args["offset"]?.jsonPrimitive?.intOrNull ?: 0

            val result = fileSystem.grep(
                pattern = pattern,
                path = path,
                glob = glob,
                type = type,
                caseInsensitive = caseInsensitive,
                outputMode = outputMode,
                contextBefore = contextBefore,
                contextAfter = contextAfter,
                showLineNumbers = showLineNumbers,
                headLimit = headLimit,
                offset = offset
            )

            McpToolResult(content = result)
        } catch (e: Exception) {
            McpToolResult("Error executing grep: ${e.message}", isError = true)
        }
    }
}
