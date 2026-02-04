# Day 16: Document Indexing with Embeddings

This document describes the document indexing system implemented for Day 16 of the AI Advent Challenge.

## Overview

The document indexing system provides a complete pipeline for:
1. Loading documents from files (markdown, text, etc.)
2. Splitting documents into semantic chunks
3. Generating embeddings using GigaChat API
4. Storing embeddings in a vector store
5. Searching documents by semantic similarity

## Architecture

### Components

#### 1. Domain Models (`shared/domain/model/`)
- **DocumentChunk**: Represents a text chunk with its embedding vector
- **DocumentMetadata**: Metadata for each chunk (source file, chunk index, etc.)
- **IndexedDocument**: Collection of chunks from a single document
- **DocumentIndex**: Complete index containing all indexed documents

#### 2. Services (`shared/domain/service/`)

##### DocumentLoader
- **Interface**: `DocumentLoader`
- **Implementation**: `DocumentLoaderImpl`
- **Purpose**: Load documents from file system
- **Features**:
  - Load single document
  - Load all documents from directory with filtering by extension
  - Extract file metadata (size, modification date)

##### TextChunker
- **Interface**: `TextChunker`
- **Implementation**: `TextChunkerImpl`
- **Purpose**: Split documents into chunks for embedding
- **Strategies**:
  - `BY_CHARACTERS`: Fixed character count with overlap (default: 500 chars, 50 overlap)
  - `BY_TOKENS`: Approximate token count (~4 chars per token)
  - `BY_SENTENCES`: Split by sentences while respecting chunk size
- **Configuration**: `ChunkConfig` with strategy, size, and overlap

##### EmbeddingService
- **Interface**: `EmbeddingService`
- **Implementation**: `EmbeddingServiceImpl`
- **Purpose**: Generate embeddings using GigaChat API
- **Features**:
  - Batch processing (default: 10 texts per batch)
  - Automatic authentication and token refresh
  - Retry logic for expired tokens

##### VectorStore
- **Interface**: `VectorStore`
- **Implementation**: `VectorStoreImpl`
- **Purpose**: Store and search embeddings
- **Features**:
  - In-memory storage with JSON persistence
  - Cosine similarity search
  - Save/load index from file
  - Index statistics (chunks count, size, etc.)

##### DocumentIndexer
- **Interface**: `DocumentIndexer`
- **Implementation**: `DocumentIndexerImpl`
- **Purpose**: Orchestrate the complete indexing pipeline
- **Features**:
  - Index single document
  - Index entire directory
  - Progress reporting
  - Error handling and batch results
  - Semantic search

#### 3. API Extensions (`shared/data/api/`)
- **EmbeddingRequest/Response**: Data models for embeddings API
- **GigaChatApi.generateEmbeddings()**: New method for generating embeddings

#### 4. Cross-Platform File System (`shared/domain/service/FileSystem`)
- **expect/actual** pattern for Android and Desktop
- Operations: readFile, fileExists, getFileSize, getLastModified, listFiles

### Data Flow

```
Documents → DocumentLoader → TextChunker → EmbeddingService → VectorStore
     ↓                           ↓              ↓                  ↓
  File I/O               Text Splitting   GigaChat API      JSON Storage
```

## Usage

### CLI Tool

The system includes a command-line interface for indexing and searching documents.

#### Prerequisites

Set GigaChat credentials as environment variables:

```bash
export GIGACHAT_CLIENT_ID="your_client_id"
export GIGACHAT_CLIENT_SECRET="your_client_secret"
```

Or use `local.properties` file (git-ignored):
```properties
gigachat.clientId=your_client_id
gigachat.clientSecret=your_client_secret
```

#### Commands

##### Index Documents

Index all markdown files in a directory:

```bash
./gradlew :shared:runIndexing --args="index ./docs ./index.json md"
```

Index multiple file types:

```bash
./gradlew :shared:runIndexing --args="index ./docs ./index.json md txt"
```

##### Search Documents

Search for similar content:

```bash
./gradlew :shared:runIndexing --args="search ./index.json 'GigaChat API' 5"
```

Parameters:
- `./index.json`: Path to index file
- `'GigaChat API'`: Search query
- `5`: Number of results (optional, default: 5)

##### Show Statistics

Display index information:

```bash
./gradlew :shared:runIndexing --args="stats ./index.json"
```

### Testing Script

A convenience script is provided for quick testing:

```bash
./test-indexing.sh
```

This script will:
1. Load credentials from `local.properties`
2. Index all markdown files in current directory
3. Show index statistics
4. Perform a test search

## API Reference

### DocumentIndexer

```kotlin
interface DocumentIndexer {
    // Index single document with progress callback
    suspend fun indexDocument(
        filePath: String,
        progressCallback: ((Float, String) -> Unit)? = null
    ): IndexingResult

    // Index directory with progress callback
    suspend fun indexDirectory(
        directoryPath: String,
        extensions: List<String> = listOf("md", "txt"),
        progressCallback: ((Float, String) -> Unit)? = null
    ): BatchIndexingResult

    // Search indexed documents
    suspend fun search(query: String, topK: Int = 5): List<SearchResult>

    // Get index statistics
    suspend fun getStats(): IndexStats
}
```

### VectorStore

```kotlin
interface VectorStore {
    // Add chunks to store
    suspend fun addChunks(chunks: List<DocumentChunk>)

    // Search by similarity
    suspend fun search(
        queryEmbedding: List<Float>,
        topK: Int = 5,
        threshold: Float = 0.0f
    ): List<SearchResult>

    // Persistence
    suspend fun save(filePath: String)
    suspend fun load(filePath: String)

    // Statistics
    suspend fun getStats(): IndexStats
}
```

## Configuration

### Chunk Configuration

Default chunk configuration:

```kotlin
ChunkConfig(
    strategy = ChunkStrategy.BY_CHARACTERS,
    chunkSize = 500,      // 500 characters per chunk
    overlapSize = 50       // 50 character overlap between chunks
)
```

You can customize this when creating `TextChunkerImpl`:

```kotlin
val chunker = TextChunkerImpl(
    ChunkConfig(
        strategy = ChunkStrategy.BY_SENTENCES,
        chunkSize = 1000,
        overlapSize = 100
    )
)
```

### Embedding Service Configuration

```kotlin
val embeddingService = EmbeddingServiceImpl(
    gigaChatApi = gigaChatApi,
    clientId = clientId,
    clientSecret = clientSecret,
    batchSize = 10  // Process 10 texts per API call
)
```

## Performance Considerations

1. **Chunking Strategy**:
   - `BY_CHARACTERS`: Fastest, but may split mid-sentence
   - `BY_SENTENCES`: Better semantic boundaries, slightly slower
   - `BY_TOKENS`: Most accurate for LLMs, uses approximation

2. **Batch Size**:
   - Default: 10 texts per batch
   - Larger batches = fewer API calls but longer wait per batch
   - Adjust based on API rate limits and response time

3. **Chunk Size**:
   - Smaller chunks = more precise search, but more API calls
   - Larger chunks = fewer embeddings, but less precise
   - Recommended: 400-800 characters

4. **Index Storage**:
   - JSON format (human-readable, portable)
   - Size estimate: ~1.5KB per chunk (embedding + text + metadata)
   - For large indexes, consider compression or database storage

## Limitations

1. **In-Memory Storage**: Current implementation loads entire index into memory
   - For large indexes (>10K chunks), consider database storage
   - Future: Add SQLite backend with vector search extension

2. **Single-File Persistence**: Index saved as single JSON file
   - For distributed systems, consider separate chunk storage
   - Future: Add incremental updates

3. **GigaChat Only**: Currently uses only GigaChat embeddings API
   - Future: Add support for HuggingFace and other providers

4. **No GPU Acceleration**: Similarity search uses CPU
   - For large-scale search, consider FAISS or similar libraries

## Future Enhancements

1. **Database Backend**: SQLite with vector extension for large indexes
2. **Incremental Updates**: Update index without full reindexing
3. **Multi-Model Support**: Support for different embedding models
4. **Advanced Search**: Hybrid search (semantic + keyword), filtering
5. **UI Integration**: Add indexing UI to main chat application
6. **Scheduled Indexing**: Automatic re-indexing on file changes
7. **Compression**: Compress embeddings for storage efficiency
8. **Distributed Storage**: Split index across multiple files/nodes

## Example: Programmatic Usage

```kotlin
// Setup
val gigaChatApi = GigaChatApiImpl(httpClient)
val fileSystem = FileSystem()
val documentLoader = DocumentLoaderImpl(fileSystem)
val textChunker = TextChunkerImpl(ChunkConfig())
val embeddingService = EmbeddingServiceImpl(
    gigaChatApi, clientId, clientSecret
)
val vectorStore = VectorStoreImpl(fileSystem)
val indexer = DocumentIndexerImpl(
    documentLoader, textChunker, embeddingService, vectorStore
)

// Index documents
val result = indexer.indexDirectory(
    directoryPath = "./docs",
    extensions = listOf("md", "txt")
) { progress, message ->
    println("Progress: ${(progress * 100).toInt()}% - $message")
}

// Save index
vectorStore.save("./index.json")

// Search
val results = indexer.search("GigaChat embeddings API", topK = 5)
results.forEach { result ->
    println("${result.chunk.metadata.sourceFile}")
    println("Similarity: ${result.similarity}")
    println("Text: ${result.chunk.text.take(100)}...")
    println()
}

// Statistics
val stats = indexer.getStats()
println("Total chunks: ${stats.totalChunks}")
println("Total documents: ${stats.totalDocuments}")
println("Index size: ${stats.indexSizeBytes / 1024} KB")
```

## Testing

Run tests with:

```bash
# Quick test with current directory
./test-indexing.sh

# Manual testing
export GIGACHAT_CLIENT_ID="..."
export GIGACHAT_CLIENT_SECRET="..."

# Index
./gradlew :shared:runIndexing --args="index . ./test-index.json md"

# Search
./gradlew :shared:runIndexing --args="search ./test-index.json 'embeddings' 3"

# Stats
./gradlew :shared:runIndexing --args="stats ./test-index.json"
```

## Troubleshooting

### Authentication Errors

```
❌ Error: GIGACHAT_CLIENT_ID not set
```

**Solution**: Set environment variables or create `local.properties`:
```bash
export GIGACHAT_CLIENT_ID="your_id"
export GIGACHAT_CLIENT_SECRET="your_secret"
```

### Out of Memory

```
java.lang.OutOfMemoryError: Java heap space
```

**Solution**: Increase heap size:
```bash
export GRADLE_OPTS="-Xmx4g"
./gradlew :shared:runIndexing --args="..."
```

### File Not Found

```
❌ Error: File not found: /path/to/file
```

**Solution**: Check file path is absolute or relative to project root

### Index Load Failure

```
❌ Error: Index file not found
```

**Solution**: Run indexing first to create the index file

## Summary

The document indexing system provides a complete, production-ready pipeline for:
- ✅ Loading documents from multiple sources
- ✅ Smart text chunking with overlap
- ✅ Generating embeddings via GigaChat API
- ✅ Efficient similarity search
- ✅ JSON-based persistence
- ✅ CLI tool for testing
- ✅ Cross-platform support (Android + Desktop)

Total implementation: ~2000 lines of Kotlin code across 20+ files.
