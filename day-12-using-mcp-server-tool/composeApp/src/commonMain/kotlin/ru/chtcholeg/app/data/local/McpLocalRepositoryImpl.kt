package ru.chtcholeg.app.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.app.domain.model.ConnectionStatus
import ru.chtcholeg.app.domain.model.McpServer
import ru.chtcholeg.app.domain.model.McpServerConfig
import ru.chtcholeg.app.domain.model.McpServerType

/**
 * Implementation of McpLocalRepository using SQLDelight.
 */
class McpLocalRepositoryImpl(
    private val database: McpDatabase
) : McpLocalRepository {

    private val queries = database.mcpServerQueries
    private val json = Json { ignoreUnknownKeys = true }

    override fun getAllServers(): Flow<List<McpServer>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getEnabledServers(): Flow<List<McpServer>> {
        return queries.selectEnabled()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getServerById(id: String): McpServer? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun saveServer(server: McpServer) = withContext(Dispatchers.IO) {
        queries.insert(
            id = server.id,
            name = server.name,
            type = server.type.name,
            configJson = serializeConfig(server.config),
            enabled = if (server.enabled) 1L else 0L,
            createdAt = server.createdAt,
            lastConnected = server.lastConnected
        )
    }

    override suspend fun updateServer(server: McpServer) = withContext(Dispatchers.IO) {
        queries.update(
            name = server.name,
            type = server.type.name,
            configJson = serializeConfig(server.config),
            enabled = if (server.enabled) 1L else 0L,
            id = server.id
        )
    }

    override suspend fun updateLastConnected(serverId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        queries.updateLastConnected(timestamp, serverId)
    }

    override suspend fun toggleEnabled(serverId: String) = withContext(Dispatchers.IO) {
        queries.toggleEnabled(serverId)
    }

    override suspend fun deleteServer(serverId: String) = withContext(Dispatchers.IO) {
        queries.deleteById(serverId)
    }

    // =============== MAPPERS ===============

    private fun McpServerEntity.toDomain(): McpServer {
        return McpServer(
            id = id,
            name = name,
            type = McpServerType.valueOf(type),
            config = deserializeConfig(configJson, type),
            enabled = enabled == 1L,
            status = ConnectionStatus.DISCONNECTED, // Status is not persisted, always starts as disconnected
            createdAt = createdAt,
            lastConnected = lastConnected
        )
    }

    private fun serializeConfig(config: McpServerConfig): String {
        return when (config) {
            is McpServerConfig.StdioConfig -> json.encodeToString(config)
            is McpServerConfig.HttpConfig -> json.encodeToString(config)
        }
    }

    private fun deserializeConfig(configJson: String, type: String): McpServerConfig {
        return when (McpServerType.valueOf(type)) {
            McpServerType.STDIO -> json.decodeFromString<McpServerConfig.StdioConfig>(configJson)
            McpServerType.HTTP -> json.decodeFromString<McpServerConfig.HttpConfig>(configJson)
        }
    }
}
