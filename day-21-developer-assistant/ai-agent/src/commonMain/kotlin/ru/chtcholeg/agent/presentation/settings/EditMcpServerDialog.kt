package ru.chtcholeg.agent.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpServerConfig
import ru.chtcholeg.shared.domain.model.McpServerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMcpServerDialog(
    server: McpServer,
    onDismiss: () -> Unit,
    onSave: (McpServer) -> Unit
) {
    var name by remember { mutableStateOf(server.name) }
    var serverType by remember { mutableStateOf(server.type) }
    var expanded by remember { mutableStateOf(false) }

    // HTTP config
    var url by remember {
        mutableStateOf(
            (server.config as? McpServerConfig.HttpConfig)?.url ?: ""
        )
    }
    var authToken by remember {
        mutableStateOf(
            (server.config as? McpServerConfig.HttpConfig)?.authToken ?: ""
        )
    }

    // STDIO config
    var command by remember {
        mutableStateOf(
            (server.config as? McpServerConfig.StdioConfig)?.command ?: ""
        )
    }
    var args by remember {
        mutableStateOf(
            (server.config as? McpServerConfig.StdioConfig)?.args?.joinToString(" ") ?: ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit MCP Server") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = serverType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        McpServerType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    serverType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                when (serverType) {
                    McpServerType.HTTP -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("URL") },
                            placeholder = { Text("http://localhost:3000") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = authToken,
                            onValueChange = { authToken = it },
                            label = { Text("Auth Token (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    McpServerType.STDIO -> {
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            label = { Text("Command") },
                            placeholder = { Text("node") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = args,
                            onValueChange = { args = it },
                            label = { Text("Arguments (space-separated)") },
                            placeholder = { Text("server.js") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = when (serverType) {
                        McpServerType.HTTP -> McpServerConfig.HttpConfig(
                            url = url,
                            authToken = authToken.takeIf { it.isNotBlank() }
                        )

                        McpServerType.STDIO -> McpServerConfig.StdioConfig(
                            command = command,
                            args = args.split(" ").filter { it.isNotBlank() }
                        )
                    }

                    val updatedServer = server.copy(
                        name = name,
                        type = serverType,
                        config = config
                    )

                    onSave(updatedServer)
                },
                enabled = name.isNotBlank() && when (serverType) {
                    McpServerType.HTTP -> url.isNotBlank()
                    McpServerType.STDIO -> command.isNotBlank()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
