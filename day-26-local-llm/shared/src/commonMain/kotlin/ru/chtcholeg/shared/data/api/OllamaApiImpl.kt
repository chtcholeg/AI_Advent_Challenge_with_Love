package ru.chtcholeg.shared.data.api

import ru.chtcholeg.shared.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class OllamaApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

class OllamaNotAvailableException(modelId: String) : Exception(
    "Ollama недоступен. Убедитесь, что сервер запущен:\n" +
    "  ollama serve\n" +
    "Загрузите модель:\n" +
    "  ollama pull $modelId"
)

class OllamaApiImpl(
    private val httpClient: HttpClient
) : OllamaApi {

    companion object {
        private const val BASE_URL = "http://localhost:11434/v1"
        private const val CHAT_COMPLETIONS_ENDPOINT = "$BASE_URL/chat/completions"
        private const val TAGS_ENDPOINT = "http://localhost:11434/api/tags"
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val response: HttpResponse = httpClient.get(TAGS_ENDPOINT)
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun sendMessage(
        messages: List<Message>,
        model: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?,
        functions: List<GigaChatFunction>?,
        systemPrompt: String?
    ): ChatResponse {
        val hasTools = !functions.isNullOrEmpty()

        if (!hasTools) {
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
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                throw OllamaApiException(
                    statusCode = response.status.value,
                    message = "Ollama API error (${response.status.value}): $errorBody"
                )
            }

            return response.body()
        }

        // With tools — use OpenAI-compatible format
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

        val httpResponse: HttpResponse = httpClient.post(CHAT_COMPLETIONS_ENDPOINT) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            throw OllamaApiException(
                statusCode = httpResponse.status.value,
                message = "Ollama API error (${httpResponse.status.value}): $errorBody"
            )
        }

        val hfResponse: HfChatResponse = httpResponse.body()
        return convertHfResponse(hfResponse)
    }

    private fun convertMessagesToHf(messages: List<Message>, systemPrompt: String?): List<HfMessage> {
        val result = mutableListOf<HfMessage>()

        if (systemPrompt != null) {
            result.add(HfMessage(role = "system", content = systemPrompt))
        }

        var callCounter = 0

        for (msg in messages) {
            when {
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

                msg.role == "function" -> {
                    val callId = "call_${callCounter - 1}"
                    result.add(
                        HfMessage(
                            role = "tool",
                            content = msg.content,
                            toolCallId = callId
                        )
                    )
                }

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

    private fun convertHfResponse(hfResponse: HfChatResponse): ChatResponse {
        val choices = hfResponse.choices.map { hfChoice ->
            val hfMsg = hfChoice.message
            val toolCall = hfMsg.toolCalls?.firstOrNull()

            val functionCall = if (toolCall != null) {
                val argsElement = try {
                    Json.parseToJsonElement(toolCall.function.arguments)
                } catch (_: Exception) {
                    JsonPrimitive(toolCall.function.arguments)
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
