package ru.chtcholeg.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a chunk of text with its embedding vector
 */
@Serializable
data class DocumentChunk(
    val id: String,
    val text: String,
    val embedding: List<Float>,
    val metadata: DocumentMetadata
)

/**
 * Metadata for a document chunk
 */
@Serializable
data class DocumentMetadata(
    val source: String, // URL or file path
    val sourceType: SourceType = SourceType.FILE,
    val chunkIndex: Int,
    val totalChunks: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Type of document source
 */
@Serializable
enum class SourceType {
    FILE,
    URL
}
