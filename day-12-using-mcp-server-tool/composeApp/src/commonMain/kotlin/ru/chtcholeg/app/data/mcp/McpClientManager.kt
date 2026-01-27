package ru.chtcholeg.app.data.mcp

import ru.chtcholeg.app.data.mcp.stub.Client
import ru.chtcholeg.app.data.mcp.stub.ClientCapabilities
import ru.chtcholeg.app.data.mcp.stub.Implementation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import ru.chtcholeg.app.domain.model.ConnectionStatus
import ru.chtcholeg.app.domain.model.McpFewShotExample
import ru.chtcholeg.app.domain.model.McpServer
import ru.chtcholeg.app.domain.model.McpTool
import ru.chtcholeg.app.domain.model.McpToolResult

/**
 * Manages MCP client connections and tool execution.
 */
class McpClientManager(
    private val transportFactory: McpTransportFactory
) {
    private val clients = mutableMapOf<String, Client>()
    private val toolsCache = mutableMapOf<String, List<McpTool>>()
    private val statusMap = mutableMapOf<String, ConnectionStatus>()
    private val errorMap = mutableMapOf<String, String>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30000L
        private const val TOOL_EXECUTION_TIMEOUT_MS = 30000L
    }

    /**
     * Connect to an MCP server.
     * @return true if connection successful, false otherwise
     */
    suspend fun connect(server: McpServer): Boolean = mutex.withLock {
        try {
            // Check if already connected
            if (clients.containsKey(server.id)) {
                errorMap.remove(server.id)
                return@withLock true
            }

            statusMap[server.id] = ConnectionStatus.CONNECTING
            errorMap.remove(server.id)

            // Create transport and client with timeout
            val transport = withTimeout(CONNECTION_TIMEOUT_MS) {
                transportFactory.createTransport(server)
            }

            val client = Client(
                clientInfo = Implementation(
                    name = "GigaChat MCP Client",
                    version = "1.0.0"
                ),
                capabilities = ClientCapabilities(),
                transport = transport
            )

            // Initialize connection
            withTimeout(CONNECTION_TIMEOUT_MS) {
                client.initialize()
            }

            clients[server.id] = client
            statusMap[server.id] = ConnectionStatus.CONNECTED
            errorMap.remove(server.id)

            // Load tools immediately after connection
            loadTools(server.id)

            true
        } catch (e: Exception) {
            statusMap[server.id] = ConnectionStatus.ERROR
            val errorMessage = buildErrorMessage(e)
            errorMap[server.id] = errorMessage
            println("Failed to connect to MCP server ${server.name}: $errorMessage")
            false
        }
    }

    /**
     * Build a user-friendly error message from an exception.
     */
    private fun buildErrorMessage(e: Exception): String {
        val message = e.message ?: e::class.simpleName ?: "Unknown error"
        return when {
            e is kotlinx.coroutines.TimeoutCancellationException -> "Connection timeout (${CONNECTION_TIMEOUT_MS / 1000}s)"
            message.contains("Connection refused") -> "Connection refused - server not running"
            message.contains("No such file") -> "Command not found - check path"
            message.contains("Permission denied") -> "Permission denied"
            message.contains("ENOENT") -> "File or command not found"
            message.contains("EACCES") -> "Access denied"
            message.contains("ETIMEDOUT") -> "Network timeout"
            message.contains("ECONNREFUSED") -> "Connection refused"
            message.contains("UnknownHostException") -> "Unknown host"
            message.contains("SocketException") -> "Network error"
            else -> message
        }
    }

    /**
     * Disconnect from an MCP server.
     */
    suspend fun disconnect(serverId: String) = mutex.withLock {
        clients.remove(serverId)?.let { client ->
            try {
                client.close()
            } catch (e: Exception) {
                println("Error closing MCP client $serverId: ${e.message}")
            }
        }
        toolsCache.remove(serverId)
        statusMap[serverId] = ConnectionStatus.DISCONNECTED
        errorMap.remove(serverId)
    }

    /**
     * List all available tools from a server.
     */
    suspend fun listTools(serverId: String): List<McpTool> = mutex.withLock {
        // Return cached tools if available
        toolsCache[serverId]?.let { return@withLock it }

        // Otherwise, load and cache
        return@withLock loadTools(serverId)
    }

    /**
     * Refresh tools list from a server (clears cache and reloads).
     */
    suspend fun refreshTools(serverId: String): List<McpTool> = mutex.withLock {
        // Clear cache for this server
        toolsCache.remove(serverId)

        // Reload tools
        return@withLock loadTools(serverId)
    }

    /**
     * Execute a tool with given parameters.
     */
    suspend fun executeTool(
        serverId: String,
        toolName: String,
        parameters: JsonElement
    ): McpToolResult {
        val client = mutex.withLock { clients[serverId] }
            ?: return McpToolResult(
                content = "Server not connected",
                isError = true
            )

        return try {
            withTimeout(TOOL_EXECUTION_TIMEOUT_MS) {
                val result = client.callTool(toolName, parameters)

                McpToolResult(
                    content = result.content.joinToString("\n") { it.text ?: "" },
                    isError = result.isError ?: false
                )
            }
        } catch (e: Exception) {
            McpToolResult(
                content = "Tool execution failed: ${e.message}",
                isError = true
            )
        }
    }

    /**
     * Get connection status of a server.
     */
    fun getConnectionStatus(serverId: String): ConnectionStatus {
        return statusMap[serverId] ?: ConnectionStatus.DISCONNECTED
    }

    /**
     * Get error message for a server (if any).
     */
    fun getConnectionError(serverId: String): String? {
        return errorMap[serverId]
    }

    /**
     * Clear error for a server.
     */
    fun clearError(serverId: String) {
        errorMap.remove(serverId)
    }

    /**
     * Get all connected server IDs.
     */
    fun getConnectedServerIds(): List<String> = clients.keys.toList()

    /**
     * Disconnect all servers.
     */
    suspend fun disconnectAll() {
        val serverIds = mutex.withLock { clients.keys.toList() }
        serverIds.forEach { disconnect(it) }
    }

    // Private helper to load tools from a server
    private suspend fun loadTools(serverId: String): List<McpTool> {
        val client = clients[serverId] ?: return emptyList()

        return try {
            val toolsListResult = client.listTools()
            val tools = toolsListResult.tools.map { tool ->
                McpTool(
                    name = tool.name,
                    description = tool.description ?: "",
                    inputSchema = tool.inputSchema,
                    serverId = serverId,
                    fewShotExamples = tool.fewShotExamples?.map { ex ->
                        McpFewShotExample(request = ex.request, params = ex.params)
                    } ?: emptyList()
                )
            }
            toolsCache[serverId] = tools
            tools
        } catch (e: Exception) {
            println("Failed to load tools from server $serverId: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
