package ru.chtcholeg.app.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlin.time.measureTimedValue

class OllamaApi(private val httpClient: HttpClient) {

    companion object {
        const val BASE_URL = "http://localhost:11434"
    }

    // Separate Json instance to parse responses independently of Ktor content negotiation.
    // Needed because Ollama returns application/x-ndjson even when stream=false,
    // which Ktor's ContentNegotiation plugin does not match to application/json.
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAvailableModels(): List<String> {
        return try {
            val response: OllamaTagsResponse = httpClient.get("$BASE_URL/api/tags").body()
            response.models.map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun isAvailable(): Boolean {
        return try {
            httpClient.get("$BASE_URL/api/tags")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendMessage(
        messages: List<ApiMessage>,
        modelId: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        maxTokens: Int,
        repetitionPenalty: Float
    ): ApiResponse {
        val timedResult = measureTimedValue {
            val responseText = httpClient.post("$BASE_URL/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(OllamaChatRequest(
                    model = modelId,
                    messages = messages,
                    stream = false,
                    options = OllamaOptions(
                        temperature = temperature,
                        topP = topP,
                        topK = topK,
                        numPredict = maxTokens,
                        repeatPenalty = repetitionPenalty
                    )
                ))
            }.bodyAsText()

            // Ollama returns application/x-ndjson even when stream=false.
            // In streaming mode each line is a chunk; the final done=true line has empty content.
            // Concatenate content from all lines to reconstruct the full response.
            val chunks = responseText.trim().lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching { json.decodeFromString<OllamaChatResponse>(line) }.getOrNull()
                }

            val fullContent = chunks.joinToString("") { it.message?.content ?: "" }

            // Wrap into a single response reusing metadata from the done=true line if present.
            val doneChunk = chunks.lastOrNull { it.done } ?: chunks.lastOrNull()
            OllamaChatResponse(
                message = ApiMessage(role = "assistant", content = fullContent),
                done = true,
                totalDuration = doneChunk?.totalDuration,
                evalCount = doneChunk?.evalCount
            )
        }

        return ApiResponse(
            content = timedResult.value.message?.content ?: "",
            executionTimeMs = timedResult.duration.inWholeMilliseconds
        )
    }
}
