package com.example.mcp.plugins

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Result of a tool execution
 */
sealed class ToolResult {
    data class Success(val content: List<ToolContent>) : ToolResult()
    data class Error(val message: String, val code: String? = null) : ToolResult()
}

/**
 * Content types that can be returned by tools
 */
sealed class ToolContent {
    data class Text(val text: String) : ToolContent()
    data class Json(val data: JsonElement) : ToolContent()
}

/**
 * JSON Schema for tool input parameters
 */
data class JsonSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val description: String? = null
)

data class PropertySchema(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null,
    val default: JsonElement? = null
)

/**
 * Few-shot example for GigaChat function calling.
 * Shows the model how to use the function with specific user requests.
 */
data class FewShotExample(
    val request: String,  // Example user query that should trigger this function
    val params: JsonObject  // Expected function parameters for this query
)

/**
 * Interface for MCP tools
 */
interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonSchema

    /**
     * Few-shot examples for GigaChat function calling.
     * Default is empty list (no examples).
     */
    val fewShotExamples: List<FewShotExample>
        get() = emptyList()

    suspend fun execute(arguments: JsonObject): ToolResult
}

/**
 * Interface for MCP plugins
 */
interface McpPlugin {
    val name: String
    val version: String
    val description: String

    fun getTools(): List<McpTool>

    fun initialize() {}
    fun shutdown() {}
}
