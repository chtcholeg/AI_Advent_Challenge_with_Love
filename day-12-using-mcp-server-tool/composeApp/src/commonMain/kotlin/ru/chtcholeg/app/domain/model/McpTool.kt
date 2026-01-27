package ru.chtcholeg.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Few-shot example for function calling.
 */
data class McpFewShotExample(
    val request: String,
    val params: JsonObject
)

/**
 * Represents an MCP tool (function) that can be called by the AI.
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonElement,  // JSON Schema for tool parameters
    val serverId: String,
    val fewShotExamples: List<McpFewShotExample> = emptyList()
)

/**
 * Result of executing an MCP tool.
 */
@Serializable
data class McpToolResult(
    val content: String,
    val isError: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)
