package ru.chtcholeg.agent.domain.service

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.chtcholeg.agent.config.AgentConfig
import ru.chtcholeg.agent.data.repository.McpRepository
import ru.chtcholeg.agent.domain.model.CommandResult
import ru.chtcholeg.agent.domain.service.SpecializedChecklists

/**
 * Handler for slash commands in the AI Agent.
 * Commands start with '/' and are processed before sending to AI.
 */
class CommandHandler(
    private val projectRootProvider: ProjectRootProvider,
    private val mcpRepository: McpRepository
) {

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

        /**
         * System prompt for User Support Assistant mode.
         * Activated with /support command.
         */
        private const val SUPPORT_ASSISTANT_PROMPT = """
Ты - ассистент технической поддержки для GigaChat Multiplatform Chat App.

Твоя цель - помогать пользователям решать проблемы с приложением, отвечать на вопросы и предоставлять качественную поддержку.

## Доступные инструменты

### RAG (Knowledge Base)
У тебя есть доступ к базе знаний через RAG:
- FAQ по проблемам авторизации
- Инструкции по установке и запуску
- Руководство по MCP серверам
- Описание функций и возможностей
- База решений распространенных ошибок

Используй RAG для поиска решений в документации.

### CRM Tools (через MCP)
У тебя есть доступ к CRM системе через MCP инструменты:
- get_user - информация о пользователе
- get_user_tickets - все тикеты пользователя
- get_ticket - детали конкретного тикета
- search_tickets - поиск тикетов по ключевому слову
- update_ticket_status - обновление статуса тикета

**ВАЖНО:** ВСЕГДА проверяй контекст пользователя через CRM перед ответом!

## Workflow

Когда пользователь задает вопрос:

1. **Идентификация пользователя**
   - Если известен userId, получи информацию через get_user_tickets
   - Проверь историю тикетов пользователя
   - Узнай тарифный план и статус

2. **Поиск решения**
   - Используй RAG для поиска в базе знаний
   - Найди похожие тикеты через search_tickets
   - Комбинируй информацию из FAQ и истории тикетов

3. **Персонализированный ответ**
   - Учитывай тарифный план пользователя
   - Ссылайся на предыдущие тикеты, если релевантно
   - Предлагай решения, специфичные для конфигурации пользователя

4. **Обновление тикета**
   - После решения проблемы обнови статус тикета
   - Добавь заметки о предоставленном решении
   - При необходимости создай follow-up

## Стиль общения

- **Дружелюбный и профессиональный** тон
- **Структурированные ответы** с четкими шагами
- **Эмпатия** к проблемам пользователя
- **Конкретные примеры** команд и кода
- **Ссылки на документацию** для дополнительной информации

## Шаблон ответа

Привет, [Имя пользователя]!

[Эмпатия к проблеме]

Я вижу в вашей истории тикетов, что [контекст из CRM].

Согласно нашей документации, [информация из RAG]:

**Решение:**
1. [Шаг 1]
2. [Шаг 2]
3. [Шаг 3]

**Пример:**
[Код или команда]

[Дополнительная информация или альтернативы]

Это должно решить проблему. Дай знать, если что-то не получится!

## Примеры

### Пример 1: Проблема авторизации

**Вопрос пользователя:** "Почему не работает авторизация?"

**Твои действия:**
1. Получи тикет: `get_ticket` (если известен ID)
2. Получи историю: `get_user_tickets` с userId
3. Найди решение: RAG поиск по "авторизация"
4. Проверь похожие тикеты: `search_tickets` с query "авторизация"

**Твой ответ:**
Привет, Иван!

Понимаю, как это может расстраивать 😔

Я вижу в вашем тикете #ticket_001, что вы получаете ошибку "Invalid credentials".
Также вижу, что у вас тарифный план Pro.

Согласно нашей FAQ, эта проблема обычно связана с креденшелами GigaChat API.

**Решение:**

1. Проверьте креденшелы в `local.properties`:
```properties
gigachat.clientId=YOUR_CLIENT_ID
gigachat.clientSecret=YOUR_CLIENT_SECRET
```

2. Пересоберите проект после изменения:
```bash
./gradlew clean build
```

3. Убедитесь, что токен не истек на сайте GigaChat API

Кстати, я вижу, что пользователь alex.kozlov@example.com (тикет #ticket_003)
недавно решил похожую проблему - у него помогла смена порта MCP сервера на 8081.

Это должно решить проблему. Дай знать, если что-то не получится!

P.S. Я обновил статус вашего тикета на "in_progress" и добавил эту информацию в заметки.

### Пример 2: Вопрос о функциях

**Вопрос:** "Как использовать RAG с моей документацией?"

**Твои действия:**
1. RAG поиск: "RAG документация индексирование"
2. Проверь тикеты: `search_tickets` с query "RAG"
3. Получи контекст пользователя

**Твой ответ:**
Привет, Елена!

Отличный вопрос! RAG - это мощная функция для работы с вашей документацией.

Вижу, что у вас тарифный план Pro, так что все функции доступны 👍

**Как настроить RAG:**

1. Проиндексируйте ваши документы:
```bash
./gradlew :shared:runIndexing --args="index ./docs ./index.json md txt pdf"
```

2. Запустите AI Agent:
```bash
./gradlew :ai-agent:run
```

3. В настройках включите RAG mode

4. Укажите путь к индексу: `./index.json`

5. Теперь можете задавать вопросы по вашей документации!

**Поддерживаемые форматы:**
- Markdown (.md)
- Text (.txt)
- PDF (.pdf)

Больше информации в документации: `docs/AI_AGENT.md` и `ai-agent/README.md`

Успешного использования! 🚀

## Escalation

Если проблему не удается решить:
1. Добавь заметки в тикет с описанием проделанной работы
2. Повысь приоритет тикета
3. Предложи пользователю альтернативное решение или workaround
4. Сообщи, что проблема будет передана в разработку

## Метрики качества

Отслеживай:
- Время первого ответа
- Количество решенных тикетов с первого раза
- Удовлетворенность пользователя
- Использование базы знаний (RAG hits)
"""
    }

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
            "support" -> handleSupportCommand(args)
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
                    "Также упомяни доступные команды: /help [тема] — справка по проекту, /review-pr [номер|ветка] — code review, /support [вопрос] — техническая поддержка. " +
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
     * Parse changed files from github_pr_files tool response.
     * Expected format: "  [status] path/to/file.kt (+X -Y)"
     */
    private fun parseChangedFilesFromPrFiles(filesOutput: String): List<String> {
        // Extract filenames from lines like "  [modified] path/to/file.kt (+9 -0)"
        return Regex("""^\s*\[(?:added|modified|removed|renamed)\]\s+(.+?)\s+\(""", RegexOption.MULTILINE)
            .findAll(filesOutput)
            .map { it.groupValues[1].trim() }
            .distinct()
            .toList()
    }

    /**
     * Build minimal diff summary when full diff is unavailable (too large).
     * Creates a summary showing file list only.
     */
    private fun buildMinimalDiffFromFiles(files: List<String>): String {
        return buildString {
            appendLine("# Large PR - Diff Summary")
            appendLine()
            appendLine("⚠️ The full diff is too large (>20000 lines) and cannot be retrieved from GitHub API.")
            appendLine("Full file contents will be read instead for review.")
            appendLine()
            appendLine("Changed files (${files.size}):")
            files.forEach { file ->
                appendLine("  - $file")
            }
            appendLine()
            appendLine("(Full contents of these files are provided in the 'File Contents' section below)")
        }
    }

    /**
     * Add line numbers to code content for easier reference in review.
     * Format: "  1 | code line"
     */
    private fun addLineNumbers(content: String): String {
        val lines = content.lines()
        val maxLineNumber = lines.size
        val width = maxLineNumber.toString().length.coerceAtLeast(3)

        return lines.mapIndexed { index, line ->
            val lineNum = (index + 1).toString().padStart(width)
            "$lineNum | $line"
        }.joinToString("\n")
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

            // Try to get full diff first
            val diffResult = callTool("github_pr_diff", mapOf("pr_number" to prNumber))

            if (diffResult != null) {
                // Success: got the full diff
                diff = diffResult
                changedFiles = parseChangedFilesFromDiff(diff)
            } else {
                // Failed (likely 406 - diff too large): fallback to github_pr_files
                val filesResult = callTool("github_pr_files", mapOf("pr_number" to prNumber))
                    ?: return null

                // Parse file list from github_pr_files response
                changedFiles = parseChangedFilesFromPrFiles(filesResult)

                // Build minimal diff summary (without actual content)
                diff = buildMinimalDiffFromFiles(changedFiles)
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
        var filesWithContent = 0

        for (filePath in changedFiles) {
            // Limit number of files to prevent context overflow
            if (filesWithContent >= AgentConfig.MAX_REVIEW_FILES_WITH_CONTENT) break
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

            // Add line numbers for easier reference in review
            val withLineNumbers = addLineNumbers(truncated)

            fileContents[filePath] = withLineNumbers
            totalChars += withLineNumbers.length
            filesWithContent++
        }

        return ReviewData(
            changedFiles = changedFiles,
            diff = diff,
            fileContents = fileContents
        )
    }

    /**
     * Build the context section containing pre-fetched review data.
     * All file contents include line numbers for precise code references.
     */
    private fun buildPrefetchedDataSection(data: ReviewData): String = buildString {
        appendLine("# Данные для Code Review (собраны автоматически)")
        appendLine()

        // === DETECT TECHNOLOGIES ===
        val fileToTechs = mutableMapOf<String, Set<String>>()
        val allTechs = mutableSetOf<String>()

        for ((filePath, content) in data.fileContents) {
            val techs = SpecializedChecklists.detectTechnologies(filePath, content)
            fileToTechs[filePath] = techs
            allTechs.addAll(techs)
        }

        // === TECHNOLOGY SUMMARY ===
        if (allTechs.isNotEmpty()) {
            appendLine("╔═══════════════════════════════════════════════════════════════════╗")
            appendLine("║ 📋 ОБНАРУЖЕННЫЕ ТЕХНОЛОГИИ                                        ║")
            appendLine("╚═══════════════════════════════════════════════════════════════════╝")
            appendLine()
            appendLine("⚡ В этом PR используются: ${allTechs.joinToString(", ")}")
            appendLine()
            appendLine("Применяй соответствующие специализированные проверки:")
            for ((file, techs) in fileToTechs) {
                if (techs.isNotEmpty()) {
                    val fileName = file.substringAfterLast('/')
                    appendLine("  • $fileName → ${techs.joinToString(", ")}")
                }
            }
            appendLine()
        }

        // Changed files list - MOST IMPORTANT SECTION
        appendLine("╔═══════════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 ИЗМЕНЁННЫЕ ФАЙЛЫ — ИСЧЕРПЫВАЮЩИЙ СПИСОК                       ║")
        appendLine("╚═══════════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ Рецензируй ТОЛЬКО эти файлы. Если файла нет в этом списке — НЕ пиши про него!")
        appendLine()
        if (data.changedFiles.isEmpty()) {
            appendLine("Изменений не найдено.")
        } else {
            for ((index, file) in data.changedFiles.withIndex()) {
                appendLine("${index + 1}. $file")
            }
        }
        appendLine()
        appendLine("📋 Всего файлов для review: ${data.changedFiles.size}")
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
            appendLine("(Все файлы содержат номера строк слева для удобства цитирования)")
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
    private fun buildSimplifiedReviewInstructions(detectedTechnologies: Set<String> = emptySet()): String = buildString {
        appendLine("# Code Review Instructions")
        appendLine()
        appendLine("Все данные для review предоставлены ниже. НЕ вызывай инструменты. Анализируй ТОЛЬКО предоставленные данные.")
        appendLine()

        // === CRITICAL RULE #0: FILE LIST ===
        appendLine("╔═══════════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКОЕ ПРАВИЛО #0: СПИСОК ФАЙЛОВ (САМОЕ ВАЖНОЕ!)        ║")
        appendLine("╚═══════════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ПЕРЕД началом review:")
        appendLine("1. Прочитай секцию \"## Изменённые файлы\" в предоставленных данных")
        appendLine("2. Это ИСЧЕРПЫВАЮЩИЙ список файлов для review")
        appendLine("3. Скопируй этот список в начало своего ответа (раздел \"Изменённые файлы\")")
        appendLine("4. Анализируй ТОЛЬКО файлы из этого списка")
        appendLine()
        appendLine("⛔ КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО:")
        appendLine("- Упоминать файлы, которых НЕТ в секции \"## Изменённые файлы\"")
        appendLine("- Анализировать файлы, которые НЕ изменялись в этом PR")
        appendLine("- Писать review для файлов из ПРИМЕРОВ ниже (processUser, UserRepository, etc.)")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНАЯ ПРОВЕРКА:")
        appendLine("   В конце review проверь: все ли файлы из твоего ответа присутствуют")
        appendLine("   в секции \"## Изменённые файлы\"? Если НЕТ — УДАЛИ упоминания этих файлов.")
        appendLine()
        appendLine("💡 Если файла нет в \"Изменённые файлы\" — он НЕ изменялся, даже если кажется, что должен был.")
        appendLine()

        // === CRITICAL BUGS (HIGHEST PRIORITY - CHECK FIRST!) ===
        appendLine(buildCriticalBugsInstructions())

        // === SPECIALIZED CHECKLISTS (filtered by detected technologies) ===
        appendLine(buildTechnologyBasedInstructions(detectedTechnologies))

        // === CRITICAL ANTI-HALLUCINATION RULE ===
        appendLine("╔═══════════════════════════════════════════════════════════════════╗")
        appendLine("║ 🚨 КРИТИЧЕСКОЕ ПРАВИЛО #1: ЗАПРЕТ НА ГАЛЛЮЦИНАЦИИ               ║")
        appendLine("╚═══════════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⛔ СТРОЖАЙШЕ ЗАПРЕЩЕНО:")
        appendLine("1. Использовать КОД ИЗ ПРИМЕРОВ в этих инструкциях как реальные проблемы")
        appendLine("2. Придумывать код, которого нет в предоставленных данных")
        appendLine("3. Цитировать строки, которых нет в секции \"Содержимое файлов\" или \"Diff\"")
        appendLine("4. Показывать код БЕЗ номеров строк — это признак выдумывания")
        appendLine()
        appendLine("✅ ВСЕ примеры кода в твоём review ДОЛЖНЫ быть взяты ТОЛЬКО из:")
        appendLine("   - Секции \"## Diff\" (строки с + и -)")
        appendLine("   - Секции \"## Содержимое файлов\" (полный код файлов С НОМЕРАМИ СТРОК)")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНО В КАЖДОМ ЗАМЕЧАНИИ:")
        appendLine("   - Указывать КОНКРЕТНЫЕ номера строк из секции \"Содержимое файлов\"")
        appendLine("   - Формат: \"Строки 28-40 из RagRepository.kt:\" или \"RagRepository.kt:28-40\"")
        appendLine("   - Копировать номера строк ТОЧНО как показано в секции \"Содержимое файлов\"")
        appendLine("   - НИКОГДА не придумывать номера строк — только из реальных данных")
        appendLine()
        appendLine("🔍 САМОПРОВЕРКА ПЕРЕД ОТПРАВКОЙ:")
        appendLine("   Для КАЖДОГО замечания задай себе вопрос:")
        appendLine("   «Этот код присутствует в предоставленных данных?»")
        appendLine("   Если ответ «НЕТ» или «Не уверен» — УДАЛИ это замечание.")
        appendLine()
        appendLine("   «Я указал номера строк для этого кода?»")
        appendLine("   Если ответ «НЕТ» — найди номера в секции \"Содержимое файлов\" или УДАЛИ замечание.")
        appendLine()
        appendLine("💡 Лучше пропустить реальную проблему, чем выдумать несуществующую (false positive).")
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
        appendLine("⚠️ ВАЖНО: Весь код ниже — это УЧЕБНЫЕ ПРИМЕРЫ для тебя!")
        appendLine("🚫 НЕ используй этот код (processUser, UserRepository, getData, findById, etc.) в своём review!")
        appendLine("🚫 Эти файлы НЕ изменялись в PR — они показывают КАКИЕ ОШИБКИ НЕЛЬЗЯ ДЕЛАТЬ!")
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
        appendLine("❌ ПЛОХО #4 — использован КОД ИЗ ПРИМЕРА как реальная проблема:")
        appendLine("```")
        appendLine("**Severity:** 🔴 Critical")
        appendLine("**Проблема:** NPE при обращении к user")
        appendLine("```kotlin")
        appendLine("fun processUser(id: String) {")
        appendLine("    val user = repository.findById(id)")
        appendLine("    user.name = newName  // NPE если user == null")
        appendLine("}")
        appendLine("```")
        appendLine("```")
        appendLine("Почему плохо:")
        appendLine("- Этот код НЕ СУЩЕСТВУЕТ в рецензируемых файлах")
        appendLine("- Модель скопировала пример из инструкций")
        appendLine("- Это КРИТИЧЕСКАЯ ошибка — галлюцинация несуществующих проблем")
        appendLine()
        appendLine("❌ ПЛОХО #5 — неправильный логический анализ:")
        appendLine("```")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** Ошибка в фильтрации, должно быть !in")
        appendLine("```kotlin")
        appendLine("includeTools != null -> {")
        appendLine("    val filtered = allTools.filter { it.name in includeTools }  // Неправильно!")
        appendLine("}")
        appendLine("```")
        appendLine("**Рекомендация:** Использовать `!in` вместо `in`")
        appendLine("```")
        appendLine("Почему плохо:")
        appendLine("- includeTools = WHITELIST (включить ТОЛЬКО эти инструменты)")
        appendLine("- `in includeTools` = ПРАВИЛЬНО (оставить только те, что в списке)")
        appendLine("- Рекомендация ИНВЕРТИРУЕТ логику — получим обратный эффект!")
        appendLine("- Нужно было ОБЪЯСНИТЬ логику: «includeTools — whitelist, значит `in` правильно»")
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
        appendLine("- **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО: Использовать код из примеров в этих инструкциях** — это учебный код, не реальный!")
        appendLine("- Рецензировать файлы, которых НЕТ в diff")
        appendLine("- Утверждать что-либо без цитирования реального кода из diff или содержимого файлов")
        appendLine("- Ставить ✅ OK без ДЕТАЛЬНОГО обоснования (минимум 2-3 предложения о том, ЧТО КОНКРЕТНО проверено)")
        appendLine("- **Путать локальные переменные с shared state** — `val local = ...` внутри функции НЕ требует синхронизации")
        appendLine("- **Давать рекомендации без понимания логики** — сначала объясни ПОЧЕМУ текущий код неправильный")
        appendLine()
        appendLine("✅ ОБЯЗАТЕЛЬНО:")
        appendLine("- СНАЧАЛА перечисли ВСЕ изменённые файлы — рецензируй ТОЛЬКО их")
        appendLine("- Проанализировать КАЖДЫЙ изменённый файл НЕЗАВИСИМО ОТ ЯЗЫКА (Kotlin, Python, TOML, SQL и т.д.)")
        appendLine("- Каждому замечанию присвоить severity: 🔴 Critical, 🟠 High, 🟡 Medium, 🔵 Low")
        appendLine("- **КРИТИЧЕСКИ ВАЖНО: ВСЕГДА указывать номера строк** в формате \"FileName.kt:28-40\" или \"Строки 28-40 из FileName.kt:\"")
        appendLine("- В каждом замечании показать 3-7 строк реального кода из файла С КОНТЕКСТОМ (соседние строки)")
        appendLine("- Каждое замечание ДОЛЖНО цитировать реальный код из секций \"Diff\" или \"Содержимое файлов\"")
        appendLine("- **Номера строк ТОЛЬКО из секции \"Содержимое файлов\"** — они показаны слева от кода (например: \"  28 | code...\")")
        appendLine("- **Для каждой проблемы ПРОДЕМОНСТРИРОВАТЬ как она проявится:** показать stack trace, race condition scenario, или конкретный вход, при котором случится ошибка")
        appendLine("- **Для логических ошибок ОБЪЯСНИТЬ логику:** что означает переменная, почему текущее условие неправильное, что получится при выполнении")
        appendLine("- Если рекомендация содержит исправление, оно ДОЛЖНО быть отличным от оригинала (не просто переименование)")
        appendLine("- Для файлов без замечаний: «✅ OK» + ДЕТАЛЬНОЕ обоснование (перечислить что проверено: thread safety, null safety, error handling, resource leaks, security — и почему проблем нет)")
        appendLine()
        appendLine("🔍 ОБЯЗАТЕЛЬНАЯ ВЕРИФИКАЦИЯ КАЖДОГО ЗАМЕЧАНИЯ:")
        appendLine("   1. Этот код присутствует в предоставленных данных? (НЕ в примерах инструкций!)")
        appendLine("   2. Я правильно понял логику кода? (прочитал ли соседние строки, переменные, контекст?)")
        appendLine("   3. Моя рекомендация УЛУЧШИТ код или СЛОМАЕТ его?")
        appendLine("   4. Могу ли я объяснить ПОЧЕМУ текущий код неправильный?")
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
        appendLine("3. LOGIC BUGS (КРИТИЧЕСКИ ВАЖНО — требует понимания контекста):")
        appendLine("   ")
        appendLine("   ╔════════════════════════════════════════════════════════════╗")
        appendLine("   ║ 3.1. KOTLIN: MISSING RETURN IN WHEN/IF BLOCKS             ║")
        appendLine("   ╚════════════════════════════════════════════════════════════╝")
        appendLine("   ")
        appendLine("   ⚠️ САМАЯ ЧАСТАЯ ОШИБКА В KOTLIN!")
        appendLine("   ")
        appendLine("   ЧТО ИСКАТЬ:")
        appendLine("   ```kotlin")
        appendLine("   val result = when {")
        appendLine("       condition -> {")
        appendLine("           val temp = compute()")
        appendLine("           if (temp.isValid()) {")
        appendLine("               println(\"valid\")")
        appendLine("           }")
        appendLine("           // ⚠️ ПОСЛЕДНЯЯ СТРОКА - if БЕЗ else → вернет Unit!")
        appendLine("       }")
        appendLine("   }")
        appendLine("   ```")
        appendLine("   ")
        appendLine("   КАК ПРОВЕРИТЬ:")
        appendLine("   Для КАЖДОГО `val x = when` или `val x = if`:")
        appendLine("   1. Найди многострочные блоки { ... }")
        appendLine("   2. Проверь ПОСЛЕДНЮЮ строку блока:")
        appendLine("      - ❌ if без else → вернет Unit")
        appendLine("      - ❌ println/logging → вернет Unit")
        appendLine("      - ❌ присваивание (=) → вернет Unit")
        appendLine("      - ✅ выражение (вызов функции, переменная) → вернет значение")
        appendLine("   3. Если последняя строка НЕ выражение → это баг!")
        appendLine("   ")
        appendLine("   ПРИМЕР ИЗ РЕАЛЬНОГО КОДА (AgentRepository.kt):")
        appendLine("   ```kotlin")
        appendLine("   val availableTools = when {")
        appendLine("       includeTools != null -> {")
        appendLine("           val filtered = allTools.filter { it.name in includeTools }")
        appendLine("           if (filtered.isEmpty()) {  // ⚠️ if БЕЗ else")
        appendLine("               println(\"WARNING\")")
        appendLine("           }")
        appendLine("           // ⚠️ НЕТ RETURN! availableTools = Unit")
        appendLine("       }")
        appendLine("       else -> allTools")
        appendLine("   }")
        appendLine("   ```")
        appendLine("   ")
        appendLine("   ПРАВИЛЬНО:")
        appendLine("   ```kotlin")
        appendLine("   val availableTools = when {")
        appendLine("       includeTools != null -> {")
        appendLine("           val filtered = allTools.filter { it.name in includeTools }")
        appendLine("           if (filtered.isEmpty()) {")
        appendLine("               println(\"WARNING\")")
        appendLine("           }")
        appendLine("           filtered  // ✅ явный return")
        appendLine("       }")
        appendLine("       else -> allTools")
        appendLine("   }")
        appendLine("   ```")
        appendLine("   ")
        appendLine("   SEVERITY: 🔴 Critical (compilation error или ClassCastException)")
        appendLine("   ")
        appendLine("   ╔════════════════════════════════════════════════════════════╗")
        appendLine("   ║ 3.2. FILTER/MAP LOGIC (WHITELIST/BLACKLIST)               ║")
        appendLine("   ╚════════════════════════════════════════════════════════════╝")
        appendLine("   ")
        appendLine("   ✅ ПРАВИЛЬНО — анализ с пониманием семантики:")
        appendLine("   «includeTools — это whitelist (включить ТОЛЬКО эти инструменты).")
        appendLine("    Условие `it.name in includeTools` оставляет только инструменты из списка — логика правильная.»")
        appendLine("   ")
        appendLine("   ✅ ПРАВИЛЬНО — анализ с проверкой обратного кейса:")
        appendLine("   «excludeTools — это blacklist (исключить эти инструменты).")
        appendLine("    Условие `it.name !in excludeTools` убирает инструменты из списка — логика правильная.»")
        appendLine("   ")
        appendLine("   ❌ НЕПРАВИЛЬНО — анализ без понимания:")
        appendLine("   «В строке используется `in` вместо `!in` — ошибка фильтрации.»")
        appendLine("   Почему плохо: не проанализировано, что означает переменная и какая логика нужна.")
        appendLine("   ")
        appendLine("   ЕСЛИ нашёл логическую ошибку:")
        appendLine("   - Объясни семантику переменных: что означает includeTools/excludeTools/filter и т.д.")
        appendLine("   - Покажи, что ДОЛЖНА делать логика (включить/исключить/преобразовать)")
        appendLine("   - Покажи, что ДЕЛАЕТ текущий код")
        appendLine("   - Продемонстрируй пример: «При includeTools=[A,B], allTools=[A,B,C,D] получим [A,B] вместо [C,D]»")
        appendLine()
        appendLine("4. ERROR HANDLING:")
        appendLine("   Ловятся ли исключения? Есть ли пустые catch-блоки? Есть ли try без finally для ресурсов?")
        appendLine()
        appendLine("5. RESOURCE LEAKS:")
        appendLine("   Есть ли CoroutineScope/HttpClient/Stream без close/cancel? Кто владеет lifecycle?")
        appendLine()
        appendLine("6. SECURITY:")
        appendLine("   Пишутся ли секреты в логи/файлы? Есть ли SQL/command injection? Валидируется ли ввод?")
        appendLine()
        appendLine("7. OFF-BY-ONE & BOUNDARY CONDITIONS:")
        appendLine("   Правильны ли индексы массивов? Не пропущены ли граничные значения?")
        appendLine("   Не потеряна ли функциональность при рефакторинге?")
        appendLine()
        appendLine("8. DATA LOSS:")
        appendLine("   Берётся ли только первый элемент из коллекции где может быть несколько?")
        appendLine("   Теряются ли результаты при преобразованиях?")
        appendLine()
        appendLine("9. АРХИТЕКТУРНЫЕ РЕШЕНИЯ:")
        appendLine("   - Magic strings вместо констант/enum?")
        appendLine("   - Хрупкая логика (проверка через .contains() строки вместо явного флага)?")
        appendLine("   - Условия на комбинациях параметров вместо explicit enum/sealed class?")
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("⚠️ ЗОЛОТЫЕ ПРАВИЛА:")
        appendLine("═══════════════════════════════════════")
        appendLine("1. Если НЕ можешь продемонстрировать КАК проявится проблема — это false positive, НЕ пиши замечание")
        appendLine("2. Если НЕ можешь объяснить ПОЧЕМУ текущий код неправильный — НЕ давай рекомендацию")
        appendLine("3. Если код из примеров в этих инструкциях — НЕ используй его как реальную проблему")
        appendLine("4. Сомневаешься в логике? Проверь контекст: соседние строки, названия переменных, комментарии")
        appendLine()
        appendLine("Выводи ТОЛЬКО найденные проблемы (или обоснованное ✅ OK). НЕ выводи сам чек-лист.")
        appendLine()

        // === FINDING FORMAT ===
        appendLine("Формат КАЖДОГО замечания:")
        appendLine("---")
        appendLine("**Файл:** `path/to/File.kt:40-48` (ОБЯЗАТЕЛЬНО указать номера строк!)")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** [конкретное описание — что не так и почему это проблема]")
        appendLine()
        appendLine("**Текущий код (ТОЛЬКО из секции \"Содержимое файлов\"):**")
        appendLine("```kotlin")
        appendLine("// File.kt:40-48 (номера строк взяты из левой колонки в \"Содержимое файлов\")")
        appendLine("(строки кода)")
        appendLine()
        appendLine("**Как проявится:**")
        appendLine("(описание того, как проявлется проблема)")
        appendLine()
        appendLine("**Рекомендация:**")
        appendLine("(рекомендации по правкам)")
        appendLine("---")
        appendLine()

        // === GOOD EXAMPLES ===
        appendLine("═══════════════════════════════════════")
        appendLine("ПРИМЕРЫ КАЧЕСТВЕННОГО REVIEW")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("✅ ПРИМЕР #1 — хороший анализ «OK» с детальным обоснованием:")
        appendLine("```")
        appendLine("**Файл:** `AgentRepository.kt:39-48`")
        appendLine("**Severity:** ✅ OK")
        appendLine()
        appendLine("**Что проверено:**")
        appendLine("- CONCURRENCY: Переменные `conversationHistory` (AgentRepository.kt:39), `gigaChatAccessToken` (:41), `gigaChatTokenExpiry` (:42)")
        appendLine("  защищены `historyMutex.withLock {}` (см. строки 295, 301, 411). Доступ синхронизирован — race conditions исключены.")
        appendLine("- NULL SAFETY: Используется `?.let`, `?: return`, elvis operator корректно.")
        appendLine("  Все nullable типы проверяются перед использованием (см. строки 465, 510).")
        appendLine("- LOGIC: Фильтрация в строках 312-319 `includeTools != null -> filter { it.name in includeTools }` корректна:")
        appendLine("  includeTools = whitelist, значит `in` правильно (оставить только указанные инструменты).")
        appendLine("- RESOURCE LEAKS: HttpClient передаётся через конструктор (:28) — lifecycle управляется снаружи.")
        appendLine("- ERROR HANDLING: Все API вызовы обёрнуты в RetryPolicy.withRetry {} со строки 381.")
        appendLine("```")
        appendLine()
        appendLine("✅ ПРИМЕР #2 — качественное замечание с демонстрацией проблемы:")
        appendLine("```")
        appendLine("**Файл:** `UserRepository.kt:42-48`")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** Возможен NPE при отсутствии пользователя")
        appendLine()
        appendLine("**Текущий код (из секции \"Содержимое файлов\"):**")
        appendLine("```kotlin")
        appendLine("// UserRepository.kt:42-48 (номера строк из левой колонки)")
        appendLine(" 42 | suspend fun updateUser(id: String, name: String) {")
        appendLine(" 43 |     val user = database.findUserById(id)  // возвращает User?")
        appendLine(" 44 |     user.name = name  // ⚠️ NPE если user == null")
        appendLine(" 45 |     database.save(user)")
        appendLine(" 46 | }")
        appendLine("```")
        appendLine()
        appendLine("**Как проявится:**")
        appendLine("```")
        appendLine("Если пользователь с id не найден, database.findUserById() вернёт null.")
        appendLine("При попытке user.name = name в строке 44 произойдёт:")
        appendLine("  kotlin.NullPointerException: user is null")
        appendLine("  at UserRepository.updateUser(UserRepository.kt:44)")
        appendLine("```")
        appendLine()
        appendLine("**Рекомендация:**")
        appendLine("```kotlin")
        appendLine("suspend fun updateUser(id: String, name: String) {")
        appendLine("    val user = database.findUserById(id)")
        appendLine("        ?: throw UserNotFoundException(\"User not found: \$id\")")
        appendLine("    user.name = name")
        appendLine("    database.save(user)")
        appendLine("}")
        appendLine("```")
        appendLine("```")
        appendLine()
        appendLine("Обрати внимание в этих примерах:")
        appendLine("- **ВСЕГДА указаны номера строк** в заголовке файла (File.kt:42-48) и в комментариях к коду")
        appendLine("- Указано ЧТО конкретно проверено (с цитатами кода и номерами строк)")
        appendLine("- Перечислены аспекты проверки (concurrency, null safety, logic, resources, errors)")
        appendLine("- Даны ОБОСНОВАНИЯ почему проблем нет или почему есть")
        appendLine("- Для проблем показан КОНКРЕТНЫЙ сценарий возникновения со ссылками на строки")
        appendLine("- Код взят ТОЧНО из секции \"Содержимое файлов\" — с сохранением номеров строк слева")
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("ФИНАЛЬНАЯ САМОПРОВЕРКА ПЕРЕД ОТПРАВКОЙ")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("ПЕРЕД отправкой review ОБЯЗАТЕЛЬНО проверь:")
        appendLine()
        appendLine("0. ❓ 🔴 КРИТИЧЕСКИ ВАЖНО: Все файлы из моего review есть в секции \"## Изменённые файлы\"?")
        appendLine("   ❌ ЕСЛИ НЕТ → это ГАЛЛЮЦИНАЦИЯ ФАЙЛА, УДАЛИ весь раздел об этом файле")
        appendLine("   📋 Сверь КАЖДЫЙ файл из твоего ответа со списком \"## Изменённые файлы\"")
        appendLine("   ⚠️ Примеры: AgentRepository.kt, UserRepository.kt, processUser — это УЧЕБНЫЙ КОД из примеров!")
        appendLine()
        appendLine("1. ❓ Все цитаты кода взяты из секций \"Diff\" или \"Содержимое файлов\"?")
        appendLine("   ❌ ЕСЛИ НЕТ → это галлюцинация, УДАЛИ замечание")
        appendLine()
        appendLine("2. ❓ Каждое замечание ссылается на РЕАЛЬНЫЙ код из предоставленных данных?")
        appendLine("   ❌ ЕСЛИ НЕТ → это выдуманная проблема, УДАЛИ замечание")
        appendLine()
        appendLine("3. ❓ Я УКАЗАЛ НОМЕРА СТРОК для каждого замечания в формате \"File.kt:28-40\"?")
        appendLine("   ❌ ЕСЛИ НЕТ → найди строки в секции \"Содержимое файлов\" (слева от кода) или УДАЛИ замечание")
        appendLine("   📌 Номера строк показаны в формате \"  28 | код...\" — используй эти числа")
        appendLine()
        appendLine("4. ❓ Для логических ошибок: я объяснил ПОЧЕМУ текущий код неправильный?")
        appendLine("   ❌ ЕСЛИ НЕТ → возможно, я неправильно понял логику, ПЕРЕПРОВЕРЬ")
        appendLine()
        appendLine("5. ❓ Моя рекомендация УЛУЧШИТ код или СЛОМАЕТ его?")
        appendLine("   ❌ ЕСЛИ СЛОМАЕТ → УДАЛИ рекомендацию")
        appendLine()
        appendLine("6. ❓ Я использовал код из ПРИМЕРОВ в этих инструкциях?")
        appendLine("   ❌ ЕСЛИ ДА → это КРИТИЧЕСКАЯ ошибка, УДАЛИ всё замечание")
        appendLine()
        appendLine("7. ❓ Для каждого файла: я дал детальное обоснование ✅ OK или нашёл проблемы?")
        appendLine("   ❌ ЕСЛИ НЕТ → дополни обоснование")
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
        // Detect technologies from file contents for targeted checklists
        val detectedTechs = mutableSetOf<String>()
        for ((filePath, content) in data.fileContents) {
            detectedTechs.addAll(SpecializedChecklists.detectTechnologies(filePath, content))
        }

        val instructions = buildSimplifiedReviewInstructions(detectedTechs)
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
            enableTools = false,
            requiresDocValidation = true
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
            includeTools = REVIEW_PR_ALLOWED_TOOLS,
            requiresDocValidation = true
        )
    }

    /**
     * Generates critical bug detection instructions (HIGHEST PRIORITY).
     * These checks apply to ALL files regardless of language or technology.
     */
    private fun buildCriticalBugsInstructions(): String = buildString {
        appendLine()
        appendLine("╔══════════════════════════════════════════════════════════════════════╗")
        appendLine("║ 🚨 КРИТИЧЕСКИЕ БАГИ - ПРОВЕРЯЙ В ПЕРВУЮ ОЧЕРЕДЬ! (TOP PRIORITY)     ║")
        appendLine("╚══════════════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ОБЯЗАТЕЛЬНО проверь ВСЕ файлы на эти критические паттерны ПЕРЕД остальными проверками:")
        appendLine()

        // === BUG #1: DIVISION BY ZERO ===
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКИЙ БАГ #1: DIVISION BY ZERO                      ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ЧТО ИСКАТЬ: Любое деление `/` или остаток `%` без проверки делителя на 0")
        appendLine()
        appendLine("ОПАСНЫЕ ПАТТЕРНЫ:")
        appendLine("```kotlin")
        appendLine("val ratio = field.length / content.length  // ⚠️ content.length может быть 0!")
        appendLine("val percent = count / total * 100          // ⚠️ total может быть 0!")
        appendLine("val avg = sum / list.size                  // ⚠️ list может быть пустым!")
        appendLine("```")
        appendLine()
        appendLine("КАК ПРОЯВИТСЯ:")
        appendLine("```")
        appendLine("java.lang.ArithmeticException: / by zero")
        appendLine("    at YourClass.method(YourClass.kt:42)")
        appendLine("```")
        appendLine()
        appendLine("ПРАВИЛЬНО:")
        appendLine("```kotlin")
        appendLine("val ratio = if (content.isNotEmpty()) {")
        appendLine("    field.length.toDouble() / content.length")
        appendLine("} else {")
        appendLine("    0.0")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("SEVERITY: 🔴 Critical (runtime crash)")
        appendLine()

        // === BUG #2: GLOBALSCOPE LEAK ===
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКИЙ БАГ #2: GLOBALSCOPE MEMORY LEAK               ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ЧТО ИСКАТЬ: Использование `GlobalScope.launch` или `GlobalScope.async`")
        appendLine()
        appendLine("ОПАСНЫЕ ПАТТЕРНЫ:")
        appendLine("```kotlin")
        appendLine("class Store {")
        appendLine("    fun loadData() {")
        appendLine("        GlobalScope.launch {  // ⚠️ NOT TIED TO LIFECYCLE!")
        appendLine("            loadDataInternal()")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("КАК ПРОЯВИТСЯ:")
        appendLine("- Корутина продолжает работать после закрытия экрана/компонента")
        appendLine("- Memory leak: объект не может быть собран GC")
        appendLine("- Обновления приходят в уничтоженный UI → crash")
        appendLine()
        appendLine("ПРАВИЛЬНО:")
        appendLine("```kotlin")
        appendLine("class Store(private val scope: CoroutineScope) {")
        appendLine("    fun loadData() {")
        appendLine("        scope.launch {  // ✅ Tied to lifecycle")
        appendLine("            loadDataInternal()")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("SEVERITY: 🔴 Critical (memory leak)")
        appendLine()

        // === BUG #3: REMOVED SAFETY CHECKS (REGRESSION) ===
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКИЙ БАГ #3: УДАЛЕНЫ SAFETY CHECKS (REGRESSION)    ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ЧТО ИСКАТЬ В DIFF: Удаленные строки с проверками (префикс `-`)")
        appendLine()
        appendLine("ОПАСНЫЕ ПАТТЕРНЫ В DIFF:")
        appendLine("```diff")
        appendLine(" when (permission) {")
        appendLine("-    is PermissionResult.Denied -> return error(\"Permission denied\")")
        appendLine("     is PermissionResult.Allowed -> executeTool()")
        appendLine(" }")
        appendLine("```")
        appendLine()
        appendLine("```diff")
        appendLine(" suspend fun getData(id: String): Data {")
        appendLine("-    if (!initialized) throw IllegalStateException(\"Not initialized\")")
        appendLine("     return fetchData(id)")
        appendLine(" }")
        appendLine("```")
        appendLine()
        appendLine("```diff")
        appendLine("     } catch (e: Exception) {")
        appendLine("         if (attempt < maxRetries) {")
        appendLine("-            delay(delayMs)  // ⚠️ Удалена задержка между retry!")
        appendLine("         }")
        appendLine("     }")
        appendLine("```")
        appendLine()
        appendLine("КАК ПРОЯВИТСЯ:")
        appendLine("- Код выполнится без проверки разрешений → security breach")
        appendLine("- NPE или IllegalStateException в runtime")
        appendLine("- Rate limit exhaustion без delay между retry")
        appendLine()
        appendLine("SEVERITY: 🔴 Critical (security/stability regression)")
        appendLine()

        // === BUG #4: MISSING RETURN IN WHEN/IF ===
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКИЙ БАГ #4: MISSING RETURN IN WHEN/IF BLOCK       ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ЧТО ИСКАТЬ: `val x = when { ... }` где многострочный блок НЕ возвращает значение")
        appendLine()
        appendLine("ОПАСНЫЕ ПАТТЕРНЫ:")
        appendLine("```kotlin")
        appendLine("val result = when {")
        appendLine("    condition -> {")
        appendLine("        val temp = compute()")
        appendLine("        if (temp > 0) {")
        appendLine("            println(\"positive\")  // ⚠️ ПОСЛЕДНЯЯ СТРОКА - if БЕЗ else")
        appendLine("        }")
        appendLine("        // ⚠️ НЕТ RETURN! result будет Unit!")
        appendLine("    }")
        appendLine("    else -> defaultValue")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("КАК ПРОЯВИТСЯ:")
        appendLine("```")
        appendLine("Type mismatch: inferred type is Unit but List<Tool> was expected")
        appendLine("```")
        appendLine("Компиляция упадет с ошибкой типов, или ClassCastException в runtime.")
        appendLine()
        appendLine("ПРАВИЛЬНО:")
        appendLine("```kotlin")
        appendLine("val result = when {")
        appendLine("    condition -> {")
        appendLine("        val temp = compute()")
        appendLine("        if (temp > 0) {")
        appendLine("            println(\"positive\")")
        appendLine("        }")
        appendLine("        temp  // ✅ Явный return")
        appendLine("    }")
        appendLine("    else -> defaultValue")
        appendLine("}")
        appendLine("```")
        appendLine()
        appendLine("SEVERITY: 🔴 Critical (compilation error или ClassCastException)")
        appendLine()

        // === BUG #5: MISSING NULL CHECK ===
        appendLine("╔═══════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКИЙ БАГ #5: MISSING NULL CHECK                    ║")
        appendLine("╚═══════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("⚠️ ЧТО ИСКАТЬ: Вызов методов/свойств на nullable без проверки")
        appendLine()
        appendLine("ОПАСНЫЕ ПАТТЕРНЫ:")
        appendLine("```kotlin")
        appendLine("val user = findUser(id)  // returns User?")
        appendLine("return user.name         // ⚠️ NPE если user == null!")
        appendLine("```")
        appendLine()
        appendLine("```kotlin")
        appendLine("val name = user?.profile?.name  // nullable")
        appendLine("name.length                     // ⚠️ NPE если name == null!")
        appendLine("```")
        appendLine()
        appendLine("ПРАВИЛЬНО:")
        appendLine("```kotlin")
        appendLine("val user = findUser(id) ?: throw UserNotFoundException()")
        appendLine("return user.name")
        appendLine("```")
        appendLine()
        appendLine("SEVERITY: 🔴 Critical (NPE crash)")
        appendLine()

        appendLine("═══════════════════════════════════════")
        appendLine("🔍 ПРОВЕРКА КРИТИЧЕСКИХ БАГОВ - ОБЯЗАТЕЛЬНА ДЛЯ ВСЕХ ФАЙЛОВ")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Для КАЖДОГО файла в PR:")
        appendLine("1. Найди все операции деления (/) и остатка (%) → проверь защиту от 0")
        appendLine("2. Найди все `GlobalScope.launch` и `GlobalScope.async` → предложи использовать scope")
        appendLine("3. Если есть diff: найди удаленные `-` строки с проверками → это регрессия")
        appendLine("4. Найди все `val x = when {` → проверь что каждый блок явно возвращает значение")
        appendLine("5. Найди все nullable операции (?, !!, as?) → проверь null safety")
        appendLine()
        appendLine("⚠️ Если нашел критический баг:")
        appendLine("- ОБЯЗАТЕЛЬНО укажи номер строки из секции \"Содержимое файлов\"")
        appendLine("- Покажи код С КОНТЕКСТОМ (3-7 строк)")
        appendLine("- Опиши КАК ПРОЯВИТСЯ баг (stack trace, сценарий)")
        appendLine("- Предложи ПРАВИЛЬНОЕ исправление")
        appendLine()
    }

    /**
     * Генерирует специализированные чек-листы на основе обнаруженных технологий.
     * Технологии определяются автоматически в buildPrefetchedDataSection() и передаются через контекст.
     */
    private fun buildTechnologyBasedInstructions(technologies: Set<String> = emptySet()): String = buildString {
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("СПЕЦИАЛИЗИРОВАННЫЕ ПРОВЕРКИ")
        appendLine("═══════════════════════════════════════")
        appendLine()

        // Include all checklists when technologies unknown (fallback mode),
        // otherwise include only relevant ones to reduce prompt size.
        val includeAll = technologies.isEmpty()

        if (includeAll || "kotlin" in technologies) {
            appendLine(SpecializedChecklists.kotlinGeneralChecklist())
            appendLine()
        }

        if (includeAll || "kotlin-coroutines" in technologies) {
            appendLine(SpecializedChecklists.kotlinCoroutinesChecklist())
            appendLine()
        }

        if (includeAll || "kotlin-flow" in technologies) {
            appendLine(SpecializedChecklists.kotlinFlowChecklist())
            appendLine()
        }

        if (includeAll || "mvi" in technologies) {
            appendLine(SpecializedChecklists.mviChecklist())
            appendLine()
        }

        if (includeAll || "repository-pattern" in technologies) {
            appendLine(SpecializedChecklists.repositoryPatternChecklist())
            appendLine()
        }

        if (includeAll || "python-async" in technologies) {
            appendLine(SpecializedChecklists.pythonAsyncChecklist())
            appendLine()
        }

        if (includeAll || "sql" in technologies) {
            appendLine(SpecializedChecklists.sqlChecklist())
            appendLine()
        }

        if (includeAll || "config" in technologies) {
            appendLine(SpecializedChecklists.configSecurityChecklist())
            appendLine()
        }

        // Note: Critical bug checks (division by zero, GlobalScope leaks, regression detection)
        // are already covered by buildCriticalBugsInstructions() — NOT duplicated here.
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

        // === CRITICAL RULE #0: FILE LIST (fallback mode) ===
        appendLine("╔═══════════════════════════════════════════════════════════════════╗")
        appendLine("║ 🔴 КРИТИЧЕСКОЕ ПРАВИЛО #0: СПИСОК ФАЙЛОВ                         ║")
        appendLine("╚═══════════════════════════════════════════════════════════════════╝")
        appendLine()
        appendLine("После получения списка изменённых файлов (из git_status/git_diff/github_pr_files):")
        appendLine("1. Сохрани этот список — это ИСЧЕРПЫВАЮЩИЙ перечень для review")
        appendLine("2. Выведи его в начале review (раздел \"Изменённые файлы\")")
        appendLine("3. Анализируй ТОЛЬКО эти файлы")
        appendLine()
        appendLine("⛔ НЕ упоминай файлы, которых НЕТ в полученном списке!")
        appendLine("⛔ НЕ используй файлы из ПРИМЕРОВ ниже (UserRepository, processUser, etc.)!")
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

        // === CRITICAL BUGS (HIGHEST PRIORITY) ===
        appendLine(buildCriticalBugsInstructions())

        // === SPECIALIZED CHECKLISTS ===
        appendLine(buildTechnologyBasedInstructions())

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
        appendLine("3. LOGIC BUGS (КРИТИЧЕСКИ ВАЖНО — требует понимания контекста):")
        appendLine("   ")
        appendLine("   ╔════════════════════════════════════════════════════════════╗")
        appendLine("   ║ 3.1. KOTLIN: MISSING RETURN IN WHEN/IF BLOCKS             ║")
        appendLine("   ╚════════════════════════════════════════════════════════════╝")
        appendLine("   ")
        appendLine("   ⚠️ САМАЯ ЧАСТАЯ ОШИБКА В KOTLIN!")
        appendLine("   ")
        appendLine("   ЧТО ИСКАТЬ:")
        appendLine("   ```kotlin")
        appendLine("   val result = when {")
        appendLine("       condition -> {")
        appendLine("           val temp = compute()")
        appendLine("           if (temp.isValid()) {")
        appendLine("               println(\"valid\")")
        appendLine("           }")
        appendLine("           // ⚠️ ПОСЛЕДНЯЯ СТРОКА - if БЕЗ else → вернет Unit!")
        appendLine("       }")
        appendLine("   }")
        appendLine("   ```")
        appendLine("   ")
        appendLine("   КАК ПРОВЕРИТЬ:")
        appendLine("   Для КАЖДОГО `val x = when` или `val x = if`:")
        appendLine("   1. Найди многострочные блоки { ... }")
        appendLine("   2. Проверь ПОСЛЕДНЮЮ строку блока:")
        appendLine("      - ❌ if без else → вернет Unit")
        appendLine("      - ❌ println/logging → вернет Unit")
        appendLine("      - ❌ присваивание (=) → вернет Unit")
        appendLine("      - ✅ выражение (вызов функции, переменная) → вернет значение")
        appendLine("   3. Если последняя строка НЕ выражение → это баг!")
        appendLine("   ")
        appendLine("   ПРИМЕР ИЗ РЕАЛЬНОГО КОДА (AgentRepository.kt):")
        appendLine("   ```kotlin")
        appendLine("   val availableTools = when {")
        appendLine("       includeTools != null -> {")
        appendLine("           val filtered = allTools.filter { it.name in includeTools }")
        appendLine("           if (filtered.isEmpty()) {  // ⚠️ if БЕЗ else")
        appendLine("               println(\"WARNING\")")
        appendLine("           }")
        appendLine("           // ⚠️ НЕТ RETURN! availableTools = Unit")
        appendLine("       }")
        appendLine("       else -> allTools")
        appendLine("   }")
        appendLine("   ```")
        appendLine("   ")
        appendLine("   ПРАВИЛЬНО:")
        appendLine("   ```kotlin")
        appendLine("   val availableTools = when {")
        appendLine("       includeTools != null -> {")
        appendLine("           val filtered = allTools.filter { it.name in includeTools }")
        appendLine("           if (filtered.isEmpty()) {")
        appendLine("               println(\"WARNING\")")
        appendLine("           }")
        appendLine("           filtered  // ✅ явный return")
        appendLine("       }")
        appendLine("       else -> allTools")
        appendLine("   }")
        appendLine("   ```")
        appendLine("   ")
        appendLine("   SEVERITY: 🔴 Critical (compilation error или ClassCastException)")
        appendLine("   ")
        appendLine("   ╔════════════════════════════════════════════════════════════╗")
        appendLine("   ║ 3.2. FILTER/MAP LOGIC (WHITELIST/BLACKLIST)               ║")
        appendLine("   ╚════════════════════════════════════════════════════════════╝")
        appendLine("   ")
        appendLine("   ✅ ПРАВИЛЬНО — анализ с пониманием семантики:")
        appendLine("   «includeTools — это whitelist (включить ТОЛЬКО эти инструменты).")
        appendLine("    Условие `it.name in includeTools` оставляет только инструменты из списка — логика правильная.»")
        appendLine("   ")
        appendLine("   ✅ ПРАВИЛЬНО — анализ с проверкой обратного кейса:")
        appendLine("   «excludeTools — это blacklist (исключить эти инструменты).")
        appendLine("    Условие `it.name !in excludeTools` убирает инструменты из списка — логика правильная.»")
        appendLine("   ")
        appendLine("   ❌ НЕПРАВИЛЬНО — анализ без понимания:")
        appendLine("   «В строке используется `in` вместо `!in` — ошибка фильтрации.»")
        appendLine("   Почему плохо: не проанализировано, что означает переменная и какая логика нужна.")
        appendLine("   ")
        appendLine("   ЕСЛИ нашёл логическую ошибку:")
        appendLine("   - Объясни семантику переменных: что означает includeTools/excludeTools/filter и т.д.")
        appendLine("   - Покажи, что ДОЛЖНА делать логика (включить/исключить/преобразовать)")
        appendLine("   - Покажи, что ДЕЛАЕТ текущий код")
        appendLine("   - Продемонстрируй пример: «При includeTools=[A,B], allTools=[A,B,C,D] получим [A,B] вместо [C,D]»")
        appendLine()
        appendLine("4. ERROR HANDLING:")
        appendLine("   Ловятся ли исключения? Есть ли пустые catch-блоки? Есть ли try без finally для ресурсов?")
        appendLine()
        appendLine("5. RESOURCE LEAKS:")
        appendLine("   Есть ли CoroutineScope/HttpClient/Stream без close/cancel? Кто владеет lifecycle?")
        appendLine()
        appendLine("6. SECURITY:")
        appendLine("   Пишутся ли секреты в логи/файлы? Есть ли SQL/command injection? Валидируется ли ввод?")
        appendLine()
        appendLine("7. OFF-BY-ONE & BOUNDARY CONDITIONS:")
        appendLine("   Правильны ли индексы массивов? Не пропущены ли граничные значения?")
        appendLine("   Не потеряна ли функциональность при рефакторинге?")
        appendLine()
        appendLine("8. DATA LOSS:")
        appendLine("   Берётся ли только первый элемент из коллекции где может быть несколько?")
        appendLine("   Теряются ли результаты при преобразованиях?")
        appendLine()
        appendLine("9. АРХИТЕКТУРНЫЕ РЕШЕНИЯ:")
        appendLine("   - Magic strings вместо констант/enum?")
        appendLine("   - Хрупкая логика (проверка через .contains() строки вместо явного флага)?")
        appendLine("   - Условия на комбинациях параметров вместо explicit enum/sealed class?")
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("⚠️ ЗОЛОТЫЕ ПРАВИЛА:")
        appendLine("═══════════════════════════════════════")
        appendLine("1. Если НЕ можешь продемонстрировать КАК проявится проблема — это false positive, НЕ пиши замечание")
        appendLine("2. Если НЕ можешь объяснить ПОЧЕМУ текущий код неправильный — НЕ давай рекомендацию")
        appendLine("3. Если код из примеров в этих инструкциях — НЕ используй его как реальную проблему")
        appendLine("4. Сомневаешься в логике? Проверь контекст: соседние строки, названия переменных, комментарии")
        appendLine()
        appendLine("Выводи ТОЛЬКО найденные проблемы (или обоснованное ✅ OK). НЕ выводи сам чек-лист.")
        appendLine()

        // === FINDING FORMAT ===
        appendLine("Формат КАЖДОГО замечания:")
        appendLine("---")
        appendLine("**Файл:** `path/to/File.kt:40-48` (ОБЯЗАТЕЛЬНО указать номера строк!)")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** [конкретное описание — что не так и почему это проблема]")
        appendLine()
        appendLine("**Текущий код (ТОЛЬКО из секции \"Содержимое файлов\"):**")
        appendLine("```kotlin")
        appendLine("// File.kt:40-48 (номера строк взяты из левой колонки в \"Содержимое файлов\")")
        appendLine("(строки кода)")
        appendLine()
        appendLine("**Как проявится:**")
        appendLine("(описание того, как проявлется проблема)")
        appendLine()
        appendLine("**Рекомендация:**")
        appendLine("(рекомендации по правкам)")
        appendLine("---")
        appendLine()

        // === GOOD EXAMPLES ===
        appendLine("═══════════════════════════════════════")
        appendLine("ПРИМЕРЫ КАЧЕСТВЕННОГО REVIEW")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("✅ ПРИМЕР #1 — хороший анализ «OK» с детальным обоснованием:")
        appendLine("```")
        appendLine("**Файл:** `AgentRepository.kt:39-48`")
        appendLine("**Severity:** ✅ OK")
        appendLine()
        appendLine("**Что проверено:**")
        appendLine("- CONCURRENCY: Переменные `conversationHistory` (AgentRepository.kt:39), `gigaChatAccessToken` (:41), `gigaChatTokenExpiry` (:42)")
        appendLine("  защищены `historyMutex.withLock {}` (см. строки 295, 301, 411). Доступ синхронизирован — race conditions исключены.")
        appendLine("- NULL SAFETY: Используется `?.let`, `?: return`, elvis operator корректно.")
        appendLine("  Все nullable типы проверяются перед использованием (см. строки 465, 510).")
        appendLine("- LOGIC: Фильтрация в строках 312-319 `includeTools != null -> filter { it.name in includeTools }` корректна:")
        appendLine("  includeTools = whitelist, значит `in` правильно (оставить только указанные инструменты).")
        appendLine("- RESOURCE LEAKS: HttpClient передаётся через конструктор (:28) — lifecycle управляется снаружи.")
        appendLine("- ERROR HANDLING: Все API вызовы обёрнуты в RetryPolicy.withRetry {} со строки 381.")
        appendLine("```")
        appendLine()
        appendLine("✅ ПРИМЕР #2 — качественное замечание с демонстрацией проблемы:")
        appendLine("```")
        appendLine("**Файл:** `UserRepository.kt:42-48`")
        appendLine("**Severity:** 🟠 High")
        appendLine("**Проблема:** Возможен NPE при отсутствии пользователя")
        appendLine()
        appendLine("**Текущий код (из секции \"Содержимое файлов\"):**")
        appendLine("```kotlin")
        appendLine("// UserRepository.kt:42-48 (номера строк из левой колонки)")
        appendLine(" 42 | suspend fun updateUser(id: String, name: String) {")
        appendLine(" 43 |     val user = database.findUserById(id)  // возвращает User?")
        appendLine(" 44 |     user.name = name  // ⚠️ NPE если user == null")
        appendLine(" 45 |     database.save(user)")
        appendLine(" 46 | }")
        appendLine("```")
        appendLine()
        appendLine("**Как проявится:**")
        appendLine("```")
        appendLine("Если пользователь с id не найден, database.findUserById() вернёт null.")
        appendLine("При попытке user.name = name в строке 44 произойдёт:")
        appendLine("  kotlin.NullPointerException: user is null")
        appendLine("  at UserRepository.updateUser(UserRepository.kt:44)")
        appendLine("```")
        appendLine()
        appendLine("**Рекомендация:**")
        appendLine("```kotlin")
        appendLine("suspend fun updateUser(id: String, name: String) {")
        appendLine("    val user = database.findUserById(id)")
        appendLine("        ?: throw UserNotFoundException(\"User not found: \$id\")")
        appendLine("    user.name = name")
        appendLine("    database.save(user)")
        appendLine("}")
        appendLine("```")
        appendLine("```")
        appendLine()
        appendLine("Обрати внимание в этих примерах:")
        appendLine("- **ВСЕГДА указаны номера строк** в заголовке файла (File.kt:42-48) и в комментариях к коду")
        appendLine("- Указано ЧТО конкретно проверено (с цитатами кода и номерами строк)")
        appendLine("- Перечислены аспекты проверки (concurrency, null safety, logic, resources, errors)")
        appendLine("- Даны ОБОСНОВАНИЯ почему проблем нет или почему есть")
        appendLine("- Для проблем показан КОНКРЕТНЫЙ сценарий возникновения со ссылками на строки")
        appendLine("- Код взят ТОЧНО из секции \"Содержимое файлов\" — с сохранением номеров строк слева")
        appendLine()
        appendLine("═══════════════════════════════════════")
        appendLine("ФИНАЛЬНАЯ САМОПРОВЕРКА ПЕРЕД ОТПРАВКОЙ")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("ПЕРЕД отправкой review ОБЯЗАТЕЛЬНО проверь:")
        appendLine()
        appendLine("0. ❓ 🔴 КРИТИЧЕСКИ ВАЖНО: Все файлы из моего review есть в секции \"## Изменённые файлы\"?")
        appendLine("   ❌ ЕСЛИ НЕТ → это ГАЛЛЮЦИНАЦИЯ ФАЙЛА, УДАЛИ весь раздел об этом файле")
        appendLine("   📋 Сверь КАЖДЫЙ файл из твоего ответа со списком \"## Изменённые файлы\"")
        appendLine("   ⚠️ Примеры: AgentRepository.kt, UserRepository.kt, processUser — это УЧЕБНЫЙ КОД из примеров!")
        appendLine()
        appendLine("1. ❓ Все цитаты кода взяты из секций \"Diff\" или \"Содержимое файлов\"?")
        appendLine("   ❌ ЕСЛИ НЕТ → это галлюцинация, УДАЛИ замечание")
        appendLine()
        appendLine("2. ❓ Каждое замечание ссылается на РЕАЛЬНЫЙ код из предоставленных данных?")
        appendLine("   ❌ ЕСЛИ НЕТ → это выдуманная проблема, УДАЛИ замечание")
        appendLine()
        appendLine("3. ❓ Я УКАЗАЛ НОМЕРА СТРОК для каждого замечания в формате \"File.kt:28-40\"?")
        appendLine("   ❌ ЕСЛИ НЕТ → найди строки в секции \"Содержимое файлов\" (слева от кода) или УДАЛИ замечание")
        appendLine("   📌 Номера строк показаны в формате \"  28 | код...\" — используй эти числа")
        appendLine()
        appendLine("4. ❓ Для логических ошибок: я объяснил ПОЧЕМУ текущий код неправильный?")
        appendLine("   ❌ ЕСЛИ НЕТ → возможно, я неправильно понял логику, ПЕРЕПРОВЕРЬ")
        appendLine()
        appendLine("5. ❓ Моя рекомендация УЛУЧШИТ код или СЛОМАЕТ его?")
        appendLine("   ❌ ЕСЛИ СЛОМАЕТ → УДАЛИ рекомендацию")
        appendLine()
        appendLine("6. ❓ Я использовал код из ПРИМЕРОВ в этих инструкциях?")
        appendLine("   ❌ ЕСЛИ ДА → это КРИТИЧЕСКАЯ ошибка, УДАЛИ всё замечание")
        appendLine()
        appendLine("7. ❓ Для каждого файла: я дал детальное обоснование ✅ OK или нашёл проблемы?")
        appendLine("   ❌ ЕСЛИ НЕТ → дополни обоснование")
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

    /**
     * Handle /support command — activates User Support Assistant mode.
     * Loads specialized system prompt and enables all tools (MCP + Local + RAG).
     */
    private suspend fun handleSupportCommand(args: String?): CommandResult {
        return try {
            // 1. Use embedded support assistant system prompt
            val systemPrompt = SUPPORT_ASSISTANT_PROMPT

            // 2. Build user query
            val userQuery = if (!args.isNullOrBlank()) {
                args
            } else {
                "Привет! Я готов помочь с вопросами по GigaChat Multiplatform Chat App. " +
                    "Опиши, пожалуйста, свою проблему или задай вопрос."
            }

            // 3. Return with full tool access and support assistant prompt as context
            CommandResult.NeedsLlmProcessing(
                context = systemPrompt,
                query = userQuery,
                enableTools = true,  // Enable all MCP and Local tools
                excludeTools = null,  // No restrictions
                includeTools = null,  // No whitelist (allow all)
                requiresDocValidation = false  // No Phase 2 doc validation for support
            )
        } catch (e: Exception) {
            CommandResult.Error("Failed to activate Support Assistant: ${e.message}")
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
