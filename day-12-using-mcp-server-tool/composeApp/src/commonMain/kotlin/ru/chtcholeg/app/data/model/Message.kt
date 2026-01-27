package ru.chtcholeg.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,  // "user", "assistant", "system", "function"
    val content: String? = null,
    val name: String? = null,  // Function name for role="function"
    @SerialName("function_call") val functionCall: FunctionCall? = null  // Function call for role="assistant"
) {
    val isFromUser: Boolean
        get() = role.lowercase() == USER

    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val SYSTEM = "system"
        const val FUNCTION = "function"
    }
}
