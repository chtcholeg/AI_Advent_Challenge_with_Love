package ru.chtcholeg.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents an indexed document with all its chunks and embeddings
 */
@Serializable
data class IndexedDocument(
    val source: String, // URL or file path
    val sourceType: SourceType = SourceType.FILE,
    val chunks: List<DocumentChunk>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Complete document index containing all indexed documents
 */
@Serializable
data class DocumentIndex(
    val documents: List<IndexedDocument>,
    val createdAt: Long = System.currentTimeMillis(),
    val version: Int = 1
)
