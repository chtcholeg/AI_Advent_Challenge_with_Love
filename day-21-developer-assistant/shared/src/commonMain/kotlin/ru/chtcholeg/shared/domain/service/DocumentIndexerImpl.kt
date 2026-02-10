package ru.chtcholeg.shared.domain.service

import ru.chtcholeg.shared.domain.model.DocumentChunk
import ru.chtcholeg.shared.domain.model.DocumentMetadata
import ru.chtcholeg.shared.domain.model.SourceType
import kotlin.system.measureTimeMillis

/**
 * Implementation of DocumentIndexer orchestrating the full indexing pipeline
 */
class DocumentIndexerImpl(
    private val documentLoader: DocumentLoader,
    private val textChunker: TextChunker,
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val webPageLoader: WebPageLoader? = null
) : DocumentIndexer {

    override suspend fun indexDocument(
        filePath: String,
        progressCallback: ((Float, String) -> Unit)?
    ): IndexingResult {
        var result: IndexingResult? = null

        val durationMs = measureTimeMillis {
            try {
                // Step 1: Load document
                progressCallback?.invoke(0.1f, "Loading document: $filePath")
                val document = documentLoader.loadDocument(filePath)

                // Step 2: Chunk document
                progressCallback?.invoke(0.3f, "Splitting into chunks...")
                val textChunks = textChunker.chunk(document.content, filePath)

                if (textChunks.isEmpty()) {
                    result = IndexingResult.Error(
                        filePath = filePath,
                        message = "Document is empty or could not be chunked"
                    )
                    return@measureTimeMillis
                }

                // Step 3: Generate embeddings
                progressCallback?.invoke(0.5f, "Generating embeddings (${textChunks.size} chunks)...")
                val texts = textChunks.map { it.text }
                val embeddings = embeddingService.generateEmbeddings(texts)

                // Step 4: Create document chunks
                progressCallback?.invoke(0.8f, "Creating index entries...")
                val documentChunks = textChunks.mapIndexed { index, textChunk ->
                    DocumentChunk(
                        id = "${textChunk.source}_chunk_${textChunk.chunkIndex}",
                        text = textChunk.text,
                        embedding = embeddings[index],
                        metadata = DocumentMetadata(
                            source = textChunk.source,
                            sourceType = SourceType.FILE,
                            chunkIndex = textChunk.chunkIndex,
                            totalChunks = textChunk.totalChunks,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                // Step 5: Add to vector store
                progressCallback?.invoke(0.9f, "Saving to index...")
                vectorStore.addChunks(documentChunks)

                progressCallback?.invoke(1.0f, "Indexing complete!")

                result = IndexingResult.Success(
                    filePath = filePath,
                    chunksCreated = documentChunks.size,
                    durationMs = 0 // Will be updated below
                )
            } catch (e: Exception) {
                result = IndexingResult.Error(
                    filePath = filePath,
                    message = e.message ?: "Unknown error",
                    cause = e
                )
            }
        }

        // Update duration in result
        return when (val finalResult = result!!) {
            is IndexingResult.Success -> finalResult.copy(durationMs = durationMs)
            is IndexingResult.Error -> finalResult
        }
    }

    override suspend fun indexDirectory(
        directoryPath: String,
        extensions: List<String>,
        progressCallback: ((Float, String) -> Unit)?
    ): BatchIndexingResult {
        var successfulFiles = 0
        var failedFiles = 0
        var totalChunks = 0
        val errors = mutableListOf<IndexingResult.Error>()

        val durationMs = measureTimeMillis {
            try {
                // Load all documents
                progressCallback?.invoke(0.0f, "Loading documents from directory...")
                val documents = documentLoader.loadDocumentsFromDirectory(directoryPath, extensions)

                if (documents.isEmpty()) {
                    progressCallback?.invoke(1.0f, "No documents found")
                    return BatchIndexingResult(
                        totalFiles = 0,
                        successfulFiles = 0,
                        failedFiles = 0,
                        totalChunks = 0,
                        durationMs = 0
                    )
                }

                // Index each document
                documents.forEachIndexed { index, document ->
                    val fileProgress = (index.toFloat() / documents.size)
                    progressCallback?.invoke(
                        fileProgress,
                        "Indexing ${index + 1}/${documents.size}: ${document.fileName}"
                    )

                    when (val result = indexDocument(document.filePath)) {
                        is IndexingResult.Success -> {
                            successfulFiles++
                            totalChunks += result.chunksCreated
                        }
                        is IndexingResult.Error -> {
                            failedFiles++
                            errors.add(result)
                        }
                    }
                }

                progressCallback?.invoke(1.0f, "Batch indexing complete!")
            } catch (e: Exception) {
                progressCallback?.invoke(1.0f, "Batch indexing failed: ${e.message}")
            }
        }

        return BatchIndexingResult(
            totalFiles = successfulFiles + failedFiles,
            successfulFiles = successfulFiles,
            failedFiles = failedFiles,
            totalChunks = totalChunks,
            durationMs = durationMs,
            errors = errors
        )
    }

    override suspend fun search(query: String, topK: Int): List<SearchResult> {
        // Generate embedding for query
        val queryEmbedding = embeddingService.generateEmbedding(query)

        // Search in vector store
        return vectorStore.search(queryEmbedding, topK)
    }

    override suspend fun getStats(): IndexStats {
        return vectorStore.getStats()
    }

    override suspend fun indexUrl(
        url: String,
        progressCallback: ((Float, String) -> Unit)?
    ): IndexingResult {
        if (webPageLoader == null) {
            return IndexingResult.Error(
                filePath = url,
                message = "Web page loader is not configured"
            )
        }

        var result: IndexingResult? = null

        val durationMs = measureTimeMillis {
            try {
                // Step 1: Load web page
                progressCallback?.invoke(0.1f, "Loading web page: $url")
                val document = webPageLoader.loadWebPage(url)

                // Step 2: Chunk document
                progressCallback?.invoke(0.3f, "Splitting into chunks...")
                val textChunks = textChunker.chunk(document.content, url)

                if (textChunks.isEmpty()) {
                    result = IndexingResult.Error(
                        filePath = url,
                        message = "Web page is empty or could not be chunked"
                    )
                    return@measureTimeMillis
                }

                // Step 3: Generate embeddings
                progressCallback?.invoke(0.5f, "Generating embeddings (${textChunks.size} chunks)...")
                val texts = textChunks.map { it.text }
                val embeddings = embeddingService.generateEmbeddings(texts)

                // Step 4: Create document chunks
                progressCallback?.invoke(0.8f, "Creating index entries...")
                val documentChunks = textChunks.mapIndexed { index, textChunk ->
                    DocumentChunk(
                        id = "${url.hashCode()}_chunk_${textChunk.chunkIndex}",
                        text = textChunk.text,
                        embedding = embeddings[index],
                        metadata = DocumentMetadata(
                            source = url,
                            sourceType = SourceType.URL,
                            chunkIndex = textChunk.chunkIndex,
                            totalChunks = textChunk.totalChunks,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                // Step 5: Add to vector store
                progressCallback?.invoke(0.9f, "Saving to index...")
                vectorStore.addChunks(documentChunks)

                progressCallback?.invoke(1.0f, "Indexing complete!")

                result = IndexingResult.Success(
                    filePath = url,
                    chunksCreated = documentChunks.size,
                    durationMs = 0
                )
            } catch (e: Exception) {
                result = IndexingResult.Error(
                    filePath = url,
                    message = e.message ?: "Unknown error",
                    cause = e
                )
            }
        }

        return when (val finalResult = result!!) {
            is IndexingResult.Success -> finalResult.copy(durationMs = durationMs)
            is IndexingResult.Error -> finalResult
        }
    }

    override suspend fun indexUrls(
        urls: List<String>,
        progressCallback: ((Float, String) -> Unit)?
    ): BatchIndexingResult {
        if (webPageLoader == null) {
            return BatchIndexingResult(
                totalFiles = urls.size,
                successfulFiles = 0,
                failedFiles = urls.size,
                totalChunks = 0,
                durationMs = 0,
                errors = urls.map {
                    IndexingResult.Error(
                        filePath = it,
                        message = "Web page loader is not configured"
                    )
                }
            )
        }

        var successfulFiles = 0
        var failedFiles = 0
        var totalChunks = 0
        val errors = mutableListOf<IndexingResult.Error>()

        val durationMs = measureTimeMillis {
            try {
                if (urls.isEmpty()) {
                    progressCallback?.invoke(1.0f, "No URLs provided")
                    return BatchIndexingResult(
                        totalFiles = 0,
                        successfulFiles = 0,
                        failedFiles = 0,
                        totalChunks = 0,
                        durationMs = 0
                    )
                }

                urls.forEachIndexed { index, url ->
                    val urlProgress = (index.toFloat() / urls.size)
                    progressCallback?.invoke(
                        urlProgress,
                        "Indexing ${index + 1}/${urls.size}: $url"
                    )

                    when (val result = indexUrl(url)) {
                        is IndexingResult.Success -> {
                            successfulFiles++
                            totalChunks += result.chunksCreated
                        }
                        is IndexingResult.Error -> {
                            failedFiles++
                            errors.add(result)
                        }
                    }
                }

                progressCallback?.invoke(1.0f, "Batch URL indexing complete!")
            } catch (e: Exception) {
                progressCallback?.invoke(1.0f, "Batch URL indexing failed: ${e.message}")
            }
        }

        return BatchIndexingResult(
            totalFiles = successfulFiles + failedFiles,
            successfulFiles = successfulFiles,
            failedFiles = failedFiles,
            totalChunks = totalChunks,
            durationMs = durationMs,
            errors = errors
        )
    }
}
