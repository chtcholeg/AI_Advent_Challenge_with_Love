package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpTool
import ru.chtcholeg.shared.domain.model.McpToolResult
import kotlinx.serialization.json.JsonElement

/**
 * Repository for managing MCP servers and executing tools.
 */
interface McpRepository {
    /**
     * Observable list of all configured MCP servers with their current status.
     */
    val servers: StateFlow<List<McpServer>>

    /**
     * Get all servers from database as Flow.
     */
    fun getAllServersFlow(): Flow<List<McpServer>>

    /**
     * Get all configured MCP servers.
     */
    suspend fun getServers(): List<McpServer>

    /**
     * Add a new MCP server.
     */
    suspend fun addServer(server: McpServer)

    /**
     * Update an existing MCP server.
     */
    suspend fun updateServer(server: McpServer)

    /**
     * Delete an MCP server.
     */
    suspend fun deleteServer(serverId: String)

    /**
     * Connect to an MCP server.
     */
    suspend fun connectServer(serverId: String): Result<Unit>

    /**
     * Disconnect from an MCP server.
     */
    suspend fun disconnectServer(serverId: String)

    /**
     * Get all available tools from all connected servers.
     */
    suspend fun getAllTools(): List<McpTool>

    /**
     * Get tools for a specific server.
     */
    suspend fun getServerTools(serverId: String): List<McpTool>

    /**
     * Execute a tool on an MCP server.
     */
    suspend fun executeTool(
        toolName: String,
        arguments: JsonElement
    ): Result<McpToolResult>

    /**
     * Toggle enabled status for a server.
     */
    suspend fun toggleEnabled(serverId: String)

    /**
     * Initialize repository - load saved servers and connect enabled ones.
     */
    suspend fun initialize()
}
