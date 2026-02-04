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

        // Save index
        println()
        println("💾 Saving index to: $outputIndexPath")
        vectorStore.save(outputIndexPath)
        println("✅ Index saved successfully!")
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
            println("${index + 1}. ${result.chunk.metadata.sourceFile}")
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
}
