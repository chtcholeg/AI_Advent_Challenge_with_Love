package ru.chtcholeg.agent.domain.service

import ru.chtcholeg.agent.config.AgentConfig
import ru.chtcholeg.shared.data.model.Message

/**
 * Manages conversation context with intelligent windowing and summarization.
 *
 * Instead of hard character truncation, uses a sliding window approach:
 * - Recent messages (within WINDOW_SIZE) are kept in full
 * - Older messages are summarized into a compact summary message
 * - Approximate token counting for better context budget management
 */
class ContextManager {

    /**
     * Apply sliding window to conversation history.
     * Returns a list of messages that fits within the token budget:
     * - A summary of old messages (if any were trimmed)
     * - The full recent messages within the window
     */
    fun applyWindow(
        messages: List<Message>,
        maxTokens: Int = AgentConfig.MAX_CONTEXT_TOKENS
    ): WindowResult {
        if (messages.isEmpty()) return WindowResult(messages, summarizedCount = 0)

        val totalTokens = messages.sumOf { estimateTokens(it) }

        // If everything fits, no windowing needed
        if (totalTokens <= maxTokens) {
            return WindowResult(messages, summarizedCount = 0)
        }

        // Find the split point: keep recent messages that fit in budget
        // Reserve tokens for the summary message
        val summaryBudget = SUMMARY_TOKEN_BUDGET
        val messageBudget = maxTokens - summaryBudget

        var tokenCount = 0
        var splitIndex = messages.size

        for (i in messages.indices.reversed()) {
            val msgTokens = estimateTokens(messages[i])
            if (tokenCount + msgTokens > messageBudget) {
                splitIndex = i + 1
                break
            }
            tokenCount += msgTokens
        }

        // Ensure we keep at least the last message
        if (splitIndex >= messages.size) {
            splitIndex = messages.size - 1
        }

        val oldMessages = messages.subList(0, splitIndex)
        val recentMessages = messages.subList(splitIndex, messages.size)

        if (oldMessages.isEmpty()) {
            return WindowResult(recentMessages, summarizedCount = 0)
        }

        // Create summary of old messages
        val summary = buildSummary(oldMessages)
        val summaryMessage = Message(
            role = "system",
            content = summary
        )

        return WindowResult(
            messages = listOf(summaryMessage) + recentMessages,
            summarizedCount = oldMessages.size
        )
    }

    /**
     * Build a compact summary of old conversation messages.
     * This is a local summary (no AI call) — extracts key information.
     */
    private fun buildSummary(messages: List<Message>): String {
        val userMessages = messages.filter { it.role == "user" }
        val assistantMessages = messages.filter { it.role == "assistant" && it.content != null }
        val functionCalls = messages.filter { it.role == "assistant" && it.functionCall != null }
        val functionResults = messages.filter { it.role == "function" }

        return buildString {
            appendLine("[Сводка предыдущей части разговора (${messages.size} сообщений)]")

            if (userMessages.isNotEmpty()) {
                appendLine()
                appendLine("Запросы пользователя:")
                userMessages.takeLast(3).forEach { msg ->
                    val truncated = msg.content?.take(200)?.let {
                        if ((msg.content?.length ?: 0) > 200) "$it..." else it
                    }
                    appendLine("- $truncated")
                }
                if (userMessages.size > 3) {
                    appendLine("- ... и ещё ${userMessages.size - 3} запросов")
                }
            }

            if (functionCalls.isNotEmpty()) {
                appendLine()
                appendLine("Вызванные инструменты (${functionCalls.size}):")
                functionCalls.takeLast(5).forEach { msg ->
                    val fc = msg.functionCall
                    if (fc != null) {
                        appendLine("- ${fc.name}(${fc.arguments.toString().take(100)})")
                    }
                }
            }

            if (assistantMessages.isNotEmpty()) {
                appendLine()
                appendLine("Ключевые ответы AI:")
                assistantMessages.takeLast(2).forEach { msg ->
                    val truncated = msg.content?.take(300)?.let {
                        if ((msg.content?.length ?: 0) > 300) "$it..." else it
                    }
                    appendLine("- $truncated")
                }
            }

            appendLine()
            appendLine("[Конец сводки — дальше идут полные сообщения]")
        }
    }

    companion object {
        /**
         * Approximate token estimation.
         * Russian: ~2-3 chars per token; English: ~4 chars per token.
         * We use 3 as a conservative middle ground.
         */
        fun estimateTokens(message: Message): Int {
            val contentLen = message.content?.length ?: 0
            val fcLen = message.functionCall?.let {
                it.name.length + it.arguments.toString().length
            } ?: 0
            val nameLen = message.name?.length ?: 0
            val roleLen = message.role.length

            return (contentLen + fcLen + nameLen + roleLen) / CHARS_PER_TOKEN
        }

        fun estimateTokens(text: String): Int = text.length / CHARS_PER_TOKEN

        // ~3 chars per token (conservative for mixed Russian/English)
        const val CHARS_PER_TOKEN = 3

        // Budget reserved for the summary message
        const val SUMMARY_TOKEN_BUDGET = 500
    }
}

/**
 * Result of context windowing.
 */
data class WindowResult(
    val messages: List<Message>,
    val summarizedCount: Int
)
