package com.example.mcp.mcp

import com.example.mcp.models.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap

/**
 * SSE (Server-Sent Events) Transport for MCP
 */
class SseTransport(
    private val protocolHandler: McpProtocolHandler
) {
    private val logger = LoggerFactory.getLogger(SseTransport::class.java)
    private val sessions = ConcurrentHashMap<String, SseSession>()
    private val json = Json { ignoreUnknownKeys = true }

    data class SseSession(
        val id: String,
        val messageChannel: Channel<String> = Channel(Channel.UNLIMITED),
        val createdAt: Long = System.currentTimeMillis()
    )

    fun setupRoutes(route: Route) {
        route.apply {
            // SSE endpoint for establishing connection
            get("/sse") {
                handleSseConnection(call)
            }

            // Message endpoint for receiving client messages
            post("/message") {
                handleMessage(call)
            }

            // Alternative: combined endpoint that accepts session ID
            post("/sse/{sessionId}") {
                val sessionId = call.parameters["sessionId"]
                if (sessionId != null) {
                    handleSessionMessage(call, sessionId)
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Session ID required"))
                }
            }
        }
    }

    private suspend fun handleSseConnection(call: ApplicationCall) {
        val sessionId = UUID.randomUUID().toString()
        val session = SseSession(id = sessionId)
        sessions[sessionId] = session

        logger.info("New SSE connection established: $sessionId")

        call.response.cacheControl(CacheControl.NoCache(null))
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            try {
                // Send endpoint event with session info
                write("event: endpoint\n")
                write("data: /message?sessionId=$sessionId\n\n")
                flush()

                // Keep connection alive and send messages
                while (coroutineContext.isActive) {
                    try {
                        val message = session.messageChannel.tryReceive().getOrNull()
                        if (message != null) {
                            write("event: message\n")
                            write("data: $message\n\n")
                            flush()
                        } else {
                            // Send keepalive ping every 30 seconds
                            delay(30000)
                            write(": keepalive\n\n")
                            flush()
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        logger.debug("Message channel closed for session: $sessionId")
                        break
                    } catch (e: CancellationException) {
                        logger.debug("SSE connection cancelled for session: $sessionId")
                        throw e // Re-throw to properly handle coroutine cancellation
                    } catch (e: IOException) {
                        logger.debug("Connection closed by client for session: $sessionId")
                        break
                    } catch (e: Exception) {
                        // Handle channel write exceptions (client disconnected)
                        if (e.message?.contains("Cannot write to a channel") == true ||
                            e.cause?.message?.contains("Cannot write to a channel") == true) {
                            logger.debug("Client disconnected for session: $sessionId")
                            break
                        }
                        logger.warn("Unexpected error in SSE session $sessionId: ${e.message}")
                        break
                    }
                }
            } catch (e: CancellationException) {
                logger.debug("SSE handler cancelled for session: $sessionId")
            } catch (e: IOException) {
                logger.debug("Connection closed during setup for session: $sessionId")
            } catch (e: Exception) {
                if (e.message?.contains("Cannot write to a channel") != true &&
                    e.cause?.message?.contains("Cannot write to a channel") != true) {
                    logger.warn("Error in SSE connection $sessionId: ${e.message}")
                }
            } finally {
                sessions.remove(sessionId)
                session.messageChannel.close()
                logger.info("SSE connection closed: $sessionId")
            }
        }
    }

    private suspend fun handleMessage(call: ApplicationCall) {
        val sessionId = call.request.queryParameters["sessionId"]
        if (sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Session ID required"))
            return
        }

        handleSessionMessage(call, sessionId)
    }

    private suspend fun handleSessionMessage(call: ApplicationCall, sessionId: String) {
        val session = sessions[sessionId]
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Session not found"))
            return
        }

        val requestBody = call.receiveText()
        logger.debug("Received message for session $sessionId: $requestBody")

        val response = protocolHandler.handleRequest(requestBody)

        // Send response through SSE channel
        session.messageChannel.send(response)

        // Also return response directly for non-SSE clients
        call.respondText(response, ContentType.Application.Json)
    }

    fun getActiveSessionCount(): Int = sessions.size

    fun closeSession(sessionId: String) {
        sessions[sessionId]?.let { session ->
            session.messageChannel.close()
            sessions.remove(sessionId)
            logger.info("Session closed: $sessionId")
        }
    }

    fun closeAllSessions() {
        for ((id, session) in sessions) {
            session.messageChannel.close()
            logger.info("Session closed: $id")
        }
        sessions.clear()
    }
}
