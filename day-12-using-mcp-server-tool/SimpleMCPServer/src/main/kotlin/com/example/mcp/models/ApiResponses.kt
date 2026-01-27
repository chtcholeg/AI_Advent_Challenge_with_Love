package com.example.mcp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Standard error response
 */
@Serializable
data class ErrorResponse(
    val error: String
)

/**
 * Health check response
 */
@Serializable
data class HealthResponse(
    val status: String,
    val uptime: Long,
    val uptime_human: String,
    val tools_count: Int,
    val active_sessions: Int
)

/**
 * Few-shot example for tool documentation
 */
@Serializable
data class ToolFewShotExample(
    val request: String,
    val params: JsonObject
)

/**
 * Tool info for listing
 */
@Serializable
data class ToolInfo(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonObject,
    @SerialName("few_shot_examples")
    val fewShotExamples: List<ToolFewShotExample>? = null
)

/**
 * Tools list response
 */
@Serializable
data class ToolsResponse(
    val tools: List<ToolInfo>
)

/**
 * Server info response
 */
@Serializable
data class ServerInfoResponse(
    val name: String,
    val version: String,
    val protocol: String,
    val endpoints: Map<String, String>
)
