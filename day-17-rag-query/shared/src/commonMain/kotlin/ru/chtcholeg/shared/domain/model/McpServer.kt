package ru.chtcholeg.shared.domain.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Represents an MCP (Model Context Protocol) server configuration.
 */
data class McpServer(
    val id: String = generateServerId(),
    val name: String,
    val type: McpServerType,
    val config: McpServerConfig,
    val enabled: Boolean = true,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastConnected: Long? = null
)

/**
 * Type of MCP server transport.
 */
enum class McpServerType {
    STDIO,  // Local server via stdin/stdout
    HTTP    // Remote server via HTTP/SSE
}

/**
 * Configuration for different MCP server types.
 */
@Serializable
sealed interface McpServerConfig {

    /**
     * Configuration for stdio-based local MCP servers.
     * @param command The command to execute (e.g., "node", "python")
     * @param args Command arguments (e.g., ["server.js"])
     * @param env Optional environment variables
     */
    @Serializable
    data class StdioConfig(
        val command: String,
        val args: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap()
    ) : McpServerConfig

    /**
     * Configuration for HTTP-based remote MCP servers.
     * @param url Server URL (e.g., "https://mcp.example.com")
     * @param authToken Optional authentication token
     * @param headers Additional HTTP headers
     */
    @Serializable
    data class HttpConfig(
        val url: String,
        val authToken: String? = null,
        val headers: Map<String, String> = emptyMap()
    ) : McpServerConfig
}

/**
 * Connection status of an MCP server.
 */
enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}

private fun generateServerId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return "mcp_" + (1..16)
        .map { chars[Random.nextInt(chars.length)] }
        .joinToString("")
}
