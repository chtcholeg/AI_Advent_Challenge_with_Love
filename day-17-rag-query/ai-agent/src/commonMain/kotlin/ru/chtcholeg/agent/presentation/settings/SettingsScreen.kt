package ru.chtcholeg.agent.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ru.chtcholeg.agent.data.repository.McpRepository
import ru.chtcholeg.agent.data.repository.RagRepository
import ru.chtcholeg.agent.data.repository.SettingsRepository
import ru.chtcholeg.agent.domain.model.AiSettings
import ru.chtcholeg.agent.domain.model.RagMode
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpTool
import ru.chtcholeg.shared.domain.model.Model

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: SettingsRepository = koinInject(),
    mcpRepository: McpRepository = koinInject()
) {
    val settings by settingsRepository.settings.collectAsState()
    var showAddServerDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val servers by mcpRepository.servers.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Model Section (Expandable)
            item {
                ExpandableAiModelCard(
                    settings = settings,
                    onSettingsChange = { newSettings ->
                        settingsRepository.updateSettings(newSettings)
                    }
                )
            }

            // RAG Section (Expandable)
            item {
                ExpandableRagCard(
                    settings = settings,
                    onSettingsChange = { newSettings ->
                        settingsRepository.updateSettings(newSettings)
                    }
                )
            }

            // MCP Servers Section (Expandable)
            item {
                ExpandableMcpServersCard(
                    servers = servers,
                    onAddServer = { showAddServerDialog = true },
                    onDeleteServer = { server ->
                        coroutineScope.launch {
                            mcpRepository.deleteServer(server.id)
                        }
                    },
                    onToggleEnabled = { server ->
                        coroutineScope.launch {
                            mcpRepository.updateServer(server.copy(enabled = !server.enabled))
                        }
                    }
                )
            }
        }
    }

    if (showAddServerDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddServerDialog = false },
            onAdd = { server ->
                coroutineScope.launch {
                    mcpRepository.addServer(server)
                }
                showAddServerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandableAiModelCard(
    settings: AiSettings,
    onSettingsChange: (AiSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Model",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${settings.model.displayName} (${settings.model.api.name})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Model selector
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = settings.model.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            Model.ALL_MODELS.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName)
                                            Text(
                                                text = "Provider: ${model.api.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSettingsChange(settings.copy(model = model))
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Temperature
                    Text(
                        text = "Temperature: ${"%.1f".format(settings.temperature)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.temperature,
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(temperature = value))
                        },
                        valueRange = 0f..2f,
                        steps = 19
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Top P
                    Text(
                        text = "Top P: ${"%.1f".format(settings.topP)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.topP,
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(topP = value))
                        },
                        valueRange = 0f..1f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Max Tokens
                    Text(
                        text = "Max Tokens: ${settings.maxTokens}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.maxTokens.toFloat(),
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(maxTokens = value.toInt()))
                        },
                        valueRange = 256f..8192f,
                        steps = 30
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show System Messages Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show System Messages",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Tool calls and results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.showSystemMessages,
                            onCheckedChange = { checked ->
                                onSettingsChange(settings.copy(showSystemMessages = checked))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableRagCard(
    settings: AiSettings,
    onSettingsChange: (AiSettings) -> Unit,
    modifier: Modifier = Modifier,
    ragRepository: RagRepository = koinInject()
) {
    var isExpanded by remember { mutableStateOf(false) }
    var chunkCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(settings.ragMode, settings.indexPath) {
        chunkCount = null
        if (settings.ragMode == RagMode.ON && settings.indexPath.isNotBlank()) {
            try {
                ragRepository.loadIndex(settings.indexPath)
                chunkCount = ragRepository.getStats().totalChunks
            } catch (_: Exception) { }
        }
    }

    val subtitle = when {
        settings.ragMode != RagMode.ON -> "Disabled"
        chunkCount != null -> "$chunkCount chunks · ${settings.indexPath}"
        settings.indexPath.isNotBlank() -> settings.indexPath
        else -> "No path set"
    }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RAG (Document Context)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // RAG on/off toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable RAG",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Inject relevant document chunks into prompt context",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.ragMode == RagMode.ON,
                            onCheckedChange = { checked ->
                                onSettingsChange(settings.copy(ragMode = if (checked) RagMode.ON else RagMode.OFF))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Index file path
                    OutlinedTextField(
                        value = settings.indexPath,
                        onValueChange = { value ->
                            onSettingsChange(settings.copy(indexPath = value))
                        },
                        label = { Text("Indexer database path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Path to .db file. Indexer GUI default: ~/.indexer/index.db",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableMcpServersCard(
    servers: List<McpServer>,
    onAddServer: () -> Unit,
    onDeleteServer: (McpServer) -> Unit,
    onToggleEnabled: (McpServer) -> Unit,
    modifier: Modifier = Modifier,
    mcpRepository: McpRepository = koinInject()
) {
    var isExpanded by remember { mutableStateOf(false) }
    val connectedCount = servers.count { it.status == ConnectionStatus.CONNECTED }
    val coroutineScope = rememberCoroutineScope()

    // Cache for server tools
    var serverToolsCache by remember { mutableStateOf<Map<String, List<McpTool>>>(emptyMap()) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MCP Servers",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (servers.isNotEmpty()) {
                            "${servers.size} configured, $connectedCount connected"
                        } else {
                            "No servers configured"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Server list
                    servers.forEach { server ->
                        McpServerCard(
                            server = server,
                            tools = serverToolsCache[server.id] ?: emptyList(),
                            onDelete = { onDeleteServer(server) },
                            onToggleEnabled = { onToggleEnabled(server) },
                            onLoadTools = {
                                coroutineScope.launch {
                                    val tools = mcpRepository.getServerTools(server.id)
                                    serverToolsCache = serverToolsCache + (server.id to tools)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (servers.isEmpty()) {
                        Text(
                            text = "No MCP servers configured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Add Server Button
                    Button(
                        onClick = onAddServer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Server")
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
    tools: List<McpTool>,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onLoadTools: () -> Unit
) {
    var isToolsExpanded by remember { mutableStateOf(false) }
    var toolsLoaded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = server.type.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Status: ${server.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (server.status) {
                            ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                            ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row {
                    Switch(
                        checked = server.enabled,
                        onCheckedChange = { onToggleEnabled() }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            }

            // Tools section (only for connected servers)
            if (server.status == ConnectionStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))

                // Tools header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!toolsLoaded) {
                                onLoadTools()
                                toolsLoaded = true
                            }
                            isToolsExpanded = !isToolsExpanded
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tools.isEmpty() && !toolsLoaded) {
                            "Tools"
                        } else {
                            "Tools (${tools.size})"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isToolsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isToolsExpanded) "Hide tools" else "Show tools",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Tools list
                AnimatedVisibility(
                    visible = isToolsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (tools.isEmpty()) {
                            Text(
                                text = "Loading tools...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            tools.forEach { tool ->
                                ToolItem(tool = tool)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItem(tool: McpTool) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (tool.description.isNotEmpty()) {
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
