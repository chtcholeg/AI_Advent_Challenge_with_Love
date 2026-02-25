package ru.chtcholeg.godagent.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlin.time.measureTimedValue

class GigaChatApi(private val httpClient: HttpClient) {

    companion object {
        private const val BASE_URL = "https://gigachat.devices.sberbank.ru/api/v1"
        private const val AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
        private const val SCOPE = "GIGACHAT_API_PERS"
    }

    private var accessToken: String? = null
    private var tokenExpirationTime: Long = 0

    suspend fun ensureAuthenticated(clientId: String, clientSecret: String) {
        val currentTime = System.currentTimeMillis()
        if (accessToken == null || currentTime >= tokenExpirationTime) {
            val authResponse = authenticate(clientId, clientSecret)
            accessToken = authResponse.accessToken
            tokenExpirationTime = authResponse.expiresAt
        }
    }

    fun getAccessToken(): String? = accessToken

    private suspend fun authenticate(clientId: String, clientSecret: String): GigaChatAuthResponse {
        return httpClient.post(AUTH_URL) {
            contentType(ContentType.Application.FormUrlEncoded)
            accept(ContentType.Application.Json)
            basicAuth(clientId, clientSecret)
            header("RqUID", generateUUID())
            setBody(FormDataContent(parametersOf("scope", SCOPE)))
        }.body()
    }

    suspend fun sendMessage(
        messages: List<ApiMessage>,
        modelId: String,
        temperature: Float?,
        topP: Float?,
        maxTokens: Int?,
        repetitionPenalty: Float?
    ): ApiResponse {
        val token = accessToken ?: throw IllegalStateException("Not authenticated")

        val timedResult = measureTimedValue {
            httpClient.post("$BASE_URL/chat/completions") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                bearerAuth(token)
                setBody(
                    GigaChatRequest(
                        model = modelId,
                        messages = messages,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens,
                        repetitionPenalty = repetitionPenalty
                    )
                )
            }.body<GigaChatResponse>()
        }

        val content = timedResult.value.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Empty response from GigaChat")

        return ApiResponse(
            content = content,
            executionTimeMs = timedResult.duration.inWholeMilliseconds
        )
    }

    private fun generateUUID(): String {
        val chars = "0123456789abcdef"
        return buildString {
            repeat(8) { append(chars[(0..15).random()]) }
            append('-')
            repeat(4) { append(chars[(0..15).random()]) }
            append('-')
            append('4')
            repeat(3) { append(chars[(0..15).random()]) }
            append('-')
            append(chars[8 + (0..3).random()])
            repeat(3) { append(chars[(0..15).random()]) }
            append('-')
            repeat(12) { append(chars[(0..15).random()]) }
        }
    }
}
