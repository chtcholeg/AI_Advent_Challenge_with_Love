package ru.chtcholeg.indexer.domain.model

/**
 * Search result containing chunk with similarity score
 */
data class SearchResult(
    val chunk: IndexedChunk,
    val file: IndexedFile,
    val similarity: Float
) {
    /**
     * Similarity percentage (0-100)
     */
    val similarityPercent: Int
        get() = (similarity * 100).toInt().coerceIn(0, 100)
}
