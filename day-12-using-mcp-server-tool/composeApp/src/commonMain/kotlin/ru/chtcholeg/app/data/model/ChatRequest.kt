package ru.chtcholeg.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Float? = null,
    val stream: Boolean = false,
    val functions: List<GigaChatFunction>? = null
)
