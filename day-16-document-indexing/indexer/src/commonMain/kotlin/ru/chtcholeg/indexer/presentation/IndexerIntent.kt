package ru.chtcholeg.indexer.presentation

/**
 * User intents for the indexer screen
 */
sealed interface IndexerIntent {
    /**
     * Select a file or directory path for indexing
     */
    data class SelectPath(val path: String) : IndexerIntent

    /**
     * Start indexing documents at the selected path
     */
    data object StartIndexing : IndexerIntent

    /**
     * Cancel ongoing indexing operation
     */
    data object CancelIndexing : IndexerIntent

    /**
     * Search indexed documents
     */
    data class Search(val query: String) : IndexerIntent

    /**
     * Clear search results
     */
    data object ClearSearch : IndexerIntent

    /**
     * Toggle expansion of a chunk result
     */
    data class ToggleChunkExpansion(val chunkId: Long) : IndexerIntent

    /**
     * Remove an indexed file
     */
    data class RemoveFile(val fileId: Long) : IndexerIntent

    /**
     * Refresh list of indexed files
     */
    data object RefreshFiles : IndexerIntent

    /**
     * Check Ollama availability
     */
    data object CheckOllamaStatus : IndexerIntent

    /**
     * Dismiss error message
     */
    data object DismissError : IndexerIntent
}
