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
     * Structured XML response mode - AI returns data in strict XML format
     */
    STRUCTURED_XML(
        displayName = "Structured XML Response",
        description = "AI responds in strict XML format with question summary, detailed response, expert role, and unicode symbols"
    ),

    /**
     * Dialog mode - AI asks clarifying questions to gather complete information
     */
    DIALOG(
        displayName = "Dialog Mode",
        description = "AI asks clarifying questions one at a time to gather all necessary information before providing comprehensive result"
    ),

    /**
     * Step-by-step reasoning mode - AI solves problems step by step
     */
    STEP_BY_STEP(
        displayName = "Step-by-Step Reasoning",
        description = "AI solves problems by breaking them down into clear, logical steps"
    ),

    /**
     * Expert panel mode - AI simulates a group of experts discussing the problem
     */
    EXPERT_PANEL(
        displayName = "Expert Panel Discussion",
        description = "AI simulates a panel of experts debating and forming a consensus on the topic"
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
                "STRUCTURED_XML", "XML" -> STRUCTURED_XML
                "DIALOG" -> DIALOG
                "STEP_BY_STEP", "STEP" -> STEP_BY_STEP
                "EXPERT_PANEL", "EXPERT" -> EXPERT_PANEL
                else -> DEFAULT
            }
        }
    }
}
