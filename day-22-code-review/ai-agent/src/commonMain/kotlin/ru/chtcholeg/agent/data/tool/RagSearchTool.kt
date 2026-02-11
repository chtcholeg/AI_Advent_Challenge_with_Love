package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.RagRepository
import ru.chtcholeg.agent.data.repository.SettingsRepository
import ru.chtcholeg.agent.domain.model.RagMode
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * RagSearch tool — AI can search the RAG index on demand.
 * Instead of always loading context upfront, the AI decides when to search.
 */
class RagSearchTool(
    private val ragRepository: RagRepository,
    private val settingsRepository: SettingsRepository
) : LocalTool {

    override val name = "rag_search"

    override val description = """
        Search the RAG document index for relevant information.
        Use when you need to find documentation, code examples, or project knowledge.

        Features:
        - Semantic search across indexed documents
        - Returns ranked chunks with relevance scores
        - Supports configurable topK and threshold

        Prerequisites:
        - RAG index must be configured in Settings (indexPath)
        - Index must contain documents (use rag_index to add new ones)
    """.trimIndent()

    override val inputSchema = toolSchema {
        string("query", "Search query — describe what information you're looking for", required = true)
        integer("top_k", "Maximum number of results to return (default: 5, max: 20)", default = 5)
        number("threshold", "Minimum similarity threshold 0.0-1.0 (default: 0.3)", default = 0.3)
    }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val args = arguments.jsonObject
            val query = args["query"]?.jsonPrimitive?.content
                ?: return McpToolResult("Missing required parameter: query", isError = true)

            val topK = args["top_k"]?.jsonPrimitive?.intOrNull ?: 5
            val threshold = args["threshold"]?.jsonPrimitive?.floatOrNull ?: 0.3f

            val settings = settingsRepository.settings.value
            if (settings.indexPath.isBlank()) {
                return McpToolResult(
                    "RAG index not configured. Set indexPath in Settings.",
                    isError = true
                )
            }

            // Load index if needed
            ragRepository.loadIndex(settings.indexPath)

            // Search
            val effectiveTopK = topK.coerceIn(1, 20)
            val results = if (settings.rerankerEnabled) {
                val rerankerResult = ragRepository.getRelevantChunksWithReranking(
                    query = query,
                    initialTopK = effectiveTopK * 2,
                    finalTopK = effectiveTopK,
                    rerankerThreshold = settings.rerankerThreshold,
                    scoreGapThreshold = settings.scoreGapThreshold
                )
                rerankerResult.rerankedResults
            } else {
                ragRepository.getRelevantChunks(query, effectiveTopK, threshold)
            }

            if (results.isEmpty()) {
                return McpToolResult("No relevant documents found for query: $query")
            }

            val output = buildString {
                appendLine("Found ${results.size} relevant chunk(s) for: \"$query\"")
                appendLine()
                results.forEachIndexed { index, result ->
                    val source = result.chunk.metadata.source.substringAfterLast("/")
                    appendLine("── [${index + 1}] $source (chunk ${result.chunk.metadata.chunkIndex}/${result.chunk.metadata.totalChunks}, sim=${"%.0f".format(result.similarity * 100)}%)")
                    appendLine(result.chunk.text.take(500))
                    if (result.chunk.text.length > 500) appendLine("...")
                    appendLine()
                }
            }

            McpToolResult(
                content = output,
                metadata = mapOf("result_count" to results.size.toString())
            )
        } catch (e: Exception) {
            McpToolResult("RAG search error: ${e.message}", isError = true)
        }
    }
}
