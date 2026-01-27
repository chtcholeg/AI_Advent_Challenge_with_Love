package ru.chtcholeg.app.domain.model

data class AiResponse(
    val content: String,
    val executionTimeMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val isError: Boolean = false
)
