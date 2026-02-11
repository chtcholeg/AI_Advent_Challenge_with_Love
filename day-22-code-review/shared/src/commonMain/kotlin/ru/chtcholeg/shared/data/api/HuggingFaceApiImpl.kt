package ru.chtcholeg.shared.data.api

import ru.chtcholeg.shared.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class HuggingFaceApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

class HuggingFaceApiImpl(
    private val httpClient: HttpClient
) : HuggingFaceApi {

    companion object {
        private const val BASE_URL = "https://router.huggingface.co/v1"
        private const val CHAT_COMPLETIONS_ENDPOINT = "$BASE_URL/chat/completions"
        private const val RATE_LIMIT_DELAY = 1000L
    }

    private var lastRequestTime = 0L

    override suspend fun sendMessage(
        accessToken: String,
        messages: List<Message>,
        model: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?,
        functions: List<GigaChatFunction>?,
        systemPrompt: String?
    ): ChatResponse {
        // Rate limiting
        val now = Clock.System.now().toEpochMilliseconds()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < RATE_LIMIT_DELAY) {
            delay(RATE_LIMIT_DELAY - timeSinceLastRequest)
        }
        lastRequestTime = Clock.System.now().toEpochMilliseconds()

        val hasTools = !functions.isNullOrEmpty()

        if (!hasTools) {
            // No tools — use simple ChatRequest
            // Strip function_call and function-role messages from history,
            // as HuggingFace (OpenAI format) rejects the "function_call" field.
            val cleanMessages = messages
                .filter { it.role != Message.FUNCTION }
                .map { it.copy(functionCall = null) }

            val request = ChatRequest(
                model = model,
                messages = if (systemPrompt != null) {
                    listOf(Message(role = "system", content = systemPrompt)) + cleanMessages
                } else {
                    cleanMessages
                },
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                repetitionPenalty = repetitionPenalty,
                stream = false
            )

            val response: HttpResponse = httpClient.post(CHAT_COMPLETIONS_ENDPOINT) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                bearerAuth(accessToken)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw HuggingFaceApiException(
                    statusCode = response.status.value,
                    message = "HuggingFace API error (${response.status.value}): $errorBody"
                )
            }

            return response.body()
        }

        // With tools — use HuggingFace OpenAI-compatible format
        val hfTools = functions.map { fn ->
            HfTool(
                type = "function",
                function = HfToolFunction(
                    name = fn.name,
                    description = fn.description,
                    parameters = fn.parameters
                )
            )
        }

        val hfMessages = convertMessagesToHf(messages, systemPrompt)

        val request = HfChatRequest(
            model = model,
            messages = hfMessages,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            repetitionPenalty = repetitionPenalty,
            stream = false,
            tools = hfTools
        )

        val hfHttpResponse: HttpResponse = httpClient.post(CHAT_COMPLETIONS_ENDPOINT) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(request)
        }

        if (!hfHttpResponse.status.isSuccess()) {
            val errorBody = hfHttpResponse.bodyAsText()
            throw HuggingFaceApiException(
                statusCode = hfHttpResponse.status.value,
                message = "HuggingFace API error (${hfHttpResponse.status.value}): $errorBody"
            )
        }

        val hfResponse: HfChatResponse = hfHttpResponse.body()
        return convertHfResponse(hfResponse)
    }

    /**
     * Convert internal Message list (GigaChat format) to HfMessage list (OpenAI format).
     *
     * Conversions:
     * - role:"function" + name → role:"tool" + tool_call_id (synthetic)
     * - role:"assistant" + functionCall → role:"assistant" + toolCalls (with matching synthetic ID)
     * - Other roles → direct copy
     *
     * Synthetic IDs: sequential "call_0", "call_1", etc. — each assistant tool_call gets
     * a unique ID, and the immediately following function result message gets the same ID.
     */
    private fun convertMessagesToHf(messages: List<Message>, systemPrompt: String?): List<HfMessage> {
        val result = mutableListOf<HfMessage>()

        // Prepend system prompt if provided
        if (systemPrompt != null) {
            result.add(HfMessage(role = "system", content = systemPrompt))
        }

        var callCounter = 0

        for (msg in messages) {
            when {
                // Assistant message with function call → assistant with tool_calls
                msg.role == "assistant" && msg.functionCall != null -> {
                    val callId = "call_$callCounter"
                    callCounter++
                    result.add(
                        HfMessage(
                            role = "assistant",
                            content = msg.content,
                            toolCalls = listOf(
                                HfToolCall(
                                    id = callId,
                                    type = "function",
                                    function = HfToolCallFunction(
                                        name = msg.functionCall.name,
                                        arguments = msg.functionCall.getArgumentsAsString()
                                    )
                                )
                            )
                        )
                    )
                }

                // Function result → tool result with matching call ID
                msg.role == "function" -> {
                    // The tool_call_id should match the preceding assistant's tool call
                    val callId = "call_${callCounter - 1}"
                    result.add(
                        HfMessage(
                            role = "tool",
                            content = msg.content,
                            toolCallId = callId
                        )
                    )
                }

                // All other roles (user, assistant without function call, system) → direct copy
                else -> {
                    result.add(
                        HfMessage(
                            role = msg.role,
                            content = msg.content,
                            name = msg.name
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Convert HfChatResponse (OpenAI format) back to ChatResponse (GigaChat format).
     *
     * Conversions:
     * - tool_calls in message → functionCall (takes the first tool call)
     * - finish_reason:"tool_calls" → "function_call"
     */
    private fun convertHfResponse(hfResponse: HfChatResponse): ChatResponse {
        val choices = hfResponse.choices.map { hfChoice ->
            val hfMsg = hfChoice.message
            val toolCall = hfMsg.toolCalls?.firstOrNull()

            val functionCall = if (toolCall != null) {
                val argsElement: JsonElement = try {
                    Json.parseToJsonElement(toolCall.function.arguments)
                } catch (_: Exception) {
                    kotlinx.serialization.json.JsonPrimitive(toolCall.function.arguments)
                }
                FunctionCall(
                    name = toolCall.function.name,
                    arguments = argsElement
                )
            } else {
                null
            }

            val finishReason = if (hfChoice.finishReason == "tool_calls") {
                "function_call"
            } else {
                hfChoice.finishReason ?: "stop"
            }

            Choice(
                message = Message(
                    role = hfMsg.role,
                    content = hfMsg.content,
                    functionCall = functionCall
                ),
                index = hfChoice.index,
                finishReason = finishReason
            )
        }

        return ChatResponse(
            choices = choices,
            created = hfResponse.created,
            model = hfResponse.model,
            usage = hfResponse.usage
        )
    }
}
