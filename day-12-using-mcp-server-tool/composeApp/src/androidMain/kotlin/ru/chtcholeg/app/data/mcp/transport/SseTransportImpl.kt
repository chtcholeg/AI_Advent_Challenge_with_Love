package ru.chtcholeg.app.data.mcp.transport

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.chtcholeg.app.data.mcp.stub.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Android implementation of SSE (Server-Sent Events) transport using Ktor.
 * Connects to an HTTP endpoint and communicates via SSE for receiving and POST for sending.
 */
class SseTransportImpl(
    private val url: String,
    private val headers: Map<String, String>,
    private val httpClient: HttpClient
) : Transport {
    private var responseChannel = Channel<String>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isActive = false
    private var sseJob: Job? = null
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
                            this@SseTransportImpl.headers.forEach { (key, value) ->
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
                        this@SseTransportImpl.headers.forEach { (key, value) ->
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

    override suspend fun send(message: String) {
        withContext(Dispatchers.IO) {
            try {
                // Wait for endpoint with timeout (10 seconds)
                val endpoint = withTimeoutOrNull(10.seconds) {
                    endpointReceived.await()
                } ?: throw IllegalStateException("Timeout waiting for message endpoint from server")

                val response = httpClient.post(endpoint) {
                    headers {
                        this@SseTransportImpl.headers.forEach { (key, value) ->
                            append(key, value)
                        }
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                    }
                    setBody(message)
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

                        httpClient.post(newEndpoint) {
                            headers {
                                this@SseTransportImpl.headers.forEach { (key, value) ->
                                    append(key, value)
                                }
                                append(HttpHeaders.ContentType, ContentType.Application.Json)
                            }
                            setBody(message)
                        }

                        println("SSE: ✅ Successfully reconnected and retried message")
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("Failed to send message via SSE: ${e.message}", e)
            }
        }
    }

    override suspend fun receive(): String? {
        return try {
            responseChannel.receive()
        } catch (e: Exception) {
            null
        }
    }
}
