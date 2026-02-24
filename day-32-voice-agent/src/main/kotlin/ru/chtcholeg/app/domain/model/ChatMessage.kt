package ru.chtcholeg.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = generateMessageId(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val executionTimeMs: Long? = null
)

private fun generateMessageId(): String =
    System.currentTimeMillis().toString() + (0..9999).random().toString().padStart(4, '0')
