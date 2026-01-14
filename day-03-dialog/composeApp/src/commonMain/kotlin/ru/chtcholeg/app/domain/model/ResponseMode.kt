package ru.chtcholeg.app.domain.model

import kotlinx.serialization.Serializable

/**
 * AI response mode configuration
 */
@Serializable
enum class ResponseMode(val displayName: String, val description: String) {
    /**
     * Normal chat mode - AI responds directly to user questions
     */
    NORMAL(
        displayName = "Normal Mode",
        description = "Standard conversational AI responses"
    ),

    /**
     * Structured JSON response mode - AI returns data in strict JSON format
     */
    STRUCTURED_JSON(
        displayName = "Structured JSON Response",
        description = "AI responds in strict JSON format with question summary, detailed response, expert role, and unicode symbols"
    ),

    /**
     * Dialog mode - AI asks clarifying questions to gather complete information
     */
    DIALOG(
        displayName = "Dialog Mode",
        description = "AI asks clarifying questions one at a time to gather all necessary information before providing comprehensive result"
    );

    companion object {
        val DEFAULT = NORMAL

        /**
         * Get ResponseMode from string identifier
         */
        fun fromString(value: String?): ResponseMode {
            return when (value?.uppercase()) {
                "NORMAL" -> NORMAL
                "STRUCTURED_JSON", "JSON" -> STRUCTURED_JSON
                "DIALOG" -> DIALOG
                else -> DEFAULT
            }
        }
    }
}
