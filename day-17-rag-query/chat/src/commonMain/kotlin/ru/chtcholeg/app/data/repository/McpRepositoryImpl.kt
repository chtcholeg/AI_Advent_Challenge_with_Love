package ru.chtcholeg.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import ru.chtcholeg.app.data.local.McpLocalRepository
import ru.chtcholeg.shared.data.mcp.McpClientManager
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpTool
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Implementation of McpRepository.
 */
class McpRepositoryImpl(
    private val localRepository: McpLocalRepository,
    private val clientManager: McpClientManager
) : McpRepository {

    override fun getAllServers(): Flow<List<McpServer>> {
        return localRepository.getAllServers()
    }

    override fun getEnabledServers(): Flow<List<McpServer>> {
        return localRepository.getEnabledServers()
    }

    override suspend fun getAvailableTools(): Flow<List<McpTool>> = flow {
        val connectedServerIds = clientManager.getConnectedServerIds()
        val allTools = mutableListOf<McpTool>()

        for (serverId in connectedServerIds) {
            val tools = clientManager.listTools(serverId)
            allTools.addAll(tools)
        }

        emit(allTools)
    }

    override suspend fun getServerById(id: String): McpServer? {
        return localRepository.getServerById(id)?.let { server ->
            // Update with current connection status
            server.copy(status = clientManager.getConnectionStatus(server.id))
        }
    }

    override suspend fun addServer(server: McpServer): Result<Unit> {
        return try {
            localRepository.saveServer(server)

            // Auto-connect if enabled
            if (server.enabled) {
                connectServer(server.id)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateServer(server: McpServer): Result<Unit> {
        return try {
            val oldServer = localRepository.getServerById(server.id)
            localRepository.updateServer(server)

            // Reconnect if server was connected and config changed
            if (oldServer != null && clientManager.getConnectionStatus(server.id) == ConnectionStatus.CONNECTED) {
                clientManager.disconnect(server.id)
                if (server.enabled) {
                    connectServer(server.id)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteServer(serverId: String): Result<Unit> {
        return try {
            clientManager.disconnect(serverId)
            localRepository.deleteServer(serverId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleServerEnabled(serverId: String): Result<Unit> {
        return try {
            localRepository.toggleEnabled(serverId)
            val server = localRepository.getServerById(serverId)

            if (server != null) {
                if (server.enabled) {
                    connectServer(serverId)
                } else {
                    clientManager.disconnect(serverId)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun connectServer(serverId: String): Result<Unit> {
        return try {
            val server = localRepository.getServerById(serverId)
                ?: return Result.failure(Exception("Server not found"))

            val success = clientManager.connect(server)

            if (success) {
                val now = Clock.System.now().toEpochMilliseconds()
                localRepository.updateLastConnected(serverId, now)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to connect to server"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnectServer(serverId: String): Result<Unit> {
        return try {
            clientManager.disconnect(serverId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getServerStatus(serverId: String): ConnectionStatus {
        return clientManager.getConnectionStatus(serverId)
    }

    override fun getServerError(serverId: String): String? {
        return clientManager.getConnectionError(serverId)
    }

    override suspend fun getServerTools(serverId: String): List<McpTool> {
        return clientManager.listTools(serverId)
    }

    override suspend fun refreshServerTools(serverId: String): List<McpTool> {
        return clientManager.refreshTools(serverId)
    }

    override suspend fun executeTool(
        toolName: String,
        parameters: JsonElement
    ): McpToolResult {
        // Find which server has this tool
        val connectedServerIds = clientManager.getConnectedServerIds()

        for (serverId in connectedServerIds) {
            val tools = clientManager.listTools(serverId)
            if (tools.any { it.name == toolName }) {
                return clientManager.executeTool(serverId, toolName, parameters)
            }
        }

        return McpToolResult(
            content = "Tool '$toolName' not found in any connected server",
            isError = true
        )
    }

    override suspend fun initialize() {
        try {
            val enabledServers = localRepository.getEnabledServers().first()

            for (server in enabledServers) {
                connectServer(server.id)
            }
        } catch (e: Exception) {
            println("Failed to initialize MCP servers: ${e.message}")
        }
    }

    override suspend fun cleanup() {
        clientManager.disconnectAll()
    }
}
