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
            changedFiles = parseChangedFilesFromDiff(diff)
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
        appendLine("Все данные для review предоставлены ниже. НЕ вызывай инструменты. Анализируй ТОЛЬКО предоставленные данные.")
        appendLine()

        // === STEP 0: UNDERSTAND THE CHANGE ===
        appendLine("═══════════════════════════════════════")
        appendLine("ШАГ 0: СНАЧАЛА ПОЙМИ СУТЬ ИЗМЕНЕНИЯ")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("ПЕРЕД анализом файлов, прочитай весь diff и ответь себе:")
        appendLine("- Какова ЦЕЛЬ этого PR? (новая фича, багфикс, рефакторинг, оптимизация?)")
        appendLine("- Что ДОБАВЛЕНО? Что УДАЛЕНО? Что ИЗМЕНЕНО?")
        appendLine("- Какие архитектурные решения приняты?")
        appendLine()
        appendLine("ТОЛЬКО после понимания общей картины — переходи к анализу отдельных файлов.")
        appendLine()

        // === ANTI-PATTERN: what a BAD review looks like ===
        appendLine("═══════════════════════════════════════")
        appendLine("ПРИМЕРЫ ПЛОХОГО REVIEW (ТАК ДЕЛАТЬ НЕЛЬЗЯ)")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("❌ ПЛОХО #1 — бесполезный review без анализа:")
        appendLine("```")
        appendLine("**AgentRepository.kt**")
        appendLine("✅ OK")
        appendLine("Файл содержит изменения, связанные с добавлением логики обработки сообщений.")
        appendLine("```")
        appendLine("Почему плохо: нет конкретики, нет цитат кода, нет анализа — модель просто пересказала название файла.")
        appendLine()
        appendLine("❌ ПЛОХО #2 — FALSE POSITIVE, локальные переменные это НЕ shared state:")
        appendLine("```")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** Shared mutable state без синхронизации")
        appendLine("```kotlin")
        appendLine("suspend fun sendMessage(text: String) {")
        appendLine("    val allTools = mcpRepository.getAllTools()")
        appendLine("    val filtered = allTools.filter { it.enabled }")
        appendLine("}")
        appendLine("```")
        appendLine("**Рекомендация:** Добавить синхронизацию к allTools")
        appendLine("```")
        appendLine("Почему плохо:")
        appendLine("- `allTools` — локальная переменная внутри suspend функции, НЕ shared state")
        appendLine("- `val` = immutable reference, никакого race condition")
        appendLine("- Это выдуманная проблема, которой не существует")
        appendLine()
        appendLine("❌ ПЛОХО #3 — утверждение без демонстрации:")
        appendLine("```")
        appendLine("**Проблема:** Возможен NPE при вызове user.name")
        appendLine("```kotlin")
        appendLine("val user = repository.findById(id)")
        appendLine("user.name = newName")
        appendLine("```")
        appendLine("```")
        appendLine("Почему плохо:")
        appendLine("- НЕ показано, что findById() возвращает nullable тип")
        appendLine("- НЕ показано, при каких условиях возникнет NPE")
        appendLine("- Нужно было привести сигнатуру метода: `fun findById(id: String): User?`")
        appendLine()

        // === FORMAT RULES ===
        appendLine("═══════════════════════════════════════")
        appendLine("ФОРМАТ ОТВЕТА (СТРОГО ОБЯЗАТЕЛЬНЫЙ)")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("⛔ ЗАПРЕЩЕНО:")
        appendLine("- Шаблонные фразы: «файл содержит изменения, связанные с...», «код выглядит хорошо», «улучшена логика работы»")
        appendLine("- Ссылки на строки без показа кода: «в строке 42 есть проблема»")
        appendLine("- Описание процесса review вместо самого review")
        appendLine("- Давать пошаговые инструкции пользователю")
        appendLine("- Копировать общие принципы (SOLID, null safety и т.д.) — они нужны ТЕБЕ для анализа")
        appendLine("- Критиковать СОДЕРЖИМОЕ строковых литералов (prompt strings, user messages) — это текст, не логика")
        appendLine("- **КРИТИЧЕСКИ ВАЖНО: Выдумывать проблемы где их нет** — лучше честно ничего не найти, чем выдумать false positive")
        appendLine("- Рецензировать файлы, которых НЕТ в diff")
        appendLine("- Утверждать что-либо без цитирования реального кода из diff или содержимого файлов")
        appendLine("- Ставить ✅ OK без ДЕТАЛЬНОГО обоснования (минимум 2-3 предложения о том, ЧТО КОНКРЕТНО проверено)")
        appendLine("- **Путать локальные переменные с shared state** — `val local = ...` внутри функции НЕ требует синхронизации")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНО:")
        appendLine("- СНАЧАЛА перечисли ВСЕ изменённые файлы — рецензируй ТОЛЬКО их")
        appendLine("- Проанализировать КАЖДЫЙ изменённый файл НЕЗАВИСИМО ОТ ЯЗЫКА (Kotlin, Python, TOML, SQL и т.д.)")
        appendLine("- Каждому замечанию присвоить severity: 🔴 Critical, 🟠 High, 🟡 Medium, 🔵 Low")
        appendLine("- В каждом замечании показать 3-5 строк реального кода из файла")
        appendLine("- Каждое замечание ДОЛЖНО цитировать реальный код из diff (`+`/`-`) или содержимого файлов")
        appendLine("- **Для каждой проблемы ПРОДЕМОНСТРИРОВАТЬ как она проявится:** показать stack trace, race condition scenario, или конкретный вход, при котором случится ошибка")
        appendLine("- Если рекомендация содержит исправление, оно ДОЛЖНО быть отличным от оригинала (не просто переименование)")
        appendLine("- Для файлов без замечаний: «✅ OK» + ДЕТАЛЬНОЕ обоснование (перечислить что проверено: thread safety, null safety, error handling, resource leaks, security — и почему проблем нет)")
        appendLine()

        // === ANALYSIS METHODOLOGY ===
        appendLine("═══════════════════════════════════════")
        appendLine("МЕТОДОЛОГИЯ АНАЛИЗА")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Для КАЖДОГО файла выполни анализ по чек-листу (внутренне, НЕ выводи сам чек-лист):")
        appendLine()
        appendLine("1. CONCURRENCY (КРИТИЧЕСКИ ВАЖНО — много false positives):")
        appendLine("   Shared state = переменная на уровне КЛАССА, используемая из нескольких корутин/потоков.")
        appendLine("   ")
        appendLine("   ✅ НЕ является shared state (синхронизация НЕ нужна):")
        appendLine("   - `val local = ...` внутри функции/метода")
        appendLine("   - `val result = repository.getData()` в suspend функции")
        appendLine("   - Параметры функций")
        appendLine("   - Return values из других функций")
        appendLine("   ")
        appendLine("   ⚠️ ЯВЛЯЕТСЯ shared state (нужна проверка синхронизации):")
        appendLine("   - `class Repo { private var cache: MutableMap<K,V> ... }` — доступ из разных корутин")
        appendLine("   - `object Singleton { val mutableList = mutableListOf<T>() }` — изменяется из разных мест")
        appendLine("   ")
        appendLine("   ЕСЛИ нашёл shared mutable state:")
        appendLine("   - Покажи ДВА места в коде, где к нему идёт доступ из разных корутин/потоков")
        appendLine("   - Опиши конкретный race condition scenario: «Корутина A читает cache[key], корутина B записывает cache[key], возможно HashMap corruption»")
        appendLine("   - Если НЕ можешь продемонстрировать race condition — это false positive, НЕ пиши замечание")
        appendLine()
        appendLine("2. NULL SAFETY:")
        appendLine("   Есть ли `!!`, unchecked casts, nullable без проверки? Может ли null прийти от внешнего API?")
        appendLine("   ЕСЛИ нашёл проблему:")
        appendLine("   - Покажи сигнатуру метода (что он возвращает nullable)")
        appendLine("   - Покажи код, который не проверяет null")
        appendLine("   - Опиши сценарий: «Если API вернёт null, вызов user.name вызовет NPE в строке X»")
        appendLine()
        appendLine("3. ERROR HANDLING:")
        appendLine("   Ловятся ли исключения? Есть ли пустые catch-блоки? Есть ли try без finally для ресурсов?")
        appendLine()
        appendLine("4. RESOURCE LEAKS:")
        appendLine("   Есть ли CoroutineScope/HttpClient/Stream без close/cancel? Кто владеет lifecycle?")
        appendLine()
        appendLine("5. SECURITY:")
        appendLine("   Пишутся ли секреты в логи/файлы? Есть ли SQL/command injection? Валидируется ли ввод?")
        appendLine()
        appendLine("6. LOGIC BUGS:")
        appendLine("   Есть ли off-by-one? Правильны ли граничные условия? Не потеряна ли функциональность при рефакторинге?")
        appendLine()
        appendLine("7. DATA LOSS:")
        appendLine("   Берётся ли только первый элемент из коллекции где может быть несколько? Теряются ли результаты?")
        appendLine()
        appendLine("8. АРХИТЕКТУРНЫЕ РЕШЕНИЯ:")
        appendLine("   - Magic strings вместо констант/enum?")
        appendLine("   - Хрупкая логика (проверка через .contains() строки вместо явного флага)?")
        appendLine("   - Условия на комбинациях параметров вместо explicit enum/sealed class?")
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("⚠️ ПРАВИЛО: Если НЕ можешь продемонстрировать КАК проявится проблема — это false positive, НЕ пиши замечание.")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Выводи ТОЛЬКО найденные проблемы (или обоснованное ✅ OK). НЕ выводи сам чек-лист.")
        appendLine()

        // === FINDING FORMAT ===
        appendLine("Формат КАЖДОГО замечания:")
        appendLine("---")
        appendLine("**Файл:** `path/to/File.kt:42`")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** [конкретное описание — что не так и почему это проблема]")
        appendLine("```kotlin")
        appendLine("// Текущий код (строки 42-48):")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("    user.name = newName  // NPE если user == null")
        appendLine("    repository.save(user)")
        appendLine("}")
        appendLine("```")
        appendLine("**Рекомендация:**")
        appendLine("```kotlin")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("        ?: throw UserNotFoundException(id)")
        appendLine("    user.name = newName")
        appendLine("    repository.save(user)")
        appendLine("}")
        appendLine("```")
        appendLine("---")
        appendLine()

        // === GOOD OK EXAMPLE ===
        appendLine("Примеры ХОРОШИХ «✅ OK»:")
        appendLine()
        appendLine("```")
        appendLine("**AgentConfig.kt**")
        appendLine("✅ OK — Увеличены лимиты для review: MAX_CONTEXT_CHARS (16K→50K), MAX_REVIEW_DIFF_CHARS (12K→20K),")
        appendLine("MAX_REVIEW_FILE_CHARS (3K→6K), MAX_REVIEW_TOTAL_FILES_CHARS (20K→40K). Проверено:")
        appendLine("- Изменения согласованы между собой")
        appendLine("- Общий бюджет (diff 20K + files 40K = 60K) превышает MAX_CONTEXT_CHARS (50K), но это OK,")
        appendLine("  т.к. есть логика обрезки в buildPrefetchedDataSection()")
        appendLine("- Все значения остаются в разумных пределах для GigaChat API")
        appendLine("```")
        appendLine()
        appendLine("```")
        appendLine("**CommandHandler.kt**")
        appendLine("✅ OK — Улучшены инструкции для review: добавлены anti-patterns (строки 346-398), методология")
        appendLine("анализа с чек-листом (строки 431-465), требование severity levels. Переупорядочены секции:")
        appendLine("инструкции перед данными (buildPrefetchedReviewResult:472-475) для лучшего следования промпту.")
        appendLine("Проверено:")
        appendLine("- SECURITY: нет SQL/command injection (нет SQL запросов, используются только string literals)")
        appendLine("- LOGIC: prompt длина ~2500 строк — вписывается в MAX_CONTEXT_CHARS (50K)")
        appendLine("- NULL SAFETY: все buildString { ... } возвращают non-null String")
        appendLine("```")
        appendLine()
        appendLine("Обрати внимание:")
        appendLine("- Указано ЧТО изменилось (конкретные строки кода, значения)")
        appendLine("- Перечислены аспекты проверки (security, logic, null safety)")
        appendLine("- Даны ОБОСНОВАНИЯ почему проблем нет (есть логика обрезки, нет SQL запросов и т.д.)")
        appendLine()

        // === RESPONSE STRUCTURE ===
        appendLine("═══════════════════════════════════════")
        appendLine("СТРУКТУРА ОТВЕТА")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("0. **Изменённые файлы** — перечисли ВСЕ файлы из diff (рецензируй ТОЛЬКО их)")
        appendLine("1. **Резюме** — что делает PR/изменение (2-3 предложения)")
        appendLine("2. **Разбор по файлам** — для КАЖДОГО файла: замечания с severity и кодом ИЛИ детальный «✅ OK»")
        appendLine("3. **Сводная таблица** (обязательно):")
        appendLine("   | Файл | Severity | Тип проблемы | Краткое описание |")
        appendLine("   |------|----------|-------------|-----------------|")
        appendLine("   | File.kt | 🟠 High | Race condition | Shared mutable state без синхронизации |")
        appendLine("   | Other.py | ✅ OK | — | — |")
        appendLine("4. **Оценка:**")
        appendLine("   - ✅ Approve — нет 🔴 Critical и 🟠 High замечаний")
        appendLine("   - ⚠️ Request Changes — есть 🟠 High или множество 🟡 Medium")
        appendLine("   - ❌ Reject — есть 🔴 Critical (security, data loss, crash)")
    }

    /**
     * Build CommandResult for pre-fetched review mode (tools disabled).
     * Order: instructions FIRST → data SECOND → project docs LAST.
     * Models follow instructions better when they appear before the data.
     */
    private fun buildPrefetchedReviewResult(
        data: ReviewData,
        isPrReview: Boolean,
        args: String?,
        projectContext: String?
    ): CommandResult {
        val instructions = buildSimplifiedReviewInstructions()
        val dataSection = buildPrefetchedDataSection(data)

        val context = buildString {
            // Instructions FIRST — model reads them before seeing the data
            appendLine(instructions)
            appendLine()
            // Data SECOND — model applies the instructions while reading
            appendLine(dataSection)
            // Project docs LAST — supplementary context
            if (projectContext != null) {
                val used = instructions.length + dataSection.length
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

        // === ANTI-PATTERN ===
        appendLine("═══════════════════════════════════════")
        appendLine("ПРИМЕРЫ ПЛОХОГО REVIEW (ТАК ДЕЛАТЬ НЕЛЬЗЯ)")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("❌ ПЛОХО #1 — бесполезный review без анализа:")
        appendLine("```")
        appendLine("**AgentRepository.kt**")
        appendLine("✅ OK")
        appendLine("Файл содержит изменения, связанные с добавлением логики обработки сообщений.")
        appendLine("```")
        appendLine("Почему плохо: нет конкретики, нет цитат кода, нет анализа — модель просто пересказала название файла.")
        appendLine()
        appendLine("❌ ПЛОХО #2 — FALSE POSITIVE, локальные переменные это НЕ shared state:")
        appendLine("```")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** Shared mutable state без синхронизации")
        appendLine("```kotlin")
        appendLine("suspend fun sendMessage(text: String) {")
        appendLine("    val allTools = mcpRepository.getAllTools()")
        appendLine("    val filtered = allTools.filter { it.enabled }")
        appendLine("}")
        appendLine("```")
        appendLine("**Рекомендация:** Добавить синхронизацию к allTools")
        appendLine("```")
        appendLine("Почему плохо:")
        appendLine("- `allTools` — локальная переменная внутри suspend функции, НЕ shared state")
        appendLine("- `val` = immutable reference, никакого race condition")
        appendLine("- Это выдуманная проблема, которой не существует")
        appendLine()
        appendLine("❌ ПЛОХО #3 — утверждение без демонстрации:")
        appendLine("```")
        appendLine("**Проблема:** Возможен NPE при вызове user.name")
        appendLine("```kotlin")
        appendLine("val user = repository.findById(id)")
        appendLine("user.name = newName")
        appendLine("```")
        appendLine("```")
        appendLine("Почему плохо:")
        appendLine("- НЕ показано, что findById() возвращает nullable тип")
        appendLine("- НЕ показано, при каких условиях возникнет NPE")
        appendLine("- Нужно было привести сигнатуру метода: `fun findById(id: String): User?`")
        appendLine()

        appendLine("═══════════════════════════════════════")
        appendLine("ФОРМАТ ОТВЕТА (СТРОГО ОБЯЗАТЕЛЬНЫЙ)")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("⛔ ЗАПРЕЩЕНО:")
        appendLine("- Шаблонные фразы: «файл содержит изменения, связанные с...», «код выглядит хорошо», «улучшена логика работы»")
        appendLine("- Ссылки на строки без показа кода: «в строке 42 есть проблема»")
        appendLine("- Описание процесса review вместо самого review")
        appendLine("- Давать пошаговые инструкции пользователю")
        appendLine("- Копировать общие принципы (SOLID, null safety и т.д.) — они нужны ТЕБЕ для анализа")
        appendLine("- Критиковать СОДЕРЖИМОЕ строковых литералов (prompt strings, user messages) — это текст, не логика")
        appendLine("- **КРИТИЧЕСКИ ВАЖНО: Выдумывать проблемы где их нет** — лучше честно ничего не найти, чем выдумать false positive")
        appendLine("- Рецензировать файлы, которых НЕТ в diff")
        appendLine("- Утверждать что-либо без цитирования реального кода из diff или результатов read")
        appendLine("- Ставить ✅ OK без ДЕТАЛЬНОГО обоснования (минимум 2-3 предложения о том, ЧТО КОНКРЕТНО проверено)")
        appendLine("- **Путать локальные переменные с shared state** — `val local = ...` внутри функции НЕ требует синхронизации")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНО:")
        appendLine("- СНАЧАЛА перечисли ВСЕ изменённые файлы из diff/git_status — рецензируй ТОЛЬКО их")
        appendLine("- Проанализировать КАЖДЫЙ изменённый файл НЕЗАВИСИМО ОТ ЯЗЫКА (Kotlin, Python, TOML, SQL и т.д.)")
        appendLine("- Каждому замечанию присвоить severity: 🔴 Critical, 🟠 High, 🟡 Medium, 🔵 Low")
        appendLine("- В каждом замечании показать 3-5 строк реального кода из файла")
        appendLine("- Каждое замечание ДОЛЖНО цитировать реальный код из diff (`+`/`-`) или из результатов `read`")
        appendLine("- **Для каждой проблемы ПРОДЕМОНСТРИРОВАТЬ как она проявится:** показать stack trace, race condition scenario, или конкретный вход, при котором случится ошибка")
        appendLine("- Если рекомендация содержит исправление, оно ДОЛЖНО быть отличным от оригинала (не просто переименование)")
        appendLine("- Для файлов без замечаний: «✅ OK» + ДЕТАЛЬНОЕ обоснование (перечислить что проверено: thread safety, null safety, error handling, resource leaks, security — и почему проблем нет)")
        appendLine()

        // === ANALYSIS METHODOLOGY ===
        appendLine("═══════════════════════════════════════")
        appendLine("МЕТОДОЛОГИЯ АНАЛИЗА")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Для КАЖДОГО файла выполни анализ по чек-листу (внутренне, НЕ выводи сам чек-лист):")
        appendLine()
        appendLine("1. CONCURRENCY (КРИТИЧЕСКИ ВАЖНО — много false positives):")
        appendLine("   Shared state = переменная на уровне КЛАССА, используемая из нескольких корутин/потоков.")
        appendLine("   ")
        appendLine("   ✅ НЕ является shared state (синхронизация НЕ нужна):")
        appendLine("   - `val local = ...` внутри функции/метода")
        appendLine("   - `val result = repository.getData()` в suspend функции")
        appendLine("   - Параметры функций")
        appendLine("   - Return values из других функций")
        appendLine("   ")
        appendLine("   ⚠️ ЯВЛЯЕТСЯ shared state (нужна проверка синхронизации):")
        appendLine("   - `class Repo { private var cache: MutableMap<K,V> ... }` — доступ из разных корутин")
        appendLine("   - `object Singleton { val mutableList = mutableListOf<T>() }` — изменяется из разных мест")
        appendLine("   ")
        appendLine("   ЕСЛИ нашёл shared mutable state:")
        appendLine("   - Покажи ДВА места в коде, где к нему идёт доступ из разных корутин/потоков")
        appendLine("   - Опиши конкретный race condition scenario: «Корутина A читает cache[key], корутина B записывает cache[key], возможно HashMap corruption»")
        appendLine("   - Если НЕ можешь продемонстрировать race condition — это false positive, НЕ пиши замечание")
        appendLine()
        appendLine("2. NULL SAFETY:")
        appendLine("   Есть ли `!!`, unchecked casts, nullable без проверки? Может ли null прийти от внешнего API?")
        appendLine("   ЕСЛИ нашёл проблему:")
        appendLine("   - Покажи сигнатуру метода (что он возвращает nullable)")
        appendLine("   - Покажи код, который не проверяет null")
        appendLine("   - Опиши сценарий: «Если API вернёт null, вызов user.name вызовет NPE в строке X»")
        appendLine()
        appendLine("3. ERROR HANDLING:")
        appendLine("   Ловятся ли исключения? Есть ли пустые catch-блоки? Есть ли try без finally для ресурсов?")
        appendLine()
        appendLine("4. RESOURCE LEAKS:")
        appendLine("   Есть ли CoroutineScope/HttpClient/Stream без close/cancel? Кто владеет lifecycle?")
        appendLine()
        appendLine("5. SECURITY:")
        appendLine("   Пишутся ли секреты в логи/файлы? Есть ли SQL/command injection? Валидируется ли ввод?")
        appendLine()
        appendLine("6. LOGIC BUGS:")
        appendLine("   Есть ли off-by-one? Правильны ли граничные условия? Не потеряна ли функциональность при рефакторинге?")
        appendLine()
        appendLine("7. DATA LOSS:")
        appendLine("   Берётся ли только первый элемент из коллекции где может быть несколько? Теряются ли результаты?")
        appendLine()
        appendLine("8. АРХИТЕКТУРНЫЕ РЕШЕНИЯ:")
        appendLine("   - Magic strings вместо констант/enum?")
        appendLine("   - Хрупкая логика (проверка через .contains() строки вместо явного флага)?")
        appendLine("   - Условия на комбинациях параметров вместо explicit enum/sealed class?")
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("⚠️ ПРАВИЛО: Если НЕ можешь продемонстрировать КАК проявится проблема — это false positive, НЕ пиши замечание.")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Выводи ТОЛЬКО найденные проблемы (или обоснованное ✅ OK). НЕ выводи сам чек-лист.")
        appendLine()

        // === FINDING FORMAT ===
        appendLine("Формат КАЖДОГО замечания:")
        appendLine("---")
        appendLine("**Файл:** `path/to/File.kt:42`")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** [конкретное описание — что не так и почему это проблема]")
        appendLine("```kotlin")
        appendLine("// Текущий код (строки 42-48):")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("    user.name = newName  // NPE если user == null")
        appendLine("    repository.save(user)")
        appendLine("}")
        appendLine("```")
        appendLine("**Рекомендация:**")
        appendLine("```kotlin")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("        ?: throw UserNotFoundException(id)")
        appendLine("    user.name = newName")
        appendLine("    repository.save(user)")
        appendLine("}")
        appendLine("```")
        appendLine("---")
        appendLine()

        // === GOOD OK EXAMPLE ===
        appendLine("Примеры ХОРОШИХ «✅ OK»:")
        appendLine()
        appendLine("```")
        appendLine("**AgentConfig.kt**")
        appendLine("✅ OK — Увеличены лимиты для review: MAX_CONTEXT_CHARS (16K→50K), MAX_REVIEW_DIFF_CHARS (12K→20K),")
        appendLine("MAX_REVIEW_FILE_CHARS (3K→6K), MAX_REVIEW_TOTAL_FILES_CHARS (20K→40K). Проверено:")
        appendLine("- Изменения согласованы между собой")
        appendLine("- Общий бюджет (diff 20K + files 40K = 60K) превышает MAX_CONTEXT_CHARS (50K), но это OK,")
        appendLine("  т.к. есть логика обрезки в buildPrefetchedDataSection()")
        appendLine("- Все значения остаются в разумных пределах для GigaChat API")
        appendLine("```")
        appendLine()
        appendLine("```")
        appendLine("**CommandHandler.kt**")
        appendLine("✅ OK — Улучшены инструкции для review: добавлены anti-patterns (строки 346-398), методология")
        appendLine("анализа с чек-листом (строки 431-465), требование severity levels. Переупорядочены секции:")
        appendLine("инструкции перед данными (buildPrefetchedReviewResult:472-475) для лучшего следования промпту.")
        appendLine("Проверено:")
        appendLine("- SECURITY: нет SQL/command injection (нет SQL запросов, используются только string literals)")
        appendLine("- LOGIC: prompt длина ~2500 строк — вписывается в MAX_CONTEXT_CHARS (50K)")
        appendLine("- NULL SAFETY: все buildString { ... } возвращают non-null String")
        appendLine("```")
        appendLine()
        appendLine("Обрати внимание:")
        appendLine("- Указано ЧТО изменилось (конкретные строки кода, значения)")
        appendLine("- Перечислены аспекты проверки (security, logic, null safety)")
        appendLine("- Даны ОБОСНОВАНИЯ почему проблем нет (есть логика обрезки, нет SQL запросов и т.д.)")
        appendLine()

        // === RESPONSE STRUCTURE ===
        appendLine("═══════════════════════════════════════")
        appendLine("СТРУКТУРА ОТВЕТА")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("0. **Изменённые файлы** — перечисли ВСЕ файлы из diff/git_status (рецензируй ТОЛЬКО их)")
        appendLine("1. **Резюме** — что делает PR/изменение (2-3 предложения)")
        appendLine("2. **Разбор по файлам** — для КАЖДОГО файла: замечания с severity и кодом ИЛИ детальный «✅ OK»")
        appendLine("3. **Сводная таблица** (обязательно):")
        appendLine("   | Файл | Severity | Тип проблемы | Краткое описание |")
        appendLine("   |------|----------|-------------|-----------------|")
        appendLine("   | File.kt | 🟠 High | Race condition | Shared mutable state без синхронизации |")
        appendLine("   | Other.py | ✅ OK | — | — |")
        appendLine("4. **Оценка:**")
        appendLine("   - ✅ Approve — нет 🔴 Critical и 🟠 High замечаний")
        appendLine("   - ⚠️ Request Changes — есть 🟠 High или множество 🟡 Medium")
        appendLine("   - ❌ Reject — есть 🔴 Critical (security, data loss, crash)")
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
