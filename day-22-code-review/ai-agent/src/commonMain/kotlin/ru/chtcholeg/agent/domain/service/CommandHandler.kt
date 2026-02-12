package ru.chtcholeg.agent.domain.service

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.chtcholeg.agent.config.AgentConfig
import ru.chtcholeg.agent.data.repository.McpRepository
import ru.chtcholeg.agent.domain.model.CommandResult

/**
 * Handler for slash commands in the AI Agent.
 * Commands start with '/' and are processed before sending to AI.
 */
class CommandHandler(
    private val projectRootProvider: ProjectRootProvider,
    private val mcpRepository: McpRepository
) {

    /**
     * Pre-fetched review data collected programmatically before sending to the LLM.
     */
    private data class ReviewData(
        val changedFiles: List<String>,
        val diff: String,
        val fileContents: Map<String, String>
    )

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
     * Handle /review-pr command — performs code review.
     * First tries to pre-fetch all review data programmatically.
     * Falls back to LLM-driven tool calling if pre-fetch fails.
     */
    private suspend fun handleReviewPrCommand(args: String?): CommandResult {
        return try {
            val projectContext = projectRootProvider.readClaudeMdFile()
            val isPrReview = args?.trim()?.matches(Regex("^\\d+$")) == true

            // Try pre-fetching review data programmatically
            val reviewData = prefetchReviewData(isPrReview, args)

            if (reviewData != null) {
                buildPrefetchedReviewResult(reviewData, isPrReview, args, projectContext)
            } else {
                buildFallbackReviewResult(isPrReview, args, projectContext)
            }
        } catch (e: Exception) {
            CommandResult.Error("Failed to prepare code review: ${e.message}")
        }
    }

    // ── Pre-fetch helpers ──────────────────────────────────────────────

    /**
     * Call an MCP tool and return its text content, or null on failure.
     */
    private suspend fun callTool(name: String, args: Map<String, String> = emptyMap()): String? {
        val jsonArgs = buildJsonObject {
            args.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        }
        return mcpRepository.executeTool(name, jsonArgs)
            .getOrNull()
            ?.takeIf { !it.isError }
            ?.content
    }

    /**
     * Extract changed file paths from unified diff format.
     */
    private fun parseChangedFilesFromDiff(diff: String): List<String> {
        return Regex("""^diff --git a/.+ b/(.+)$""", RegexOption.MULTILINE)
            .findAll(diff)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    /**
     * Try to pre-fetch all data needed for code review.
     * Returns null if critical tools (diff) are unavailable.
     */
    private suspend fun prefetchReviewData(isPrReview: Boolean, args: String?): ReviewData? {
        val diff: String
        val changedFiles: List<String>

        if (isPrReview) {
            val prNumber = args?.trim() ?: return null
            diff = callTool("github_pr_diff", mapOf("pr_number" to prNumber)) ?: return null
            val filesResult = callTool("github_pr_files", mapOf("pr_number" to prNumber))
            changedFiles = if (filesResult != null) {
                parseChangedFilesFromDiff(diff)
            } else {
                parseChangedFilesFromDiff(diff)
            }
        } else {
            // Local mode: get status + diff
            callTool("git_status") // warm up / verify availability
            diff = callTool("git_diff") ?: return null
            changedFiles = parseChangedFilesFromDiff(diff)
        }

        if (changedFiles.isEmpty()) {
            // Return empty review data — the LLM will report "no changes"
            return ReviewData(changedFiles = emptyList(), diff = diff, fileContents = emptyMap())
        }

        // Read file contents within budget
        val fileContents = mutableMapOf<String, String>()
        var totalChars = 0

        for (filePath in changedFiles) {
            if (totalChars >= AgentConfig.MAX_REVIEW_TOTAL_FILES_CHARS) break

            val content = projectRootProvider.readProjectFile(filePath) ?: continue
            val budgetRemaining = AgentConfig.MAX_REVIEW_TOTAL_FILES_CHARS - totalChars
            val maxForFile = minOf(AgentConfig.MAX_REVIEW_FILE_CHARS, budgetRemaining)

            val truncated = if (content.length <= maxForFile) {
                content
            } else {
                val cutoff = content.lastIndexOf('\n', maxForFile)
                if (cutoff > 0) {
                    content.substring(0, cutoff) + "\n// ... (truncated)"
                } else {
                    content.substring(0, maxForFile) + "\n// ... (truncated)"
                }
            }

            fileContents[filePath] = truncated
            totalChars += truncated.length
        }

        return ReviewData(
            changedFiles = changedFiles,
            diff = diff,
            fileContents = fileContents
        )
    }

    /**
     * Build the context section containing pre-fetched review data.
     */
    private fun buildPrefetchedDataSection(data: ReviewData): String = buildString {
        appendLine("# Данные для Code Review (собраны автоматически)")
        appendLine()

        // Changed files list
        appendLine("## Изменённые файлы")
        if (data.changedFiles.isEmpty()) {
            appendLine("Изменений не найдено.")
        } else {
            for (file in data.changedFiles) {
                appendLine("- $file")
            }
        }
        appendLine()

        // Diff
        appendLine("## Diff")
        val truncatedDiff = if (data.diff.length <= AgentConfig.MAX_REVIEW_DIFF_CHARS) {
            data.diff
        } else {
            data.diff.take(AgentConfig.MAX_REVIEW_DIFF_CHARS) + "\n... (diff truncated)"
        }
        appendLine("```diff")
        appendLine(truncatedDiff)
        appendLine("```")
        appendLine()

        // File contents
        if (data.fileContents.isNotEmpty()) {
            appendLine("## Содержимое файлов")
            appendLine()
            for ((path, content) in data.fileContents) {
                val ext = path.substringAfterLast('.', "")
                appendLine("### $path")
                appendLine("```$ext")
                appendLine(content)
                appendLine("```")
                appendLine()
            }
        }
    }

    /**
     * Build simplified review instructions for pre-fetched mode (no tool calling steps).
     */
    private fun buildSimplifiedReviewInstructions(): String = buildString {
        appendLine("# Code Review Instructions")
        appendLine()
        appendLine("Все данные для review предоставлены выше. НЕ вызывай инструменты. Анализируй ТОЛЬКО предоставленные данные.")
        appendLine()

        // === FORMAT RULES ===
        appendLine("═══════════════════════════════════════")
        appendLine("ФОРМАТ ОТВЕТА (СТРОГО ОБЯЗАТЕЛЬНЫЙ)")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("⛔ ЗАПРЕЩЕНО:")
        appendLine("- Общие фразы без кода: «код выглядит хорошо», «стоит улучшить обработку ошибок»")
        appendLine("- Ссылки на строки без показа кода: «в строке 42 есть проблема»")
        appendLine("- Описание процесса review вместо самого review")
        appendLine("- Давать пошаговые инструкции пользователю")
        appendLine("- Копировать общие принципы (SOLID, null safety и т.д.) в раздел Рекомендации — они нужны ТЕБЕ для анализа, а не пользователю")
        appendLine("- Критиковать СОДЕРЖИМОЕ строковых литералов (prompt strings, user messages) как код — это текст инструкций для модели или пользователя, а не логика программы")
        appendLine("- Выдумывать проблемы там, где их нет — если код корректен, лучше написать «✅ OK» чем придумывать ложное замечание")
        appendLine("- Рецензировать файлы, которых НЕТ в diff — если файл не изменён, его НЕ ДОЛЖНО быть в review")
        appendLine("- Утверждать что-либо о коде без цитирования реальных строк из diff или содержимого файлов — каждое утверждение должно быть подкреплено конкретным кодом")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНО:")
        appendLine("- СНАЧАЛА перечисли ВСЕ изменённые файлы из раздела «Изменённые файлы» — рецензируй ТОЛЬКО эти файлы")
        appendLine("- Проанализировать КАЖДЫЙ изменённый файл НЕЗАВИСИМО ОТ ЯЗЫКА (Kotlin, Python, TOML, SQL и т.д.)")
        appendLine("- Для каждого файла — минимум одно замечание или «✅ Файл OK» С ОБОСНОВАНИЕМ (что именно проверил: concurrency, error handling, null safety и т.д.)")
        appendLine("- В каждом замечании показать минимум 3-5 строк реального кода из файла (не 1-2 строки)")
        appendLine("- Каждое замечание ДОЛЖНО цитировать реальный код из diff (строки `+`/`-`) или из содержимого файлов")
        appendLine("- Если рекомендация содержит исправление, исправление ДОЛЖНО быть синтаксически и семантически отличным от оригинала (не просто переименование переменной)")
        appendLine("- Рекомендации должны быть КОНКРЕТНЫМИ для данного кода, с примерами исправлений")
        appendLine("- Для многопоточного кода проверяй thread safety: shared mutable state, race conditions, отсутствие синхронизации")
        appendLine()
        appendLine("Формат КАЖДОГО замечания:")
        appendLine("---")
        appendLine("**Файл:** `path/to/File.kt:42`")
        appendLine("**Проблема:** [конкретное описание — что не так и почему это проблема]")
        appendLine("```kotlin")
        appendLine("// Текущий код (строки 42-48):")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("    user.name = newName  // NPE если user == null")
        appendLine("    repository.save(user)")
        appendLine("    logger.info(\"Updated user ${'$'}id\")")
        appendLine("}")
        appendLine("```")
        appendLine("**Исправление:**")
        appendLine("```kotlin")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("        ?: throw UserNotFoundException(id)")
        appendLine("    user.name = newName")
        appendLine("    repository.save(user)")
        appendLine("    logger.info(\"Updated user ${'$'}id\")")
        appendLine("}")
        appendLine("```")
        appendLine("---")
        appendLine()

        // === REVIEW ASPECTS ===
        appendLine("При анализе каждого файла задавай себе вопросы:")
        appendLine("- Есть ли потенциальные NPE, race conditions, утечки ресурсов?")
        appendLine("- Shared mutable state (var, MutableList, HashMap) используется из нескольких корутин/потоков — есть ли синхронизация?")
        appendLine("- Правильно ли обрабатываются ошибки и null-значения?")
        appendLine("- Соответствует ли код архитектуре проекта (MVI, слои)?")
        appendLine("- Есть ли проблемы с безопасностью (инъекции, утечки секретов)?")
        appendLine("- Понятны ли имена переменных и функций?")
        appendLine("- Есть ли неэффективные алгоритмы или лишние аллокации?")
        appendLine("- Не потеряна ли функциональность при рефакторинге (вызов метода убран, но замена не добавлена)?")
        appendLine()

        // === RESPONSE STRUCTURE ===
        appendLine("Структура ответа:")
        appendLine("0. **Изменённые файлы** — перечисли ВСЕ файлы из раздела «Изменённые файлы» (якорь для review, рецензируй ТОЛЬКО их)")
        appendLine("1. **Резюме** — что делает PR/изменение (2-3 предложения)")
        appendLine("2. **Разбор по файлам** — для КАЖДОГО файла из п.0: замечания с кодом ИЛИ «✅ OK»")
        appendLine("3. **Оценка** — ✅ Approve / ⚠️ Request Changes / ❌ Reject с обоснованием")
    }

    /**
     * Build CommandResult for pre-fetched review mode (tools disabled).
     */
    private fun buildPrefetchedReviewResult(
        data: ReviewData,
        isPrReview: Boolean,
        args: String?,
        projectContext: String?
    ): CommandResult {
        val dataSection = buildPrefetchedDataSection(data)
        val instructions = buildSimplifiedReviewInstructions()

        val context = buildString {
            appendLine(dataSection)
            appendLine(instructions)
            if (projectContext != null) {
                val used = dataSection.length + instructions.length
                val budgetForDocs = AgentConfig.MAX_CONTEXT_CHARS - used
                if (budgetForDocs > 500) {
                    appendLine()
                    appendLine("# Project Documentation")
                    appendLine()
                    if (projectContext.length <= budgetForDocs) {
                        appendLine(projectContext)
                    } else {
                        appendLine(projectContext.take(budgetForDocs))
                        appendLine("// ... (project docs truncated)")
                    }
                }
            }
        }

        val trimmedArgs = args?.trim()
        val query = when {
            isPrReview -> "Проведи code review для Pull Request #$trimmedArgs на основе предоставленных данных."
            !trimmedArgs.isNullOrBlank() -> "Проведи code review изменений ветки '$trimmedArgs' на основе предоставленных данных."
            else -> "Проведи code review текущих изменений на основе предоставленных данных."
        }

        return CommandResult.NeedsLlmProcessing(
            context = context,
            query = query,
            enableTools = false
        )
    }

    /**
     * Build CommandResult for fallback mode (LLM calls tools itself).
     * Used when pre-fetch fails (e.g., MCP server unavailable).
     */
    private fun buildFallbackReviewResult(
        isPrReview: Boolean,
        args: String?,
        projectContext: String?
    ): CommandResult {
        val reviewInstructions = buildFallbackReviewInstructions(isPrReview)

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

        val trimmedArgs = args?.trim()
        val query = when {
            trimmedArgs != null && trimmedArgs.matches(Regex("^\\d+$")) -> {
                val prNumber = trimmedArgs
                "Проведи code review для Pull Request #$prNumber. " +
                    "Начни ПРЯМО СЕЙЧАС: вызови github_pr_get с параметром pr_number=$prNumber. " +
                    "Затем github_pr_files, затем github_pr_diff, затем read для КАЖДОГО изменённого файла. " +
                    "В финальном review ОБЯЗАТЕЛЬНО цитируй конкретные строки кода из файлов."
            }
            !trimmedArgs.isNullOrBlank() -> {
                "Проведи code review изменений ветки '$trimmedArgs'. " +
                    "Начни ПРЯМО СЕЙЧАС: вызови git_diff для получения изменений. " +
                    "Затем вызови read для КАЖДОГО изменённого файла. " +
                    "В финальном review ОБЯЗАТЕЛЬНО цитируй конкретные строки кода из файлов."
            }
            else -> {
                "Проведи code review текущих изменений в рабочей директории. " +
                    "Начни ПРЯМО СЕЙЧАС: вызови git_status для получения списка изменений. " +
                    "Затем git_diff, затем read для КАЖДОГО изменённого файла. " +
                    "В финальном review ОБЯЗАТЕЛЬНО цитируй конкретные строки кода из файлов."
            }
        }

        return CommandResult.NeedsLlmProcessing(
            context = context,
            query = query,
            enableTools = true,
            includeTools = REVIEW_PR_ALLOWED_TOOLS
        )
    }

    /**
     * Build full review instructions with tool-calling steps (fallback mode).
     */
    private fun buildFallbackReviewInstructions(isPrReview: Boolean): String = buildString {
        appendLine("# Code Review Instructions")
        appendLine()
        appendLine("⚠️ КРИТИЧЕСКИ ВАЖНО: Ты ДОЛЖЕН ВЫЗЫВАТЬ инструменты (function calls) для получения данных.")
        appendLine("НЕ ОПИСЫВАЙ шаги словами. НЕ ДАВАЙ инструкции пользователю.")
        appendLine("ВЫЗОВИ инструменты ПРЯМО СЕЙЧАС через function call.")
        appendLine()

        if (isPrReview) {
            appendLine("ОБЯЗАТЕЛЬНАЯ последовательность вызовов инструментов:")
            appendLine("ШАГ 1: ВЫЗОВИ github_pr_get — получить метаданные PR")
            appendLine("ШАГ 2: ВЫЗОВИ github_pr_files — получить список изменённых файлов")
            appendLine("ШАГ 3: ВЫЗОВИ github_pr_diff — получить diff изменений")
            appendLine("ШАГ 4 (ОБЯЗАТЕЛЬНЫЙ): ВЫЗОВИ read для КАЖДОГО изменённого файла из списка.")
            appendLine("  - read нужен чтобы увидеть ПОЛНЫЙ контекст: номера строк, окружающий код, импорты.")
            appendLine("  - Без read ты НЕ МОЖЕШЬ дать конкретный review с номерами строк.")
            appendLine("  - Пример: если изменён файл 'src/Foo.kt', вызови read с path='src/Foo.kt'.")
            appendLine("ШАГ 5: Напиши review С ЦИТАТАМИ КОНКРЕТНЫХ СТРОК КОДА (см. формат ниже).")
            appendLine()
            appendLine("НЕ используй git_status/git_diff/github_whoami/github_pr_list")
        } else {
            appendLine("ОБЯЗАТЕЛЬНАЯ последовательность вызовов инструментов:")
            appendLine("ШАГ 1: ВЫЗОВИ git_status — получить список изменённых файлов.")
            appendLine("ШАГ 2: ВЫЗОВИ git_diff — получить diff изменений.")
            appendLine("ШАГ 3 (ОБЯЗАТЕЛЬНЫЙ): ВЫЗОВИ read для КАЖДОГО изменённого файла.")
            appendLine("  - read нужен чтобы увидеть ПОЛНЫЙ контекст: номера строк, окружающий код, импорты.")
            appendLine("  - Без read ты НЕ МОЖЕШЬ дать конкретный review с номерами строк.")
            appendLine("ШАГ 4: Напиши review С ЦИТАТАМИ КОНКРЕТНЫХ СТРОК КОДА (см. формат ниже).")
        }
        appendLine()

        appendLine("═══════════════════════════════════════")
        appendLine("САМОПРОВЕРКА ПЕРЕД НАПИСАНИЕМ REVIEW")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("ПЕРЕД написанием review ПРОВЕРЬ:")
        appendLine("1. Ты ВЫЗВАЛ инструменты и ПОЛУЧИЛ данные (diff, содержимое файлов)?")
        appendLine("2. Если НЕТ — напиши ТОЛЬКО: «Не удалось получить данные для review. Убедитесь, что MCP-сервер запущен.» и ОСТАНОВИСЬ.")
        appendLine("3. У тебя есть КОНКРЕТНЫЙ diff с строками `+` и `-`?")
        appendLine("4. Если diff пустой — напиши: «Изменений не найдено.» и ОСТАНОВИСЬ.")
        appendLine()

        appendLine("═══════════════════════════════════════")
        appendLine("ФОРМАТ ОТВЕТА (СТРОГО ОБЯЗАТЕЛЬНЫЙ)")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("⛔ ЗАПРЕЩЕНО:")
        appendLine("- Общие фразы без кода: «код выглядит хорошо», «стоит улучшить обработку ошибок»")
        appendLine("- Ссылки на строки без показа кода: «в строке 42 есть проблема»")
        appendLine("- Описание процесса review вместо самого review")
        appendLine("- Давать пошаговые инструкции пользователю")
        appendLine("- Копировать общие принципы (SOLID, null safety и т.д.) в раздел Рекомендации — они нужны ТЕБЕ для анализа, а не пользователю")
        appendLine("- Критиковать СОДЕРЖИМОЕ строковых литералов (prompt strings, user messages) как код — это текст инструкций для модели или пользователя, а не логика программы")
        appendLine("- Выдумывать проблемы там, где их нет — если код корректен, лучше написать «✅ OK» чем придумывать ложное замечание")
        appendLine("- Рецензировать файлы, которых НЕТ в diff — если файл не изменён, его НЕ ДОЛЖНО быть в review")
        appendLine("- Утверждать что-либо о коде без цитирования реальных строк из diff или результатов read — каждое утверждение должно быть подкреплено конкретным кодом")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНО:")
        appendLine("- СНАЧАЛА перечисли ВСЕ изменённые файлы из diff/git_status — рецензируй ТОЛЬКО эти файлы")
        appendLine("- Проанализировать КАЖДЫЙ изменённый файл НЕЗАВИСИМО ОТ ЯЗЫКА (Kotlin, Python, TOML, SQL и т.д.)")
        appendLine("- Для каждого файла — минимум одно замечание или «✅ Файл OK» С ОБОСНОВАНИЕМ (что именно проверил: concurrency, error handling, null safety и т.д.)")
        appendLine("- В каждом замечании показать минимум 3-5 строк реального кода из файла (не 1-2 строки)")
        appendLine("- Каждое замечание ДОЛЖНО цитировать реальный код из diff (строки `+`/`-`) или из результатов `read`")
        appendLine("- Если рекомендация содержит исправление, исправление ДОЛЖНО быть синтаксически и семантически отличным от оригинала (не просто переименование переменной)")
        appendLine("- Рекомендации должны быть КОНКРЕТНЫМИ для данного кода, с примерами исправлений")
        appendLine("- Для многопоточного кода проверяй thread safety: shared mutable state, race conditions, отсутствие синхронизации")
        appendLine()
        appendLine("Формат КАЖДОГО замечания:")
        appendLine("---")
        appendLine("**Файл:** `path/to/File.kt:42`")
        appendLine("**Проблема:** [конкретное описание — что не так и почему это проблема]")
        appendLine("```kotlin")
        appendLine("// Текущий код (строки 42-48):")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("    user.name = newName  // NPE если user == null")
        appendLine("    repository.save(user)")
        appendLine("    logger.info(\"Updated user ${'$'}id\")")
        appendLine("}")
        appendLine("```")
        appendLine("**Исправление:**")
        appendLine("```kotlin")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("        ?: throw UserNotFoundException(id)")
        appendLine("    user.name = newName")
        appendLine("    repository.save(user)")
        appendLine("    logger.info(\"Updated user ${'$'}id\")")
        appendLine("}")
        appendLine("```")
        appendLine("---")
        appendLine()

        appendLine("При анализе каждого файла задавай себе вопросы:")
        appendLine("- Есть ли потенциальные NPE, race conditions, утечки ресурсов?")
        appendLine("- Shared mutable state (var, MutableList, HashMap) используется из нескольких корутин/потоков — есть ли синхронизация?")
        appendLine("- Правильно ли обрабатываются ошибки и null-значения?")
        appendLine("- Соответствует ли код архитектуре проекта (MVI, слои)?")
        appendLine("- Есть ли проблемы с безопасностью (инъекции, утечки секретов)?")
        appendLine("- Понятны ли имена переменных и функций?")
        appendLine("- Есть ли неэффективные алгоритмы или лишние аллокации?")
        appendLine("- Не потеряна ли функциональность при рефакторинге (вызов метода убран, но замена не добавлена)?")
        appendLine()

        appendLine("Структура ответа:")
        appendLine("0. **Изменённые файлы** — перечисли ВСЕ файлы из diff/git_status (якорь для review, рецензируй ТОЛЬКО их)")
        appendLine("1. **Резюме** — что делает PR/изменение (2-3 предложения)")
        appendLine("2. **Разбор по файлам** — для КАЖДОГО файла из п.0: замечания с кодом ИЛИ «✅ OK»")
        appendLine("3. **Оценка** — ✅ Approve / ⚠️ Request Changes / ❌ Reject с обоснованием")
    }

    companion object {
        private const val MAX_SOURCE_FILES = 3
        private const val MAX_README_FALLBACK_CHARS = 8_000

        /**
         * Whitelist of tools allowed during /review-pr execution (fallback mode).
         * Narrow set keeps the model focused on the right tools.
         */
        private val REVIEW_PR_ALLOWED_TOOLS = listOf(
            // GitHub PR tools (primary for PR review)
            "github_pr_get",
            "github_pr_files",
            "github_pr_diff",
            // Git tools (for branch/local review)
            "git_status",
            "git_diff",
            "git_log",
            // File reading for full context
            "read",
            "glob",
            "grep"
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
