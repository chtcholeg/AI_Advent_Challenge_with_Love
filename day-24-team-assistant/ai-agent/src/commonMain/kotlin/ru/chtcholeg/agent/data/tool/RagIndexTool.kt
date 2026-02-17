package ru.chtcholeg.agent.data.tool

import kotlinx.serialization.json.*
import ru.chtcholeg.agent.data.repository.RagRepository
import ru.chtcholeg.agent.data.repository.SettingsRepository
import ru.chtcholeg.agent.domain.tool.LocalTool
import ru.chtcholeg.agent.domain.tool.toolSchema
import ru.chtcholeg.shared.domain.model.McpToolResult

/**
 * RagIndex tool — check the status of the RAG index (stats, loaded path, file count).
 * Useful for the AI to understand what's available before searching.
 */
class RagIndexTool(
    private val ragRepository: RagRepository,
    private val settingsRepository: SettingsRepository
) : LocalTool {

    override val name = "rag_status"

    override val description = """
        Check the status of the RAG document index.
        Returns information about loaded index: path, file count, chunk count.

        Use this before rag_search to verify the index is loaded and understand what's available.
    """.trimIndent()

    override val inputSchema = toolSchema { /* No parameters */ }

    override suspend fun execute(arguments: JsonElement): McpToolResult {
        return try {
            val settings = settingsRepository.settings.value

            if (settings.indexPath.isBlank()) {
                return McpToolResult(
                    "RAG index not configured.\n" +
                    "To enable RAG: set indexPath in Settings → RAG section.\n" +
                    "To create an index: use ./gradlew :shared:runIndexing --args=\"index <dir> <output.json> md txt pdf\""
                )
            }

            ragRepository.loadIndex(settings.indexPath)
            val stats = ragRepository.getStats()

            val output = buildString {
                appendLine("RAG Index Status:")
                appendLine()
                appendLine("Index path: ${settings.indexPath}")
                appendLine("Files indexed: ${stats.totalDocuments}")
                appendLine("Total chunks: ${stats.totalChunks}")
                appendLine("Index size: ${stats.indexSizeBytes} bytes")
                appendLine()
                appendLine("Settings:")
                appendLine("  RAG mode: ${settings.ragMode}")
                appendLine("  Reranker: ${if (settings.rerankerEnabled) "ON" else "OFF"}")
                if (settings.rerankerEnabled) {
                    appendLine("  Reranker threshold: ${settings.rerankerThreshold}")
                    appendLine("  Score gap threshold: ${settings.scoreGapThreshold}")
                    appendLine("  Initial top-K: ${settings.ragInitialTopK}")
                    appendLine("  Final top-K: ${settings.ragFinalTopK}")
                }
            }

            McpToolResult(content = output)
        } catch (e: Exception) {
            McpToolResult("RAG status error: ${e.message}", isError = true)
        }
    }
}
