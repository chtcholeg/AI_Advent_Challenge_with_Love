package ru.chtcholeg.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
) {
    val title: String
        get() {
            val firstUserMsg = messages.firstOrNull { it.isFromUser }
            return firstUserMsg?.content?.take(40)?.let {
                if (firstUserMsg.content.length > 40) "$it..." else it
            } ?: "New Chat"
        }
}
