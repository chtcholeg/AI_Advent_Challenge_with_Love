package ru.chtcholeg.shared.domain.service

import kotlin.math.sqrt

/**
 * Service for generating embeddings for text
 */
interface EmbeddingService {
    /**
     * Generate embeddings for a list of texts
     * @param texts List of texts to generate embeddings for
     * @return List of embedding vectors (one per input text)
     */
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>>

    /**
     * Generate embedding for a single text
     * @param text Text to generate embedding for
     * @return Embedding vector
     */
    suspend fun generateEmbedding(text: String): List<Float> {
        return generateEmbeddings(listOf(text)).first()
    }
}

/**
 * Result of embedding generation with status
 */
sealed class EmbeddingResult {
    data class Success(val embeddings: List<List<Float>>) : EmbeddingResult()
    data class Error(val message: String, val cause: Throwable? = null) : EmbeddingResult()
}

/**
 * L2-normalize a vector (unit length).
 * Normalized vectors allow using dot product instead of cosine similarity.
 */
fun normalizeL2(vector: List<Float>): List<Float> {
    if (vector.isEmpty()) return vector
    val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
    return if (norm > 0f) vector.map { it / norm } else vector
}

/**
 * L2-normalize a batch of vectors
 */
fun normalizeL2Batch(vectors: List<List<Float>>): List<List<Float>> {
    return vectors.map { normalizeL2(it) }
}
