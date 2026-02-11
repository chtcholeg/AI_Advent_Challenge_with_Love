package ru.chtcholeg.agent.domain.service

/**
 * Provides access to project root files like README.md
 * Platform-specific implementation (expect/actual pattern).
 */
interface ProjectRootProvider {
    /**
     * Read README.md file from project root.
     * Returns the content of README.md or throws exception if not found.
     */
    suspend fun readReadmeFile(): String

    /**
     * Read CLAUDE.md file from project root.
     * Returns the content or null if the file does not exist.
     */
    suspend fun readClaudeMdFile(): String?

    /**
     * Read any project file by relative path from the project root.
     * Returns the content or null if the file does not exist or is inaccessible.
     * Implementations must protect against path traversal attacks.
     */
    suspend fun readProjectFile(relativePath: String): String?
}

/**
 * Create a platform-specific ProjectRootProvider instance.
 */
expect fun createProjectRootProvider(): ProjectRootProvider
