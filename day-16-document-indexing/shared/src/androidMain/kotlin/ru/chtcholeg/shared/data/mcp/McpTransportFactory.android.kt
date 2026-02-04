package ru.chtcholeg.shared.data.mcp

import io.ktor.client.HttpClient
import ru.chtcholeg.shared.data.mcp.stub.SseTransport
import ru.chtcholeg.shared.data.mcp.stub.StdioServerParameters
import ru.chtcholeg.shared.data.mcp.stub.StdioTransport
import ru.chtcholeg.shared.data.mcp.stub.Transport
import ru.chtcholeg.shared.domain.model.McpServer
import ru.chtcholeg.shared.domain.model.McpServerConfig
import ru.chtcholeg.shared.domain.model.McpServerType

/**
 * Android implementation of McpTransportFactory.
 */
actual class McpTransportFactory actual constructor(
    private val httpClient: HttpClient
) {

    actual suspend fun createTransport(server: McpServer): Transport {
        return when (server.type) {
            McpServerType.STDIO -> createStdioTransport(server.config as McpServerConfig.StdioConfig)
            McpServerType.HTTP -> createHttpTransport(server.config as McpServerConfig.HttpConfig)
        }
    }

    private fun createStdioTransport(config: McpServerConfig.StdioConfig): Transport {
        val params = StdioServerParameters(
            command = config.command,
            args = config.args,
            env = config.env
        )
        return StdioTransport(params)
    }

    private fun createHttpTransport(config: McpServerConfig.HttpConfig): Transport {
        val headers = buildMap {
            putAll(config.headers)
            config.authToken?.let { token ->
                put("X-API-Key", token)
                println("✅ MCP Auth: Adding X-API-Key header with token (first 10 chars): ${token.take(10)}...")
            } ?: println("⚠️ MCP Auth: No authToken provided in config")
        }
        println("📡 MCP Transport: Creating HTTP transport to ${config.url}")
        println("📋 MCP Headers: ${headers.keys.joinToString(", ")}")
        return SseTransport(config.url, headers, httpClient)
    }
}
