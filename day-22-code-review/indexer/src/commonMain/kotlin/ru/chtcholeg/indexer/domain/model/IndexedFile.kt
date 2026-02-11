package ru.chtcholeg.indexer.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model for an indexed file
 */
data class IndexedFile(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val checksum: String,
    val fileSize: Long,
    val chunkCount: Int,
    val indexedAt: Instant,
    val lastModified: Instant
)
