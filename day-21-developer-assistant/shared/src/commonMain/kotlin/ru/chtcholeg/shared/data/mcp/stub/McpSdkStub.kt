package ru.chtcholeg.shared.data.mcp.stub

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.seconds

/**
 * Stub implementation of MCP SDK classes.
 * TODO: Replace with official io.modelcontextprotocol:kotlin-sdk when available.
 */

/**
 * JSON-RPC 2.0 Request
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val method: String,
    val params: JsonObject? = null
)

/**
 * JSON-RPC 2.0 Response
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 Error
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Tools list result from MCP server
 */
@Serializable
data class ToolsListResult(
    val tools: List<Tool>
)

/**
 * Few-shot example for function calling.
 */
@Serializable
data class FewShotExample(
    val request: String,
    val params: JsonObject
)

/**
 * Negative few-shot example - shows when NOT to use a tool.
 */
@Serializable
data class NegativeFewShotExample(
    val request: String,
    val reason: String
)

/**
 * Represents a transport mechanism for MCP communication.
 */
interface Transport {
    suspend fun start()
    suspend fun close()
    suspend fun send(message: String)
    suspend fun receive(): String?
}

/**
 * MCP Client implementation info.
 */
data class Implementation(
    val name: String,
    val version: String
)

/**
 * Client capabilities for MCP protocol.
 */
data class ClientCapabilities(
    val tools: Boolean = true
)

/**
 * Tool definition from MCP server.
 */
@Serializable
data class Tool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonElement,
    val fewShotExamples: List<FewShotExample>? = null,
    val negativeFewShotExamples: List<NegativeFewShotExample>? = null
)

/**
 * Response from tool execution.
 */
@Serializable
data class CallToolResult(
    val content: List<ContentItem>,
    val isError: Boolean? = null
)

@Serializable
data class ContentItem(
    val type: String = "text",
    val text: String? = null
)


/**
 * MCP Client for communicating with servers.
 */
class Client(
    val clientInfo: Implementation,
    val capabilities: ClientCapabilities,
    val transport: Transport
) {
    private var initialized = false
    private var requestIdCounter = 0
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun initialize() {
        transport.start()
        initialized = true
    }

    suspend fun close() {
        transport.close()
        initialized = false
    }

    suspend fun listTools(): ToolsListResult {
        if (!initialized) throw IllegalStateException("Client not initialized")

        // Create JSON-RPC request for tools/list
        val request = JsonRpcRequest(
            id = JsonPrimitive(++requestIdCounter),
            method = "tools/list",
            params = null
        )

        // Send request
        val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
        transport.send(requestJson)

        // Receive response
        val responseJson = transport.receive()
            ?: throw IllegalStateException("No response received from MCP server")

        // Parse response
        val response = json.decodeFromString(JsonRpcResponse.serializer(), responseJson)

        // Check for error
        response.error?.let { error ->
            throw IllegalStateException("MCP server error: ${error.message} (code: ${error.code})")
        }

        // Parse result
        val result = response.result
            ?: throw IllegalStateException("No result in MCP server response")

        val toolsListResult = json.decodeFromJsonElement(ToolsListResult.serializer(), result)
        return toolsListResult
    }

    suspend fun callTool(name: String, parameters: JsonElement): CallToolResult {
        if (!initialized) throw IllegalStateException("Client not initialized")

        // Create JSON-RPC request for tools/call
        val request = JsonRpcRequest(
            id = JsonPrimitive(++requestIdCounter),
            method = "tools/call",
            params = buildJsonObject {
                put("name", JsonPrimitive(name))
                put("arguments", parameters.jsonObject)
            }
        )

        // Send request
        val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
        transport.send(requestJson)

        // Receive response
        val responseJson = transport.receive()
            ?: throw IllegalStateException("No response received from MCP server")

        // Parse response
        val response = json.decodeFromString(JsonRpcResponse.serializer(), responseJson)

        // Check for error
        response.error?.let { error ->
            return CallToolResult(
                content = listOf(ContentItem(text = "Error: ${error.message}")),
                isError = true
            )
        }

        // Parse result
        val result = response.result
            ?: throw IllegalStateException("No result in MCP server response")

        return json.decodeFromJsonElement(CallToolResult.serializer(), result)
    }
}

/**
 * Parameters for stdio-based MCP server.
 */
data class StdioServerParameters(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

/**
 * Stdio transport implementation (stub).
 */
class StdioTransport(
    private val params: StdioServerParameters
) : Transport {
    override suspend fun start() {
        // Stub: Would start process and connect stdio
        println("Stub: Starting stdio transport with command ${params.command}")
    }

    override suspend fun close() {
        // Stub: Would close process
        println("Stub: Closing stdio transport")
    }

    override suspend fun send(message: String) {
        // Stub: Would write to stdin
        println("Stub: Sending to stdin: $message")
    }

    override suspend fun receive(): String? {
        // Stub: Would read from stdout
        return null
    }
}

/**
 * SSE (Server-Sent Events) transport implementation.
 *
 * Protocol flow:
 * 1. Connect to /sse endpoint to get session ID
 * 2. Send JSON-RPC requests via POST to /message?sessionId=<id>
 * 3. Read response from POST response body
 */
class SseTransport(
    private val url: String,
    private val headers: Map<String, String>,
    private val httpClient: HttpClient
) : Transport {
    private var responseChannel = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isActive = false
    private var sseJob: kotlinx.coroutines.Job? = null
    private var messageEndpoint: String? = null
    private var endpointReceived = CompletableDeferred<String>()

    override suspend fun start() {
        try {
            isActive = true

            // Reset endpoint state for reconnection
            if (endpointReceived.isCompleted || endpointReceived.isCancelled) {
                endpointReceived = CompletableDeferred()
            }
            messageEndpoint = null

            // Normalize base URL (remove trailing slash)
            val baseUrl = url.trimEnd('/')
            val sseUrl = "$baseUrl/sse"

            // Start SSE connection in background
            sseJob = scope.launch {
                try {
                    println("SSE: Connecting to $sseUrl...")
                    httpClient.prepareGet(sseUrl) {
                        headers {
                            this@SseTransport.headers.forEach { (key, value) ->
                                append(key, value)
                            }
                            append(HttpHeaders.Accept, "text/event-stream")
                            append(HttpHeaders.CacheControl, "no-cache")
                        }
                        // SSE connections should be long-lived, set very long timeout (10 hours)
                        timeout {
                            requestTimeoutMillis = 36000000
                            socketTimeoutMillis = 36000000
                        }
                    }.execute { response ->
                        println("SSE: Connected! Status: ${response.status}")
                        println("SSE: Response headers: ${response.headers.entries().joinToString { "${it.key}: ${it.value}" }}")

                        if (response.status.value == 401) {
                            val errorBody = kotlin.runCatching { response.bodyAsText() }.getOrNull()
                            println("❌ SSE: 401 Unauthorized!")
                            println("❌ SSE: Error body: $errorBody")
                            println("❌ SSE: Sent headers: ${this@SseTransport.headers}")
                            throw IllegalStateException("401 Unauthorized: Check your auth token. Body: $errorBody")
                        }

                        val channel: ByteReadChannel = response.bodyAsChannel()
                        var currentEvent: String? = null

                        while (isActive && !channel.isClosedForRead) {
                            val line = channel.readUTF8Line() ?: break
                            println("SSE: Received line: '$line'")

                            when {
                                line.startsWith("event: ") -> {
                                    currentEvent = line.substring(7).trim()
                                    println("SSE: Event type: $currentEvent")
                                }
                                line.startsWith("data: ") -> {
                                    val data = line.substring(6).trim()
                                    println("SSE: Data: $data")
                                    if (data.isNotEmpty()) {
                                        when (currentEvent) {
                                            "endpoint" -> {
                                                // Extract message endpoint URL
                                                val endpoint = if (data.startsWith("/")) {
                                                    "$baseUrl$data"
                                                } else {
                                                    data
                                                }
                                                messageEndpoint = endpoint
                                                endpointReceived.complete(endpoint)
                                                println("SSE: ✅ Received message endpoint: $endpoint")
                                            }
                                            "message" -> {
                                                // MCP protocol message
                                                responseChannel.send(data)
                                                println("SSE: Received MCP message")
                                            }
                                            else -> {
                                                // Default: treat as message
                                                responseChannel.send(data)
                                                println("SSE: Received data without event type")
                                            }
                                        }
                                    }
                                }
                                line.isEmpty() -> {
                                    // Empty line marks end of event
                                    println("SSE: Event completed (empty line)")
                                    currentEvent = null
                                }
                                line.startsWith(":") -> {
                                    // Comment/keepalive - ignore
                                    println("SSE: Keepalive received")
                                }
                                else -> {
                                    println("SSE: Unknown line format: $line")
                                }
                            }
                        }
                        println("SSE: Connection closed by server or client")
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        println("SSE connection error: ${e.message}")
                        e.printStackTrace()
                    }
                    // Complete with error to unblock waiting coroutines
                    if (!endpointReceived.isCompleted) {
                        endpointReceived.cancel()
                    }
                }
            }

            println("Started SSE transport to: $sseUrl")

            // Wait for endpoint to be received (with timeout)
            println("SSE: Waiting for endpoint from server...")
            withTimeoutOrNull(10.seconds) {
                endpointReceived.await()
            }?.let {
                println("SSE: ✅ Start completed successfully, endpoint ready: $it")
            } ?: run {
                println("SSE: ⚠️ Timeout waiting for endpoint during start()")
                throw IllegalStateException("Timeout: Server did not send endpoint event within 10 seconds")
            }
        } catch (e: Exception) {
            isActive = false
            if (!endpointReceived.isCompleted) {
                endpointReceived.cancel()
            }
            throw IllegalStateException("Failed to start SSE transport: ${e.message}", e)
        }
    }

    override suspend fun close() {
        isActive = false
        sseJob?.cancel()
        sseJob = null
        responseChannel.close()
        if (!endpointReceived.isCompleted) {
            endpointReceived.cancel()
        }
        println("Closed SSE transport")
    }

    /**
     * Reconnect to the SSE endpoint without closing the response channel.
     * Used for recovering from session expiration.
     */
    private suspend fun reconnect() {
        println("SSE: Starting reconnection...")

        // Cancel old SSE connection
        isActive = false
        sseJob?.cancel()
        sseJob = null

        // Wait briefly for old connection to fully close
        delay(100)

        // Reset endpoint state
        if (endpointReceived.isCompleted || endpointReceived.isCancelled) {
            endpointReceived = CompletableDeferred()
        }
        messageEndpoint = null

        // Start new connection (reuse start() logic)
        isActive = true

        // Normalize base URL (remove trailing slash)
        val baseUrl = url.trimEnd('/')
        val sseUrl = "$baseUrl/sse"

        // Start SSE connection in background
        sseJob = scope.launch {
            try {
                println("SSE: Reconnecting to $sseUrl...")
                httpClient.prepareGet(sseUrl) {
                    headers {
                        this@SseTransport.headers.forEach { (key, value) ->
                            append(key, value)
                        }
                        append(HttpHeaders.Accept, "text/event-stream")
                        append(HttpHeaders.CacheControl, "no-cache")
                    }
                    // SSE connections should be long-lived, set very long timeout (10 hours)
                    timeout {
                        requestTimeoutMillis = 36000000
                        socketTimeoutMillis = 36000000
                    }
                }.execute { response ->
                    println("SSE: Reconnected! Status: ${response.status}")

                    if (response.status.value == 401) {
                        val errorBody = kotlin.runCatching { response.bodyAsText() }.getOrNull()
                        println("❌ SSE: 401 Unauthorized on reconnect!")
                        println("❌ SSE: Error body: $errorBody")
                        println("❌ SSE: Sent headers: ${this@SseTransport.headers}")
                        throw IllegalStateException("401 Unauthorized on reconnect: Check your auth token. Body: $errorBody")
                    }

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    var currentEvent: String? = null

                    while (isActive && !channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break

                        when {
                            line.startsWith("event: ") -> {
                                currentEvent = line.substring(7).trim()
                            }
                            line.startsWith("data: ") -> {
                                val data = line.substring(6).trim()
                                if (data.isNotEmpty()) {
                                    when (currentEvent) {
                                        "endpoint" -> {
                                            val endpoint = if (data.startsWith("/")) {
                                                "$baseUrl$data"
                                            } else {
                                                data
                                            }
                                            messageEndpoint = endpoint
                                            endpointReceived.complete(endpoint)
                                            println("SSE: ✅ Received new message endpoint: $endpoint")
                                        }
                                        "message" -> {
                                            responseChannel.send(data)
                                        }
                                        else -> {
                                            responseChannel.send(data)
                                        }
                                    }
                                }
                            }
                            line.isEmpty() -> {
                                currentEvent = null
                            }
                            line.startsWith(":") -> {
                                // Keepalive - ignore
                            }
                        }
                    }
                    println("SSE: Reconnected connection closed")
                }
            } catch (e: Exception) {
                if (isActive) {
                    println("SSE reconnection error: ${e.message}")
                }
                if (!endpointReceived.isCompleted) {
                    endpointReceived.cancel()
                }
            }
        }

        // Wait for new endpoint
        println("SSE: Waiting for new endpoint after reconnection...")
        withTimeoutOrNull(10.seconds) {
            endpointReceived.await()
        } ?: throw IllegalStateException("Timeout waiting for endpoint after reconnection")

        println("SSE: ✅ Reconnection completed successfully")
    }

    // Stores the last POST response body for receive() to return
    private var lastResponseBody: String? = null

    override suspend fun send(message: String) {
        withContext(Dispatchers.IO) {
            try {
                // Wait for endpoint with timeout (10 seconds)
                val endpoint = withTimeoutOrNull(10.seconds) {
                    endpointReceived.await()
                } ?: throw IllegalStateException("Timeout waiting for message endpoint from server")

                println("📤 SSE: Sending message to $endpoint")
                println("📤 SSE: Headers: ${this@SseTransport.headers}")

                val response = httpClient.post(endpoint) {
                    headers {
                        this@SseTransport.headers.forEach { (key, value) ->
                            append(key, value)
                        }
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    }
                    setBody(message)
                }

                println("📥 SSE: Response status: ${response.status}")

                if (response.status.value == 401) {
                    val errorBody = kotlin.runCatching { response.bodyAsText() }.getOrNull()
                    println("❌ SSE: 401 Unauthorized on message send!")
                    println("❌ SSE: Error body: $errorBody")
                    println("❌ SSE: Sent headers: ${this@SseTransport.headers}")
                    throw IllegalStateException("401 Unauthorized: Check your auth token. Body: $errorBody")
                }

                // Check for session expiration (404 with "Session not found")
                if (response.status == HttpStatusCode.NotFound) {
                    val body = response.bodyAsText()
                    if (body.contains("Session not found", ignoreCase = true)) {
                        println("SSE: Session expired, attempting reconnection...")

                        // Reconnect without closing response channel
                        reconnect()

                        // Retry the send with new session
                        val newEndpoint = messageEndpoint
                            ?: throw IllegalStateException("No endpoint available after reconnection")

                        val retryResponse = httpClient.post(newEndpoint) {
                            headers {
                                this@SseTransport.headers.forEach { (key, value) ->
                                    append(key, value)
                                }
                                append(HttpHeaders.ContentType, ContentType.Application.Json)
                            }
                            setBody(message)
                        }

                        // Store the response from retry
                        lastResponseBody = retryResponse.bodyAsText()
                        println("SSE: ✅ Successfully reconnected and retried message")
                        return@withContext
                    }
                }

                // Store the response body for receive() to return
                // This ensures request-response matching instead of relying on SSE stream order
                lastResponseBody = response.bodyAsText()
                println("📥 SSE: Stored response body for receive()")
            } catch (e: Exception) {
                lastResponseBody = null
                throw IllegalStateException("Failed to send message via SSE: ${e.message}", e)
            }
        }
    }

    override suspend fun receive(): String? {
        // Return the response from the POST request directly
        // This guarantees correct request-response matching
        // (Previously read from SSE channel which could arrive out of order)
        return lastResponseBody.also {
            lastResponseBody = null // Clear after reading
        }
    }
}



/**
 * Create a stdio-based MCP client.
 */
suspend fun createStdioClient(params: StdioServerParameters): Transport {
    return StdioTransport(params)
}

/**
 * Create an SSE-based MCP client.
 */
suspend fun createSseClient(url: String, headers: Map<String, String> = emptyMap(), httpClient: HttpClient): Transport {
    return SseTransport(url, headers, httpClient)
}
