package ru.chtcholeg.agent.util

/**
 * Android implementation of FileSystem.
 * Note: File operations on Android have limited access due to scoped storage.
 * This is a basic implementation that works with app-accessible directories.
 */
actual class FileSystem {

    actual fun getCurrentDirectory(): String {
        // On Android, use app-specific external storage
        return android.os.Environment.getExternalStorageDirectory().absolutePath
    }

    actual suspend fun readFile(path: String, offset: Int, limit: Int): String {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
    }

    actual suspend fun writeFile(path: String, content: String, createDirectories: Boolean) {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
    }

    actual suspend fun editFile(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean
    ): Boolean {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
    }

    actual suspend fun fileExists(path: String): Boolean {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
    }

    actual suspend fun isDirectory(path: String): Boolean {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
    }

    actual suspend fun glob(pattern: String, path: String?): List<String> {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
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
    ): String {
        throw UnsupportedOperationException("File operations not fully implemented on Android yet")
    }

    actual suspend fun bash(command: String, timeout: Long): String {
        throw UnsupportedOperationException("Bash commands not available on Android")
    }
}
