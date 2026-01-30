package ru.chtcholeg.agent.domain.model

import ru.chtcholeg.shared.domain.model.Model

data class AiSettings(
    val model: Model = Model.GigaChat,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val repetitionPenalty: Float = 1.0f,
    val showSystemMessages: Boolean = false  // Show TOOL_CALL, TOOL_RESULT, SYSTEM messages
) {
    fun validated(): AiSettings = copy(
        temperature = temperature.coerceIn(0.0f, 2.0f),
        topP = topP.coerceIn(0.0f, 1.0f),
        maxTokens = maxTokens.coerceIn(1, 8192),
        repetitionPenalty = repetitionPenalty.coerceIn(0.0f, 2.0f)
    )
}
