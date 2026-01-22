package ru.chtcholeg.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val choices: List<Choice>,
    val created: Long,
    val model: String,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: Message,
    val index: Int,
    @SerialName("finish_reason") val finishReason: String
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)
