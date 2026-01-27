package ru.chtcholeg.app.data.mcp

import ru.chtcholeg.app.data.mcp.stub.Transport
import ru.chtcholeg.app.domain.model.McpServer

/**
 * Factory for creating MCP transports.
 * Platform-specific implementation for stdio and HTTP transports.
 */
expect class McpTransportFactory {
    /**
     * Create a transport for the given MCP server configuration.
     * @throws IllegalArgumentException if server configuration is invalid
     */
    suspend fun createTransport(server: McpServer): Transport
}
