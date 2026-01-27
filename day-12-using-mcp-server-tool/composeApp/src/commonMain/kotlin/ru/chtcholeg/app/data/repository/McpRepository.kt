package ru.chtcholeg.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import ru.chtcholeg.app.domain.model.ConnectionStatus
import ru.chtcholeg.app.domain.model.McpServer
import ru.chtcholeg.app.domain.model.McpTool
import ru.chtcholeg.app.domain.model.McpToolResult

/**
 * Repository for managing MCP servers and tools.
 */
interface McpRepository {
    /**
     * Get all MCP servers as a Flow.
     */
    fun getAllServers(): Flow<List<McpServer>>

    /**
     * Get only enabled MCP servers as a Flow.
     */
    fun getEnabledServers(): Flow<List<McpServer>>

    /**
     * Get all available tools from all connected enabled servers.
     */
    suspend fun getAvailableTools(): Flow<List<McpTool>>

    /**
     * Get a server by its ID.
     */
    suspend fun getServerById(id: String): McpServer?

    /**
     * Add a new MCP server.
     */
    suspend fun addServer(server: McpServer): Result<Unit>

    /**
     * Update an existing MCP server.
     */
    suspend fun updateServer(server: McpServer): Result<Unit>

    /**
     * Delete a server by its ID.
     */
    suspend fun deleteServer(serverId: String): Result<Unit>

    /**
     * Toggle the enabled status of a server.
     */
    suspend fun toggleServerEnabled(serverId: String): Result<Unit>

    /**
     * Connect to a server.
     */
    suspend fun connectServer(serverId: String): Result<Unit>

    /**
     * Disconnect from a server.
     */
    suspend fun disconnectServer(serverId: String): Result<Unit>

    /**
     * Get connection status of a server.
     */
    fun getServerStatus(serverId: String): ConnectionStatus

    /**
     * Get connection error message for a server (if any).
     */
    fun getServerError(serverId: String): String?

    /**
     * Get tools for a specific server.
     */
    suspend fun getServerTools(serverId: String): List<McpTool>

    /**
     * Refresh tools list for a specific server (clears cache and reloads).
     */
    suspend fun refreshServerTools(serverId: String): List<McpTool>

    /**
     * Execute a tool.
     */
    suspend fun executeTool(
        toolName: String,
        parameters: JsonElement
    ): McpToolResult

    /**
     * Initialize - connect to all enabled servers.
     */
    suspend fun initialize()

    /**
     * Cleanup - disconnect all servers.
     */
    suspend fun cleanup()
}
