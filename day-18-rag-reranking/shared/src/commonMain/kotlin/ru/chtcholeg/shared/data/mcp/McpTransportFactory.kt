package ru.chtcholeg.shared.data.mcp

import io.ktor.client.HttpClient
import ru.chtcholeg.shared.data.mcp.stub.Transport
import ru.chtcholeg.shared.domain.model.McpServer

/**
 * Factory for creating MCP transports.
 * Platform-specific implementation for stdio and HTTP transports.
 */
expect class McpTransportFactory(httpClient: HttpClient) {
    /**
     * Create a transport for the given MCP server configuration.
     * @throws IllegalArgumentException if server configuration is invalid
     */
    suspend fun createTransport(server: McpServer): Transport
}
