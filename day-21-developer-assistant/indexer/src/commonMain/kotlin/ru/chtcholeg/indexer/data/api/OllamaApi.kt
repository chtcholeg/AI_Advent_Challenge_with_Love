package ru.chtcholeg.indexer.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API interface for Ollama embeddings
 */
interface OllamaApi {
    /**
     * Generate embeddings for a list of texts
     * @param input List of texts to embed
     * @param model Model to use for embeddings
     * @return Response with embeddings
     */
    suspend fun generateEmbeddings(
        input: List<String>,
        model: String = DEFAULT_MODEL
    ): OllamaEmbeddingResponse

    /**
     * Check if Ollama server is available
     * @return true if available
     */
    suspend fun isAvailable(): Boolean

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
        const val DEFAULT_MODEL = "nomic-embed-text"
    }
}

/**
 * Request body for Ollama embed endpoint
 */
@Serializable
data class OllamaEmbeddingRequest(
    @SerialName("model")
    val model: String,
    @SerialName("input")
    val input: List<String>
)

/**
 * Response from Ollama embed endpoint
 */
@Serializable
data class OllamaEmbeddingResponse(
    @SerialName("model")
    val model: String? = null,
    @SerialName("embeddings")
    val embeddings: List<List<Float>>
)
