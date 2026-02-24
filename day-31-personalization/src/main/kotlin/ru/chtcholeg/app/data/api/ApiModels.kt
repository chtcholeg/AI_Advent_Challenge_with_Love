package ru.chtcholeg.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Shared ────────────────────────────────────────────────────────────────────

@Serializable
data class ApiMessage(
    val role: String,
    val content: String
)

data class ApiResponse(
    val content: String,
    val executionTimeMs: Long
)

// ── GigaChat ──────────────────────────────────────────────────────────────────

@Serializable
data class GigaChatAuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_at") val expiresAt: Long
)

@Serializable
data class GigaChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Float? = null,
    val stream: Boolean = false
)

@Serializable
data class GigaChatResponse(
    val choices: List<GigaChatChoice>,
    val usage: GigaChatUsage? = null
)

@Serializable
data class GigaChatChoice(
    val message: ApiMessage
)

@Serializable
data class GigaChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

// ── Ollama ────────────────────────────────────────────────────────────────────

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModelInfo> = emptyList()
)

@Serializable
data class OllamaModelInfo(
    val name: String,
    val size: Long = 0
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions = OllamaOptions()
)

@Serializable
data class OllamaOptions(
    val temperature: Float = 0.7f,
    @SerialName("top_p") val topP: Float = 0.9f,
    @SerialName("top_k") val topK: Int = 40,
    @SerialName("num_predict") val numPredict: Int = 2048,
    @SerialName("repeat_penalty") val repeatPenalty: Float = 1.0f
)

@Serializable
data class OllamaChatResponse(
    val message: ApiMessage? = null,
    val done: Boolean = false,
    @SerialName("total_duration") val totalDuration: Long? = null,
    @SerialName("eval_count") val evalCount: Int? = null
)
