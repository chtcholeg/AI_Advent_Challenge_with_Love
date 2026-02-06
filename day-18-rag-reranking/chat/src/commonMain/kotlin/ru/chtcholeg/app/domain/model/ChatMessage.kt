package ru.chtcholeg.app.domain.model

import kotlinx.datetime.Clock
import kotlin.random.Random

enum class MessageType {
    USER,
    AI,
    SYSTEM,
    REMINDER
}

data class ChatMessage(
    val id: String = generateId(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val messageType: MessageType = if (isFromUser) MessageType.USER else MessageType.AI,
    val executionTimeMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val isSummary: Boolean = false,
    val compressedMessageCount: Int? = null
)

private fun generateId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..16)
        .map { chars[Random.nextInt(chars.length)] }
        .joinToString("")
}
