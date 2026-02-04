package ru.chtcholeg.shared.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class FileSystem {
    actual suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText(Charsets.UTF_8)
    }

    actual suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).exists()
    }

    actual suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        File(path).length()
    }

    actual suspend fun getLastModified(path: String): Long = withContext(Dispatchers.IO) {
        File(path).lastModified()
    }

    actual suspend fun listFiles(
        directoryPath: String,
        extensions: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        val directory = File(directoryPath)
        if (!directory.exists()) {
            return@withContext emptyList()
        }

        // Handle both single file and directory
        if (directory.isFile) {
            val hasMatchingExtension = extensions.any { ext ->
                directory.name.endsWith(".$ext", ignoreCase = true)
            }
            return@withContext if (hasMatchingExtension) listOf(directory.absolutePath) else emptyList()
        }

        // Recursively walk directory tree
        directory.walkTopDown()
            .filter { file ->
                file.isFile && extensions.any { ext ->
                    file.name.endsWith(".$ext", ignoreCase = true)
                }
            }
            .map { it.absolutePath }
            .toList()
    }

    actual suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        File(path).writeText(content, Charsets.UTF_8)
    }
}
