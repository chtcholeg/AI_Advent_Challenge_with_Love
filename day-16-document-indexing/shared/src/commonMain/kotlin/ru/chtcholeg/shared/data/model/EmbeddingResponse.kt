package ru.chtcholeg.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingData>,
    val model: String,
    val `object`: String = "list"
)

@Serializable
data class EmbeddingData(
    val embedding: List<Float>,
    val index: Int,
    val `object`: String = "embedding"
)
