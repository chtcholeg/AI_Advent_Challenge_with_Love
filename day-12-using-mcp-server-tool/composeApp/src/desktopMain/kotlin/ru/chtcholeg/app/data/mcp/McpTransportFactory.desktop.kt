package ru.chtcholeg.app.data.mcp

import io.ktor.client.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.chtcholeg.app.data.mcp.stub.Transport
import ru.chtcholeg.app.data.mcp.stub.StdioServerParameters
import ru.chtcholeg.app.data.mcp.transport.SseTransportImpl
import ru.chtcholeg.app.data.mcp.transport.StdioTransportImpl
import ru.chtcholeg.app.domain.model.McpServer
import ru.chtcholeg.app.domain.model.McpServerConfig
import ru.chtcholeg.app.domain.model.McpServerType

/**
 * Desktop (JVM) implementation of McpTransportFactory.
 */
actual class McpTransportFactory : KoinComponent {
    private val httpClient: HttpClient by inject()

    actual suspend fun createTransport(server: McpServer): Transport {
        return when (server.type) {
            McpServerType.STDIO -> createStdioTransport(server.config as McpServerConfig.StdioConfig)
            McpServerType.HTTP -> createHttpTransport(server.config as McpServerConfig.HttpConfig)
        }
    }

    private suspend fun createStdioTransport(config: McpServerConfig.StdioConfig): Transport {
        val params = StdioServerParameters(
            command = config.command,
            args = config.args,
            env = config.env
        )
        return StdioTransportImpl(params)
    }

    private suspend fun createHttpTransport(config: McpServerConfig.HttpConfig): Transport {
        // Add Authorization header if token is provided
        val headers = buildMap {
            putAll(config.headers)
            config.authToken?.let { token ->
                put("Authorization", "Bearer $token")
            }
        }

        return SseTransportImpl(
            url = config.url,
            headers = headers,
            httpClient = httpClient
        )
    }
}
