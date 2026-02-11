package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import ru.chtcholeg.agent.domain.model.AllowedPrompt
import ru.chtcholeg.agent.domain.model.PlanModeState
import ru.chtcholeg.agent.util.FileSystem

/**
 * Repository for managing planning mode state.
 */
class PlanModeRepository(
    private val fileSystem: FileSystem
) {
    private val _planModeState = MutableStateFlow(PlanModeState())
    val planModeState: StateFlow<PlanModeState> = _planModeState.asStateFlow()

    /**
     * Enter planning mode.
     */
    suspend fun enterPlanMode(task: String): String {
        if (_planModeState.value.isActive) {
            throw IllegalStateException("Already in plan mode")
        }

        // Generate plan file path in current directory
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val planFilePath = "${fileSystem.getCurrentDirectory()}/.claude_plan_$timestamp.md"

        // Create initial plan file with template
        val initialPlanContent = buildString {
            appendLine("# Implementation Plan")
            appendLine()
            appendLine("**Task:** $task")
            appendLine()
            appendLine("**Created:** ${Clock.System.now()}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Analysis")
            appendLine()
            appendLine("<!-- Analyze the task and codebase here -->")
            appendLine()
            appendLine("## Approach")
            appendLine()
            appendLine("<!-- Describe the implementation approach -->")
            appendLine()
            appendLine("## Steps")
            appendLine()
            appendLine("<!-- List implementation steps -->")
            appendLine()
            appendLine("1. Step 1")
            appendLine("2. Step 2")
            appendLine()
            appendLine("## Files to Modify")
            appendLine()
            appendLine("<!-- List files that will be modified -->")
            appendLine()
            appendLine("- `path/to/file1.kt`")
            appendLine("- `path/to/file2.kt`")
            appendLine()
            appendLine("## Considerations")
            appendLine()
            appendLine("<!-- Any important considerations, trade-offs, or risks -->")
            appendLine()
        }

        fileSystem.writeFile(planFilePath, initialPlanContent)

        _planModeState.value = PlanModeState(
            isActive = true,
            planFilePath = planFilePath,
            originalTask = task,
            enteredAt = Clock.System.now().toEpochMilliseconds()
        )

        return planFilePath
    }

    /**
     * Exit planning mode with approval.
     */
    suspend fun exitPlanMode(allowedPrompts: List<AllowedPrompt>): String? {
        if (!_planModeState.value.isActive) {
            throw IllegalStateException("Not in plan mode")
        }

        val planFilePath = _planModeState.value.planFilePath
            ?: throw IllegalStateException("No plan file path")

        // Update state with allowed prompts before exiting
        _planModeState.value = _planModeState.value.copy(
            allowedPrompts = allowedPrompts
        )

        // Don't exit yet - wait for user approval
        // This is handled by the tool
        return planFilePath
    }

    /**
     * Cancel planning mode without approval.
     */
    suspend fun cancelPlanMode() {
        if (!_planModeState.value.isActive) {
            return
        }

        // Delete plan file if it exists
        val planFilePath = _planModeState.value.planFilePath
        if (planFilePath != null && fileSystem.fileExists(planFilePath)) {
            // Keep the file for reference, just exit the mode
        }

        _planModeState.value = PlanModeState()
    }

    /**
     * Complete planning mode after user approval.
     */
    fun completePlanMode() {
        _planModeState.value = PlanModeState()
    }

    /**
     * Read plan file content.
     */
    suspend fun readPlanFile(): String? {
        val planFilePath = _planModeState.value.planFilePath ?: return null
        return if (fileSystem.fileExists(planFilePath)) {
            fileSystem.readFile(planFilePath)
        } else {
            null
        }
    }

    /**
     * Update plan file content.
     */
    suspend fun updatePlanFile(content: String) {
        val planFilePath = _planModeState.value.planFilePath
            ?: throw IllegalStateException("No plan file path")

        fileSystem.writeFile(planFilePath, content)
    }
}
