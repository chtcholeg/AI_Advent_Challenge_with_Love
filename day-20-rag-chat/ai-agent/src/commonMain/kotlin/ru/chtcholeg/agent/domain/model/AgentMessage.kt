package ru.chtcholeg.agent.domain.model

import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Reference to a source document chunk used in RAG context.
 */
data class SourceReference(
    val filePath: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val similarity: Float,
    val isUrl: Boolean = false,  // true if source is a web page URL
    val text: String = ""         // the actual text of the chunk (citation/quote)
)

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
    val imageBase64: String? = null,  // Base64-encoded image data (for screenshots)
    val sources: Map<Int, SourceReference>? = null  // key = source number (1, 2, 3...)
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
