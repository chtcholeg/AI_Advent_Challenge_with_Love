package ru.chtcholeg.shared.domain.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class FileSystem(
    private val context: Context
) {
    actual suspend fun readFile(path: String): String = withContext(Dispatchers.IO) {
        File(path).readText()
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
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        directory.listFiles()
            ?.filter { file ->
                file.isFile && extensions.any { ext ->
                    file.name.endsWith(".$ext", ignoreCase = true)
                }
            }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    actual suspend fun writeFile(path: String, content: String) = withContext(Dispatchers.IO) {
        File(path).writeText(content)
    }
}
