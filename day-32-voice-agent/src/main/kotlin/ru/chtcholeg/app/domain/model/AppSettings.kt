package ru.chtcholeg.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val selectedModelId: String = "GigaChat",
    val gigachatClientId: String = "",
    val gigachatClientSecret: String = "",
    val modelParameters: ModelParameters = ModelParameters(),
    val userProfile: UserProfile = UserProfile(),
    val voiceKeywords: VoiceKeywords = VoiceKeywords()
)

@Serializable
data class VoiceKeywords(
    val enabled: Boolean = false,
    val wakeWord: String = "компьютер",
    val stopSendWord: String = "отправить",
    val stopCancelWord: String = "отмена"
)

@Serializable
data class ModelParameters(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val repetitionPenalty: Float = 1.0f
) {
    companion object {
        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f
        const val MIN_TOP_P = 0.0f
        const val MAX_TOP_P = 1.0f
        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 100
        const val MIN_MAX_TOKENS = 64
        const val MAX_MAX_TOKENS = 8192
        const val MIN_REPETITION_PENALTY = 0.0f
        const val MAX_REPETITION_PENALTY = 2.0f
    }
}

@Serializable
data class UserProfile(
    val isEnabled: Boolean = false,
    val name: String = "",
    val preferredLanguage: String = "",
    val areasOfInterest: String = "",
    val detailLevel: DetailLevel = DetailLevel.MEDIUM,
    val useExamples: Boolean = true,
    val additionalDescription: String = ""
)

@Serializable
enum class DetailLevel(val displayName: String) {
    BRIEF("Краткий"),
    MEDIUM("Средний"),
    DETAILED("Подробный")
}

fun UserProfile.buildSystemPrompt(): String {
    if (!isEnabled) return ""
    return buildString {
        if (name.isNotBlank()) append("Обращайся к пользователю по имени: $name. ")
        if (preferredLanguage.isNotBlank()) append("Отвечай на языке: $preferredLanguage. ")
        if (areasOfInterest.isNotBlank()) append("Области интересов пользователя: $areasOfInterest. ")
        append("Уровень детализации ответов: ${detailLevel.displayName}. ")
        if (useExamples) append("Используй практические примеры в ответах. ")
        else append("Отвечай без конкретных примеров, только теория. ")
        if (additionalDescription.isNotBlank()) append("Дополнительно о пользователе: $additionalDescription")
    }.trim()
}
