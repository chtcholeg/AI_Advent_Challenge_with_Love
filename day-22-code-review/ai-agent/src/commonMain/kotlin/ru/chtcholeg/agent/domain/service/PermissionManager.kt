package ru.chtcholeg.agent.domain.service

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Permission system for controlling access to dangerous tool operations.
 *
 * Tools are classified as:
 * - SAFE: always allowed (read, glob, grep, task_list, task_get, ask_user_question)
 * - MODERATE: allowed but logged (write, edit, task_create, task_update)
 * - DANGEROUS: checked against whitelist/blacklist (bash)
 * - RESTRICTED: blocked in plan mode (bash, edit)
 */
class PermissionManager {

    /**
     * Check if a tool call is allowed.
     * Returns PermissionResult with decision and optional reason.
     */
    fun checkPermission(
        toolName: String,
        arguments: JsonElement?,
        inPlanMode: Boolean
    ): PermissionResult {
        // Plan mode restrictions
        if (inPlanMode) {
            if (toolName !in PLAN_MODE_ALLOWED) {
                return PermissionResult.Denied(
                    "Tool '$toolName' is not allowed in plan mode. " +
                    "Allowed: ${PLAN_MODE_ALLOWED.joinToString(", ")}"
                )
            }
        }

        // Safe tools — always allowed
        if (toolName in SAFE_TOOLS) {
            return PermissionResult.Allowed
        }

        // Bash tool — check command against whitelist/blacklist
        if (toolName == "bash") {
            return checkBashPermission(arguments)
        }

        // Moderate tools (write, edit) — allowed but tracked
        if (toolName in MODERATE_TOOLS) {
            return PermissionResult.Allowed
        }

        // Unknown tools — allow by default (MCP server tools)
        return PermissionResult.Allowed
    }

    /**
     * Check bash command permission using a three-tier security model:
     * 1. Blacklist check: Block destructive commands (rm -rf, git push --force, etc.)
     * 2. Whitelist check: Auto-approve safe read-only commands (git status, ls, etc.)
     * 3. Default: Allow with warning for commands not in blacklist or whitelist
     *
     * @param arguments Tool arguments containing the command
     * @return PermissionResult indicating allowed/denied with reason
     */
    private fun checkBashPermission(arguments: JsonElement?): PermissionResult {
        val command = arguments?.jsonObject?.get("command")?.jsonPrimitive?.content
            ?: return PermissionResult.Allowed // can't check without command

        val trimmedCommand = command.trim()

        // Check blacklist first — always blocked
        for (pattern in BASH_BLACKLIST) {
            if (pattern.containsMatchIn(trimmedCommand)) {
                return PermissionResult.Denied(
                    "Command blocked by security policy: '$trimmedCommand'. " +
                    "This command is considered destructive and requires manual execution."
                )
            }
        }

        // Check whitelist — automatically safe
        for (pattern in BASH_WHITELIST) {
            if (pattern.matches(trimmedCommand)) {
                return PermissionResult.Allowed
            }
        }

        // Not in whitelist or blacklist — allowed but warn
        return PermissionResult.AllowedWithWarning(
            "Executing bash command: $trimmedCommand"
        )
    }

    companion object {
        val SAFE_TOOLS = setOf(
            "read", "glob", "grep",
            "task_list", "task_get",
            "ask_user_question",
            "enter_plan_mode", "exit_plan_mode"
        )

        val MODERATE_TOOLS = setOf(
            "write", "edit",
            "task_create", "task_update",
            "task"  // subagent launch
        )

        val PLAN_MODE_ALLOWED = setOf(
            "read", "glob", "grep",
            "write",  // for plan file only
            "ask_user_question",
            "enter_plan_mode", "exit_plan_mode",
            "task_list", "task_get"
        )

        // Bash commands that are always blocked
        val BASH_BLACKLIST = listOf(
            Regex("""rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|(-[a-zA-Z]*f[a-zA-Z]*r))\s"""),  // rm -rf
            Regex("""rm\s+-rf\s*$"""),
            Regex("""git\s+push\s+.*--force"""),     // git push --force
            Regex("""git\s+push\s+-f\b"""),           // git push -f
            Regex("""git\s+reset\s+--hard"""),        // git reset --hard
            Regex("""git\s+clean\s+-[a-zA-Z]*f"""),   // git clean -f
            Regex("""git\s+checkout\s+\.\s*$"""),      // git checkout .
            Regex(""">\s*/dev/sd[a-z]"""),              // write to disk device
            Regex("""mkfs\."""),                        // format filesystem
            Regex("""dd\s+.*of=/dev/"""),               // dd to device
            Regex(""":\s*\(\)\s*\{"""),                 // fork bomb
        )

        // Bash commands that are always allowed without confirmation
        val BASH_WHITELIST = listOf(
            Regex("""^git\s+status(\s.*)?$"""),
            Regex("""^git\s+log(\s.*)?$"""),
            Regex("""^git\s+diff(\s.*)?$"""),
            Regex("""^git\s+branch(\s.*)?$"""),
            Regex("""^git\s+show(\s.*)?$"""),
            Regex("""^git\s+remote(\s.*)?$"""),
            Regex("""^git\s+rev-parse(\s.*)?$"""),
            Regex("""^ls(\s.*)?$"""),
            Regex("""^pwd\s*$"""),
            Regex("""^echo\s.*$"""),
            Regex("""^which\s.*$"""),
            Regex("""^whoami\s*$"""),
            Regex("""^date\s*$"""),
            Regex("""^uname(\s.*)?$"""),
            Regex("""^(./)?gradlew\s.*$"""),
            Regex("""^python\s.*$"""),
            Regex("""^pip\s+install(\s.*)?$"""),
            Regex("""^npm\s+(install|run|test|build)(\s.*)?$"""),
            Regex("""^yarn\s+(install|run|test|build)(\s.*)?$"""),
            Regex("""^docker\s+(ps|images|logs)(\s.*)?$"""),
            Regex("""^cat\s.*$"""),
            Regex("""^head\s.*$"""),
            Regex("""^tail\s.*$"""),
            Regex("""^wc\s.*$"""),
            Regex("""^sort\s.*$"""),
            Regex("""^tree(\s.*)?$"""),
        )
    }
}

/**
 * Result of a permission check.
 */
sealed class PermissionResult {
    /** Tool call is allowed */
    data object Allowed : PermissionResult()

    /** Tool call is allowed but with a warning */
    data class AllowedWithWarning(val warning: String) : PermissionResult()

    /** Tool call is denied */
    data class Denied(val reason: String) : PermissionResult()
}
