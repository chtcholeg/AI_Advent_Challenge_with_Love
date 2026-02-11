package ru.chtcholeg.agent.domain.service

import ru.chtcholeg.agent.config.AgentConfig
import ru.chtcholeg.agent.domain.model.CommandResult

/**
 * Handler for slash commands in the AI Agent.
 * Commands start with '/' and are processed before sending to AI.
 */
class CommandHandler(
    private val projectRootProvider: ProjectRootProvider
) {

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
            "review-pr", "review" -> handleReviewPrCommand(args)
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
                    "Также упомяни доступные команды: /help [тема] — справка по проекту, /review-pr [номер|ветка] — code review. " +
                    "Ответ дай компактно и структурированно."
            }

            // 3. Select relevant source files based on query keywords
            val relevantFiles = if (!args.isNullOrBlank()) {
                selectRelevantFiles(args)
            } else {
                emptyList()
            }

            // 4. Load code fragments within budget
            val budgetForCode = AgentConfig.MAX_CONTEXT_CHARS - primaryContext.length
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
     * Handle /review-pr command — performs code review using MCP tools.
     * Supports: PR number, branch name, or empty (current changes).
     */
    private suspend fun handleReviewPrCommand(args: String?): CommandResult {
        return try {
            // Load CLAUDE.md as project context
            val projectContext = projectRootProvider.readClaudeMdFile()

            // Detect if reviewing a PR (need different tools)
            val isPrReview = args?.trim()?.matches(Regex("^\\d+$")) == true

            // Build review instructions
            val reviewInstructions = buildString {
                appendLine("# Code Review Instructions")
                appendLine()
                appendLine("⚠️ КРИТИЧЕСКИ ВАЖНО: Ты ДОЛЖЕН ВЫЗЫВАТЬ инструменты (function calls) для получения данных.")
                appendLine("НЕ ОПИСЫВАЙ шаги словами. НЕ ДАВАЙ инструкции пользователю.")
                appendLine("ВЫЗОВИ инструменты ПРЯМО СЕЙЧАС через function call.")
                appendLine()
                if (isPrReview) {
                    appendLine("Последовательность вызовов инструментов:")
                    appendLine("1. ВЫЗОВИ github_pr_get — получить метаданные PR")
                    appendLine("2. ВЫЗОВИ github_pr_files — получить список изменённых файлов")
                    appendLine("3. ВЫЗОВИ github_pr_diff — получить diff изменений")
                    appendLine("НЕ используй git_status/git_diff — они показывают только локальные изменения, а не PR.")
                } else {
                    appendLine("Начни с ВЫЗОВА git_status или git_diff для получения изменений.")
                }
                appendLine("Затем ОБЯЗАТЕЛЬНО ВЫЗОВИ read для каждого изменённого файла, чтобы видеть полный контекст кода.")
                appendLine("В ответе ВСЕГДА цитируй конкретные строки кода — не просто ссылайся на номера строк.")
                appendLine()
                appendLine("ЗАПРЕЩЕНО: описывать процесс review словами, давать пошаговые инструкции, предлагать сделать review вручную.")
                appendLine("ОБЯЗАТЕЛЬНО: вызвать инструменты, получить реальные данные, проанализировать код и дать review.")
                appendLine()
                appendLine("Проведи детальное code review. Проверь следующие аспекты:")
                appendLine("1. **Архитектура** — соответствие паттернам проекта (MVI, слои, модули)")
                appendLine("2. **Баги** — потенциальные ошибки, NPE, race conditions, утечки ресурсов")
                appendLine("3. **Стиль кода** — именование, форматирование, идиоматичность Kotlin/Python")
                appendLine("4. **Безопасность** — инъекции, утечки секретов, небезопасные операции")
                appendLine("5. **Именование** — понятные имена переменных, функций, классов")
                appendLine("6. **Тесты** — покрытие тестами, пропущенные edge cases")
                appendLine("7. **Производительность** — неэффективные алгоритмы, лишние аллокации")
                appendLine()
                appendLine("Формат ответа:")
                appendLine("- Краткое резюме изменений")
                appendLine("- Список найденных проблем. Для КАЖДОЙ проблемы ОБЯЗАТЕЛЬНО:")
                appendLine("  - Укажи файл и номер строки")
                appendLine("  - Покажи САМИ СТРОКИ КОДА в блоке ```kotlin или ```python, которые содержат проблему")
                appendLine("  - Если предлагаешь исправление, покажи исправленный вариант кода тоже в блоке кода")
                appendLine("  - Пример формата:")
                appendLine("    **Файл:** `path/to/File.kt:42`")
                appendLine("    **Проблема:** описание")
                appendLine("    ```kotlin")
                appendLine("    // Текущий код (строки 42-45):")
                appendLine("    val result = someCall()")
                appendLine("    ```")
                appendLine("    **Рекомендация:**")
                appendLine("    ```kotlin")
                appendLine("    // Предлагаемый вариант:")
                appendLine("    val result = someCall() ?: defaultValue")
                appendLine("    ```")
                appendLine("- Рекомендации по улучшению")
                appendLine("- Общая оценка: ✅ Approve / ⚠️ Request Changes / ❌ Reject")
            }

            // Build context: review instructions + project documentation
            val context = buildString {
                appendLine(reviewInstructions)
                if (projectContext != null) {
                    appendLine()
                    appendLine("# Project Documentation")
                    appendLine()
                    val budgetForDocs = AgentConfig.MAX_CONTEXT_CHARS - reviewInstructions.length
                    if (projectContext.length <= budgetForDocs) {
                        appendLine(projectContext)
                    } else {
                        appendLine(projectContext.take(budgetForDocs))
                        appendLine("// ... (project docs truncated)")
                    }
                }
            }

            // Build query based on args
            val trimmedArgs = args?.trim()
            val query = when {
                // PR number
                trimmedArgs != null && trimmedArgs.matches(Regex("^\\d+$")) -> {
                    val prNumber = trimmedArgs
                    "Проведи code review для Pull Request #$prNumber. " +
                        "Начни ПРЯМО СЕЙЧАС: вызови github_pr_get с параметром pr_number=$prNumber."
                }
                // Branch name
                !trimmedArgs.isNullOrBlank() -> {
                    "Проведи code review изменений ветки '$trimmedArgs'. " +
                        "Начни ПРЯМО СЕЙЧАС: вызови git_diff для получения изменений."
                }
                // No args — current changes
                else -> {
                    "Проведи code review текущих изменений в рабочей директории. " +
                        "Начни ПРЯМО СЕЙЧАС: вызови git_status для получения списка изменений."
                }
            }

            CommandResult.NeedsLlmProcessing(
                context = context,
                query = query,
                enableTools = true,
                excludeTools = REVIEW_PR_EXCLUDED_TOOLS
            )
        } catch (e: Exception) {
            CommandResult.Error("Failed to prepare code review: ${e.message}")
        }
    }

    companion object {
        private const val MAX_SOURCE_FILES = 3
        private const val MAX_README_FALLBACK_CHARS = 8_000

        /**
         * Tools to exclude during /review-pr execution.
         * Only git, file operations, and bash are needed for code review.
         */
        private val REVIEW_PR_EXCLUDED_TOOLS = listOf(
            "task_create",
            "task_update",
            "task_list",
            "task_get",
            "task",
            "enter_plan_mode",
            "exit_plan_mode",
            "ask_user_question",
            "rag_search",
            "rag_status"
        )
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
