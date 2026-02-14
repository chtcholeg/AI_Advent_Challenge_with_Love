package ru.chtcholeg.agent.domain.model

import kotlinx.serialization.Serializable

/**
 * User question with multiple choice options.
 */
@Serializable
data class UserQuestion(
    val question: String,
    val header: String,  // Short label (max 12 chars) for chip/tag display
    val options: List<QuestionOption>,
    val multiSelect: Boolean = false
)

/**
 * Single option in a question.
 */
@Serializable
data class QuestionOption(
    val label: String,  // Display text (1-5 words)
    val description: String  // Explanation of this option
)

/**
 * User's answer to a question.
 */
@Serializable
data class UserAnswer(
    val questionId: String,
    val selectedOptions: List<String>,  // Selected option labels
    val customText: String? = null  // Custom text if user chose "Other"
)
