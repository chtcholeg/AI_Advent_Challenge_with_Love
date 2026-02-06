package ru.chtcholeg.shared.domain.service

import ru.chtcholeg.shared.data.api.GigaChatApi
import ru.chtcholeg.shared.data.api.GigaChatApiException

/**
 * Implementation of EmbeddingService using GigaChat API
 */
class EmbeddingServiceImpl(
    private val gigaChatApi: GigaChatApi,
    private val clientId: String,
    private val clientSecret: String,
    private val batchSize: Int = 10
) : EmbeddingService {

    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0

    override suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) {
            return emptyList()
        }

        // Ensure we have a valid access token
        ensureAuthenticated()

        val allEmbeddings = mutableListOf<List<Float>>()

        // Process in batches to avoid API limits
        texts.chunked(batchSize).forEach { batch ->
            try {
                val response = gigaChatApi.generateEmbeddings(
                    accessToken = accessToken!!,
                    input = batch,
                    model = "Embeddings"
                )

                // Sort by index to maintain order
                val embeddings = response.data
                    .sortedBy { it.index }
                    .map { normalizeL2(it.embedding) }

                allEmbeddings.addAll(embeddings)
            } catch (e: GigaChatApiException) {
                if (e.statusCode == 401 || e.statusCode == 403) {
                    // Token expired, re-authenticate and retry
                    accessToken = null
                    tokenExpiresAt = 0
                    ensureAuthenticated()

                    val response = gigaChatApi.generateEmbeddings(
                        accessToken = accessToken!!,
                        input = batch,
                        model = "Embeddings"
                    )

                    val embeddings = response.data
                        .sortedBy { it.index }
                        .map { normalizeL2(it.embedding) }

                    allEmbeddings.addAll(embeddings)
                } else {
                    throw e
                }
            }
        }

        return allEmbeddings
    }

    /**
     * Ensure we have a valid access token
     */
    private suspend fun ensureAuthenticated() {
        val currentTime = System.currentTimeMillis()

        if (accessToken == null || currentTime >= tokenExpiresAt) {
            val authResponse = gigaChatApi.authenticate(clientId, clientSecret)
            accessToken = authResponse.accessToken

            // Set expiration time with 5 minute buffer
            // expiresAt is already a timestamp in milliseconds
            tokenExpiresAt = authResponse.expiresAt - (5 * 60 * 1000)
        }
    }
}
