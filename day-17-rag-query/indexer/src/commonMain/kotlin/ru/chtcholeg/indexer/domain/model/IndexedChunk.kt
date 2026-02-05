package ru.chtcholeg.indexer.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model for an indexed chunk with embedding
 */
data class IndexedChunk(
    val id: Long,
    val fileId: Long,
    val chunkIndex: Int,
    val totalChunks: Int,
    val text: String,
    val embedding: List<Float>,
    val createdAt: Instant
)

/**
 * Chunk with associated file information for display
 */
data class ChunkWithFile(
    val chunk: IndexedChunk,
    val fileName: String,
    val filePath: String
)
