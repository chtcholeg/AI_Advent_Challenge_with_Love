package ru.chtcholeg.agent.domain.model

import ru.chtcholeg.shared.domain.model.Model

data class AiSettings(
    val model: Model = Model.GigaChat,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val repetitionPenalty: Float = 1.0f,
    val showSystemMessages: Boolean = false,  // Show TOOL_CALL, TOOL_RESULT, SYSTEM messages
    val ragMode: RagMode = RagMode.OFF,
    val indexPath: String = "",
    // Reranker settings
    val rerankerEnabled: Boolean = false,
    val rerankerThreshold: Float = 0.5f,       // Stricter threshold for second stage
    val ragInitialTopK: Int = 10,               // Candidates to fetch from vector store
    val ragFinalTopK: Int = 3,                  // Results to keep after reranking
    val scoreGapThreshold: Float = 0.15f        // Cut off when similarity drops by this much
) {
    fun validated(): AiSettings = copy(
        temperature = temperature.coerceIn(0.0f, 2.0f),
        topP = topP.coerceIn(0.0f, 1.0f),
        maxTokens = maxTokens.coerceIn(1, 8192),
        repetitionPenalty = repetitionPenalty.coerceIn(0.0f, 2.0f),
        rerankerThreshold = rerankerThreshold.coerceIn(0.0f, 1.0f),
        ragInitialTopK = ragInitialTopK.coerceIn(1, 50),
        ragFinalTopK = ragFinalTopK.coerceIn(1, 20),
        scoreGapThreshold = scoreGapThreshold.coerceIn(0.0f, 0.5f)
    )
}
