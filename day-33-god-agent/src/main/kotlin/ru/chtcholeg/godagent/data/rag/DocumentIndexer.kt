package ru.chtcholeg.godagent.data.rag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import ru.chtcholeg.godagent.data.api.GigaChatApi
import ru.chtcholeg.godagent.domain.model.AppSettings
import java.io.File

class DocumentIndexer(
    private val httpClient: HttpClient,
    private val gigaChatApi: GigaChatApi,
    private val vectorStore: VectorStore,
    private val settingsProvider: () -> AppSettings
) {
    suspend fun indexFolder(folderPath: String, onProgress: (String) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) return@withContext 0

            vectorStore.clearAll()
            var indexed = 0

            val supportedExtensions = setOf(
                "txt", "md", "kt", "py", "java", "json", "yaml", "yml",
                "xml", "gradle", "properties"
            )
            val files = folder.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in supportedExtensions && it.length() < 500_000 }
                .toList()

            for (file in files) {
                try {
                    onProgress("Индексация: ${file.name}")
                    val text = file.readText()
                    val chunks = chunkText(text, file.absolutePath)
                    for (chunk in chunks) {
                        val embedding = getEmbedding(chunk.content)
                        if (embedding.isNotEmpty()) {
                            vectorStore.saveChunk(chunk.filePath, chunk.content, embedding)
                            indexed++
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read
                }
            }
            indexed
        }

    private fun chunkText(text: String, filePath: String): List<DocumentChunk> {
        val chunkSize = 800
        val overlap = 100
        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + chunkSize, text.length)
            chunks.add(
                DocumentChunk(
                    filePath = filePath,
                    content = text.substring(start, end),
                    embedding = emptyList()
                )
            )
            start += chunkSize - overlap
        }
        return chunks
    }

    private suspend fun getEmbedding(text: String): List<Float> {
        val settings = settingsProvider()
        return try {
            val isGigaChat = settings.selectedModelId.startsWith("GigaChat")
            if (isGigaChat) {
                getGigaChatEmbedding(text, settings)
            } else {
                getOllamaEmbedding(text, settings)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getGigaChatEmbedding(text: String, settings: AppSettings): List<Float> {
        gigaChatApi.ensureAuthenticated(settings.gigachatClientId, settings.gigachatClientSecret)
        val token = gigaChatApi.getAccessToken() ?: return emptyList()
        val response: String = httpClient.post("https://gigachat.devices.sberbank.ru/api/v1/embeddings") {
            header("Authorization", "Bearer $token")
            header("Content-Type", "application/json")
            setBody("""{"model":"Embeddings","input":["${text.replace("\"", "\\\"").take(2000)}"]}""")
        }.body()
        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(response).jsonObject
        return json["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("embedding")?.jsonArray
            ?.map { it.jsonPrimitive.float }
            ?: emptyList()
    }

    private suspend fun getOllamaEmbedding(text: String, settings: AppSettings): List<Float> {
        val baseUrl = settings.ollamaBaseUrl.trimEnd('/')
        val response: String = httpClient.post("$baseUrl/api/embeddings") {
            header("Content-Type", "application/json")
            setBody("""{"model":"nomic-embed-text","prompt":"${text.replace("\"", "\\\"").take(2000)}"}""")
        }.body()
        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(response).jsonObject
        return json["embedding"]?.jsonArray?.map { it.jsonPrimitive.float } ?: emptyList()
    }

    suspend fun search(query: String, topK: Int = 5): String {
        val embedding = getEmbedding(query)
        if (embedding.isEmpty()) return "RAG: embeddings unavailable"
        val results = vectorStore.search(embedding, topK)
        if (results.isEmpty()) return "Ничего не найдено в базе знаний"
        return results.mapIndexed { i, (chunk, score) ->
            "[${i + 1}] (${String.format("%.2f", score)}) ${chunk.filePath.substringAfterLast('/')}\n${chunk.content.take(400)}"
        }.joinToString("\n\n---\n\n")
    }

    suspend fun getChunkCount() = vectorStore.getChunkCount()
}
