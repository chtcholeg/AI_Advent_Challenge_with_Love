# Document Indexing Examples

This document provides practical examples for using the document indexing system.

## Setup

First, set up your credentials:

```bash
# Option 1: Environment variables
export GIGACHAT_CLIENT_ID="your_client_id_here"
export GIGACHAT_CLIENT_SECRET="your_client_secret_here"

# Option 2: local.properties file (git-ignored)
cat > local.properties << EOF
gigachat.clientId=your_client_id_here
gigachat.clientSecret=your_client_secret_here
EOF
```

Build the project:
```bash
./gradlew :shared:build
```

## Example 1: Index Project Documentation

Index all markdown files in the current project:

```bash
# Using environment variables
./gradlew :shared:runIndexing --args="index . ./project-docs-index.json md"

# Using test script (loads from local.properties)
./test-indexing.sh
```

**Output:**
```
🔍 Starting document indexing...
📁 Directory: .
📝 Extensions: md

⏳ 10% - Loading documents from directory...
⏳ 20% - Indexing 1/5: README.md
⏳ 30% - Loading document: ./README.md
⏳ 35% - Splitting into chunks...
⏳ 50% - Generating embeddings (8 chunks)...
⏳ 80% - Creating index entries...
⏳ 90% - Saving to index...
⏳ 100% - Indexing complete!

✅ Indexing complete!
📊 Statistics:
   Total files: 5
   Successful: 5
   Failed: 0
   Total chunks: 42
   Duration: 15234ms

💾 Saving index to: ./project-docs-index.json
✅ Index saved successfully!
```

## Example 2: Search for Specific Topics

### Search for "embeddings"

```bash
./gradlew :shared:runIndexing --args="search ./project-docs-index.json 'embeddings API' 5"
```

**Output:**
```
📂 Loading index from: ./project-docs-index.json
🔍 Searching for: "embeddings API"

📊 Found 5 results:

1. INDEXING.md
   Similarity: 92%
   Chunk: 5/18
   Text preview: The EmbeddingService generates vector representations using GigaChat Embeddings API.
   It supports batch processing of up to 10 texts per request with automatic token refresh and retry logic...

2. DAY-16-SUMMARY.md
   Similarity: 87%
   Chunk: 3/12
   Text preview: Extended GigaChatApi with embeddings support. The new generateEmbeddings() method
   accepts a list of texts and returns 1024-dimension vectors for semantic similarity search...

3. QUICKSTART.md
   Similarity: 78%
   Chunk: 2/8
   Text preview: Generate embeddings for your documents using GigaChat API. Each text chunk is
   converted to a 1024-dimension vector that captures semantic meaning...
```

### Search for "Docker"

```bash
./gradlew :shared:runIndexing --args="search ./project-docs-index.json 'Docker container management' 3"
```

### Search with Natural Language Query

```bash
./gradlew :shared:runIndexing --args="search ./project-docs-index.json 'How do I split documents into chunks?' 5"
```

## Example 3: Index Multiple File Types

Index markdown and text files:

```bash
./gradlew :shared:runIndexing --args="index ./docs ./docs-index.json md txt"
```

Index only specific extension:

```bash
# Only README files
./gradlew :shared:runIndexing --args="index . ./readme-index.json md" | grep -i readme

# Only code documentation
./gradlew :shared:runIndexing --args="index ./src ./code-docs-index.json kt java"
```

## Example 4: Index Statistics

Get detailed information about the index:

```bash
./gradlew :shared:runIndexing --args="stats ./project-docs-index.json"
```

**Output:**
```
📂 Loading index from: ./project-docs-index.json
📊 Index Statistics:
   Total chunks: 42
   Total documents: 5
   Index size: 156 KB
   Last updated: Sun Feb 02 15:30:45 MSK 2026
```

## Example 5: Compare Different Queries

```bash
# Technical query
./gradlew :shared:runIndexing --args="search ./index.json 'vector embeddings cosine similarity' 3"

# Conversational query
./gradlew :shared:runIndexing --args="search ./index.json 'How does semantic search work?' 3"

# Specific feature query
./gradlew :shared:runIndexing --args="search ./index.json 'GigaChat authentication' 3"
```

## Example 6: Batch Processing

Create a script to index multiple directories:

```bash
#!/bin/bash

# index-all-docs.sh
export GIGACHAT_CLIENT_ID=$(grep "gigachat.clientId" local.properties | cut -d'=' -f2)
export GIGACHAT_CLIENT_SECRET=$(grep "gigachat.clientSecret" local.properties | cut -d'=' -f2)

echo "📚 Indexing all project documentation..."

# Index different document types
./gradlew :shared:runIndexing --args="index . ./all-docs-index.json md txt" --quiet

echo "✅ Complete! Index saved to ./all-docs-index.json"

# Show statistics
./gradlew :shared:runIndexing --args="stats ./all-docs-index.json"
```

Make it executable and run:
```bash
chmod +x index-all-docs.sh
./index-all-docs.sh
```

## Example 7: Programmatic Usage in Kotlin

### Basic Indexing

```kotlin
import ru.chtcholeg.shared.domain.service.*
import ru.chtcholeg.shared.data.api.GigaChatApiImpl
import io.ktor.client.*

suspend fun indexDocuments() {
    // Setup
    val httpClient = HttpClient()
    val gigaChatApi = GigaChatApiImpl(httpClient)
    val fileSystem = FileSystem()

    val documentLoader = DocumentLoaderImpl(fileSystem)
    val textChunker = TextChunkerImpl(
        ChunkConfig(
            strategy = ChunkStrategy.BY_CHARACTERS,
            chunkSize = 500,
            overlapSize = 50
        )
    )
    val embeddingService = EmbeddingServiceImpl(
        gigaChatApi = gigaChatApi,
        clientId = System.getenv("GIGACHAT_CLIENT_ID")!!,
        clientSecret = System.getenv("GIGACHAT_CLIENT_SECRET")!!,
        batchSize = 10
    )
    val vectorStore = VectorStoreImpl(fileSystem)
    val indexer = DocumentIndexerImpl(
        documentLoader = documentLoader,
        textChunker = textChunker,
        embeddingService = embeddingService,
        vectorStore = vectorStore
    )

    // Index
    val result = indexer.indexDirectory(
        directoryPath = "./docs",
        extensions = listOf("md", "txt")
    ) { progress, message ->
        println("${(progress * 100).toInt()}% - $message")
    }

    println("Indexed ${result.totalChunks} chunks from ${result.successfulFiles} files")

    // Save
    vectorStore.save("./my-index.json")
}
```

### Search with Custom Threshold

```kotlin
suspend fun searchDocuments(query: String) {
    val vectorStore = VectorStoreImpl(fileSystem)
    vectorStore.load("./my-index.json")

    val embeddingService = EmbeddingServiceImpl(/* ... */)
    val queryEmbedding = embeddingService.generateEmbedding(query)

    // Search with custom threshold (min 70% similarity)
    val results = vectorStore.search(
        queryEmbedding = queryEmbedding,
        topK = 10,
        threshold = 0.7f
    )

    results.forEach { result ->
        println("📄 ${result.chunk.metadata.sourceFile}")
        println("   Similarity: ${(result.similarity * 100).toInt()}%")
        println("   ${result.chunk.text.take(100)}...")
        println()
    }
}
```

### Custom Chunking Strategy

```kotlin
// By sentences (better semantic boundaries)
val sentenceChunker = TextChunkerImpl(
    ChunkConfig(
        strategy = ChunkStrategy.BY_SENTENCES,
        chunkSize = 1000,
        overlapSize = 100
    )
)

// By tokens (optimized for LLMs)
val tokenChunker = TextChunkerImpl(
    ChunkConfig(
        strategy = ChunkStrategy.BY_TOKENS,
        chunkSize = 128,  // ~128 tokens
        overlapSize = 16   // ~16 token overlap
    )
)
```

### Progress Tracking

```kotlin
var currentFile = ""
var processedChunks = 0

val result = indexer.indexDirectory(
    directoryPath = "./docs",
    extensions = listOf("md", "txt")
) { progress, message ->
    when {
        message.startsWith("Indexing") -> {
            currentFile = message.substringAfter(": ")
            println("\n📄 Processing: $currentFile")
        }
        message.contains("chunks") -> {
            val chunks = message.filter { it.isDigit() }.toInt()
            processedChunks += chunks
            println("   ✓ Created $chunks chunks (total: $processedChunks)")
        }
        progress == 1.0f -> {
            println("\n✅ Complete! Total chunks: $processedChunks")
        }
    }
}
```

## Example 8: RAG Integration

Use indexed documents as context for chat:

```kotlin
suspend fun chatWithContext(userQuery: String) {
    // Search relevant documents
    val indexer = setupIndexer() // from previous examples
    val results = indexer.search(userQuery, topK = 3)

    // Build context from top results
    val context = results.joinToString("\n\n") { result ->
        "Source: ${result.chunk.metadata.sourceFile}\n" +
        result.chunk.text
    }

    // Send to chat API with context
    val systemPrompt = """
        Use the following context to answer the user's question.
        If the answer is not in the context, say so.

        Context:
        $context
    """.trimIndent()

    val response = chatRepository.sendMessage(
        messages = listOf(
            Message(role = "system", content = systemPrompt),
            Message(role = "user", content = userQuery)
        )
    )

    println("Answer: ${response.content}")
}
```

## Example 9: Monitoring Index Growth

Track how your index grows over time:

```bash
# index-and-track.sh
#!/bin/bash

INDEX_FILE="./growing-index.json"
LOG_FILE="./index-growth.log"

export GIGACHAT_CLIENT_ID=$(grep "gigachat.clientId" local.properties | cut -d'=' -f2)
export GIGACHAT_CLIENT_SECRET=$(grep "gigachat.clientSecret" local.properties | cut -d'=' -f2)

# Index
./gradlew :shared:runIndexing --args="index . $INDEX_FILE md txt" --quiet

# Get stats and log
STATS=$(./gradlew :shared:runIndexing --args="stats $INDEX_FILE" --quiet)
CHUNKS=$(echo "$STATS" | grep "Total chunks" | grep -o '[0-9]*')
SIZE=$(echo "$STATS" | grep "Index size" | grep -o '[0-9]* KB')
TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")

echo "$TIMESTAMP | Chunks: $CHUNKS | Size: $SIZE" >> $LOG_FILE

# Show trend
echo "📈 Index Growth:"
tail -5 $LOG_FILE
```

## Example 10: Error Handling

Handle indexing errors gracefully:

```kotlin
suspend fun safeIndexing() {
    val indexer = setupIndexer()

    try {
        val result = indexer.indexDirectory("./docs", listOf("md", "txt"))

        when {
            result.successfulFiles == result.totalFiles -> {
                println("✅ All files indexed successfully!")
            }
            result.failedFiles > 0 -> {
                println("⚠️  Partial success:")
                println("   - Success: ${result.successfulFiles}")
                println("   - Failed: ${result.failedFiles}")
                println("\nErrors:")
                result.errors.forEach { error ->
                    println("   ❌ ${error.filePath}: ${error.message}")
                }
            }
            else -> {
                println("❌ No files were indexed")
            }
        }
    } catch (e: Exception) {
        println("❌ Indexing failed: ${e.message}")
        e.printStackTrace()
    }
}
```

## Tips & Best Practices

1. **Chunk Size**:
   - Smaller (200-400 chars) = more precise, more API calls
   - Larger (600-1000 chars) = broader context, fewer API calls
   - Default (500 chars) = good balance

2. **Overlap**:
   - Use 10-15% of chunk size for overlap
   - Prevents losing context at chunk boundaries

3. **Batch Size**:
   - 10 texts per batch = good default
   - Increase for better throughput (if API allows)
   - Decrease if hitting rate limits

4. **Search Threshold**:
   - 0.8-1.0 = Highly relevant
   - 0.6-0.8 = Related content
   - 0.4-0.6 = Tangentially related
   - <0.4 = Likely not relevant

5. **Index Persistence**:
   - Save index after indexing: `vectorStore.save("./index.json")`
   - Load before searching: `vectorStore.load("./index.json")`
   - Keep backups of large indexes

6. **Performance**:
   - Index once, search many times
   - Use batch processing for multiple queries
   - Consider caching for frequently searched queries

## Troubleshooting

See [INDEXING.md](./INDEXING.md#troubleshooting) for common issues and solutions.

## Summary

These examples demonstrate:
- ✅ Basic indexing and searching
- ✅ Multiple file types and directories
- ✅ Custom chunking strategies
- ✅ Programmatic usage in Kotlin
- ✅ RAG integration patterns
- ✅ Error handling and monitoring
- ✅ Best practices for production use

For complete API reference, see [INDEXING.md](./INDEXING.md).
