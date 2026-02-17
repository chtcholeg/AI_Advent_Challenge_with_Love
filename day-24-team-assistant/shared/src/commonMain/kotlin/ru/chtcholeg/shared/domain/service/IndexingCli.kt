package ru.chtcholeg.shared.domain.service

/**
 * CLI interface for document indexing operations
 */
interface IndexingCli {
    /**
     * Index documents from a directory
     */
    suspend fun indexDocuments(
        directoryPath: String,
        outputIndexPath: String,
        extensions: List<String> = listOf("md", "txt")
    )

    /**
     * Search indexed documents
     */
    suspend fun searchDocuments(
        indexPath: String,
        query: String,
        topK: Int = 5
    )

    /**
     * Show index statistics
     */
    suspend fun showStats(indexPath: String)

    /**
     * Index a web page from URL
     */
    suspend fun indexUrl(
        url: String,
        outputIndexPath: String
    )

    /**
     * Index multiple web pages from URLs
     */
    suspend fun indexUrls(
        urls: List<String>,
        outputIndexPath: String
    )
}
