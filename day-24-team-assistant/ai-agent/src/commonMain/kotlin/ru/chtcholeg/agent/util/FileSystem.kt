package ru.chtcholeg.agent.util

/**
 * Platform-specific file system operations.
 */
expect class FileSystem() {
    /**
     * Read file content as text.
     */
    suspend fun readFile(path: String, offset: Int = 0, limit: Int = Int.MAX_VALUE): String

    /**
     * Write content to file.
     */
    suspend fun writeFile(path: String, content: String, createDirectories: Boolean = true)

    /**
     * Replace exact string in file.
     */
    suspend fun editFile(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean = false
    ): Boolean

    /**
     * Check if file exists.
     */
    suspend fun fileExists(path: String): Boolean

    /**
     * Check if path is a directory.
     */
    suspend fun isDirectory(path: String): Boolean

    /**
     * Get current working directory.
     */
    fun getCurrentDirectory(): String

    /**
     * Find files matching glob pattern.
     */
    suspend fun glob(pattern: String, path: String? = null): List<String>

    /**
     * Search for text pattern in files using regex.
     */
    suspend fun grep(
        pattern: String,
        path: String? = null,
        glob: String? = null,
        type: String? = null,
        caseInsensitive: Boolean = false,
        outputMode: String = "files_with_matches",
        contextBefore: Int = 0,
        contextAfter: Int = 0,
        showLineNumbers: Boolean = true,
        headLimit: Int = 0,
        offset: Int = 0
    ): String

    /**
     * Execute bash command.
     */
    suspend fun bash(command: String, timeout: Long = 120000): String
}
