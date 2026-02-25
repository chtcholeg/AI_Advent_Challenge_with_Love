package ru.chtcholeg.godagent.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

class FileReadTool : AgentTool {
    override val name = "file_read"
    override val description = "Read contents of a file"
    override val parametersDescription = """{"path": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: path required"
        try {
            val file = File(path)
            if (!file.exists()) return@withContext "Error: file not found: $path"
            if (file.length() > 100_000) return@withContext "Error: file too large (>100KB)"
            file.readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }
}

class FileWriteTool : AgentTool {
    override val name = "file_write"
    override val description = "Write content to a file"
    override val parametersDescription = """{"path": string, "content": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: path required"
        val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""
        try {
            File(path).apply { parentFile?.mkdirs() }.writeText(content)
            "Written ${content.length} chars to $path"
        } catch (e: Exception) {
            "Error writing file: ${e.message}"
        }
    }
}

class FileListTool : AgentTool {
    override val name = "file_list"
    override val description = "List files in a directory"
    override val parametersDescription = """{"path": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val path = args["path"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: path required"
        try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) return@withContext "Error: not a directory: $path"
            dir.listFiles()?.joinToString("\n") { f ->
                val type = if (f.isDirectory) "DIR " else "FILE"
                "$type  ${f.name}"
            } ?: "(empty)"
        } catch (e: Exception) {
            "Error listing directory: ${e.message}"
        }
    }
}
