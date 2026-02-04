package ru.chtcholeg.shared.domain.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.shared.domain.model.DocumentChunk
import ru.chtcholeg.shared.domain.model.DocumentIndex
import ru.chtcholeg.shared.domain.model.IndexedDocument

/**
 * In-memory vector store implementation with JSON persistence
 */
class VectorStoreImpl(
    private val fileSystem: FileSystem,
    private val json: Json = Json { prettyPrint = true }
) : VectorStore {

    private val chunks = mutableListOf<DocumentChunk>()
    private var lastUpdated: Long = System.currentTimeMillis()

    override suspend fun addChunks(chunks: List<DocumentChunk>) {
        this.chunks.addAll(chunks)
        lastUpdated = System.currentTimeMillis()
    }

    override suspend fun search(
        queryEmbedding: List<Float>,
        topK: Int,
        threshold: Float
    ): List<SearchResult> {
        if (chunks.isEmpty()) {
            return emptyList()
        }

        return chunks
            .map { chunk ->
                val similarity = dotProduct(queryEmbedding, chunk.embedding)
                SearchResult(chunk, similarity)
            }
            .filter { it.similarity >= threshold }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    override suspend fun getAllChunks(): List<DocumentChunk> {
        return chunks.toList()
    }

    override suspend fun clear() {
        chunks.clear()
        lastUpdated = System.currentTimeMillis()
    }

    override suspend fun save(filePath: String) {
        // Group chunks by source file
        val documentMap = chunks.groupBy { it.metadata.sourceFile }

        val indexedDocuments = documentMap.map { (sourceFile, chunks) ->
            IndexedDocument(
                sourceFile = sourceFile,
                chunks = chunks,
                timestamp = lastUpdated
            )
        }

        val index = DocumentIndex(
            documents = indexedDocuments,
            createdAt = lastUpdated,
            version = 1
        )

        val jsonString = json.encodeToString(index)

        // Write to file using FileSystem
        writeFile(filePath, jsonString)
    }

    override suspend fun load(filePath: String) {
        if (!fileSystem.fileExists(filePath)) {
            throw IllegalArgumentException("Index file not found: $filePath")
        }

        val jsonString = fileSystem.readFile(filePath)
        val index = json.decodeFromString<DocumentIndex>(jsonString)

        chunks.clear()
        index.documents.forEach { document ->
            chunks.addAll(document.chunks)
        }

        lastUpdated = index.createdAt
    }

    override suspend fun getStats(): IndexStats {
        val uniqueDocuments = chunks.map { it.metadata.sourceFile }.distinct().size
        val indexSizeBytes = calculateIndexSize()

        return IndexStats(
            totalChunks = chunks.size,
            totalDocuments = uniqueDocuments,
            indexSizeBytes = indexSizeBytes,
            lastUpdated = lastUpdated
        )
    }

    /**
     * Calculate dot product between two L2-normalized vectors.
     * For normalized vectors: dot product = cosine similarity.
     */
    private fun dotProduct(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) {
            throw IllegalArgumentException("Vectors must have the same dimension")
        }

        var result = 0.0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }

    /**
     * Calculate approximate index size in bytes
     */
    private fun calculateIndexSize(): Long {
        // Approximate: each float is 4 bytes, plus metadata overhead
        val embeddingSize = chunks.sumOf { it.embedding.size * 4L }
        val textSize = chunks.sumOf { it.text.length * 2L } // UTF-16 = 2 bytes per char
        val overhead = chunks.size * 100L // Approximate metadata overhead

        return embeddingSize + textSize + overhead
    }

    /**
     * Write file content using FileSystem
     */
    private suspend fun writeFile(path: String, content: String) {
        fileSystem.writeFile(path, content)
    }
}
