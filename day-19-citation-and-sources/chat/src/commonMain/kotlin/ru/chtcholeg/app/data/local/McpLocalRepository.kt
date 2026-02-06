package ru.chtcholeg.app.data.local

import kotlinx.coroutines.flow.Flow
import ru.chtcholeg.shared.domain.model.McpServer

/**
 * Local repository for MCP server persistence.
 */
interface McpLocalRepository {
    /**
     * Get all MCP servers as a Flow.
     */
    fun getAllServers(): Flow<List<McpServer>>

    /**
     * Get only enabled MCP servers as a Flow.
     */
    fun getEnabledServers(): Flow<List<McpServer>>

    /**
     * Get a server by its ID.
     */
    suspend fun getServerById(id: String): McpServer?

    /**
     * Save a new MCP server.
     */
    suspend fun saveServer(server: McpServer)

    /**
     * Update an existing MCP server.
     */
    suspend fun updateServer(server: McpServer)

    /**
     * Update the last connected timestamp for a server.
     */
    suspend fun updateLastConnected(serverId: String, timestamp: Long)

    /**
     * Toggle the enabled status of a server.
     */
    suspend fun toggleEnabled(serverId: String)

    /**
     * Delete a server by its ID.
     */
    suspend fun deleteServer(serverId: String)
}
