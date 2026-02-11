package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Write tool - writes content to a file.
 * IMPORTANT: Always prefer editing existing files. Only write new files when explicitly required.
 */
class WriteTool(private val fileSystem: FileSystem) : LocalTool {

    override val name = "write"

    override val description = """
        Writes a file to the local filesystem.
        - Overwrites existing file if present
        - Creates parent directories automatically
        - IMPORTANT: Always prefer editing existing files using the 'edit' tool
        - Only use this tool when creating genuinely new files
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("file_path", "Absolute path to the file to write (must be absolute, not relative)", required = true)
        string("content", "Content to write to the file", required = true)
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val filePath = args["file_path"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: file_path", isError = true)

            val content = args["content"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: content", isError = true)

            // Check if file already exists - warn user
            if (fileSystem.fileExists(filePath)) {
                fileSystem.writeFile(filePath, content, createDirectories = true)
                McpToolResult("Warning: File already existed and was overwritten: $filePath")
            } else {
                fileSystem.writeFile(filePath, content, createDirectories = true)
                McpToolResult("File created successfully: $filePath")
            }
        } catch (e: Exception) {
            McpToolResult("Error writing file: ${e.message}", isError = true)
        }
    }
}
