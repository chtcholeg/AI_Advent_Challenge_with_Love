package ru.chtcholeg.app.domain.model

import kotlinx.serialization.Serializable

/**
 * AI model configuration settings
 */
@Serializable
data class AiSettings(
    val model: String = DEFAULT_MODEL,
    val temperature: Float? = DEFAULT_TEMPERATURE,
    val topP: Float? = DEFAULT_TOP_P,
    val maxTokens: Int? = DEFAULT_MAX_TOKENS,
    val repetitionPenalty: Float? = DEFAULT_REPETITION_PENALTY
) {
    companion object {
        const val DEFAULT_MODEL = "GigaChat"
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_REPETITION_PENALTY = 1.0f

        // Valid ranges
        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f
        const val MIN_TOP_P = 0.0f
        const val MAX_TOP_P = 1.0f
        const val MIN_MAX_TOKENS = 1
        const val MAX_MAX_TOKENS = 8192
        const val MIN_REPETITION_PENALTY = 0.0f
        const val MAX_REPETITION_PENALTY = 2.0f

        val DEFAULT = AiSettings()
    }

    /**
     * Validate and clamp settings to valid ranges
     */
    fun validated(): AiSettings = copy(
        temperature = temperature?.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE),
        topP = topP?.coerceIn(MIN_TOP_P, MAX_TOP_P),
        maxTokens = maxTokens?.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS),
        repetitionPenalty = repetitionPenalty?.coerceIn(MIN_REPETITION_PENALTY, MAX_REPETITION_PENALTY)
    )
}
