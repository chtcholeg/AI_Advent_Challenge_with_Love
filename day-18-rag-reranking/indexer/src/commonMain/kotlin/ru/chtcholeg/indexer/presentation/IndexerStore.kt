package ru.chtcholeg.indexer.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException
import ru.chtcholeg.indexer.domain.service.DocumentIndexerService
import ru.chtcholeg.indexer.domain.service.IndexingEvent
import ru.chtcholeg.indexer.domain.service.OllamaEmbeddingService
import ru.chtcholeg.indexer.BuildKonfig
import ru.chtcholeg.shared.data.api.GigaChatApi
import ru.chtcholeg.shared.domain.service.EmbeddingServiceImpl

/**
 * MVI Store for managing indexer state
 */
class IndexerStore(
    private val indexerService: DocumentIndexerService,
    private val ollamaEmbeddingService: OllamaEmbeddingService,
    private val gigaChatApi: GigaChatApi
) {
    /** GigaChat embedding service, initialized from build-time credentials */
    private val gigaChatEmbeddingService: EmbeddingServiceImpl? =
        if (BuildKonfig.GIGACHAT_CLIENT_ID.isNotBlank() && BuildKonfig.GIGACHAT_CLIENT_SECRET.isNotBlank()) {
            EmbeddingServiceImpl(gigaChatApi, BuildKonfig.GIGACHAT_CLIENT_ID, BuildKonfig.GIGACHAT_CLIENT_SECRET)
        } else null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(IndexerState(gigaChatReady = gigaChatEmbeddingService != null))
    val state: StateFlow<IndexerState> = _state.asStateFlow()

    private var indexingJob: Job? = null
    private var searchJob: Job? = null

    companion object {
        /** Debounce delay for search in milliseconds */
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    init {
        // Check Ollama status on init
        processIntent(IndexerIntent.CheckOllamaStatus)
        // Load initial files
        processIntent(IndexerIntent.RefreshFiles)
    }

    /**
     * Process user intent
     */
    fun processIntent(intent: IndexerIntent) {
        when (intent) {
            is IndexerIntent.SelectPath -> handleSelectPath(intent.path)
            is IndexerIntent.StartIndexing -> handleStartIndexing()
            is IndexerIntent.CancelIndexing -> handleCancelIndexing()
            is IndexerIntent.Search -> handleSearch(intent.query)
            is IndexerIntent.ClearSearch -> handleClearSearch()
            is IndexerIntent.ToggleChunkExpansion -> handleToggleChunk(intent.chunkId)
            is IndexerIntent.RemoveFile -> handleRemoveFile(intent.fileId)
            is IndexerIntent.RefreshFiles -> handleRefreshFiles()
            is IndexerIntent.CheckOllamaStatus -> handleCheckOllamaStatus()
            is IndexerIntent.DismissError -> handleDismissError()
            is IndexerIntent.SelectEmbeddingModel -> handleSelectEmbeddingModel(intent.model)
            is IndexerIntent.SelectInputMode -> handleSelectInputMode(intent.mode)
        }
    }

    private fun handleSelectPath(path: String) {
        _state.update { it.copy(selectedPath = path) }
    }

    private fun handleStartIndexing() {
        val path = _state.value.selectedPath
        if (path.isBlank()) {
            _state.update { it.copy(error = "Please select a path to index") }
            return
        }

        val currentState = _state.value
        when (currentState.embeddingModel) {
            EmbeddingModelType.OLLAMA -> {
                if (!currentState.isOllamaAvailable) {
                    _state.update { it.copy(error = "Ollama is not available. Please start Ollama first.") }
                    return
                }
            }
            EmbeddingModelType.GIGACHAT -> {
                if (gigaChatEmbeddingService == null) {
                    _state.update { it.copy(error = "GigaChat credentials are not configured in local.properties.") }
                    return
                }
            }
        }

        val currentInputMode = _state.value.inputMode

        indexingJob = scope.launch {
            _state.update {
                it.copy(
                    isIndexing = true,
                    indexingProgress = IndexingProgress(),
                    error = null
                )
            }

            val indexingFlow = when (currentInputMode) {
                InputMode.FILE -> indexerService.indexDocuments(path)
                InputMode.URL -> indexerService.indexUrl(path)
            }

            indexingFlow
                .catch { e ->
                    _state.update {
                        it.copy(
                            isIndexing = false,
                            error = "Indexing failed: ${e.message}"
                        )
                    }
                }
                .collect { event ->
                    handleIndexingEvent(event)
                }
        }
    }

    private fun handleIndexingEvent(event: IndexingEvent) {
        when (event) {
            is IndexingEvent.Started -> {
                _state.update {
                    it.copy(
                        indexingProgress = IndexingProgress(
                            totalFiles = event.totalFiles,
                            status = "Starting..."
                        )
                    )
                }
            }
            is IndexingEvent.FileStarted -> {
                _state.update {
                    it.copy(
                        indexingProgress = it.indexingProgress?.copy(
                            currentFileName = event.fileName,
                            currentFileIndex = event.fileIndex,
                            status = "Processing ${event.fileName}..."
                        )
                    )
                }
            }
            is IndexingEvent.ChunksCreated -> {
                _state.update {
                    it.copy(
                        indexingProgress = it.indexingProgress?.copy(
                            status = "Created ${event.chunkCount} chunks for ${event.fileName}"
                        )
                    )
                }
            }
            is IndexingEvent.EmbeddingsGenerated -> {
                _state.update {
                    it.copy(
                        indexingProgress = it.indexingProgress?.copy(
                            status = "Generated embeddings for ${event.fileName}"
                        )
                    )
                }
            }
            is IndexingEvent.FileCompleted -> {
                _state.update {
                    it.copy(
                        indexingProgress = it.indexingProgress?.copy(
                            currentFileIndex = event.fileIndex,
                            status = "Completed ${event.fileName}"
                        )
                    )
                }
            }
            is IndexingEvent.FileSkipped -> {
                _state.update {
                    it.copy(
                        indexingProgress = it.indexingProgress?.copy(
                            status = "Skipped ${event.fileName}: ${event.reason}"
                        )
                    )
                }
            }
            is IndexingEvent.Completed -> {
                _state.update {
                    it.copy(
                        isIndexing = false,
                        indexingProgress = it.indexingProgress?.copy(
                            isComplete = true,
                            status = "Completed! Indexed ${event.totalFiles} files, ${event.totalChunks} chunks"
                        )
                    )
                }
                // Refresh file list
                handleRefreshFiles()
            }
            is IndexingEvent.Error -> {
                _state.update {
                    it.copy(
                        isIndexing = false,
                        error = event.message
                    )
                }
            }
        }
    }

    private fun handleCancelIndexing() {
        indexingJob?.cancel()
        _state.update {
            it.copy(
                isIndexing = false,
                indexingProgress = null
            )
        }
    }

    private fun handleSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }

        // Cancel previous search
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
            return
        }

        searchJob = scope.launch {
            // Debounce: wait before starting search
            delay(SEARCH_DEBOUNCE_MS)

            _state.update { it.copy(isSearching = true) }

            try {
                val results = indexerService.search(query, topK = 10)
                _state.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: CancellationException) {
                // Ignore cancellation - this is normal when user types quickly
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        error = "Search failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun handleClearSearch() {
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                expandedChunkIds = emptySet()
            )
        }
    }

    private fun handleToggleChunk(chunkId: Long) {
        _state.update { state ->
            val newExpandedIds = if (chunkId in state.expandedChunkIds) {
                state.expandedChunkIds - chunkId
            } else {
                state.expandedChunkIds + chunkId
            }
            state.copy(expandedChunkIds = newExpandedIds)
        }
    }

    private fun handleRemoveFile(fileId: Long) {
        scope.launch {
            try {
                indexerService.deleteFile(fileId)
                handleRefreshFiles()
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to delete file: ${e.message}")
                }
            }
        }
    }

    private fun handleRefreshFiles() {
        scope.launch {
            try {
                val files = indexerService.getAllFiles()
                val stats = indexerService.getStats()
                _state.update {
                    it.copy(
                        indexedFiles = files,
                        fileCount = stats.fileCount,
                        chunkCount = stats.chunkCount
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to load files: ${e.message}")
                }
            }
        }
    }

    private fun handleCheckOllamaStatus() {
        if (_state.value.embeddingModel != EmbeddingModelType.OLLAMA) return
        scope.launch {
            _state.update { it.copy(isCheckingOllama = true) }
            try {
                val isAvailable = ollamaEmbeddingService.isAvailable()
                _state.update {
                    it.copy(
                        isOllamaAvailable = isAvailable,
                        isCheckingOllama = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isOllamaAvailable = false,
                        isCheckingOllama = false
                    )
                }
            }
        }
    }

    private fun handleSelectEmbeddingModel(model: EmbeddingModelType) {
        _state.update { it.copy(embeddingModel = model) }
        when (model) {
            EmbeddingModelType.OLLAMA -> {
                indexerService.embeddingService = ollamaEmbeddingService
                processIntent(IndexerIntent.CheckOllamaStatus)
            }
            EmbeddingModelType.GIGACHAT -> {
                gigaChatEmbeddingService?.let { indexerService.embeddingService = it }
            }
        }
    }

    private fun handleDismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun handleSelectInputMode(mode: InputMode) {
        _state.update { it.copy(inputMode = mode, selectedPath = "") }
    }

    fun onDispose() {
        indexingJob?.cancel()
        searchJob?.cancel()
        scope.cancel()
    }
}
