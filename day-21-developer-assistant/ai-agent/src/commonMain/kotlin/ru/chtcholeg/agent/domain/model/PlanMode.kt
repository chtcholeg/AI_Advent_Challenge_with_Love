package ru.chtcholeg.agent.domain.model

import kotlinx.serialization.Serializable

/**
 * Planning mode state.
 */
@Serializable
data class PlanModeState(
    val isActive: Boolean = false,
    val planFilePath: String? = null,
    val originalTask: String? = null,
    val enteredAt: Long? = null,
    val allowedPrompts: List<AllowedPrompt> = emptyList()
)

/**
 * Prompt-based permission needed to implement the plan.
 * Describes categories of actions rather than specific commands.
 */
@Serializable
data class AllowedPrompt(
    val tool: String,  // e.g., "Bash"
    val prompt: String  // e.g., "run tests", "install dependencies"
)

/**
 * Plan approval result.
 */
@Serializable
data class PlanApproval(
    val approved: Boolean,
    val planContent: String,
    val allowedPrompts: List<AllowedPrompt>,
    val feedback: String? = null  // User feedback if not approved
)
