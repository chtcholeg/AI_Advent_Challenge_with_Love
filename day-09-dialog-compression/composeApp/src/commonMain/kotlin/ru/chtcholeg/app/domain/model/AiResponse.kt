package ru.chtcholeg.app.domain.model

data class AiResponse(
    val content: String,
    val executionTimeMs: Long,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)
