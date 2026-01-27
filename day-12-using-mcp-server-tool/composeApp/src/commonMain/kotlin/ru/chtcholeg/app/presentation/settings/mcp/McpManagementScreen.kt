package ru.chtcholeg.app.presentation.settings.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ru.chtcholeg.app.data.repository.McpRepository
import ru.chtcholeg.app.domain.model.ConnectionStatus
import ru.chtcholeg.app.domain.model.McpServer
import ru.chtcholeg.app.domain.model.McpServerConfig
import ru.chtcholeg.app.domain.model.McpServerType
import ru.chtcholeg.app.domain.model.McpTool
import ru.chtcholeg.app.presentation.theme.chatColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpManagementScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mcpRepository: McpRepository = koinInject()
    val colors = chatColors()
    val scope = rememberCoroutineScope()

    var servers by remember { mutableStateOf<List<McpServer>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<McpServer?>(null) }
    var serverToDelete by remember { mutableStateOf<McpServer?>(null) }
    var serverToViewTools by remember { mutableStateOf<McpServer?>(null) }
    var serverTools by remember { mutableStateOf<List<McpTool>>(emptyList()) }
    var isLoadingTools by remember { mutableStateOf(false) }

    // Load servers
    LaunchedEffect(Unit) {
        mcpRepository.getAllServers().collect { serverList ->
            servers = serverList.map { server ->
                server.copy(status = mcpRepository.getServerStatus(server.id))
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("MCP Servers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.headerBackground,
                    titleContentColor = colors.headerText,
                    navigationIconContentColor = colors.headerText
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background)
        ) {
            if (servers.isEmpty()) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            errorMessage = mcpRepository.getServerError(server.id),
                            onToggleEnabled = {
                                scope.launch {
                                    mcpRepository.toggleServerEnabled(server.id)
                                }
                            },
                            onEdit = { serverToEdit = server },
                            onDelete = { serverToDelete = server },
                            onConnect = {
                                scope.launch {
                                    mcpRepository.connectServer(server.id)
                                }
                            },
                            onDisconnect = {
                                scope.launch {
                                    mcpRepository.disconnectServer(server.id)
                                }
                            },
                            onViewTools = {
                                scope.launch {
                                    serverToViewTools = server
                                    isLoadingTools = true
                                    serverTools = mcpRepository.getServerTools(server.id)
                                    isLoadingTools = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || serverToEdit != null) {
        McpServerDialog(
            server = serverToEdit,
            onDismiss = {
                showAddDialog = false
                serverToEdit = null
            },
            onSave = { server ->
                scope.launch {
                    if (serverToEdit != null) {
                        mcpRepository.updateServer(server)
                    } else {
                        mcpRepository.addServer(server)
                    }
                    showAddDialog = false
                    serverToEdit = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete '${server.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            mcpRepository.deleteServer(server.id)
                            serverToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View Tools Dialog
    serverToViewTools?.let { server ->
        AlertDialog(
            onDismissRequest = {
                serverToViewTools = null
                serverTools = emptyList()
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tools: ${server.name}")
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoadingTools = true
                                serverTools = mcpRepository.refreshServerTools(server.id)
                                isLoadingTools = false
                            }
                        },
                        enabled = !isLoadingTools
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh tools",
                            tint = if (isLoadingTools)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isLoadingTools) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (serverTools.isEmpty()) {
                        Text(
                            text = "No tools available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.headerText.copy(alpha = 0.6f)
                        )
                    } else {
                        serverTools.forEachIndexed { index, tool ->
                            ToolItem(tool = tool)
                            if (index < serverTools.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = colors.divider
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        serverToViewTools = null
                        serverTools = emptyList()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ServerCard(
    server: McpServer,
    errorMessage: String?,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onViewTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.headerText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ConnectionStatusBadge(status = server.status)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${server.type.name} • ${getConfigSummary(server.config)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = server.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(modifier = Modifier.height(12.dp))

            // Error message display
            if (server.status == ConnectionStatus.ERROR && errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Connection Error",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.headerText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // First row: Connect/Disconnect, View Tools (when connected)
            if (server.status == ConnectionStatus.CONNECTED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect")
                    }
                    OutlinedButton(
                        onClick = onViewTools,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tools")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (server.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Second row: Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBadge(status: ConnectionStatus) {
    val (color, text) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50) to "Connected"
        ConnectionStatus.CONNECTING -> Color(0xFFFFC107) to "Connecting"
        ConnectionStatus.ERROR -> Color(0xFFF44336) to "Error"
        ConnectionStatus.DISCONNECTED -> Color.Gray to "Disconnected"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val colors = chatColors()

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = colors.headerText.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No MCP Servers",
            style = MaterialTheme.typography.titleLarge,
            color = colors.headerText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add an MCP server to connect external tools",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.headerText.copy(alpha = 0.6f)
        )
    }
}

private fun getConfigSummary(config: McpServerConfig): String {
    return when (config) {
        is McpServerConfig.StdioConfig -> config.command
        is McpServerConfig.HttpConfig -> config.url
    }
}

@Composable
private fun ToolItem(
    tool: McpTool,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.primaryAccent
            )
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.headerText
            )
        }
        if (tool.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.headerText.copy(alpha = 0.7f)
            )
        }
    }
}
