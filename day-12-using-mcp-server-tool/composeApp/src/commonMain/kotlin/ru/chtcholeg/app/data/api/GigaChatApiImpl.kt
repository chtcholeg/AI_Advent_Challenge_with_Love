package ru.chtcholeg.app.data.api

import ru.chtcholeg.app.data.model.AuthResponse
import ru.chtcholeg.app.data.model.ChatRequest
import ru.chtcholeg.app.data.model.ChatResponse
import ru.chtcholeg.app.data.model.Message
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.random.Random

class GigaChatApiImpl(
    private val httpClient: HttpClient
) : GigaChatApi {

    companion object {
        private const val BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1"
        private const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
        private const val SCOPE = "GIGACHAT_API_PERS"
    }

    override suspend fun authenticate(
        clientId: String,
        clientSecret: String
    ): AuthResponse {
        val response = httpClient.post(AUTH_URL) {
            contentType(ContentType.Application.FormUrlEncoded)
            accept(ContentType.Application.Json)
            basicAuth(clientId, clientSecret)
            header("RqUID", generateUUID())
            setBody(FormDataContent(parametersOf("scope", SCOPE)))
        }

        return response.body()
    }

    override suspend fun sendMessage(
        accessToken: String,
        messages: List<Message>,
        model: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?,
        functions: List<ru.chtcholeg.app.data.model.GigaChatFunction>?
    ): ChatResponse {
        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            repetitionPenalty = repetitionPenalty,
            functions = functions
        )

        val response = httpClient.post("$BASE_URL/chat/completions") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(request)
        }

        return response.body()
    }

    /**
     * Generate UUID for request tracking
     */
    private fun generateUUID(): String {
        val chars = "0123456789abcdef"
        val result =  buildString {
            repeat(8) { append(chars[Random.nextInt(chars.length)]) }
            append('-')
            repeat(4) { append(chars[Random.nextInt(chars.length)]) }
            append('-')
            append('4')  // UUID version 4
            repeat(3) { append(chars[Random.nextInt(chars.length)]) }
            append('-')
            append(chars[8 + Random.nextInt(4)])  // Variant
            repeat(3) { append(chars[Random.nextInt(chars.length)]) }
            append('-')
            repeat(12) { append(chars[Random.nextInt(chars.length)]) }
        }
        return result
    }
}

