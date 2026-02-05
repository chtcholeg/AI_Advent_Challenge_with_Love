package ru.chtcholeg.agent.data.repository

import ru.chtcholeg.shared.domain.service.EmbeddingService
import ru.chtcholeg.shared.domain.service.IndexStats
import ru.chtcholeg.shared.domain.service.SearchResult
import ru.chtcholeg.shared.domain.service.VectorStore

/**
 * Orchestrates the RAG pipeline: index loading → query embedding → vector search → context formatting.
 */
class RagRepository(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore
) {
    private var indexLoaded = false
    private var currentIndexPath: String? = null

    /**
     * Load (or reload) the vector index from the given JSON file.
     * Skips reload if the same file is already loaded.
     */
    suspend fun loadIndex(path: String) {
        if (path == currentIndexPath && indexLoaded) return
        vectorStore.load(path)
        currentIndexPath = path
        indexLoaded = true
    }

    /**
     * Return stats for the currently loaded index.
     * Caller must ensure loadIndex() was called beforehand.
     */
    suspend fun getStats(): IndexStats {
        if (!indexLoaded) throw IllegalStateException("Index not loaded. Call loadIndex() first.")
        return vectorStore.getStats()
    }

    /**
     * Embed the query and search for the most relevant chunks.
     */
    suspend fun getRelevantChunks(query: String, topK: Int = 5, threshold: Float = 0.3f): List<SearchResult> {
        if (!indexLoaded) throw IllegalStateException("Index not loaded. Call loadIndex() first.")
        val embedding = embeddingService.generateEmbedding(query)
        return vectorStore.search(embedding, topK, threshold)
    }

    /**
     * Format chunks into a context block for injection into the system prompt.
     */
    fun formatContext(chunks: List<SearchResult>): String {
        if (chunks.isEmpty()) return ""
        return chunks.joinToString("\n---\n") { result ->
            "[${result.chunk.metadata.source}, chunk ${result.chunk.metadata.chunkIndex}/${result.chunk.metadata.totalChunks}, sim=${"%.2f".format(result.similarity)}]\n${result.chunk.text}"
        }
    }

    /**
     * Short human-readable summary of retrieved chunks (shown in the [rag] message).
     */
    fun formatChunksSummary(chunks: List<SearchResult>): String {
        if (chunks.isEmpty()) return ""
        return chunks.joinToString("\n") { result ->
            "  · ${result.chunk.metadata.source} [chunk ${result.chunk.metadata.chunkIndex}] sim=${"%.2f".format(result.similarity)}"
        }
    }
}
