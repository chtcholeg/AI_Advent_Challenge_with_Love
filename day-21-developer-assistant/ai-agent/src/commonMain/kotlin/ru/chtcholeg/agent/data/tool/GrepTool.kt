package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
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

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") {
                put("type", "string")
                put("description", "Regular expression pattern to search for in file contents")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "File or directory to search in (defaults to current working directory)")
            }
            putJsonObject("glob") {
                put("type", "string")
                put("description", "Glob pattern to filter files (e.g., '*.kt', '*.{ts,tsx}')")
            }
            putJsonObject("type") {
                put("type", "string")
                put("description", "File type to search (e.g., 'kt', 'py', 'rust', 'go', 'java')")
            }
            putJsonObject("case_insensitive") {
                put("type", "boolean")
                put("description", "Case insensitive search")
                put("default", false)
            }
            putJsonObject("output_mode") {
                put("type", "string")
                put("description", "Output mode: 'content' (matching lines), 'files_with_matches' (file paths), 'count' (match counts)")
                put("default", "files_with_matches")
                putJsonArray("enum") {
                    add("content")
                    add("files_with_matches")
                    add("count")
                }
            }
            putJsonObject("context_before") {
                put("type", "integer")
                put("description", "Lines of context before match (requires output_mode: 'content')")
                put("default", 0)
            }
            putJsonObject("context_after") {
                put("type", "integer")
                put("description", "Lines of context after match (requires output_mode: 'content')")
                put("default", 0)
            }
            putJsonObject("show_line_numbers") {
                put("type", "boolean")
                put("description", "Show line numbers in content mode")
                put("default", true)
            }
            putJsonObject("head_limit") {
                put("type", "integer")
                put("description", "Limit output to first N entries")
                put("default", 0)
            }
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "Skip first N entries")
                put("default", 0)
            }
        }
        putJsonArray("required") {
            add("pattern")
        }
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
