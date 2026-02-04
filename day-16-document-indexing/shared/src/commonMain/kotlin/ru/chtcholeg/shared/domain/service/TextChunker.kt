package ru.chtcholeg.shared.domain.service

/**
 * Service for splitting text into chunks for embedding generation
 */
interface TextChunker {
    /**
     * Split text into chunks based on the configured strategy
     * @param text The text to split
     * @param sourceFile The source file name for metadata
     * @return List of text chunks with metadata
     */
    fun chunk(text: String, sourceFile: String): List<TextChunk>
}

/**
 * Represents a text chunk before embedding generation
 */
data class TextChunk(
    val text: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val sourceFile: String
)

/**
 * Strategy for chunking text
 */
enum class ChunkStrategy {
    /**
     * Split by approximate token count (most accurate for LLMs)
     */
    BY_TOKENS,

    /**
     * Split by character count (simple and fast)
     */
    BY_CHARACTERS,

    /**
     * Split by sentences (preserves semantic boundaries)
     */
    BY_SENTENCES
}

/**
 * Configuration for text chunking
 */
data class ChunkConfig(
    val strategy: ChunkStrategy = ChunkStrategy.BY_CHARACTERS,
    val chunkSize: Int = 500,
    val overlapSize: Int = 50,
    /** Minimum chunk size - if last chunk is smaller, it will be merged with previous */
    val minChunkSize: Int = 200
)
