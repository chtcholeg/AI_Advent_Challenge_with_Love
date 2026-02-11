package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Edit tool - performs exact string replacements in files.
 */
class EditTool(private val fileSystem: FileSystem) : LocalTool {

    override val name = "edit"

    override val description = """
        Performs exact string replacements in files.
        - You MUST use 'read' tool before editing to see the current file content
        - Preserves exact indentation from the file (ignore line number prefix in read output)
        - The edit will FAIL if old_string is not unique (unless replace_all=true)
        - Use replace_all for renaming variables/strings across the entire file
        - Never include emojis unless explicitly requested
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("file_path", "Absolute path to the file to modify", required = true)
        string("old_string", "Exact text to replace (must match exactly as it appears in the file)", required = true)
        string("new_string", "Text to replace it with (must be different from old_string)", required = true)
        boolean("replace_all", "Replace all occurrences (default: false, only replaces if unique)", default = false)
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val filePath = args["file_path"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: file_path", isError = true)

            val oldString = args["old_string"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: old_string", isError = true)

            val newString = args["new_string"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: new_string", isError = true)

            val replaceAll = args["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false

            if (oldString == newString) {
                return McpToolResult("old_string and new_string are identical", isError = true)
            }

            val success = fileSystem.editFile(filePath, oldString, newString, replaceAll)

            if (success) {
                McpToolResult("File edited successfully: $filePath")
            } else {
                McpToolResult("Edit failed", isError = true)
            }
        } catch (e: Exception) {
            McpToolResult("Error editing file: ${e.message}", isError = true)
        }
    }
}
