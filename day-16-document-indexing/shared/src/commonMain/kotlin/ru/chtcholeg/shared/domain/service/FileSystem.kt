package ru.chtcholeg.shared.domain.service

/**
 * Cross-platform file system operations
 */
expect class FileSystem {
    /**
     * Read file content as string
     */
    suspend fun readFile(path: String): String

    /**
     * Check if file exists
     */
    suspend fun fileExists(path: String): Boolean

    /**
     * Get file size in bytes
     */
    suspend fun getFileSize(path: String): Long

    /**
     * Get file last modified timestamp
     */
    suspend fun getLastModified(path: String): Long

    /**
     * List files in directory with given extensions
     */
    suspend fun listFiles(
        directoryPath: String,
        extensions: List<String>
    ): List<String>

    /**
     * Write file content
     */
    suspend fun writeFile(path: String, content: String)
}
