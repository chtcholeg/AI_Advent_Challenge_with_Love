package ru.chtcholeg.shared.domain.service

import kotlin.math.sqrt

/**
 * Vector mathematics utilities for similarity calculations
 */
object VectorMath {
    /**
     * Calculate dot product between two vectors.
     * For L2-normalized vectors, dot product equals cosine similarity.
     *
     * @param a First vector
     * @param b Second vector
     * @return Dot product result
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    fun dotProduct(a: List<Float>, b: List<Float>): Float {
        require(a.size == b.size) {
            "Vectors must have the same dimension (${a.size} vs ${b.size})"
        }

        var result = 0.0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }

    /**
     * Calculate cosine similarity between two vectors.
     *
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity in range [-1, 1]
     */
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        val dotProd = dotProduct(a, b)
        val normA = sqrt(a.sumOf { (it * it).toDouble() }.toFloat())
        val normB = sqrt(b.sumOf { (it * it).toDouble() }.toFloat())
        return if (normA > 0 && normB > 0) dotProd / (normA * normB) else 0f
    }
}
