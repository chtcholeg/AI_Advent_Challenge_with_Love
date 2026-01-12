package ru.chtcholeg.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,  // "user", "assistant", "system", "function"
    val content: String
) {
    val isFromUser: Boolean
        get() = role.lowercase() == USER

    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val SYSTEM = "system"
    }
}
