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
import ru.chtcholeg.shared.data.mcp.McpClientManager
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpTool
import ru.chtcholeg.shared.domain.model.McpToolResult

class McpRepositoryImpl(
    private val mcpClientManager: McpClientManager,
    private val mcpLocalRepository: McpLocalRepository
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

        return try {
            val connected = mcpClientManager.connect(server)
            if (connected) {
                updateServerStatus(serverId, ConnectionStatus.CONNECTED)
                // Update last connected timestamp
                mcpLocalRepository.updateLastConnected(serverId, Clock.System.now().toEpochMilliseconds())
                Result.success(Unit)
            } else {
                updateServerStatus(serverId, ConnectionStatus.ERROR)
                Result.failure(Exception("Failed to connect"))
            }
        } catch (e: Exception) {
            updateServerStatus(serverId, ConnectionStatus.ERROR)
            Result.failure(e)
        }
    }

    override suspend fun disconnectServer(serverId: String) {
        mcpClientManager.disconnect(serverId)
        updateServerStatus(serverId, ConnectionStatus.DISCONNECTED)
    }

    override suspend fun getAllTools(): List<McpTool> {
        val connectedServerIds = mcpClientManager.getConnectedServerIds()
        val allTools = mutableListOf<McpTool>()

        for (serverId in connectedServerIds) {
            val tools = mcpClientManager.listTools(serverId)
            allTools.addAll(tools)
        }

        return allTools
    }

    override suspend fun getServerTools(serverId: String): List<McpTool> {
        return mcpClientManager.listTools(serverId)
    }

    override suspend fun executeTool(
        toolName: String,
        arguments: JsonElement
    ): Result<McpToolResult> {
        return try {
            // Find which server has this tool
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

    private fun updateServerStatus(serverId: String, status: ConnectionStatus) {
        _servers.value = _servers.value.map {
            if (it.id == serverId) it.copy(status = status) else it
        }
    }
}
