package ru.chtcholeg.agent.data.repository

import io.ktor.client.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ru.chtcholeg.agent.BuildKonfig
import ru.chtcholeg.shared.data.api.GigaChatApi
import ru.chtcholeg.shared.data.api.GigaChatApiImpl
import ru.chtcholeg.shared.data.api.HuggingFaceApi
import ru.chtcholeg.shared.data.api.HuggingFaceApiImpl
import ru.chtcholeg.shared.data.model.*
import ru.chtcholeg.agent.domain.model.*
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.Model
import ru.chtcholeg.shared.domain.model.McpToolResult
import kotlin.time.measureTimedValue

class AgentRepository(
    httpClient: HttpClient,
    private val settingsRepository: SettingsRepository,
    private val mcpRepository: McpRepository
) {
    private val gigaChatApi: GigaChatApi = GigaChatApiImpl(httpClient)
    private val huggingFaceApi: HuggingFaceApi = HuggingFaceApiImpl(httpClient)

    private val conversationHistory = mutableListOf<Message>()
    private var gigaChatAccessToken: String? = null
    private var gigaChatTokenExpiry: Long? = null

    /**
     * Generates system prompt based on connected MCP servers.
     * Returns null if no servers are connected.
     */
    private fun buildSystemPrompt(serverCategories: List<String>): String? {
        if (serverCategories.isEmpty()) return null

        val categoriesList = serverCategories.joinToString("\n") { "- $it" }

        return """
Ты — точный и полезный AI-ассистент. Твоя задача — решать проблемы пользователя, используя доступные инструменты (API).

ПРАВИЛА РАБОТЫ:
1.  Анализируй запрос пользователя. Если для ответа нужны актуальные данные, вычисления или действия, которые ты не можешь выполнить сам, — используй инструменты.
2.  Когда получишь результат вызова инструмента, проанализируй его.
3.  Если результата достаточно для полного ответа пользователю — дай ответ.
4.  Если нужна дополнительная информация — вызови следующий инструмент (вернись к шагу 2).
5.  Если инструменты не нужны или недоступны, дай ответ, используя свои знания.
6.  Будь краток в рассуждениях. Главное — точный вызов инструмента или четкий ответ.

КРИТИЧЕСКИЕ ПРАВИЛА ВЫБОРА ИНСТРУМЕНТОВ:
- "установи APK", "инсталлируй", "install APK", "deploy" → install_apk
- "запусти приложение", "открой приложение", "launch app" → launch_app
- "собери APK", "build APK", "скомпилируй" → build_apk
- "запусти эмулятор", "start emulator" → start_emulator
- "останови эмулятор", "выключи эмулятор", "stop emulator" → stop_emulator (ТОЛЬКО для остановки!)

⚠️ ВАЖНО: stop_emulator ТОЛЬКО для остановки эмулятора! НЕ используй его для установки или запуска приложений!

Доступные категории инструментов:
$categoriesList

Твой вывод ДОЛЖЕН быть либо вызовом инструмента в указанном JSON-формате, либо финальным ответом пользователю.
        """.trimIndent()
    }

    /**
     * Send a message and get AI response with metadata.
     * Supports chained function calls (multiple tools in sequence).
     * Returns a list of messages: tool calls, tool results (including screenshots), and final AI response.
     */
    suspend fun sendMessage(userMessage: String): List<AgentMessage> {
        val settings = settingsRepository.settings.value
        val resultMessages = mutableListOf<AgentMessage>()

        // Add user message to history
        conversationHistory.add(
            Message(
                role = "user",
                content = userMessage
            )
        )

        // Get connected servers and build system prompt
        val connectedServers = mcpRepository.servers.value
            .filter { it.status == ConnectionStatus.CONNECTED }
        val serverCategories = connectedServers.map { it.name }
        val systemPrompt = buildSystemPrompt(serverCategories)

        // Get available tools from MCP servers
        val availableTools = mcpRepository.getAllTools()
        val functions = availableTools.map { tool ->
            // Include negative examples in description to help model avoid wrong tool selection
            val enhancedDescription = if (tool.negativeFewShotExamples.isNotEmpty()) {
                val negativeExamplesText = tool.negativeFewShotExamples.joinToString("\n") {
                    "- \"${it.request}\" → ${it.reason}"
                }
                "${tool.description}\n\n⛔ НЕ ИСПОЛЬЗУЙ этот инструмент для:\n$negativeExamplesText"
            } else {
                tool.description
            }

            GigaChatFunction(
                name = tool.name,
                description = enhancedDescription,
                parameters = tool.inputSchema,
                fewShotExamples = tool.fewShotExamples.map { example ->
                    FewShotExample(
                        request = example.request,
                        params = example.params
                    )
                }
            )
        }

        // Track total execution time and token usage across all calls
        var totalExecutionTimeMs = 0L
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokens = 0

        // Loop to handle chained function calls
        val maxIterations = 10  // Safety limit to prevent infinite loops
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            // Measure execution time for this call
            val (response, duration) = measureTimedValue {
                when (settings.model.api) {
                    Model.Api.GIGACHAT -> sendToGigaChat(settings, functions, systemPrompt)
                    Model.Api.HUGGINGFACE -> sendToHuggingFace(settings)
                }
            }

            totalExecutionTimeMs += duration.inWholeMilliseconds
            totalPromptTokens += response.usage?.promptTokens ?: 0
            totalCompletionTokens += response.usage?.completionTokens ?: 0
            totalTokens += response.usage?.totalTokens ?: 0

            val choice = response.choices.firstOrNull()
                ?: error("No response choice from AI")

            // Check if AI wants to call a function
            if (choice.finishReason == "function_call") {
                val functionCall = choice.message.functionCall
                    ?: error("Function call expected but not found")

                // Add assistant's function call message to history
                conversationHistory.add(
                    Message(
                        role = "assistant",
                        content = null,
                        functionCall = functionCall
                    )
                )

                // Add tool call message for UI
                val argsString = functionCall.arguments.toString()
                resultMessages.add(
                    AgentMessage(
                        content = "${functionCall.name}($argsString)",
                        type = MessageType.TOOL_CALL
                    )
                )

                // Execute the tool
                val toolResult = executeFunctionCall(functionCall)

                // Check if this is a screenshot result (extract for UI before modifying content)
                val screenshotMessage = extractScreenshotFromResult(functionCall.name, toolResult.content)

                // For history: replace image data with placeholder to avoid context overflow
                val contentForHistory = if (screenshotMessage != null) {
                    // Screenshot captured - use placeholder instead of large base64 data
                    "[Screenshot image captured successfully. The image is displayed to the user.]"
                } else {
                    // Check for other base64 image patterns in the result
                    createPlaceholderForImageContent(toolResult.content) ?: toolResult.content
                }

                // Format function result as JSON for GigaChat
                val functionResultJson = buildJsonObject {
                    put("result", contentForHistory)
                    put("is_error", toolResult.isError)
                }.toString()

                // Add function result to history (with placeholder for images)
                conversationHistory.add(
                    Message(
                        role = "function",
                        name = functionCall.name,
                        content = functionResultJson
                    )
                )
                if (screenshotMessage != null) {
                    resultMessages.add(screenshotMessage)
                } else {
                    // Add regular tool result message for UI
                    resultMessages.add(
                        AgentMessage(
                            content = toolResult.content,
                            type = MessageType.TOOL_RESULT
                        )
                    )
                }

                // Continue loop to get next response (may be another function call or final answer)
                continue
            }

            // Regular response - we're done
            val aiMessage = choice.message.content ?: ""
            conversationHistory.add(
                Message(
                    role = "assistant",
                    content = aiMessage
                )
            )

            resultMessages.add(
                AgentMessage(
                    content = aiMessage.trim(),
                    type = MessageType.AI,
                    executionTimeMs = totalExecutionTimeMs,
                    promptTokens = totalPromptTokens,
                    completionTokens = totalCompletionTokens,
                    totalTokens = totalTokens
                )
            )

            return resultMessages
        }

        // Safety: if we hit max iterations, return error
        resultMessages.add(
            AgentMessage(
                content = "Error: Too many function calls in chain (max $maxIterations)",
                type = MessageType.ERROR,
                executionTimeMs = totalExecutionTimeMs,
                promptTokens = totalPromptTokens,
                completionTokens = totalCompletionTokens,
                totalTokens = totalTokens
            )
        )
        return resultMessages
    }

    /**
     * Checks if content contains base64 image data and returns a placeholder if so.
     * This prevents sending large image data to the AI model which causes context overflow.
     * Returns null if no image data detected.
     */
    private fun createPlaceholderForImageContent(content: String): String? {
        // Check if content is suspiciously large (likely contains base64 data)
        if (content.length < 1000) return null

        return try {
            val json = Json.parseToJsonElement(content).jsonObject

            // Check for common base64 image field names
            val base64Fields = listOf("base64", "image", "image_data", "data", "screenshot")
            for (fieldName in base64Fields) {
                val field = json[fieldName]?.jsonPrimitive?.contentOrNull
                if (field != null && field.length > 1000 && looksLikeBase64(field)) {
                    // Found base64 image data - return placeholder with other fields preserved
                    val otherFields = json.filterKeys { it != fieldName }
                        .map { (key, value) -> "$key: ${value.toString().take(100)}" }
                        .joinToString(", ")

                    return "[Image data received. ${if (otherFields.isNotEmpty()) "Other fields: $otherFields" else "The image is displayed to the user."}]"
                }
            }
            null
        } catch (e: Exception) {
            // Not JSON or parsing error - check if raw content looks like base64
            if (content.length > 5000 && looksLikeBase64(content)) {
                "[Image data received and displayed to the user.]"
            } else {
                null
            }
        }
    }

    /**
     * Simple heuristic to check if a string looks like base64-encoded data.
     */
    private fun looksLikeBase64(str: String): Boolean {
        if (str.length < 100) return false
        // Base64 typically has high ratio of alphanumeric chars and +/= characters
        val sample = str.take(200)
        val validChars = sample.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        return validChars.toFloat() / sample.length > 0.95
    }

    /**
     * Extract image from tool result if present.
     * Returns an AgentMessage with SCREENSHOT type and imageBase64 if found.
     * Works with any tool that returns base64 image data.
     */
    private fun extractScreenshotFromResult(toolName: String, content: String): AgentMessage? {
        // Skip small content (unlikely to contain image)
        if (content.length < 1000) return null

        return try {
            val json = Json.parseToJsonElement(content).jsonObject

            // Check for common base64 image field names
            val base64Fields = listOf("base64", "image", "image_data", "data", "screenshot")
            for (fieldName in base64Fields) {
                val base64 = json[fieldName]?.jsonPrimitive?.contentOrNull
                if (base64 != null && base64.length > 1000 && looksLikeBase64(base64)) {
                    // Found base64 image data
                    val status = json["status"]?.jsonPrimitive?.contentOrNull
                    val deviceId = json["device_id"]?.jsonPrimitive?.contentOrNull

                    // Build description from available metadata
                    val description = when {
                        toolName == "screenshot" && deviceId != null -> "Screenshot captured from $deviceId"
                        status != null -> "Image from $toolName (status: $status)"
                        else -> "Image from $toolName"
                    }

                    return AgentMessage(
                        content = description,
                        type = MessageType.SCREENSHOT,
                        imageBase64 = base64
                    )
                }
            }
            null
        } catch (e: Exception) {
            // Not JSON - check if raw content is base64 image
            if (content.length > 5000 && looksLikeBase64(content)) {
                AgentMessage(
                    content = "Image from $toolName",
                    type = MessageType.SCREENSHOT,
                    imageBase64 = content
                )
            } else {
                null
            }
        }
    }

    private suspend fun executeFunctionCall(functionCall: FunctionCall): McpToolResult {
        return try {
            val result = mcpRepository.executeTool(
                toolName = functionCall.name,
                arguments = functionCall.arguments
            )
            result.getOrThrow()
        } catch (e: Exception) {
            McpToolResult(
                content = "Error executing tool: ${e.message}",
                isError = true
            )
        }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 5000  // Hard limit for any message content
    }

    /**
     * Sanitize message content to remove large base64 image data.
     * This prevents context overflow when sending to the AI model.
     */
    private fun sanitizeMessageForApi(message: Message): Message {
        val content = message.content ?: return message

        // Hard limit: any content over MAX_MESSAGE_LENGTH gets truncated
        if (content.length > MAX_MESSAGE_LENGTH) {
            println("[AgentRepository] Truncating large message: ${content.length} chars -> placeholder")
            return message.copy(content = buildJsonObject {
                put("result", "[Content truncated - data displayed to user]")
                put("is_error", false)
            }.toString())
        }

        // Try to sanitize JSON content with base64 fields
        val sanitizedContent = try {
            val json = Json.parseToJsonElement(content).jsonObject
            val base64Fields = listOf("base64", "image", "image_data", "data", "screenshot")

            // Check direct fields
            var hasLargeField = false
            for (fieldName in base64Fields) {
                val field = json[fieldName]?.jsonPrimitive?.contentOrNull
                if (field != null && field.length > 1000 && looksLikeBase64(field)) {
                    hasLargeField = true
                    break
                }
            }

            // Also check nested "result" field which may contain JSON string
            if (!hasLargeField) {
                val resultField = json["result"]?.jsonPrimitive?.contentOrNull
                if (resultField != null && resultField.length > 2000) {
                    // Result field is large - check if it contains base64
                    try {
                        val nestedJson = Json.parseToJsonElement(resultField).jsonObject
                        for (fieldName in base64Fields) {
                            val field = nestedJson[fieldName]?.jsonPrimitive?.contentOrNull
                            if (field != null && field.length > 1000 && looksLikeBase64(field)) {
                                hasLargeField = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Result is not JSON - check if it's raw base64
                        if (resultField.length > 5000 && looksLikeBase64(resultField)) {
                            hasLargeField = true
                        }
                    }
                }
            }

            if (hasLargeField) {
                // Replace with placeholder
                buildJsonObject {
                    put("result", "[Image data - displayed to user]")
                    json["is_error"]?.let { put("is_error", it) }
                }.toString()
            } else {
                content
            }
        } catch (e: Exception) {
            // Check raw content for base64
            if (content.length > 5000 && looksLikeBase64(content)) {
                "[Image data - displayed to user]"
            } else {
                content
            }
        }

        return message.copy(content = sanitizedContent)
    }

    private suspend fun sendToGigaChat(
        settings: AiSettings,
        functions: List<GigaChatFunction>,
        systemPrompt: String?
    ): ChatResponse {
        // Check token expiry and re-authenticate if needed
        val now = Clock.System.now().toEpochMilliseconds()
        if (gigaChatAccessToken == null || gigaChatTokenExpiry == null || now >= gigaChatTokenExpiry!!) {
            val authResponse = gigaChatApi.authenticate(
                clientId = BuildKonfig.GIGACHAT_CLIENT_ID,
                clientSecret = BuildKonfig.GIGACHAT_CLIENT_SECRET
            )
            gigaChatAccessToken = authResponse.accessToken
            gigaChatTokenExpiry = authResponse.expiresAt
        }

        // Log original history size
        val originalSize = conversationHistory.sumOf { (it.content?.length ?: 0) }
        println("[AgentRepository] Original history: ${conversationHistory.size} messages, $originalSize chars")

        // Sanitize messages to remove any large base64 image data
        val sanitizedHistory = conversationHistory.map { sanitizeMessageForApi(it) }

        // Log sanitized size
        val sanitizedSize = sanitizedHistory.sumOf { (it.content?.length ?: 0) }
        println("[AgentRepository] Sanitized history: ${sanitizedHistory.size} messages, $sanitizedSize chars")

        // Additional safety: limit total context size (keep recent messages only)
        val maxTotalChars = 50000  // ~12k tokens max for history
        var totalChars = 0
        val limitedHistory = sanitizedHistory.reversed().takeWhile { msg ->
            val msgLen = msg.content?.length ?: 0
            totalChars += msgLen
            totalChars <= maxTotalChars
        }.reversed()

        if (limitedHistory.size < sanitizedHistory.size) {
            println("[AgentRepository] Limited history from ${sanitizedHistory.size} to ${limitedHistory.size} messages")
        }

        // Build messages with optional system prompt
        val messages = if (systemPrompt != null) {
            listOf(Message(role = "system", content = systemPrompt)) + limitedHistory
        } else {
            limitedHistory
        }

        val finalSize = messages.sumOf { (it.content?.length ?: 0) }
        println("[AgentRepository] Final messages: ${messages.size} messages, $finalSize chars")

        return gigaChatApi.sendMessage(
            accessToken = gigaChatAccessToken!!,
            messages = messages,
            model = settings.model.id,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            repetitionPenalty = settings.repetitionPenalty,
            functions = functions.takeIf { it.isNotEmpty() }
        )
    }

    private suspend fun sendToHuggingFace(settings: AiSettings): ChatResponse {
        // Sanitize messages to remove any large base64 image data
        val sanitizedHistory = conversationHistory.map { sanitizeMessageForApi(it) }

        return huggingFaceApi.sendMessage(
            accessToken = BuildKonfig.HUGGINGFACE_API_TOKEN,
            messages = sanitizedHistory,
            model = settings.model.id,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            repetitionPenalty = settings.repetitionPenalty
        )
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}
