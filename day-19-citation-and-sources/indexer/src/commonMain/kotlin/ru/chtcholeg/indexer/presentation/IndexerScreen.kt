package ru.chtcholeg.indexer.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.chtcholeg.indexer.domain.model.IndexedFile
import ru.chtcholeg.indexer.domain.model.SearchResult

// Design system constants
private object DesignSystem {
    val ButtonHeight = 40.dp
    val TextFieldHeight = 56.dp
    val CardPadding = 16.dp
    val IconSize = 24.dp
    val SmallIconSize = 20.dp
    val CornerRadius = 12.dp
    val SmallCornerRadius = 8.dp
    val StandardSpacing = 16.dp
    val SmallSpacing = 8.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexerScreen(
    store: IndexerStore,
    onPickDirectory: () -> String?
) {
    val state by store.state.collectAsState()

    DisposableEffect(store) {
        onDispose { store.onDispose() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Document Indexer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    EmbeddingStatusIndicator(
                        embeddingModel = state.embeddingModel,
                        isOllamaAvailable = state.isOllamaAvailable,
                        isCheckingOllama = state.isCheckingOllama,
                        gigaChatReady = state.gigaChatReady,
                        onRefreshOllama = { store.processIntent(IndexerIntent.CheckOllamaStatus) }
                    )
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = remember { SnackbarHostState() })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(DesignSystem.StandardSpacing)
        ) {
            // Error message
            state.error?.let { error ->
                ErrorCard(
                    message = error,
                    onDismiss = { store.processIntent(IndexerIntent.DismissError) }
                )
                Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))
            }

            // Path selection section
            PathSelectionSection(
                selectedPath = state.selectedPath,
                isIndexing = state.isIndexing,
                embeddingModel = state.embeddingModel,
                inputMode = state.inputMode,
                isOllamaAvailable = state.isOllamaAvailable,
                gigaChatReady = state.gigaChatReady,
                onPathChange = { store.processIntent(IndexerIntent.SelectPath(it)) },
                onBrowse = {
                    onPickDirectory()?.let { path ->
                        store.processIntent(IndexerIntent.SelectPath(path))
                    }
                },
                onIndex = { store.processIntent(IndexerIntent.StartIndexing) },
                onCancel = { store.processIntent(IndexerIntent.CancelIndexing) },
                onModelChange = { store.processIntent(IndexerIntent.SelectEmbeddingModel(it)) },
                onInputModeChange = { store.processIntent(IndexerIntent.SelectInputMode(it)) }
            )

            Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))

            // Indexing progress
            AnimatedVisibility(
                visible = state.indexingProgress != null && state.isIndexing,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                state.indexingProgress?.let { progress ->
                    IndexingProgressCard(progress = progress)
                    Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))
                }
            }

            // Search bar
            SearchBar(
                query = state.searchQuery,
                isSearching = state.isSearching,
                onQueryChange = { store.processIntent(IndexerIntent.Search(it)) },
                onClear = { store.processIntent(IndexerIntent.ClearSearch) }
            )

            Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))

            // Stats
            StatsRow(
                fileCount = state.fileCount,
                chunkCount = state.chunkCount
            )

            Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))

            // Content - either search results or file list
            if (state.searchQuery.isNotBlank()) {
                SearchResultsList(
                    results = state.searchResults,
                    expandedChunkIds = state.expandedChunkIds,
                    onToggleExpand = { store.processIntent(IndexerIntent.ToggleChunkExpansion(it)) }
                )
            } else {
                IndexedFilesList(
                    files = state.indexedFiles,
                    onDeleteFile = { store.processIntent(IndexerIntent.RemoveFile(it)) }
                )
            }
        }
    }
}

@Composable
private fun EmbeddingStatusIndicator(
    embeddingModel: EmbeddingModelType,
    isOllamaAvailable: Boolean,
    isCheckingOllama: Boolean,
    gigaChatReady: Boolean,
    onRefreshOllama: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = DesignSystem.SmallSpacing)
    ) {
        when (embeddingModel) {
            EmbeddingModelType.OLLAMA -> {
                if (isCheckingOllama) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DesignSystem.SmallIconSize),
                        strokeWidth = 2.dp,
                        color = Color(0xFF2196F3)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isOllamaAvailable) Color(0xFF2196F3) else MaterialTheme.colorScheme.error)
                    )
                }
                Spacer(modifier = Modifier.width(DesignSystem.SmallSpacing))
                Text(
                    text = if (isOllamaAvailable) "Ollama" else "Ollama offline",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onRefreshOllama) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Ollama status",
                        modifier = Modifier.size(DesignSystem.SmallIconSize)
                    )
                }
            }
            EmbeddingModelType.GIGACHAT -> {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (gigaChatReady) Color(0xFF2196F3) else MaterialTheme.colorScheme.error)
                )
                Spacer(modifier = Modifier.width(DesignSystem.SmallSpacing))
                Text(
                    text = if (gigaChatReady) "GigaChat" else "GigaChat: no credentials",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignSystem.CornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignSystem.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(DesignSystem.IconSize)
            )
            Spacer(modifier = Modifier.width(DesignSystem.SmallSpacing))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(onClick = {
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(java.awt.datatransfer.StringSelection(message), null)
                copied = true
                scope.launch {
                    delay(1500)
                    copied = false
                }
            }) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.Share,
                    contentDescription = if (copied) "Copied" else "Copy error",
                    tint = if (copied) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(DesignSystem.IconSize)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(DesignSystem.IconSize)
                )
            }
        }
    }
}

@Composable
private fun PathSelectionSection(
    selectedPath: String,
    isIndexing: Boolean,
    embeddingModel: EmbeddingModelType,
    inputMode: InputMode,
    isOllamaAvailable: Boolean,
    gigaChatReady: Boolean,
    onPathChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onIndex: () -> Unit,
    onCancel: () -> Unit,
    onModelChange: (EmbeddingModelType) -> Unit,
    onInputModeChange: (InputMode) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    val isServiceReady = when (embeddingModel) {
        EmbeddingModelType.OLLAMA -> isOllamaAvailable
        EmbeddingModelType.GIGACHAT -> gigaChatReady
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignSystem.CornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Collapsible header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(DesignSystem.CardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Index Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(DesignSystem.IconSize)
                )
            }

            // Collapsible content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignSystem.CardPadding)
                        .padding(bottom = DesignSystem.CardPadding)
                ) {
                    // --- Embedding model selector ---
            Text(
                text = "Embedding model",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))
            Row(horizontalArrangement = Arrangement.spacedBy(DesignSystem.SmallSpacing)) {
                FilterChip(
                    selected = embeddingModel == EmbeddingModelType.OLLAMA,
                    onClick = { onModelChange(EmbeddingModelType.OLLAMA) },
                    enabled = !isIndexing,
                    label = { Text("Ollama (768-dim)") }
                )
                FilterChip(
                    selected = embeddingModel == EmbeddingModelType.GIGACHAT,
                    onClick = { onModelChange(EmbeddingModelType.GIGACHAT) },
                    enabled = !isIndexing,
                    label = { Text("GigaChat (1024-dim)") }
                )
            }

            Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))

            // --- Input mode selector ---
            Text(
                text = "Source type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))
            Row(horizontalArrangement = Arrangement.spacedBy(DesignSystem.SmallSpacing)) {
                FilterChip(
                    selected = inputMode == InputMode.FILE,
                    onClick = { onInputModeChange(InputMode.FILE) },
                    enabled = !isIndexing,
                    label = { Text("File / Directory") },
                    leadingIcon = if (inputMode == InputMode.FILE) {
                        { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = inputMode == InputMode.URL,
                    onClick = { onInputModeChange(InputMode.URL) },
                    enabled = !isIndexing,
                    label = { Text("Web Page (URL)") },
                    leadingIcon = if (inputMode == InputMode.URL) {
                        { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))

            // --- Path/URL input ---
            if (inputMode == InputMode.FILE) {
                OutlinedTextField(
                    value = selectedPath,
                    onValueChange = onPathChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    label = { Text("Path to file or directory") },
                    placeholder = { Text("/path/to/documents") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    trailingIcon = {
                        Button(
                            onClick = onBrowse,
                            enabled = !isIndexing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(DesignSystem.CornerRadius),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(DesignSystem.ButtonHeight)
                                .width(70.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(
                                text = "...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !isIndexing,
                    shape = RoundedCornerShape(DesignSystem.CornerRadius)
                )
            } else {
                OutlinedTextField(
                    value = selectedPath,
                    onValueChange = onPathChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    label = { Text("Web page URL") },
                    placeholder = { Text("https://example.com/article") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(DesignSystem.IconSize)
                        )
                    },
                    singleLine = true,
                    enabled = !isIndexing,
                    shape = RoundedCornerShape(DesignSystem.CornerRadius)
                )
            }

            Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))

            Row {
                if (isIndexing) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(DesignSystem.ButtonHeight),
                        shape = RoundedCornerShape(DesignSystem.CornerRadius),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(DesignSystem.IconSize)
                        )
                        Spacer(modifier = Modifier.width(DesignSystem.SmallSpacing))
                        Text("Cancel")
                    }
                } else {
                    Button(
                        onClick = onIndex,
                        enabled = selectedPath.isNotBlank() && isServiceReady,
                        modifier = Modifier.height(DesignSystem.ButtonHeight),
                        shape = RoundedCornerShape(DesignSystem.CornerRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(DesignSystem.IconSize)
                        )
                        Spacer(modifier = Modifier.width(DesignSystem.SmallSpacing))
                        Text("Start Indexing")
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun IndexingProgressCard(progress: IndexingProgress) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignSystem.CornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignSystem.CardPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!progress.isComplete) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(DesignSystem.SmallIconSize),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(DesignSystem.StandardSpacing))
                }
                Text(
                    text = if (progress.isComplete) "Indexing Complete" else "Indexing...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))

            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))

            Text(
                text = "${progress.currentFileIndex}/${progress.totalFiles} files (${progress.progressPercent}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))

            Text(
                text = progress.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        label = { Text("Search indexed documents") },
        placeholder = { Text("Enter search query...") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(DesignSystem.IconSize)
            )
        },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(DesignSystem.SmallIconSize),
                    strokeWidth = 2.dp
                )
            } else if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(DesignSystem.IconSize)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(DesignSystem.CornerRadius)
    )
}

@Composable
private fun StatsRow(
    fileCount: Long,
    chunkCount: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatChip(
            label = "Files",
            value = fileCount.toString()
        )
        StatChip(
            label = "Chunks",
            value = chunkCount.toString()
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(DesignSystem.CornerRadius),
        color = Color(0xFF2196F3) // Blue accent color
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignSystem.StandardSpacing,
                vertical = DesignSystem.SmallSpacing
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(DesignSystem.SmallSpacing))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    expandedChunkIds: Set<Long>,
    onToggleExpand: (Long) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))
                Text(
                    text = "No results found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(DesignSystem.SmallSpacing)
        ) {
            items(results) { result ->
                SearchResultItem(
                    result = result,
                    isExpanded = result.chunk.id in expandedChunkIds,
                    onToggleExpand = { onToggleExpand(result.chunk.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val isUrl = result.file.filePath.startsWith("http://") || result.file.filePath.startsWith("https://")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(DesignSystem.CornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignSystem.CardPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUrl) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Web page",
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = result.file.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Chunk ${result.chunk.chunkIndex + 1}/${result.chunk.totalChunks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Similarity badge
                Surface(
                    shape = RoundedCornerShape(DesignSystem.SmallCornerRadius),
                    color = when {
                        result.similarityPercent >= 80 -> MaterialTheme.colorScheme.primary
                        result.similarityPercent >= 60 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                ) {
                    Text(
                        text = "${result.similarityPercent}%",
                        modifier = Modifier.padding(
                            horizontal = DesignSystem.StandardSpacing,
                            vertical = DesignSystem.SmallSpacing
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .padding(start = DesignSystem.SmallSpacing)
                        .size(DesignSystem.IconSize)
                )
            }

            // Preview (always visible)
            Text(
                text = result.chunk.text.take(150) + if (result.chunk.text.length > 150) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = DesignSystem.SmallSpacing),
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = DesignSystem.SmallSpacing)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))

                    Text(
                        text = "Full Text:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.chunk.text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = DesignSystem.SmallSpacing)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(DesignSystem.SmallCornerRadius)
                            )
                            .padding(DesignSystem.StandardSpacing)
                    )

                    Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))

                    Text(
                        text = "File: ${result.file.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexedFilesList(
    files: List<IndexedFile>,
    onDeleteFile: (Long) -> Unit
) {
    if (files.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(DesignSystem.StandardSpacing))
                Text(
                    text = "No indexed files",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(DesignSystem.SmallSpacing))
                Text(
                    text = "Select a directory and click 'Start Indexing'",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(DesignSystem.SmallSpacing)
        ) {
            items(files) { file ->
                FileListItem(
                    file = file,
                    onDelete = { onDeleteFile(file.id) }
                )
            }
        }
    }
}

@Composable
private fun FileListItem(
    file: IndexedFile,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isUrl = file.filePath.startsWith("http://") || file.filePath.startsWith("https://")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignSystem.CornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignSystem.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isUrl) Icons.Default.Share else Icons.Default.Info,
                contentDescription = if (isUrl) "Web page" else "File",
                tint = if (isUrl) Color(0xFF2196F3) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(DesignSystem.IconSize)
            )

            Spacer(modifier = Modifier.width(DesignSystem.StandardSpacing))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = file.filePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignSystem.SmallSpacing)
                ) {
                    Text(
                        text = "${file.chunkCount} chunks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(file.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(file.indexedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showDeleteConfirm) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignSystem.SmallSpacing)
                ) {
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        shape = RoundedCornerShape(DesignSystem.SmallCornerRadius)
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            onDelete()
                            showDeleteConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(DesignSystem.SmallCornerRadius)
                    ) {
                        Text("Delete")
                    }
                }
            } else {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(DesignSystem.IconSize)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

private fun formatTimestamp(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
