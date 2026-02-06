package ru.chtcholeg.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingRequest(
    val model: String,
    val input: List<String>
)
