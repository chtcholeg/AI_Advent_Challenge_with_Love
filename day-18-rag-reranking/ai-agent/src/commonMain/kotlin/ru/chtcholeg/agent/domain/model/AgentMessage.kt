package ru.chtcholeg.agent.domain.model

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Represents a message in the agent conversation
 */
data class AgentMessage(
    val id: String = generateId(),
    val content: String,
    val type: MessageType,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val executionTimeMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val imageBase64: String? = null  // Base64-encoded image data (for screenshots)
) {
    companion object {
        private fun generateId(): String =
            "${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt()}"
    }
}

/**
 * Type of message in the conversation
 */
enum class MessageType {
    USER,           // User input
    AI,             // AI response
    TOOL_CALL,      // AI calling a tool
    TOOL_RESULT,    // Result from tool execution
    SCREENSHOT,     // Screenshot image from device
    SYSTEM,         // System message
    ERROR,          // Error message
    RAG_CONTEXT     // Retrieved document chunks summary
}
