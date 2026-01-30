package ru.chtcholeg.shared.data.api

import ru.chtcholeg.shared.data.model.ChatRequest
import ru.chtcholeg.shared.data.model.ChatResponse
import ru.chtcholeg.shared.data.model.Message
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

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
        repetitionPenalty: Float?
    ): ChatResponse {
        // Rate limiting
        val now = Clock.System.now().toEpochMilliseconds()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < RATE_LIMIT_DELAY) {
            delay(RATE_LIMIT_DELAY - timeSinceLastRequest)
        }
        lastRequestTime = Clock.System.now().toEpochMilliseconds()

        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            repetitionPenalty = repetitionPenalty,
            stream = false
        )

        val response: ChatResponse = httpClient.post(CHAT_COMPLETIONS_ENDPOINT) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            bearerAuth(accessToken)
            setBody(request)
        }.body()

        return response
    }
}
