package ru.chtcholeg.indexer.data.local

import ru.chtcholeg.indexer.domain.model.ChunkWithFile
import ru.chtcholeg.indexer.domain.model.IndexedChunk
import ru.chtcholeg.indexer.domain.model.IndexedFile

/**
 * Repository interface for indexed files and chunks
 */
interface IndexerLocalRepository {
    // File operations
    suspend fun getAllFiles(): List<IndexedFile>
    suspend fun getFileByPath(filePath: String): IndexedFile?
    suspend fun getFileById(id: Long): IndexedFile?
    suspend fun getFileByChecksum(checksum: String): IndexedFile?
    suspend fun insertFile(
        fileName: String,
        filePath: String,
        checksum: String,
        fileSize: Long,
        chunkCount: Int,
        indexedAt: Long,
        lastModified: Long
    ): Long
    suspend fun updateFile(
        id: Long,
        fileName: String,
        checksum: String,
        fileSize: Long,
        chunkCount: Int,
        indexedAt: Long,
        lastModified: Long
    )
    suspend fun deleteFile(id: Long)
    suspend fun deleteFileByPath(filePath: String)
    suspend fun getFileCount(): Long

    // Chunk operations
    suspend fun getChunksByFileId(fileId: Long): List<IndexedChunk>
    suspend fun getAllChunks(): List<IndexedChunk>
    suspend fun getAllChunksWithFileInfo(): List<ChunkWithFile>
    suspend fun insertChunk(
        fileId: Long,
        chunkIndex: Int,
        totalChunks: Int,
        text: String,
        embedding: List<Float>,
        createdAt: Long
    )
    suspend fun deleteChunksByFileId(fileId: Long)
    suspend fun getChunkCount(): Long
}
