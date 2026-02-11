package ru.chtcholeg.shared.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenAI-compatible data classes for HuggingFace tool calling.
 * HuggingFace uses the OpenAI format which differs from GigaChat:
 * - Request: `tools` array (not `functions`)
 * - Response: `tool_calls` in message (not `function_call`), `finish_reason: "tool_calls"` (not `"function_call"`)
 * - Tool results: `role: "tool"` with `tool_call_id` (not `role: "function"` with `name`)
 */

// ── Request models ──

@Serializable
data class HfChatRequest(
    val model: String,
    val messages: List<HfMessage>,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Float? = null,
    val stream: Boolean = false,
    val tools: List<HfTool>? = null
)

@Serializable
data class HfMessage(
    val role: String,
    val content: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<HfToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
data class HfTool(
    val type: String,
    val function: HfToolFunction
)

@Serializable
data class HfToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

// ── Response models ──

@Serializable
data class HfChatResponse(
    val choices: List<HfChoice>,
    val created: Long = 0,
    val model: String = "",
    val usage: Usage? = null
)

@Serializable
data class HfChoice(
    val message: HfMessage,
    val index: Int = 0,
    @SerialName("finish_reason") val finishReason: String? = null
)

// ── Tool call in response ──

@Serializable
data class HfToolCall(
    val id: String,
    val type: String = "function",
    val function: HfToolCallFunction
)

@Serializable
data class HfToolCallFunction(
    val name: String,
    val arguments: String  // JSON string of arguments
)
