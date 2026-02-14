package ru.chtcholeg.agent.domain.service

/**
 * Android implementation of ProjectRootProvider.
 * On Android, we cannot access the project README.md at runtime,
 * so we provide a fallback message.
 */
class AndroidProjectRootProvider : ProjectRootProvider {
    override suspend fun readReadmeFile(): String {
        // On Android, we can't access the file system like on Desktop
        // Return a static help message instead
        return """
            # GigaChat AI Agent

            This is the AI Agent module of the GigaChat Multiplatform Chat Application.

            ## Features
            - Multi-model AI support (GigaChat, HuggingFace)
            - RAG (Retrieval-Augmented Generation) with document indexing
            - MCP Server integration for external tools
            - Git MCP Server for developer assistant capabilities
            - Session management with SQLite storage
            - Command system for quick actions

            ## Available Commands
            /help - Show this help information

            For full documentation, please refer to the README.md file in the project repository.

            Note: Full README.md access is only available in Desktop version.
        """.trimIndent()
    }

    override suspend fun readClaudeMdFile(): String? = null

    override suspend fun readProjectFile(relativePath: String): String? = null
}

/**
 * Create an Android-specific ProjectRootProvider instance.
 */
actual fun createProjectRootProvider(): ProjectRootProvider =
    AndroidProjectRootProvider()
