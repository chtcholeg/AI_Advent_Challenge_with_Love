package ru.chtcholeg.shared.domain.service

import ru.chtcholeg.shared.domain.model.DocumentChunk
import ru.chtcholeg.shared.domain.model.DocumentIndex

/**
 * Service for storing and searching document embeddings
 */
interface VectorStore {
    /**
     * Add chunks to the store
     */
    suspend fun addChunks(chunks: List<DocumentChunk>)

    /**
     * Search for similar chunks using cosine similarity
     * @param queryEmbedding The query embedding vector
     * @param topK Number of results to return
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return List of chunks with similarity scores
     */
    suspend fun search(
        queryEmbedding: List<Float>,
        topK: Int = 5,
        threshold: Float = 0.0f
    ): List<SearchResult>

    /**
     * Get all chunks from the store
     */
    suspend fun getAllChunks(): List<DocumentChunk>

    /**
     * Clear all chunks from the store
     */
    suspend fun clear()

    /**
     * Save the index to file
     */
    suspend fun save(filePath: String)

    /**
     * Load the index from file
     */
    suspend fun load(filePath: String)

    /**
     * Get index statistics
     */
    suspend fun getStats(): IndexStats
}

/**
 * Search result with similarity score
 */
data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Float
)

/**
 * Statistics about the index
 */
data class IndexStats(
    val totalChunks: Int,
    val totalDocuments: Int,
    val indexSizeBytes: Long,
    val lastUpdated: Long
)
