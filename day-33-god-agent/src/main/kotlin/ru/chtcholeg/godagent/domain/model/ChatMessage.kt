package ru.chtcholeg.godagent.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole = MessageRole.USER,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val executionTimeMs: Long? = null,
    val isSummary: Boolean = false
) {
    val isFromUser: Boolean get() = role == MessageRole.USER
}

@Serializable
enum class MessageRole { USER, ASSISTANT, TOOL_CALL, TOOL_RESULT, SUMMARY }
