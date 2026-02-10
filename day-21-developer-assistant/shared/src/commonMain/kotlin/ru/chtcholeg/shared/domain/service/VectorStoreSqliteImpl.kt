package ru.chtcholeg.shared.domain.service

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.shared.domain.model.DocumentChunk
import ru.chtcholeg.shared.domain.model.DocumentMetadata

/**
 * VectorStore backed by SQLite via RagDatabase.
 * Schema is identical to indexer/ GUI — both CLI-generated and GUI-generated .db files are readable.
 *
 * @param driverFactory maps a file path to a SqlDriver.
 *   The caller is responsible for ~ resolution and schema creation when needed.
 */
class VectorStoreSqliteImpl(
    private val driverFactory: (String) -> SqlDriver
) : VectorStore {

    private var db: RagDatabase? = null

    private fun requireDb(): RagDatabase =
        db ?: throw IllegalStateException("Database not opened. Call load() or save() first.")

    override suspend fun save(filePath: String) {
        // For SQLite: save = open/create the database file.
        // The driverFactory decides whether to run Schema.create (new file) or just open (existing).
        val driver = driverFactory(filePath)
        db = RagDatabase(driver)
    }

    override suspend fun load(filePath: String) {
        // Same as save — driverFactory opens the existing file.
        val driver = driverFactory(filePath)
        db = RagDatabase(driver)
    }

    override suspend fun addChunks(chunks: List<DocumentChunk>) {
        val database = requireDb()
        val now = System.currentTimeMillis()

        // Group chunks by source
        val bySource = chunks.groupBy { it.metadata.source }

        for ((source, fileChunks) in bySource) {
            // Insert file record
            database.ragFileQueries.insert(
                fileName = source,
                filePath = source,
                checksum = "",
                fileSize = 0L,
                chunkCount = fileChunks.size.toLong(),
                indexedAt = now,
                lastModified = now
            )
            val fileId = database.ragFileQueries.lastInsertRowId().executeAsOne()

            // Insert each chunk
            for (chunk in fileChunks) {
                database.ragChunkQueries.insert(
                    fileId = fileId,
                    chunkIndex = chunk.metadata.chunkIndex.toLong(),
                    totalChunks = chunk.metadata.totalChunks.toLong(),
                    text = chunk.text,
                    embedding = Json.encodeToString(chunk.embedding),
                    createdAt = chunk.metadata.timestamp
                )
            }
        }
    }

    override suspend fun search(
        queryEmbedding: List<Float>,
        topK: Int,
        threshold: Float
    ): List<SearchResult> {
        val database = requireDb()
        val rows = database.ragChunkQueries.selectAllWithFileInfo().executeAsList()

        if (rows.isEmpty()) return emptyList()

        // Validate embedding dimensions before computing similarities
        val firstStoredDim = Json.decodeFromString<List<Float>>(rows[0].embedding).size
        if (firstStoredDim > 0 && queryEmbedding.size != firstStoredDim) {
            throw IllegalArgumentException(embeddingDimensionError(firstStoredDim, queryEmbedding.size))
        }

        return rows
            .map { row ->
                val embedding = Json.decodeFromString<List<Float>>(row.embedding)
                val similarity = dotProduct(queryEmbedding, embedding)
                // Detect source type from file path/URL
                val sourceType = if (row.filePath.startsWith("http://") || row.filePath.startsWith("https://")) {
                    ru.chtcholeg.shared.domain.model.SourceType.URL
                } else {
                    ru.chtcholeg.shared.domain.model.SourceType.FILE
                }
                val chunk = DocumentChunk(
                    id = row.chunkId.toString(),
                    text = row.text,
                    embedding = embedding,
                    metadata = DocumentMetadata(
                        source = row.filePath,
                        sourceType = sourceType,
                        chunkIndex = row.chunkIndex.toInt(),
                        totalChunks = row.totalChunks.toInt(),
                        timestamp = row.createdAt
                    )
                )
                SearchResult(chunk, similarity)
            }
            .filter { it.similarity >= threshold }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    override suspend fun getAllChunks(): List<DocumentChunk> {
        val database = requireDb()
        return database.ragChunkQueries.selectAllWithFileInfo().executeAsList().map { row ->
            // Detect source type from file path/URL
            val sourceType = if (row.filePath.startsWith("http://") || row.filePath.startsWith("https://")) {
                ru.chtcholeg.shared.domain.model.SourceType.URL
            } else {
                ru.chtcholeg.shared.domain.model.SourceType.FILE
            }
            DocumentChunk(
                id = row.chunkId.toString(),
                text = row.text,
                embedding = Json.decodeFromString(row.embedding),
                metadata = DocumentMetadata(
                    source = row.filePath,
                    sourceType = sourceType,
                    chunkIndex = row.chunkIndex.toInt(),
                    totalChunks = row.totalChunks.toInt(),
                    timestamp = row.createdAt
                )
            )
        }
    }

    override suspend fun clear() {
        val database = requireDb()
        // Chunks first (FK constraint), then files
        database.ragChunkQueries.deleteAll()
        database.ragFileQueries.deleteAll()
    }

    override suspend fun getStats(): IndexStats {
        val database = requireDb()
        val totalChunks = database.ragChunkQueries.countChunks().executeAsOne().toInt()
        val totalDocuments = database.ragFileQueries.countFiles().executeAsOne().toInt()
        val lastUpdated = database.ragChunkQueries.maxCreatedAt().executeAsOne().MAX ?: 0L

        return IndexStats(
            totalChunks = totalChunks,
            totalDocuments = totalDocuments,
            indexSizeBytes = 0L, // not meaningful for SQLite; file size would need platform FS
            lastUpdated = lastUpdated
        )
    }

    private fun dotProduct(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) {
            throw IllegalArgumentException("Vectors must have the same dimension (${a.size} vs ${b.size})")
        }
        var result = 0.0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }
}
