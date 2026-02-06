package ru.chtcholeg.indexer.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Implementation of OllamaApi using Ktor HTTP client
 */
class OllamaApiImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String = OllamaApi.DEFAULT_BASE_URL
) : OllamaApi {

    override suspend fun generateEmbeddings(
        input: List<String>,
        model: String
    ): OllamaEmbeddingResponse {
        println("[OllamaApi] generateEmbeddings: model=$model, inputCount=${input.size}")
        input.forEachIndexed { index, text ->
            println("[OllamaApi] Input[$index]: length=${text.length}, content='${text.take(100).replace("\n", "\\n")}...'")
        }

        val request = OllamaEmbeddingRequest(
            model = model,
            input = input
        )

        println("[OllamaApi] Sending request to $baseUrl/api/embed")

        val response = httpClient.post("$baseUrl/api/embed") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        println("[OllamaApi] Response status: ${response.status}")

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            println("[OllamaApi] ERROR response body: $errorBody")
            throw OllamaApiException(
                "Ollama API error: ${response.status.value} - $errorBody"
            )
        }

        val result: OllamaEmbeddingResponse = response.body()
        println("[OllamaApi] Success: received ${result.embeddings.size} embeddings")
        return result
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val response = httpClient.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Exception for Ollama API errors
 */
class OllamaApiException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
