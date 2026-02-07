package ru.chtcholeg.indexer.presentation

import ru.chtcholeg.indexer.domain.model.IndexedFile
import ru.chtcholeg.indexer.domain.model.SearchResult

/** Available embedding backends */
enum class EmbeddingModelType { OLLAMA, GIGACHAT }

/** Input mode for indexing */
enum class InputMode { FILE, URL }

/**
 * UI state for the indexer screen
 */
data class IndexerState(
    /** Selected embedding model */
    val embeddingModel: EmbeddingModelType = EmbeddingModelType.OLLAMA,

    /** Whether GigaChat credentials are available (from build config) */
    val gigaChatReady: Boolean = false,

    /** Input mode: FILE or URL */
    val inputMode: InputMode = InputMode.FILE,

    /**
     * Selected path or URL for indexing
     */
    val selectedPath: String = "",

    /**
     * Whether indexing is in progress
     */
    val isIndexing: Boolean = false,

    /**
     * Current indexing progress
     */
    val indexingProgress: IndexingProgress? = null,

    /**
     * List of indexed files
     */
    val indexedFiles: List<IndexedFile> = emptyList(),

    /**
     * Current search query
     */
    val searchQuery: String = "",

    /**
     * Search results
     */
    val searchResults: List<SearchResult> = emptyList(),

    /**
     * Whether search is in progress
     */
    val isSearching: Boolean = false,

    /**
     * IDs of expanded chunks in search results
     */
    val expandedChunkIds: Set<Long> = emptySet(),

    /**
     * Whether Ollama server is available
     */
    val isOllamaAvailable: Boolean = false,

    /**
     * Whether checking Ollama status
     */
    val isCheckingOllama: Boolean = true,

    /**
     * Error message to display
     */
    val error: String? = null,

    /**
     * Statistics
     */
    val fileCount: Long = 0,
    val chunkCount: Long = 0
)

/**
 * Progress information during indexing
 */
data class IndexingProgress(
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val status: String = "",
    val isComplete: Boolean = false
) {
    val progress: Float
        get() = if (totalFiles > 0) currentFileIndex.toFloat() / totalFiles else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()
}
