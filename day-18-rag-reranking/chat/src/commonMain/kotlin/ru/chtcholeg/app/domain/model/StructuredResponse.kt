package ru.chtcholeg.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Structured JSON response format for AI answers
 */
@Serializable
data class StructuredResponse(
    @SerialName("question_short")
    val questionShort: String,
    @SerialName("response")
    val response: String,
    @SerialName("responder_role")
    val responderRole: String,
    @SerialName("unicode_symbols")
    val unicodeSymbols: String
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Try to parse a JSON string into StructuredResponse
         * Returns null if parsing fails
         */
        fun tryParse(jsonString: String): StructuredResponse? {
            return try {
                // Remove markdown code blocks if present
                val cleanedJson = jsonString
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                json.decodeFromString<StructuredResponse>(cleanedJson)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Check if a string looks like it might be a structured JSON response
         */
        fun looksLikeStructuredResponse(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.startsWith("{") &&
                   trimmed.endsWith("}") &&
                   (trimmed.contains("\"question_short\"") ||
                    trimmed.contains("question_short"))
        }
    }
}
