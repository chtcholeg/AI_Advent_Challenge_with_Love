package ru.chtcholeg.shared.domain.service

/**
 * Implementation of IndexingCli
 */
class IndexingCliImpl(
    private val documentIndexer: DocumentIndexer,
    private val vectorStore: VectorStore
) : IndexingCli {

    override suspend fun indexDocuments(
        directoryPath: String,
        outputIndexPath: String,
        extensions: List<String>
    ) {
        println("🔍 Starting document indexing...")
        println("📁 Directory: $directoryPath")
        println("📝 Extensions: ${extensions.joinToString(", ")}")
        println()

        // Open/create the database before indexing so that addChunks() can write immediately
        println("🗄️  Opening database: $outputIndexPath")
        vectorStore.save(outputIndexPath)

        val result = documentIndexer.indexDirectory(
            directoryPath = directoryPath,
            extensions = extensions
        ) { progress, message ->
            print("\r⏳ ${(progress * 100).toInt()}% - $message")
        }

        println() // New line after progress
        println()
        println("✅ Indexing complete!")
        println("📊 Statistics:")
        println("   Total files: ${result.totalFiles}")
        println("   Successful: ${result.successfulFiles}")
        println("   Failed: ${result.failedFiles}")
        println("   Total chunks: ${result.totalChunks}")
        println("   Duration: ${result.durationMs}ms")

        if (result.errors.isNotEmpty()) {
            println()
            println("❌ Errors:")
            result.errors.forEach { error ->
                println("   - ${error.filePath}: ${error.message}")
            }
        }

        println()
        println("✅ Index saved to: $outputIndexPath")
    }

    override suspend fun searchDocuments(
        indexPath: String,
        query: String,
        topK: Int
    ) {
        println("📂 Loading index from: $indexPath")
        vectorStore.load(indexPath)

        println("🔍 Searching for: \"$query\"")
        println()

        val results = documentIndexer.search(query, topK)

        if (results.isEmpty()) {
            println("❌ No results found")
            return
        }

        println("📊 Found ${results.size} results:")
        println()

        results.forEachIndexed { index, result ->
            val sourceLabel = if (result.chunk.metadata.sourceType == ru.chtcholeg.shared.domain.model.SourceType.URL) {
                "🌐 URL: ${result.chunk.metadata.source}"
            } else {
                "📄 File: ${result.chunk.metadata.source}"
            }
            println("${index + 1}. $sourceLabel")
            println("   Similarity: ${(result.similarity * 100).toInt()}%")
            println("   Chunk: ${result.chunk.metadata.chunkIndex + 1}/${result.chunk.metadata.totalChunks}")
            println("   Text preview: ${result.chunk.text.take(150)}...")
            println()
        }
    }

    override suspend fun showStats(indexPath: String) {
        println("📂 Loading index from: $indexPath")
        vectorStore.load(indexPath)

        val stats = documentIndexer.getStats()

        println("📊 Index Statistics:")
        println("   Total chunks: ${stats.totalChunks}")
        println("   Total documents: ${stats.totalDocuments}")
        println("   Index size: ${stats.indexSizeBytes / 1024} KB")
        println("   Last updated: ${java.util.Date(stats.lastUpdated)}")
    }

    override suspend fun indexUrl(
        url: String,
        outputIndexPath: String
    ) {
        println("🔍 Starting URL indexing...")
        println("🌐 URL: $url")
        println()

        // Open/create the database before indexing
        println("🗄️  Opening database: $outputIndexPath")
        vectorStore.save(outputIndexPath)

        val result = documentIndexer.indexUrl(url) { progress, message ->
            print("\r⏳ ${(progress * 100).toInt()}% - $message")
        }

        println() // New line after progress
        println()

        when (result) {
            is IndexingResult.Success -> {
                println("✅ Indexing complete!")
                println("📊 Statistics:")
                println("   URL: ${result.filePath}")
                println("   Chunks created: ${result.chunksCreated}")
                println("   Duration: ${result.durationMs}ms")
            }
            is IndexingResult.Error -> {
                println("❌ Indexing failed!")
                println("   URL: ${result.filePath}")
                println("   Error: ${result.message}")
            }
        }

        println()
        println("✅ Index saved to: $outputIndexPath")
    }

    override suspend fun indexUrls(
        urls: List<String>,
        outputIndexPath: String
    ) {
        println("🔍 Starting batch URL indexing...")
        println("🌐 URLs: ${urls.size} total")
        println()

        // Open/create the database before indexing
        println("🗄️  Opening database: $outputIndexPath")
        vectorStore.save(outputIndexPath)

        val result = documentIndexer.indexUrls(urls) { progress, message ->
            print("\r⏳ ${(progress * 100).toInt()}% - $message")
        }

        println() // New line after progress
        println()
        println("✅ Batch indexing complete!")
        println("📊 Statistics:")
        println("   Total URLs: ${result.totalFiles}")
        println("   Successful: ${result.successfulFiles}")
        println("   Failed: ${result.failedFiles}")
        println("   Total chunks: ${result.totalChunks}")
        println("   Duration: ${result.durationMs}ms")

        if (result.errors.isNotEmpty()) {
            println()
            println("❌ Errors:")
            result.errors.forEach { error ->
                println("   - ${error.filePath}: ${error.message}")
            }
        }

        println()
        println("✅ Index saved to: $outputIndexPath")
    }
}
