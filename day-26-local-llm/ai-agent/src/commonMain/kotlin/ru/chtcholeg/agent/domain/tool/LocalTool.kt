package ru.chtcholeg.agent.domain.tool

import kotlinx.serialization.json.JsonElement
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * Base interface for local tools that execute directly in the agent.
 * These tools don't require external MCP servers.
 */
interface LocalTool {
    /**
     * Unique name of the tool.
     */
    val name: String

    /**
     * Human-readable description for AI to understand when to use this tool.
     */
    val description: String

    /**
     * JSON Schema describing the tool's input parameters.
     */
    val inputSchema: JsonElement

    /**
     * Execute the tool with given arguments.
     */
    suspend fun execute(arguments: JsonElement): McpToolResult
}
