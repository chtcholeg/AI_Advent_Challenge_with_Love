package ru.chtcholeg.app.domain.model

import kotlinx.datetime.Clock
import kotlin.random.Random

data class ChatSession(
    val id: String = generateSessionId(),
    val title: String,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val modelName: String,
    val systemPrompt: String? = null,
    val isArchived: Boolean = false,
    val lastMessage: ChatMessage? = null,
    val messageCount: Int = 0,
    val isCompressed: Boolean = false,
    val originalSessionId: String? = null,
    val compressionPoint: Long? = null
)

private fun generateSessionId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..20)
        .map { chars[Random.nextInt(chars.length)] }
        .joinToString("")
}
