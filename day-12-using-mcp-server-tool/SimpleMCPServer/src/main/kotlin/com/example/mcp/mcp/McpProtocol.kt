package com.example.mcp.mcp

import com.example.mcp.plugins.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * MCP Protocol version
 */
const val MCP_PROTOCOL_VERSION = "2024-11-05"

/**
 * JSON-RPC request
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

/**
 * JSON-RPC response
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * MCP Server capabilities
 */
@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability? = ToolsCapability()
)

@Serializable
data class ToolsCapability(
    val listChanged: Boolean = false
)

/**
 * MCP Server info
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

/**
 * Initialize result
 */
@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

/**
 * Few-shot example for serialization
 */
@Serializable
data class FewShotExampleDto(
    val request: String,
    val params: JsonObject
)

/**
 * Tool definition for listing
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val fewShotExamples: List<FewShotExampleDto>? = null
)

/**
 * Tools list result
 */
@Serializable
data class ToolsListResult(
    val tools: List<ToolDefinition>
)

/**
 * Tool call result content
 */
@Serializable
data class ToolResultContent(
    val type: String,
    val text: String? = null,
    val data: JsonElement? = null
)

/**
 * Tool call result
 */
@Serializable
data class CallToolResult(
    val content: List<ToolResultContent>,
    val isError: Boolean = false
)

/**
 * MCP Protocol handler
 */
class McpProtocolHandler(
    private val toolRegistry: ToolRegistry,
    private val serverName: String = "mcp-server",
    private val serverVersion: String = "1.0.0"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun handleRequest(requestJson: String): String {
        val request = try {
            json.decodeFromString<JsonRpcRequest>(requestJson)
        } catch (e: Exception) {
            return json.encodeToString(
                JsonRpcResponse.serializer(),
                JsonRpcResponse(
                    error = JsonRpcError(
                        code = -32700,
                        message = "Parse error: ${e.message}"
                    )
                )
            )
        }

        val result = when (request.method) {
            "initialize" -> handleInitialize(request)
            "initialized" -> handleInitialized(request)
            "tools/list" -> handleToolsList(request)
            "tools/call" -> handleToolsCall(request)
            "ping" -> handlePing(request)
            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32601,
                    message = "Method not found: ${request.method}"
                )
            )
        }

        return json.encodeToString(JsonRpcResponse.serializer(), result)
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val initResult = InitializeResult(
            protocolVersion = MCP_PROTOCOL_VERSION,
            capabilities = ServerCapabilities(
                tools = ToolsCapability(listChanged = false)
            ),
            serverInfo = ServerInfo(
                name = serverName,
                version = serverVersion
            )
        )

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(InitializeResult.serializer(), initResult)
        )
    }

    private fun handleInitialized(request: JsonRpcRequest): JsonRpcResponse {
        // Notification, no response needed but we'll acknowledge
        return JsonRpcResponse(
            id = request.id,
            result = JsonObject(emptyMap())
        )
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = toolRegistry.getAllTools().map { tool ->
            val examples = tool.fewShotExamples.takeIf { it.isNotEmpty() }?.map { ex ->
                FewShotExampleDto(request = ex.request, params = ex.params)
            }
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                inputSchema = schemaToJsonObject(tool.inputSchema),
                fewShotExamples = examples
            )
        }

        val result = ToolsListResult(tools = tools)

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(ToolsListResult.serializer(), result)
        )
    }

    private suspend fun handleToolsCall(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = -32602,
                message = "Invalid params: missing parameters"
            )
        )

        val toolName = params["name"]?.jsonPrimitive?.contentOrNull ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = -32602,
                message = "Invalid params: missing tool name"
            )
        )

        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        val tool = toolRegistry.getTool(toolName) ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = -32602,
                message = "Tool not found: $toolName"
            )
        )

        return try {
            val toolResult = tool.execute(arguments)
            val content = when (toolResult) {
                is ToolResult.Success -> {
                    CallToolResult(
                        content = toolResult.content.map { c ->
                            when (c) {
                                is ToolContent.Text -> ToolResultContent(type = "text", text = c.text)
                                is ToolContent.Json -> ToolResultContent(type = "json", data = c.data)
                            }
                        },
                        isError = false
                    )
                }
                is ToolResult.Error -> {
                    CallToolResult(
                        content = listOf(ToolResultContent(type = "text", text = toolResult.message)),
                        isError = true
                    )
                }
            }

            JsonRpcResponse(
                id = request.id,
                result = json.encodeToJsonElement(CallToolResult.serializer(), content)
            )
        } catch (e: Exception) {
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32000,
                    message = "Tool execution error: ${e.message}"
                )
            )
        }
    }

    private fun handlePing(request: JsonRpcRequest): JsonRpcResponse {
        return JsonRpcResponse(
            id = request.id,
            result = JsonObject(emptyMap())
        )
    }

    private fun schemaToJsonObject(schema: JsonSchema): JsonObject {
        val properties = buildJsonObject {
            for ((name, prop) in schema.properties) {
                put(name, buildJsonObject {
                    put("type", JsonPrimitive(prop.type))
                    prop.description?.let { put("description", JsonPrimitive(it)) }
                    prop.enum?.let { enumList ->
                        put("enum", JsonArray(enumList.map { JsonPrimitive(it) }))
                    }
                    prop.default?.let { put("default", it) }
                })
            }
        }

        return buildJsonObject {
            put("type", JsonPrimitive(schema.type))
            put("properties", properties)
            if (schema.required.isNotEmpty()) {
                put("required", JsonArray(schema.required.map { JsonPrimitive(it) }))
            }
            schema.description?.let { put("description", JsonPrimitive(it)) }
        }
    }
}
