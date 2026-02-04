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
    val sourceFile: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val timestamp: Long = System.currentTimeMillis()
)
