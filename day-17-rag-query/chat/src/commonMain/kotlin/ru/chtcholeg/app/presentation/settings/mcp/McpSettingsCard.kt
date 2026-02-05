package ru.chtcholeg.app.presentation.settings.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ru.chtcholeg.app.data.repository.McpRepository
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.app.presentation.theme.chatColors

/**
 * Expandable MCP settings card with add server functionality.
 */
@Composable
fun McpSettingsCard(
    onNavigateToManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mcpRepository: McpRepository = koinInject()
    val colors = chatColors()
    val scope = rememberCoroutineScope()

    var isExpanded by remember { mutableStateOf(false) }
    var servers by remember { mutableStateOf<List<McpServer>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<McpServer?>(null) }

    // Load servers
    LaunchedEffect(Unit) {
        mcpRepository.getAllServers().collect { serverList ->
            servers = serverList
        }
    }

    val serverCount = servers.size
    val enabledCount = servers.count { it.enabled }
    val connectedCount = servers.count {
        mcpRepository.getServerStatus(it.id) == ConnectionStatus.CONNECTED
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = colors.divider,
                spotColor = colors.divider
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MCP Servers",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.headerText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (serverCount > 0) {
                            "$serverCount configured, $connectedCount connected"
                        } else {
                            "No servers configured"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = colors.primaryAccent
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
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Status summary
                    if (serverCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusItem(
                                label = "Total",
                                count = serverCount,
                                color = colors.headerText.copy(alpha = 0.6f)
                            )
                            StatusItem(
                                label = "Enabled",
                                count = enabledCount,
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatusItem(
                                label = "Connected",
                                count = connectedCount,
                                color = Color(0xFF4CAF50)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = colors.divider)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Server list
                        servers.forEach { server ->
                            ServerListItem(
                                server = server,
                                status = mcpRepository.getServerStatus(server.id),
                                onEdit = {
                                    serverToEdit = server
                                    showAddDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Add Server Button
                    Button(
                        onClick = {
                            serverToEdit = null
                            showAddDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryAccent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Server")
                    }

                    // Link to full management
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onNavigateToManagement,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Full Management")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // Add/Edit Server Dialog
    if (showAddDialog) {
        McpServerDialog(
            server = serverToEdit,
            onDismiss = {
                showAddDialog = false
                serverToEdit = null
            },
            onSave = { newServer ->
                scope.launch {
                    if (serverToEdit == null) {
                        mcpRepository.addServer(newServer)
                    } else {
                        mcpRepository.updateServer(newServer)
                    }
                    showAddDialog = false
                    serverToEdit = null
                }
            }
        )
    }
}

@Composable
private fun StatusItem(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ServerListItem(
    server: McpServer,
    status: ConnectionStatus,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(
            containerColor = colors.background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.headerText
                    )

                    // Status indicator
                    val (statusColor, statusText) = when (status) {
                        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50) to "Connected"
                        ConnectionStatus.CONNECTING -> Color(0xFFFFA726) to "Connecting"
                        ConnectionStatus.DISCONNECTED -> colors.headerText.copy(alpha = 0.4f) to "Disconnected"
                        ConnectionStatus.ERROR -> Color(0xFFF44336) to "Error"
                    }

                    Surface(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = server.type.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.headerText.copy(alpha = 0.6f)
                    )

                    if (!server.enabled) {
                        Text(
                            text = "• Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.headerText.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit server",
                tint = colors.headerText.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
