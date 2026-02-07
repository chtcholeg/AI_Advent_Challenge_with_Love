package ru.chtcholeg.indexer.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.indexer.domain.model.ChunkWithFile
import ru.chtcholeg.indexer.domain.model.IndexedChunk
import ru.chtcholeg.indexer.domain.model.IndexedFile

/**
 * SQLDelight implementation of IndexerLocalRepository
 */
class IndexerLocalRepositoryImpl(
    private val database: IndexerDatabase
) : IndexerLocalRepository {

    private val fileQueries = database.indexedFileQueries
    private val chunkQueries = database.indexedChunkQueries
    private val json = Json { ignoreUnknownKeys = true }

    // File operations

    override suspend fun getAllFiles(): List<IndexedFile> = withContext(Dispatchers.Default) {
        fileQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun getFileByPath(filePath: String): IndexedFile? = withContext(Dispatchers.Default) {
        fileQueries.selectByPath(filePath).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getFileById(id: Long): IndexedFile? = withContext(Dispatchers.Default) {
        fileQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getFileByChecksum(checksum: String): IndexedFile? = withContext(Dispatchers.Default) {
        fileQueries.selectByChecksum(checksum).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insertFile(
        fileName: String,
        filePath: String,
        checksum: String,
        fileSize: Long,
        chunkCount: Int,
        indexedAt: Long,
        lastModified: Long
    ): Long = withContext(Dispatchers.Default) {
        database.transactionWithResult {
            fileQueries.insert(
                fileName = fileName,
                filePath = filePath,
                checksum = checksum,
                fileSize = fileSize,
                chunkCount = chunkCount.toLong(),
                indexedAt = indexedAt,
                lastModified = lastModified
            )
            fileQueries.lastInsertRowId().executeAsOne()
        }
    }

    override suspend fun updateFile(
        id: Long,
        fileName: String,
        checksum: String,
        fileSize: Long,
        chunkCount: Int,
        indexedAt: Long,
        lastModified: Long
    ) = withContext(Dispatchers.Default) {
        fileQueries.update(
            fileName = fileName,
            checksum = checksum,
            fileSize = fileSize,
            chunkCount = chunkCount.toLong(),
            indexedAt = indexedAt,
            lastModified = lastModified,
            id = id
        )
    }

    override suspend fun deleteFile(id: Long) = withContext(Dispatchers.Default) {
        fileQueries.deleteById(id)
    }

    override suspend fun deleteFileByPath(filePath: String) = withContext(Dispatchers.Default) {
        fileQueries.deleteByPath(filePath)
    }

    override suspend fun getFileCount(): Long = withContext(Dispatchers.Default) {
        fileQueries.countFiles().executeAsOne()
    }

    // Chunk operations

    override suspend fun getChunksByFileId(fileId: Long): List<IndexedChunk> = withContext(Dispatchers.Default) {
        chunkQueries.selectByFileId(fileId).executeAsList().map { it.toDomain() }
    }

    override suspend fun getAllChunks(): List<IndexedChunk> = withContext(Dispatchers.Default) {
        chunkQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun getAllChunksWithFileInfo(): List<ChunkWithFile> = withContext(Dispatchers.Default) {
        val chunks = chunkQueries.selectAllWithFileInfo()
        chunks.executeAsList().map { row ->
            ChunkWithFile(
                chunk = IndexedChunk(
                    id = row.chunkId,
                    fileId = row.fileId,
                    chunkIndex = row.chunkIndex.toInt(),
                    totalChunks = row.totalChunks.toInt(),
                    text = row.text,
                    embedding = parseEmbedding(row.embedding),
                    createdAt = Instant.fromEpochMilliseconds(row.createdAt)
                ),
                fileName = row.fileName,
                filePath = row.filePath
            )
        }
    }

    override suspend fun insertChunk(
        fileId: Long,
        chunkIndex: Int,
        totalChunks: Int,
        text: String,
        embedding: List<Float>,
        createdAt: Long
    ) = withContext(Dispatchers.Default) {
        chunkQueries.insert(
            fileId = fileId,
            chunkIndex = chunkIndex.toLong(),
            totalChunks = totalChunks.toLong(),
            text = text,
            embedding = json.encodeToString(embedding),
            createdAt = createdAt
        )
    }

    override suspend fun deleteChunksByFileId(fileId: Long) = withContext(Dispatchers.Default) {
        chunkQueries.deleteByFileId(fileId)
    }

    override suspend fun getChunkCount(): Long = withContext(Dispatchers.Default) {
        chunkQueries.countChunks().executeAsOne()
    }

    // Mapping functions

    private fun IndexedFileEntity.toDomain(): IndexedFile = IndexedFile(
        id = id,
        fileName = fileName,
        filePath = filePath,
        checksum = checksum,
        fileSize = fileSize,
        chunkCount = chunkCount.toInt(),
        indexedAt = Instant.fromEpochMilliseconds(indexedAt),
        lastModified = Instant.fromEpochMilliseconds(lastModified)
    )

    private fun IndexedChunkEntity.toDomain(): IndexedChunk = IndexedChunk(
        id = id,
        fileId = fileId,
        chunkIndex = chunkIndex.toInt(),
        totalChunks = totalChunks.toInt(),
        text = text,
        embedding = parseEmbedding(embedding),
        createdAt = Instant.fromEpochMilliseconds(createdAt)
    )

    private fun parseEmbedding(embeddingJson: String): List<Float> {
        return try {
            json.decodeFromString<List<Float>>(embeddingJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
