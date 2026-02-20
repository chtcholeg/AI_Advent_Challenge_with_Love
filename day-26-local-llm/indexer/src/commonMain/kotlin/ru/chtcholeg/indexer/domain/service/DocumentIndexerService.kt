package ru.chtcholeg.indexer.domain.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import ru.chtcholeg.indexer.data.local.IndexerLocalRepository
import ru.chtcholeg.indexer.domain.model.IndexedChunk
import ru.chtcholeg.indexer.domain.model.IndexedFile
import ru.chtcholeg.indexer.domain.model.SearchResult
import ru.chtcholeg.shared.domain.service.DocumentLoader
import ru.chtcholeg.shared.domain.service.EmbeddingService
import ru.chtcholeg.shared.domain.service.TextChunker
import ru.chtcholeg.shared.domain.service.WebPageLoader
import ru.chtcholeg.shared.domain.service.embeddingDimensionError
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Progress events during indexing
 */
sealed class IndexingEvent {
    data class Started(val totalFiles: Int) : IndexingEvent()
    data class FileStarted(val fileName: String, val fileIndex: Int, val totalFiles: Int) : IndexingEvent()
    data class ChunksCreated(val fileName: String, val chunkCount: Int) : IndexingEvent()
    data class EmbeddingsGenerated(val fileName: String, val chunkCount: Int) : IndexingEvent()
    data class FileCompleted(val fileName: String, val fileIndex: Int, val totalFiles: Int) : IndexingEvent()
    data class FileSkipped(val fileName: String, val reason: String) : IndexingEvent()
    data class Completed(val totalFiles: Int, val totalChunks: Int) : IndexingEvent()
    data class Error(val message: String, val cause: Throwable? = null) : IndexingEvent()
}

/**
 * Service for indexing documents with embeddings
 */
class DocumentIndexerService(
    private val documentLoader: DocumentLoader,
    private val textChunker: TextChunker,
    var embeddingService: EmbeddingService,
    private val localRepository: IndexerLocalRepository,
    private val webPageLoader: WebPageLoader
) {
    /**
     * Index documents from a path (file or directory)
     * @param path Path to file or directory
     * @param extensions File extensions to process (for directories)
     * @return Flow of indexing events
     */
    fun indexDocuments(
        path: String,
        extensions: List<String> = listOf("md", "txt")
    ): Flow<IndexingEvent> = flow {
        try {
            // Load documents
            val documents = if (path.endsWith("/") || !path.contains(".")) {
                documentLoader.loadDocumentsFromDirectory(path, extensions)
            } else {
                listOf(documentLoader.loadDocument(path))
            }

            if (documents.isEmpty()) {
                emit(IndexingEvent.Completed(0, 0))
                return@flow
            }

            emit(IndexingEvent.Started(documents.size))

            var totalChunksIndexed = 0

            documents.forEachIndexed { index, document ->
                emit(IndexingEvent.FileStarted(document.fileName, index + 1, documents.size))

                // Calculate checksum
                val checksum = calculateMd5(document.content)

                // Check if file already indexed with same checksum
                val existingFile = localRepository.getFileByPath(document.filePath)
                if (existingFile != null && existingFile.checksum == checksum) {
                    emit(IndexingEvent.FileSkipped(document.fileName, "Already indexed (unchanged)"))
                    return@forEachIndexed
                }

                // Delete existing file data if re-indexing
                existingFile?.let {
                    localRepository.deleteChunksByFileId(it.id)
                    localRepository.deleteFile(it.id)
                }

                // Create chunks
                val chunks = textChunker.chunk(document.content, document.fileName)
                if (chunks.isEmpty()) {
                    emit(IndexingEvent.FileSkipped(document.fileName, "No content to index"))
                    return@forEachIndexed
                }

                emit(IndexingEvent.ChunksCreated(document.fileName, chunks.size))

                // Generate embeddings
                val chunkTexts = chunks.map { it.text }
                val embeddings = embeddingService.generateEmbeddings(chunkTexts)

                emit(IndexingEvent.EmbeddingsGenerated(document.fileName, embeddings.size))

                // Store file
                val now = Clock.System.now().toEpochMilliseconds()
                val fileId = localRepository.insertFile(
                    fileName = document.fileName,
                    filePath = document.filePath,
                    checksum = checksum,
                    fileSize = document.fileSize,
                    chunkCount = chunks.size,
                    indexedAt = now,
                    lastModified = document.lastModified
                )

                // Store chunks with embeddings
                chunks.forEachIndexed { chunkIndex, chunk ->
                    val embedding = embeddings.getOrElse(chunkIndex) { emptyList() }
                    localRepository.insertChunk(
                        fileId = fileId,
                        chunkIndex = chunk.chunkIndex,
                        totalChunks = chunk.totalChunks,
                        text = chunk.text,
                        embedding = embedding,
                        createdAt = now
                    )
                }

                totalChunksIndexed += chunks.size
                emit(IndexingEvent.FileCompleted(document.fileName, index + 1, documents.size))
            }

            emit(IndexingEvent.Completed(documents.size, totalChunksIndexed))

        } catch (e: Exception) {
            emit(IndexingEvent.Error(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Index a web page from URL
     * @param url URL of the web page to index
     * @return Flow of indexing events
     */
    fun indexUrl(url: String): Flow<IndexingEvent> = flow {
        try {
            emit(IndexingEvent.Started(1))
            emit(IndexingEvent.FileStarted(url, 1, 1))

            // Load web page content
            val document = webPageLoader.loadWebPage(url)

            // Calculate checksum from content
            val checksum = calculateMd5(document.content)

            // Check if URL already indexed with same checksum
            val existingFile = localRepository.getFileByPath(url)
            if (existingFile != null && existingFile.checksum == checksum) {
                emit(IndexingEvent.FileSkipped(url, "Already indexed (unchanged)"))
                emit(IndexingEvent.Completed(1, 0))
                return@flow
            }

            // Delete existing data if re-indexing
            existingFile?.let {
                localRepository.deleteChunksByFileId(it.id)
                localRepository.deleteFile(it.id)
            }

            // Create chunks
            val chunks = textChunker.chunk(document.content, url)
            if (chunks.isEmpty()) {
                emit(IndexingEvent.FileSkipped(url, "No content to index"))
                emit(IndexingEvent.Completed(1, 0))
                return@flow
            }

            emit(IndexingEvent.ChunksCreated(url, chunks.size))

            // Generate embeddings
            val chunkTexts = chunks.map { it.text }
            val embeddings = embeddingService.generateEmbeddings(chunkTexts)

            emit(IndexingEvent.EmbeddingsGenerated(url, embeddings.size))

            // Store file record (URL stored in filePath)
            val now = Clock.System.now().toEpochMilliseconds()
            val fileId = localRepository.insertFile(
                fileName = document.fileName,
                filePath = url,
                checksum = checksum,
                fileSize = document.fileSize,
                chunkCount = chunks.size,
                indexedAt = now,
                lastModified = now
            )

            // Store chunks with embeddings
            chunks.forEachIndexed { chunkIndex, chunk ->
                val embedding = embeddings.getOrElse(chunkIndex) { emptyList() }
                localRepository.insertChunk(
                    fileId = fileId,
                    chunkIndex = chunk.chunkIndex,
                    totalChunks = chunk.totalChunks,
                    text = chunk.text,
                    embedding = embedding,
                    createdAt = now
                )
            }

            emit(IndexingEvent.FileCompleted(url, 1, 1))
            emit(IndexingEvent.Completed(1, chunks.size))

        } catch (e: Exception) {
            emit(IndexingEvent.Error(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Search indexed documents
     * @param query Search query
     * @param topK Number of results to return
     * @return List of search results sorted by similarity
     */
    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }

        // Generate query embedding
        val queryEmbedding = embeddingService.generateEmbedding(query)

        // Get all chunks with embeddings
        val chunksWithFiles = localRepository.getAllChunksWithFileInfo()

        // Validate embedding dimensions before computing similarities
        val firstNonEmpty = chunksWithFiles.firstOrNull { it.chunk.embedding.isNotEmpty() }
        if (firstNonEmpty != null && queryEmbedding.size != firstNonEmpty.chunk.embedding.size) {
            throw IllegalArgumentException(
                embeddingDimensionError(firstNonEmpty.chunk.embedding.size, queryEmbedding.size)
            )
        }

        // Calculate similarities and rank
        val results = chunksWithFiles.mapNotNull { chunkWithFile ->
            if (chunkWithFile.chunk.embedding.isEmpty()) {
                return@mapNotNull null
            }

            val similarity = cosineSimilarity(queryEmbedding, chunkWithFile.chunk.embedding)
            val file = localRepository.getFileById(chunkWithFile.chunk.fileId)
                ?: return@mapNotNull null

            SearchResult(
                chunk = chunkWithFile.chunk,
                file = file,
                similarity = similarity
            )
        }

        // Sort by similarity and take top K
        return results
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    /**
     * Get all indexed files
     */
    suspend fun getAllFiles(): List<IndexedFile> {
        return localRepository.getAllFiles()
    }

    /**
     * Get chunks for a file
     */
    suspend fun getChunksForFile(fileId: Long): List<IndexedChunk> {
        return localRepository.getChunksByFileId(fileId)
    }

    /**
     * Delete a file and its chunks
     */
    suspend fun deleteFile(fileId: Long) {
        // Explicitly delete chunks first to ensure cleanup
        localRepository.deleteChunksByFileId(fileId)
        localRepository.deleteFile(fileId)
    }

    /**
     * Get indexing statistics
     */
    suspend fun getStats(): IndexingStats {
        return IndexingStats(
            fileCount = localRepository.getFileCount(),
            chunkCount = localRepository.getChunkCount()
        )
    }

    // Helper functions

    private fun calculateMd5(content: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a.size != b.size) {
            throw IllegalArgumentException(embeddingDimensionError(b.size, a.size))
        }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}

/**
 * Statistics about indexed documents
 */
data class IndexingStats(
    val fileCount: Long,
    val chunkCount: Long
)
