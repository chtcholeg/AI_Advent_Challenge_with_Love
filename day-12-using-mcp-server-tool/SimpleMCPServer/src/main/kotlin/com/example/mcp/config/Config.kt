package com.example.mcp.config

import com.typesafe.config.ConfigFactory

data class ServerConfig(
    val host: String,
    val port: Int
)

data class RateLimitConfig(
    val requestsPerMinute: Int
)

data class PluginsConfig(
    val directory: String
)

data class AppConfig(
    val server: ServerConfig,
    val rateLimit: RateLimitConfig,
    val plugins: PluginsConfig,
    val apiKey: String?
) {
    companion object {
        fun load(requireApiKey: Boolean = true): AppConfig {
            val config = ConfigFactory.load()

            val serverConfig = ServerConfig(
                host = config.getString("server.host"),
                port = config.getInt("server.port")
            )

            val rateLimitConfig = RateLimitConfig(
                requestsPerMinute = config.getInt("rateLimit.requestsPerMinute")
            )

            val pluginsConfig = PluginsConfig(
                directory = config.getString("plugins.directory")
            )

            // API key from environment variable (priority) or config file
            val apiKey = System.getenv("MCP_API_KEY")
                ?: if (config.hasPath("auth.apiKey")) config.getString("auth.apiKey") else null

            if (requireApiKey) {
                require(!apiKey.isNullOrBlank()) {
                    "MCP_API_KEY environment variable or auth.apiKey config must be set"
                }
            }

            return AppConfig(
                server = serverConfig,
                rateLimit = rateLimitConfig,
                plugins = pluginsConfig,
                apiKey = apiKey
            )
        }
    }
}
