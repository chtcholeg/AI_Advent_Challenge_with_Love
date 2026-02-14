package ru.chtcholeg.agent.data.repository

import ru.chtcholeg.agent.domain.model.SourceReference
import ru.chtcholeg.shared.domain.model.SourceType
import ru.chtcholeg.shared.domain.service.EmbeddingService
import ru.chtcholeg.shared.domain.service.IndexStats
import ru.chtcholeg.shared.domain.service.SearchResult
import ru.chtcholeg.shared.domain.service.VectorStore

/**
 * Result of the reranking stage, carrying both the original and filtered results
 * so callers can compare quality.
 */
data class RerankerResult(
    val initialResults: List<SearchResult>,
    val rerankedResults: List<SearchResult>,
    val removedByThreshold: Int,
    val removedByScoreGap: Int
)

/**
 * Orchestrates the RAG pipeline: index loading → query embedding → vector search → reranking → context formatting.
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
     * Two-stage retrieval: first fetch a broad set of candidates, then apply
     * stricter reranking filters (threshold + score-gap detection).
     *
     * @param query           user query
     * @param initialTopK     how many candidates to retrieve in the first stage
     * @param finalTopK       max results to keep after reranking
     * @param initialThreshold minimum similarity for the first stage (broad)
     * @param rerankerThreshold stricter similarity cutoff for the second stage
     * @param scoreGapThreshold if the similarity drops by more than this between
     *                          consecutive results, everything below is discarded
     */
    suspend fun getRelevantChunksWithReranking(
        query: String,
        initialTopK: Int = 10,
        finalTopK: Int = 3,
        initialThreshold: Float = 0.3f,
        rerankerThreshold: Float = 0.5f,
        scoreGapThreshold: Float = 0.15f
    ): RerankerResult {
        if (!indexLoaded) throw IllegalStateException("Index not loaded. Call loadIndex() first.")

        val embedding = embeddingService.generateEmbedding(query)

        // Stage 1: broad vector search
        val initialResults = vectorStore.search(embedding, initialTopK, initialThreshold)

        if (initialResults.isEmpty()) {
            return RerankerResult(initialResults, emptyList(), 0, 0)
        }

        // Stage 2a: apply stricter similarity threshold
        val afterThreshold = initialResults.filter { it.similarity >= rerankerThreshold }
        val removedByThreshold = initialResults.size - afterThreshold.size

        // Stage 2b: score-gap detection — cut off where similarity drops sharply
        val afterGap = applyScoreGapFilter(afterThreshold, scoreGapThreshold)
        val removedByGap = afterThreshold.size - afterGap.size

        // Stage 2c: keep at most finalTopK
        val finalResults = afterGap.take(finalTopK)

        return RerankerResult(
            initialResults = initialResults,
            rerankedResults = finalResults,
            removedByThreshold = removedByThreshold,
            removedByScoreGap = removedByGap
        )
    }

    /**
     * Detect large drops in similarity between consecutive results.
     * Results are assumed to be sorted descending by similarity.
     * If result[i].sim - result[i+1].sim > gap, cut at i (keep 0..i).
     */
    private fun applyScoreGapFilter(
        results: List<SearchResult>,
        gapThreshold: Float
    ): List<SearchResult> {
        if (results.size <= 1) return results

        for (i in 0 until results.size - 1) {
            val gap = results[i].similarity - results[i + 1].similarity
            if (gap > gapThreshold) {
                return results.subList(0, i + 1)
            }
        }
        return results
    }

    /**
     * Build a map from source number (1-based) to [SourceReference].
     * Numbering mirrors [formatContext] so [Источник N] in the AI response maps to key N.
     */
    fun buildSourceReferences(chunks: List<SearchResult>): Map<Int, SourceReference> {
        return chunks.mapIndexed { index, result ->
            (index + 1) to SourceReference(
                filePath = result.chunk.metadata.source,
                chunkIndex = result.chunk.metadata.chunkIndex,
                totalChunks = result.chunk.metadata.totalChunks,
                similarity = result.similarity,
                isUrl = result.chunk.metadata.sourceType == SourceType.URL,
                text = result.chunk.text  // Store the actual chunk text as citation
            )
        }.toMap()
    }

    /**
     * Format chunks into a numbered context block for injection into the system prompt.
     * Each source gets an [N] label for easy citation by the model.
     */
    fun formatContext(chunks: List<SearchResult>): String {
        if (chunks.isEmpty()) return ""
        return chunks.mapIndexed { index, result ->
            val sourceLabel = extractSourceName(result.chunk.metadata.source)
            "[Источник ${index + 1}] $sourceLabel (фрагмент ${result.chunk.metadata.chunkIndex}/${result.chunk.metadata.totalChunks}, релевантность: ${"%.0f".format(result.similarity * 100)}%)\n${result.chunk.text}"
        }.joinToString("\n\n---\n\n")
    }

    /**
     * Extract a human-readable source name from a file path.
     */
    private fun extractSourceName(source: String): String {
        return source.substringAfterLast("/").substringAfterLast("\\")
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

    /**
     * Format a comparison report showing what the reranker did.
     */
    fun formatRerankerReport(result: RerankerResult): String {
        val sb = StringBuilder()

        sb.appendLine("── Stage 1: Vector Search ──")
        sb.appendLine("Retrieved ${result.initialResults.size} candidate(s):")
        result.initialResults.forEach { r ->
            sb.appendLine("  · ${r.chunk.metadata.source} [chunk ${r.chunk.metadata.chunkIndex}] sim=${"%.2f".format(r.similarity)}")
        }

        sb.appendLine()
        sb.appendLine("── Stage 2: Reranking ──")
        sb.appendLine("Removed by threshold: ${result.removedByThreshold}")
        sb.appendLine("Removed by score gap: ${result.removedByScoreGap}")
        sb.appendLine("Kept ${result.rerankedResults.size} result(s):")
        result.rerankedResults.forEach { r ->
            sb.appendLine("  · ${r.chunk.metadata.source} [chunk ${r.chunk.metadata.chunkIndex}] sim=${"%.2f".format(r.similarity)}")
        }

        return sb.toString().trimEnd()
    }
}
