package ru.chtcholeg.agent.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.streams.toList

actual class FileSystem {

    actual fun getCurrentDirectory(): String {
        return System.getProperty("user.dir")
    }

    actual suspend fun readFile(path: String, offset: Int, limit: Int): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $path")
        }
        if (file.isDirectory) {
            throw IllegalArgumentException("Path is a directory, not a file: $path")
        }

        val lines = file.readLines()
        val startLine = offset.coerceIn(0, lines.size)
        val endLine = (startLine + limit).coerceIn(0, lines.size)

        val selectedLines = lines.subList(startLine, endLine)

        // Format with line numbers (cat -n format)
        selectedLines.mapIndexed { index, line ->
            val lineNumber = startLine + index + 1  // 1-based line numbers
            val truncated = if (line.length > 2000) line.take(2000) + "..." else line
            "$lineNumber→$truncated"
        }.joinToString("\n")
    }

    actual suspend fun writeFile(
        path: String,
        content: String,
        createDirectories: Boolean
    ) = withContext(Dispatchers.IO) {
        val file = File(path)

        if (createDirectories) {
            file.parentFile?.mkdirs()
        }

        file.writeText(content)
    }

    actual suspend fun editFile(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $path")
        }

        val content = file.readText()

        if (!replaceAll) {
            // Check if oldString is unique
            val occurrences = content.split(oldString).size - 1
            if (occurrences == 0) {
                throw IllegalArgumentException("String not found in file")
            }
            if (occurrences > 1) {
                throw IllegalArgumentException(
                    "String appears $occurrences times in file. Use replaceAll=true to replace all occurrences."
                )
            }
        }

        val newContent = if (replaceAll) {
            content.replace(oldString, newString)
        } else {
            content.replaceFirst(oldString, newString)
        }

        file.writeText(newContent)
        true
    }

    actual suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).exists()
    }

    actual suspend fun isDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        file.exists() && file.isDirectory
    }

    actual suspend fun glob(pattern: String, path: String?): List<String> = withContext(Dispatchers.IO) {
        val basePath = Paths.get(path ?: getCurrentDirectory())
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

        val results = mutableListOf<Path>()

        Files.walk(basePath)
            .filter { p ->
                val relativePath = basePath.relativize(p)
                matcher.matches(relativePath)
            }
            .forEach { results.add(it) }

        // Sort by modification time (most recent first)
        results.sortedByDescending {
            try {
                Files.getLastModifiedTime(it).toMillis()
            } catch (e: Exception) {
                0L
            }
        }.map { it.absolutePathString() }
    }

    actual suspend fun grep(
        pattern: String,
        path: String?,
        glob: String?,
        type: String?,
        caseInsensitive: Boolean,
        outputMode: String,
        contextBefore: Int,
        contextAfter: Int,
        showLineNumbers: Boolean,
        headLimit: Int,
        offset: Int
    ): String = withContext(Dispatchers.IO) {
        val searchPath = path ?: getCurrentDirectory()

        // Build ripgrep command
        val cmd = buildList {
            add("rg")
            if (caseInsensitive) add("-i")

            when (outputMode) {
                "content" -> {
                    if (showLineNumbers) add("-n")
                    if (contextBefore > 0) {
                        add("-B")
                        add(contextBefore.toString())
                    }
                    if (contextAfter > 0) {
                        add("-A")
                        add(contextAfter.toString())
                    }
                }
                "files_with_matches" -> add("-l")
                "count" -> add("-c")
            }

            if (glob != null) {
                add("--glob")
                add(glob)
            }

            if (type != null) {
                add("--type")
                add(type)
            }

            add(pattern)
            add(searchPath)
        }

        try {
            val result = executeCommand(cmd, timeout = 30000)

            // Apply offset and head limit if needed
            if (offset > 0 || headLimit > 0) {
                val lines = result.split("\n")
                val startIdx = offset.coerceIn(0, lines.size)
                val endIdx = if (headLimit > 0) {
                    (startIdx + headLimit).coerceIn(0, lines.size)
                } else {
                    lines.size
                }
                lines.subList(startIdx, endIdx).joinToString("\n")
            } else {
                result
            }
        } catch (e: Exception) {
            "Error executing grep: ${e.message}"
        }
    }

    actual suspend fun bash(command: String, timeout: Long): String = withContext(Dispatchers.IO) {
        executeCommand(listOf("bash", "-c", command), timeout)
    }

    private fun executeCommand(command: List<String>, timeout: Long): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(timeout, TimeUnit.MILLISECONDS)

        if (!completed) {
            process.destroyForcibly()
            throw IllegalStateException("Command timed out after ${timeout}ms")
        }

        val exitCode = process.exitValue()
        val output = process.inputStream.bufferedReader().readText()

        if (exitCode != 0) {
            throw IllegalStateException("Command failed with exit code $exitCode:\n$output")
        }

        return output
    }
}
