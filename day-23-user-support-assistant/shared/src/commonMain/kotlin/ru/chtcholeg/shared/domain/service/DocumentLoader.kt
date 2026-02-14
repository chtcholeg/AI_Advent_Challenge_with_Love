package ru.chtcholeg.shared.domain.service

/**
 * Service for loading documents from various sources
 */
interface DocumentLoader {
    /**
     * Load a document from a file path
     * @param filePath Path to the document file
     * @return DocumentContent containing file content and metadata
     */
    suspend fun loadDocument(filePath: String): DocumentContent

    /**
     * Load multiple documents from a directory
     * @param directoryPath Path to the directory
     * @param extensions File extensions to include (e.g., listOf("md", "txt"))
     * @return List of DocumentContent objects
     */
    suspend fun loadDocumentsFromDirectory(
        directoryPath: String,
        extensions: List<String> = listOf("md", "txt")
    ): List<DocumentContent>
}

/**
 * Represents loaded document content with metadata
 */
data class DocumentContent(
    val filePath: String,
    val content: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long
)
