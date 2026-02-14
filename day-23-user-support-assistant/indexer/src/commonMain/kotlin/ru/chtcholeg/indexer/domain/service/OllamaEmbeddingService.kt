package ru.chtcholeg.indexer.domain.service

import ru.chtcholeg.indexer.data.api.OllamaApi
import ru.chtcholeg.shared.domain.service.EmbeddingService
import ru.chtcholeg.shared.domain.service.normalizeL2Batch

/**
 * EmbeddingService implementation using Ollama
 */
class OllamaEmbeddingService(
    private val ollamaApi: OllamaApi,
    private val model: String = OllamaApi.DEFAULT_MODEL,
    private val batchSize: Int = 10
) : EmbeddingService {

    companion object {
        /** Maximum ratio of non-printable characters allowed */
        const val MAX_BINARY_RATIO = 0.1
    }

    /**
     * Check if text appears to be binary content.
     * Only checks for actual control characters and null bytes,
     * not printable Unicode characters (including Cyrillic punctuation).
     */
    private fun isBinaryContent(text: String): Boolean {
        if (text.isEmpty()) return true

        // Count actual binary/control characters (not printable text)
        val binaryCount = text.count { char ->
            // Control characters (0x00-0x1F except tab, newline, carriage return)
            // and DEL (0x7F) are binary indicators
            val code = char.code
            (code in 0x00..0x08) ||
            (code in 0x0E..0x1F) ||
            (code == 0x7F) ||
            // Also check for replacement character (often indicates encoding issues)
            (char == '\uFFFD')
        }

        val ratio = binaryCount.toDouble() / text.length
        return ratio > MAX_BINARY_RATIO
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<List<Float>> {
        println("[OllamaEmbeddingService] generateEmbeddings called with ${texts.size} texts")

        if (texts.isEmpty()) {
            println("[OllamaEmbeddingService] Empty texts list, returning empty")
            return emptyList()
        }

        // Log input texts info and filter binary content
        val validTexts = mutableListOf<Pair<Int, String>>() // original index -> text
        texts.forEachIndexed { index, text ->
            val isBinary = isBinaryContent(text)
            println("[OllamaEmbeddingService] Text[$index]: length=${text.length}, binary=$isBinary, preview='${text.take(50)}...'")
            if (!isBinary) {
                validTexts.add(index to text)
            } else {
                println("[OllamaEmbeddingService] SKIPPING binary text at index $index")
            }
        }

        if (validTexts.isEmpty()) {
            println("[OllamaEmbeddingService] All texts are binary, returning empty embeddings")
            return texts.map { emptyList() }
        }

        val validEmbeddings = mutableListOf<List<Float>>()

        // Process in batches (no padding - Ollama handles short texts well)
        val textsToEmbed = validTexts.map { it.second }
        textsToEmbed.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            println("[OllamaEmbeddingService] Processing batch $batchIndex with ${batch.size} texts")
            batch.forEachIndexed { i, text ->
                println("[OllamaEmbeddingService] Batch[$batchIndex][$i]: length=${text.length}")
            }

            try {
                val response = ollamaApi.generateEmbeddings(batch, model)
                println("[OllamaEmbeddingService] Batch $batchIndex: received ${response.embeddings.size} embeddings")
                validEmbeddings.addAll(normalizeL2Batch(response.embeddings))
            } catch (e: Exception) {
                println("[OllamaEmbeddingService] ERROR in batch $batchIndex: ${e.message}")
                throw e
            }
        }

        // Build result with empty embeddings for binary texts
        val result = MutableList<List<Float>>(texts.size) { emptyList() }
        validTexts.forEachIndexed { validIndex, (originalIndex, _) ->
            if (validIndex < validEmbeddings.size) {
                result[originalIndex] = validEmbeddings[validIndex]
            }
        }

        println("[OllamaEmbeddingService] Total embeddings generated: ${validEmbeddings.size}, result size: ${result.size}")
        return result
    }

    /**
     * Check if Ollama is available
     */
    suspend fun isAvailable(): Boolean {
        return ollamaApi.isAvailable()
    }
}
