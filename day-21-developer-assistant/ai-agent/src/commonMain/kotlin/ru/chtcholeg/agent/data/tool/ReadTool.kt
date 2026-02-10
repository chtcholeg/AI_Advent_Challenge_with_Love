package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Read tool - reads file content with optional offset and limit.
 */
class ReadTool(private val fileSystem: FileSystem) : LocalTool {

    override val name = "read"

    override val description = """
        Reads a file from the local filesystem.
        - Supports reading with optional line offset and limit for large files
        - Returns content with line numbers (cat -n format)
        - Can read text files, images (base64), PDFs, etc.
        - Lines longer than 2000 characters are truncated
    """.trimIndent()

    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("file_path") {
                put("type", "string")
                put("description", "Absolute path to the file to read")
            }
            putJsonObject("offset") {
                put("type", "integer")
                put("description", "Line number to start reading from (0-based). Only use for large files.")
                put("default", 0)
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Maximum number of lines to read. Only use for large files.")
                put("default", 2000)
            }
        }
        putJsonArray("required") {
            add("file_path")
        }
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val filePath = args["file_path"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: file_path", isError = true)

            val offset = args["offset"]?.jsonPrimitive?.intOrNull ?: 0
            val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE

            val content = fileSystem.readFile(filePath, offset, limit)

            McpToolResult(content = content)
        } catch (e: Exception) {
            McpToolResult("Error reading file: ${e.message}", isError = true)
        }
    }
}
