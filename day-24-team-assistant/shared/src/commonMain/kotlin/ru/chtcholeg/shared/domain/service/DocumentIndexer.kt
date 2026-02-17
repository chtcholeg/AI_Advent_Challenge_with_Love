package ru.chtcholeg.shared.domain.service

import ru.chtcholeg.shared.domain.model.DocumentChunk
import ru.chtcholeg.shared.domain.model.DocumentMetadata

/**
 * Service for orchestrating document indexing pipeline
 */
interface DocumentIndexer {
    /**
     * Index a single document
     * @param filePath Path to the document file
     * @param progressCallback Callback for progress updates (0.0 to 1.0)
     * @return List of indexed chunks
     */
    suspend fun indexDocument(
        filePath: String,
        progressCallback: ((Float, String) -> Unit)? = null
    ): IndexingResult

    /**
     * Index multiple documents from a directory
     * @param directoryPath Path to the directory
     * @param extensions File extensions to include
     * @param progressCallback Callback for progress updates
     * @return Batch indexing result
     */
    suspend fun indexDirectory(
        directoryPath: String,
        extensions: List<String> = listOf("md", "txt"),
        progressCallback: ((Float, String) -> Unit)? = null
    ): BatchIndexingResult

    /**
     * Search indexed documents
     * @param query Query text
     * @param topK Number of results to return
     * @return List of search results
     */
    suspend fun search(query: String, topK: Int = 5): List<SearchResult>

    /**
     * Get indexing statistics
     */
    suspend fun getStats(): IndexStats

    /**
     * Index a web page from URL
     * @param url URL of the web page
     * @param progressCallback Callback for progress updates (0.0 to 1.0)
     * @return Indexing result
     */
    suspend fun indexUrl(
        url: String,
        progressCallback: ((Float, String) -> Unit)? = null
    ): IndexingResult

    /**
     * Index multiple web pages from URLs
     * @param urls List of URLs to index
     * @param progressCallback Callback for progress updates
     * @return Batch indexing result
     */
    suspend fun indexUrls(
        urls: List<String>,
        progressCallback: ((Float, String) -> Unit)? = null
    ): BatchIndexingResult
}

/**
 * Result of indexing a single document
 */
sealed class IndexingResult {
    data class Success(
        val filePath: String,
        val chunksCreated: Int,
        val durationMs: Long
    ) : IndexingResult()

    data class Error(
        val filePath: String,
        val message: String,
        val cause: Throwable? = null
    ) : IndexingResult()
}

/**
 * Result of batch indexing
 */
data class BatchIndexingResult(
    val totalFiles: Int,
    val successfulFiles: Int,
    val failedFiles: Int,
    val totalChunks: Int,
    val durationMs: Long,
    val errors: List<IndexingResult.Error> = emptyList()
)
