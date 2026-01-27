package com.example.mcp

import com.example.mcp.auth.API_KEY_AUTH
import com.example.mcp.auth.apiKeyAuth
import com.example.mcp.config.AppConfig
import com.example.mcp.mcp.McpProtocolHandler
import com.example.mcp.mcp.SseTransport
import com.example.mcp.mcp.ToolRegistry
import com.example.mcp.models.*
import com.example.mcp.plugins.JsonSchema
import com.example.mcp.plugins.McpTool
import com.example.mcp.plugins.PluginLoader
import com.example.mcp.tools.github.GitHubPlugin
import com.example.mcp.tools.weather.WeatherPlugin
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

private val logger = LoggerFactory.getLogger("Application")

fun main(args: Array<String>) {
    logger.info("Starting MCP Server...")

    // Parse command line arguments
    val disableAuth = args.contains("--no-auth") || args.contains("--disable-auth")

    if (disableAuth) {
        logger.warn("⚠️  Authentication is DISABLED. Server is running in insecure mode!")
    }

    val config = try {
        AppConfig.load(requireApiKey = !disableAuth)
    } catch (e: Exception) {
        logger.error("Failed to load configuration: ${e.message}")
        System.exit(1)
        return
    }

    logger.info("Configuration loaded successfully")
    logger.info("Server will listen on ${config.server.host}:${config.server.port}")

    // Initialize components
    val toolRegistry = ToolRegistry()
    val pluginLoader = PluginLoader(config.plugins.directory)

    // Load plugins from directory
    val externalPlugins = pluginLoader.loadPlugins()
    toolRegistry.registerPlugins(externalPlugins)

    // Register built-in plugins
    val weatherPlugin = WeatherPlugin()
    weatherPlugin.initialize()
    toolRegistry.registerPlugin(weatherPlugin)

    val gitHubPlugin = GitHubPlugin()
    gitHubPlugin.initialize()
    toolRegistry.registerPlugin(gitHubPlugin)

    logger.info("Registered ${toolRegistry.toolCount()} tool(s)")

    // Create protocol handler and transport
    val protocolHandler = McpProtocolHandler(toolRegistry)
    val sseTransport = SseTransport(protocolHandler)

    // Start server
    val server = embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        configurePlugins(config, disableAuth)
        configureRouting(config, sseTransport, toolRegistry, disableAuth)
    }

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        sseTransport.closeAllSessions()
        weatherPlugin.shutdown()
        gitHubPlugin.shutdown()
        pluginLoader.shutdownPlugins()
        server.stop(1000, 2000)
        logger.info("Shutdown complete")
    })

    server.start(wait = true)
}

private fun Application.configurePlugins(config: AppConfig, disableAuth: Boolean) {
    // JSON serialization
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        })
    }

    // CORS
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }

    // Rate limiting
    install(RateLimit) {
        global {
            rateLimiter(limit = config.rateLimit.requestsPerMinute, refillPeriod = 1.minutes)
        }
    }

    // Authentication (only if not disabled)
    if (!disableAuth) {
        install(Authentication) {
            apiKeyAuth(config.apiKey ?: "")
        }
    }

    // Error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Check if this is a channel write exception (client disconnected during SSE)
            val isChannelWriteException = cause.message?.contains("Cannot write to a channel") == true ||
                cause.cause?.message?.contains("Cannot write to a channel") == true ||
                cause::class.simpleName == "ChannelWriteException"

            if (isChannelWriteException) {
                // Client disconnected during SSE - this is expected, don't log as error
                logger.debug("Client disconnected: ${cause.message}")
                return@exception
            }

            logger.error("Unhandled exception: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = cause.message ?: "Internal server error")
            )
        }

        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(error = "Unauthorized: Invalid or missing API key")
            )
        }

        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse(error = "Rate limit exceeded. Try again later.")
            )
        }
    }
}

private fun Application.configureRouting(
    config: AppConfig,
    sseTransport: SseTransport,
    toolRegistry: ToolRegistry,
    disableAuth: Boolean
) {
    val startTime = System.currentTimeMillis()

    routing {
        // Health check (no auth required)
        get("/health") {
            val uptime = System.currentTimeMillis() - startTime
            call.respond(
                HealthResponse(
                    status = "ok",
                    uptime = uptime,
                    uptime_human = formatUptime(uptime),
                    tools_count = toolRegistry.toolCount(),
                    active_sessions = sseTransport.getActiveSessionCount()
                )
            )
        }

        // Protected MCP endpoints (conditionally protected)
        if (disableAuth) {
            // No authentication - direct access
            sseTransport.setupRoutes(this)

            get("/tools") {
                val tools = toolRegistry.getAllTools().map { it.toToolInfo() }
                call.respond(ToolsResponse(tools = tools))
            }
        } else {
            // With authentication
            authenticate(API_KEY_AUTH) {
                // SSE transport routes
                sseTransport.setupRoutes(this)

                // Tool listing endpoint (convenience)
                get("/tools") {
                    val tools = toolRegistry.getAllTools().map { it.toToolInfo() }
                    call.respond(ToolsResponse(tools = tools))
                }
            }
        }

        // Root info
        get("/") {
            val authStatus = if (disableAuth) "" else " (requires auth)"
            call.respond(
                ServerInfoResponse(
                    name = "MCP Server",
                    version = "1.0.0",
                    protocol = "MCP 2024-11-05",
                    endpoints = mapOf(
                        "health" to "/health",
                        "sse" to "/sse$authStatus",
                        "message" to "/message$authStatus",
                        "tools" to "/tools$authStatus"
                    )
                )
            )
        }
    }
}

private fun formatUptime(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
        hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

/**
 * Convert McpTool to ToolInfo for API response.
 */
private fun McpTool.toToolInfo(): ToolInfo {
    val schemaJson = buildJsonObject {
        put("type", JsonPrimitive(inputSchema.type))
        put("properties", buildJsonObject {
            for ((name, prop) in inputSchema.properties) {
                put(name, buildJsonObject {
                    put("type", JsonPrimitive(prop.type))
                    prop.description?.let { put("description", JsonPrimitive(it)) }
                    prop.enum?.let { enumList ->
                        put("enum", JsonArray(enumList.map { JsonPrimitive(it) }))
                    }
                    prop.default?.let { put("default", it) }
                })
            }
        })
        if (inputSchema.required.isNotEmpty()) {
            put("required", JsonArray(inputSchema.required.map { JsonPrimitive(it) }))
        }
        inputSchema.description?.let { put("description", JsonPrimitive(it)) }
    }

    val examples = fewShotExamples.takeIf { it.isNotEmpty() }?.map { ex ->
        ToolFewShotExample(request = ex.request, params = ex.params)
    }

    return ToolInfo(
        name = name,
        description = description,
        inputSchema = schemaJson,
        fewShotExamples = examples
    )
}
