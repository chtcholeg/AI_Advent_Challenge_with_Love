package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import ru.chtcholeg.agent.data.local.McpLocalRepository
import ru.chtcholeg.agent.data.tool.LocalToolsProvider
import ru.chtcholeg.shared.data.mcp.McpClientManager
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpTool
import ru.chtcholeg.shared.domain.model.McpToolResult

class McpRepositoryImpl(
    private val mcpClientManager: McpClientManager,
    private val mcpLocalRepository: McpLocalRepository,
    private val localToolsProvider: LocalToolsProvider
) : McpRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _servers = MutableStateFlow<List<McpServer>>(emptyList())
    override val servers: StateFlow<List<McpServer>> = _servers.asStateFlow()

    init {
        // Observe database changes and update state
        scope.launch {
            mcpLocalRepository.getAllServers().collect { dbServers ->
                // Merge database servers with current connection status
                _servers.value = dbServers.map { dbServer ->
                    val currentStatus = mcpClientManager.getConnectionStatus(dbServer.id)
                    dbServer.copy(status = currentStatus)
                }
            }
        }
    }

    override fun getAllServersFlow(): Flow<List<McpServer>> {
        return mcpLocalRepository.getAllServers()
    }

    override suspend fun getServers(): List<McpServer> = _servers.value

    override suspend fun addServer(server: McpServer) {
        // Save to database
        mcpLocalRepository.saveServer(server)

        // Auto-connect if enabled
        if (server.enabled) {
            connectServer(server.id)
        }
    }

    override suspend fun updateServer(server: McpServer) {
        val oldServer = mcpLocalRepository.getServerById(server.id)

        // Update in database
        mcpLocalRepository.updateServer(server)

        // Handle connection changes
        if (oldServer?.enabled == true) {
            disconnectServer(server.id)
        }

        if (server.enabled) {
            connectServer(server.id)
        }
    }

    override suspend fun deleteServer(serverId: String) {
        disconnectServer(serverId)
        mcpLocalRepository.deleteServer(serverId)
    }

    override suspend fun toggleEnabled(serverId: String) {
        val server = mcpLocalRepository.getServerById(serverId) ?: return

        mcpLocalRepository.toggleEnabled(serverId)

        if (server.enabled) {
            // Was enabled, now disabled - disconnect
            disconnectServer(serverId)
        } else {
            // Was disabled, now enabled - connect
            connectServer(serverId)
        }
    }

    override suspend fun connectServer(serverId: String): Result<Unit> {
        val server = mcpLocalRepository.getServerById(serverId) ?: return Result.failure(
            IllegalArgumentException("Server not found: $serverId")
        )

        updateServerStatus(serverId, ConnectionStatus.CONNECTING)
        // Clear previous error message when starting a new connection attempt
        mcpLocalRepository.updateErrorMessage(serverId, null)

        return try {
            val connected = mcpClientManager.connect(server)
            if (connected) {
                updateServerStatus(serverId, ConnectionStatus.CONNECTED)
                // Update last connected timestamp and clear error
                mcpLocalRepository.updateLastConnected(serverId, Clock.System.now().toEpochMilliseconds())
                mcpLocalRepository.updateErrorMessage(serverId, null)
                Result.success(Unit)
            } else {
                updateServerStatus(serverId, ConnectionStatus.ERROR)
                val errorMsg = "Failed to connect to MCP server. Please check server configuration and ensure the server is running."
                mcpLocalRepository.updateErrorMessage(serverId, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            updateServerStatus(serverId, ConnectionStatus.ERROR)
            val detailedError = buildString {
                append("Connection error: ${e.message ?: "Unknown error"}")
                e.cause?.let { cause ->
                    append("\nCause: ${cause.message}")
                }
                e.stackTraceToString().lines().take(5).forEach { line ->
                    append("\n  $line")
                }
            }
            mcpLocalRepository.updateErrorMessage(serverId, detailedError)
            Result.failure(e)
        }
    }

    override suspend fun disconnectServer(serverId: String) {
        mcpClientManager.disconnect(serverId)
        updateServerStatus(serverId, ConnectionStatus.DISCONNECTED)
    }

    override suspend fun getAllTools(): List<McpTool> {
        val allTools = mutableListOf<McpTool>()

        // Add local tools first (always available)
        allTools.addAll(localToolsProvider.getTools())

        // Add MCP server tools
        val connectedServerIds = mcpClientManager.getConnectedServerIds()
        for (serverId in connectedServerIds) {
            val tools = mcpClientManager.listTools(serverId)
            allTools.addAll(tools)
        }

        return allTools
    }

    override suspend fun getFilteredTools(allowedNames: List<String>?): List<McpTool> {
        val allTools = getAllTools()
        if (allowedNames == null) return allTools
        return allTools.filter { it.name in allowedNames }
    }

    override suspend fun getServerTools(serverId: String): List<McpTool> {
        return mcpClientManager.listTools(serverId)
    }

    override suspend fun executeTool(
        toolName: String,
        arguments: JsonElement
    ): Result<McpToolResult> {
        return try {
            // Check if this is a local tool first
            if (localToolsProvider.isLocalTool(toolName)) {
                val result = localToolsProvider.executeTool(toolName, arguments)
                return Result.success(result)
            }

            // Otherwise, find which MCP server has this tool
            val allTools = getAllTools()
            val tool = allTools.find { it.name == toolName }
                ?: return Result.failure(IllegalArgumentException("Tool not found: $toolName"))

            val result = mcpClientManager.executeTool(tool.serverId, toolName, arguments)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun initialize() {
        // Load enabled servers from database and connect them
        val enabledServers = mcpLocalRepository.getEnabledServers().first()

        for (server in enabledServers) {
            try {
                connectServer(server.id)
            } catch (e: Exception) {
                // Log error but continue with other servers
                println("Failed to connect to server ${server.name}: ${e.message}")
            }
        }
    }

    private fun updateServerStatus(serverId: String, status: ConnectionStatus, errorMessage: String? = null) {
        _servers.value = _servers.value.map {
            if (it.id == serverId) {
                it.copy(status = status, errorMessage = errorMessage ?: it.errorMessage)
            } else {
                it
            }
        }
    }
}
