package ru.chtcholeg.agent.domain.service

import ru.chtcholeg.agent.domain.model.CommandResult

/**
 * Handler for slash commands in the AI Agent.
 * Commands start with '/' and are processed before sending to AI.
 */
class CommandHandler(
    private val projectRootProvider: ProjectRootProvider
) {

    companion object {
        private const val MAX_CONTEXT_CHARS = 16_000
        private const val MAX_SOURCE_FILES = 3
        private const val MAX_README_FALLBACK_CHARS = 8_000
    }

    /**
     * Keyword-to-source-file mapping for selecting relevant code fragments.
     * Paths are relative to the project root.
     */
    private val topicFileMap: Map<String, List<String>> = mapOf(
        "mvi" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentStore.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentIntent.kt"
        ),
        "store" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentStore.kt"
        ),
        "intent" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentIntent.kt"
        ),
        "state" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentStore.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentIntent.kt"
        ),
        "model" to listOf(
            "shared/src/commonMain/kotlin/ru/chtcholeg/shared/domain/model/Model.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/CommandResult.kt"
        ),
        "sealed" to listOf(
            "shared/src/commonMain/kotlin/ru/chtcholeg/shared/domain/model/Model.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/CommandResult.kt"
        ),
        "koin" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/di/Koin.kt"
        ),
        "di" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/di/Koin.kt"
        ),
        "dependency" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/di/Koin.kt"
        ),
        "settings" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/AiSettings.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/data/repository/SettingsRepository.kt"
        ),
        "validation" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/AiSettings.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/data/repository/SettingsRepository.kt"
        ),
        "repository" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/data/repository/SettingsRepository.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentStore.kt"
        ),
        "stateflow" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/data/repository/SettingsRepository.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/presentation/agent/AgentStore.kt"
        ),
        "command" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/service/CommandHandler.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/CommandResult.kt"
        ),
        "tool" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/service/LocalToolsProvider.kt",
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/LocalTool.kt"
        ),
        "message" to listOf(
            "ai-agent/src/commonMain/kotlin/ru/chtcholeg/agent/domain/model/AgentMessage.kt"
        )
    )

    /**
     * Check if a message is a command (starts with '/').
     */
    fun isCommand(message: String): Boolean {
        return message.trim().startsWith("/")
    }

    /**
     * Process a command and return the result.
     * Returns null if the command is not recognized.
     */
    suspend fun handleCommand(message: String): CommandResult? {
        val trimmedMessage = message.trim()

        if (!isCommand(trimmedMessage)) {
            return null
        }

        // Parse command and arguments
        val parts = trimmedMessage.substring(1).split(Regex("\\s+"), limit = 2)
        val command = parts[0].lowercase()
        val args = parts.getOrNull(1)

        return when (command) {
            "help" -> handleHelpCommand(args)
            else -> CommandResult.Error("Unknown command: /$command. Type /help for available commands.")
        }
    }

    /**
     * Handle /help command — uses CLAUDE.md as primary context (fallback to truncated README.md)
     * and selectively includes relevant source files based on the query topic.
     */
    private suspend fun handleHelpCommand(args: String?): CommandResult {
        return try {
            // 1. Read primary context: CLAUDE.md preferred, README.md as fallback
            val primaryContext = projectRootProvider.readClaudeMdFile()
                ?: projectRootProvider.readReadmeFile().take(MAX_README_FALLBACK_CHARS)

            // 2. Build the query
            val query = if (!args.isNullOrBlank()) {
                args
            } else {
                "Опиши структуру проекта: модули, их назначение, ключевые возможности и команды для сборки/запуска. " +
                    "Ответ дай компактно и структурированно."
            }

            // 3. Select relevant source files based on query keywords
            val relevantFiles = if (!args.isNullOrBlank()) {
                selectRelevantFiles(args)
            } else {
                emptyList()
            }

            // 4. Load code fragments within budget
            val budgetForCode = MAX_CONTEXT_CHARS - primaryContext.length
            val codeFragments = if (relevantFiles.isNotEmpty() && budgetForCode > 500) {
                loadCodeFragments(relevantFiles, budgetForCode)
            } else {
                ""
            }

            // 5. Compose structured context
            val context = buildString {
                appendLine("# Project Reference")
                appendLine()
                appendLine(primaryContext)
                if (codeFragments.isNotBlank()) {
                    appendLine()
                    appendLine("# Code Examples")
                    appendLine()
                    appendLine(codeFragments)
                }
            }

            // 6. Build enhanced query prompt
            val enhancedQuery = buildHelpQueryPrompt(query, codeFragments.isNotBlank())

            CommandResult.NeedsLlmProcessing(
                context = context,
                query = enhancedQuery
            )
        } catch (e: Exception) {
            CommandResult.Error("Failed to read project information: ${e.message}")
        }
    }

    /**
     * Select up to [MAX_SOURCE_FILES] relevant source files based on keyword matching in the query.
     */
    private fun selectRelevantFiles(query: String): List<String> {
        val queryLower = query.lowercase()
        val matchedFiles = mutableListOf<String>()

        for ((keyword, files) in topicFileMap) {
            if (keyword in queryLower) {
                for (file in files) {
                    if (file !in matchedFiles) {
                        matchedFiles.add(file)
                    }
                }
            }
        }

        return matchedFiles.take(MAX_SOURCE_FILES)
    }

    /**
     * Load code fragments from the given file paths within the character budget.
     * Truncates large files at a newline boundary.
     */
    private suspend fun loadCodeFragments(paths: List<String>, budgetChars: Int): String {
        val result = StringBuilder()
        var remaining = budgetChars

        for (path in paths) {
            if (remaining <= 200) break

            val content = projectRootProvider.readProjectFile(path) ?: continue
            val fileName = path.substringAfterLast('/')

            // Reserve space for the header
            val header = "## $fileName\n`$path`\n```kotlin\n"
            val footer = "\n```\n\n"
            val overhead = header.length + footer.length
            val availableForContent = remaining - overhead

            if (availableForContent <= 100) break

            val truncatedContent = if (content.length <= availableForContent) {
                content
            } else {
                // Truncate at the last newline within budget
                val cutoff = content.lastIndexOf('\n', availableForContent)
                if (cutoff > 0) {
                    content.substring(0, cutoff) + "\n// ... (truncated)"
                } else {
                    content.substring(0, availableForContent) + "\n// ... (truncated)"
                }
            }

            result.append(header)
            result.append(truncatedContent)
            result.append(footer)

            remaining -= (header.length + truncatedContent.length + footer.length)
        }

        return result.toString()
    }

    /**
     * Build the query prompt with instructions for code-aware responses.
     */
    private fun buildHelpQueryPrompt(query: String, hasCode: Boolean): String {
        return if (hasCode) {
            "$query\n\n" +
                "В ответе используй предоставленные примеры кода: ссылайся на файлы и пути, " +
                "показывай релевантные фрагменты, объясняй паттерны на основе реального кода проекта."
        } else {
            query
        }
    }
}
