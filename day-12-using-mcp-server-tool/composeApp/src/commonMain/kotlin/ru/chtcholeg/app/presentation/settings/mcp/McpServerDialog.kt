package ru.chtcholeg.app.presentation.settings.mcp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chtcholeg.app.domain.model.McpServer
import ru.chtcholeg.app.domain.model.McpServerConfig
import ru.chtcholeg.app.domain.model.McpServerType
import ru.chtcholeg.app.presentation.theme.chatColors
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerDialog(
    server: McpServer?,
    onDismiss: () -> Unit,
    onSave: (McpServer) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = chatColors()
    val isEdit = server != null

    var name by remember { mutableStateOf(server?.name ?: "") }
    var serverType by remember { mutableStateOf(server?.type ?: McpServerType.STDIO) }

    // STDIO fields
    var stdioCommand by remember {
        mutableStateOf((server?.config as? McpServerConfig.StdioConfig)?.command ?: "")
    }
    var stdioArgs by remember {
        mutableStateOf((server?.config as? McpServerConfig.StdioConfig)?.args?.joinToString(" ") ?: "")
    }

    // HTTP fields
    var httpUrl by remember {
        mutableStateOf((server?.config as? McpServerConfig.HttpConfig)?.url ?: "")
    }
    var httpToken by remember {
        mutableStateOf((server?.config as? McpServerConfig.HttpConfig)?.authToken ?: "")
    }

    var nameError by remember { mutableStateOf(false) }
    var configError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 400.dp, max = 600.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = colors.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = if (isEdit) "Edit MCP Server" else "Add MCP Server",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.headerText
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Server Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = false
                        },
                        label = { Text("Server Name") },
                        placeholder = { Text("My MCP Server") },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Server name is required") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Server Type Selector
                    Text(
                        text = "Server Type",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.headerText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = serverType == McpServerType.STDIO,
                            onClick = { serverType = McpServerType.STDIO },
                            label = { Text("Local (stdio)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = serverType == McpServerType.HTTP,
                            onClick = { serverType = McpServerType.HTTP },
                            label = { Text("Remote (HTTP)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Configuration fields based on type
                    when (serverType) {
                        McpServerType.STDIO -> {
                            StdioConfigFields(
                                command = stdioCommand,
                                onCommandChange = {
                                    stdioCommand = it
                                    configError = false
                                },
                                args = stdioArgs,
                                onArgsChange = { stdioArgs = it },
                                commandError = configError
                            )
                        }
                        McpServerType.HTTP -> {
                            HttpConfigFields(
                                url = httpUrl,
                                onUrlChange = {
                                    httpUrl = it
                                    configError = false
                                },
                                token = httpToken,
                                onTokenChange = { httpToken = it },
                                urlError = configError
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Validation
                            if (name.isBlank()) {
                                nameError = true
                                return@Button
                            }

                            val config = when (serverType) {
                                McpServerType.STDIO -> {
                                    if (stdioCommand.isBlank()) {
                                        configError = true
                                        return@Button
                                    }
                                    McpServerConfig.StdioConfig(
                                        command = stdioCommand.trim(),
                                        args = stdioArgs.trim()
                                            .split("\\s+".toRegex())
                                            .filter { it.isNotBlank() }
                                    )
                                }
                                McpServerType.HTTP -> {
                                    if (httpUrl.isBlank()) {
                                        configError = true
                                        return@Button
                                    }
                                    McpServerConfig.HttpConfig(
                                        url = httpUrl.trim(),
                                        authToken = httpToken.takeIf { it.isNotBlank() }
                                    )
                                }
                            }

                            val newServer = McpServer(
                                id = server?.id ?: generateServerId(),
                                name = name.trim(),
                                type = serverType,
                                config = config,
                                enabled = server?.enabled ?: true,
                                createdAt = server?.createdAt ?: System.currentTimeMillis()
                            )

                            onSave(newServer)
                        }
                    ) {
                        Text(if (isEdit) "Save" else "Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun StdioConfigFields(
    command: String,
    onCommandChange: (String) -> Unit,
    args: String,
    onArgsChange: (String) -> Unit,
    commandError: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = command,
            onValueChange = onCommandChange,
            label = { Text("Command") },
            placeholder = { Text("node") },
            isError = commandError,
            supportingText = if (commandError) {
                { Text("Command is required") }
            } else {
                { Text("Executable command (e.g., node, python)") }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = args,
            onValueChange = onArgsChange,
            label = { Text("Arguments (optional)") },
            placeholder = { Text("server.js --port 3000") },
            supportingText = { Text("Space-separated arguments") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HttpConfigFields(
    url: String,
    onUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    urlError: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://mcp.example.com") },
            isError = urlError,
            supportingText = if (urlError) {
                { Text("URL is required") }
            } else {
                { Text("Full URL including protocol") }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text("Auth Token (optional)") },
            placeholder = { Text("Bearer token") },
            supportingText = { Text("Authentication token if required") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

/**
 * Generate a unique server ID using timestamp and random string.
 */
private fun generateServerId(): String {
    val timestamp = System.currentTimeMillis()
    val randomPart = Random.nextInt(10000, 99999)
    return "mcp_${timestamp}_${randomPart}"
}
