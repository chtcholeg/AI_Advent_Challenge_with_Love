package ru.chtcholeg.agent.data.repository

import io.ktor.client.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import ru.chtcholeg.agent.BuildKonfig
import ru.chtcholeg.shared.data.api.GigaChatApi
import ru.chtcholeg.shared.data.api.GigaChatApiImpl
import ru.chtcholeg.shared.data.api.OllamaApi
import ru.chtcholeg.shared.data.api.OllamaApiImpl
import ru.chtcholeg.shared.data.api.OllamaNotAvailableException
import ru.chtcholeg.shared.data.model.*
import ru.chtcholeg.agent.domain.model.*
import ru.chtcholeg.shared.domain.model.ConnectionStatus
import ru.chtcholeg.shared.domain.model.Model
import ru.chtcholeg.agent.config.AgentConfig
import ru.chtcholeg.agent.domain.service.ContextManager
import ru.chtcholeg.agent.domain.service.ImageProcessor
import ru.chtcholeg.agent.domain.service.RetryPolicy
import ru.chtcholeg.agent.domain.service.ToolExecutor
import kotlin.time.measureTimedValue

class AgentRepository(
    httpClient: HttpClient,
    private val settingsRepository: SettingsRepository,
    private val mcpRepository: McpRepository,
    private val planModeRepository: PlanModeRepository,
    private val imageProcessor: ImageProcessor = ImageProcessor(),
    private val toolExecutor: ToolExecutor,
    private val contextManager: ContextManager = ContextManager(),
    private val ollamaApi: OllamaApi = OllamaApiImpl(httpClient),
) {
    private val gigaChatApi: GigaChatApi = GigaChatApiImpl(httpClient)

    private val conversationHistory = mutableListOf<Message>()
    private val historyMutex = Mutex()
    private var gigaChatAccessToken: String? = null
    private var gigaChatTokenExpiry: Long? = null

    /**
     * Builds unified system prompt based on all available capabilities.
     *
     * Scenarios:
     * 1. MCP + RAG → intelligent routing between tools and documents, conditional citations
     * 2. MCP only → tool-focused instructions
     * 3. RAG only → document-focused with mandatory citations
     * 4. Neither → null (plain AI response)
     * 5. /help context (ragCitations=false) → simple context prompt, no tools, no citations
     */
    private fun buildSystemPrompt(
        serverCategories: List<String>,
        hasLocalTools: Boolean,
        inPlanMode: Boolean,
        ragContext: String?,
        ragCitations: Boolean,
        commandContext: String? = null
    ): String? {
        val hasTools = serverCategories.isNotEmpty() || hasLocalTools

        // --- Special case: /help command — simple context, no tools, no citations ---
        if (ragContext != null && !ragCitations && commandContext == null) {
            return """
Ты — AI-ассистент. Используй предоставленный контекст документа для ответа на вопрос пользователя.
Отвечай компактно и по существу на человеческом языке (НЕ в JSON, НЕ в XML).
НЕ добавляй раздел "Источники" и ссылки вида [Источник N].
НЕ вызывай никакие инструменты — просто ответь текстом.

<context>
$ragContext
</context>
            """.trimIndent()
        }

        // --- Special case: pre-fetched review — command context with instructions, no tools ---
        if (commandContext != null && ragContext == null && !ragCitations) {
            return """
Ты — опытный code reviewer. Все данные и инструкции предоставлены ниже.
НЕ вызывай никакие инструменты — все данные уже собраны.
Строго следуй инструкциям по формату и методологии анализа.

$commandContext
            """.trimIndent()
        }

        // --- Neither tools nor documents → plain AI response ---
        if (!hasTools && ragContext == null && commandContext == null) return null

        // --- Build reusable sections ---

        val categories = mutableListOf<String>()
        if (hasLocalTools) {
            categories.add("- Local Tools (file operations, search, bash, task management, planning)")
        }
        categories.addAll(serverCategories.map { "- $it" })
        val categoriesList = categories.joinToString("\n")

        val planModeSection = if (inPlanMode) {
            """

🎯 PLAN MODE ACTIVE:
- You are in PLANNING MODE - designing implementation, NOT implementing
- ✓ ALLOWED: read, glob, grep, ask_user_question, write (for plan file only)
- ✗ FORBIDDEN: edit, bash (no code changes or commands in plan mode)
- When plan is complete, use exit_plan_mode to request user approval
- Do NOT ask "Is the plan ready?" - exit_plan_mode handles approval"""
        } else {
            """

УПРАВЛЕНИЕ ЗАДАЧАМИ (task_create - только для ОТСЛЕЖИВАНИЯ прогресса):
⚠️ task_create НЕ ВЫПОЛНЯЕТ работу - это TODO-list для организации!
- Используй task_create только для отслеживания прогресса сложных задач (3+ шагов)
- task_create подходит, когда ты САМ будешь делать работу и нужно отследить прогресс
- НЕ используй task_create для команд (как /review-pr) - выполняй работу НАПРЯМУЮ!
- Ставь in_progress ПЕРЕД началом работы
- Ставь completed ТОЛЬКО когда задача полностью выполнена
- Если заблокирован: оставь in_progress + создай новую задачу для блокера
- После завершения вызови task_list для проверки разблокированных задач

РЕЖИМ ПЛАНИРОВАНИЯ:
- Используй enter_plan_mode для нетривиальных задач реализации
- Предпочитай планирование для: новых фич, архитектурных решений, многофайловых изменений
- В режиме планирования: исследуй, проектируй, пиши план, затем exit_plan_mode
- exit_plan_mode автоматически запрашивает одобрение пользователя"""
        }

        val toolInstructionsBlock = if (hasTools) """
ПРАВИЛА РАБОТЫ С ИНСТРУМЕНТАМИ:
1. Анализируй запрос пользователя. Если для ответа нужны актуальные данные, вычисления или действия — используй инструменты.
2. Когда получишь результат инструмента — проанализируй его.
3. Если результата достаточно — дай ответ.
4. Если нужна дополнительная информация — вызови следующий инструмент.
5. Если инструменты не нужны — ответь, используя свои знания.
6. Будь краток в рассуждениях. Главное — точный вызов инструмента или четкий ответ.

КРИТИЧЕСКИЕ ПРАВИЛА ВЫБОРА ИНСТРУМЕНТОВ:
- Для чтения файлов ВСЕГДА используй 'read', НЕ 'bash' с cat/head/tail
- Для записи файлов ВСЕГДА используй 'write', НЕ 'bash' с echo/cat
- Для редактирования файлов ВСЕГДА используй 'edit', НЕ 'bash' с sed/awk
- Для поиска файлов ВСЕГДА используй 'glob', НЕ 'bash' с find/ls
- Для поиска в содержимом ВСЕГДА используй 'grep', НЕ 'bash' с grep/rg
- 'bash' только для git, npm, docker и других terminal операций
- Вызывай ОДИН инструмент за раз. После получения результата — анализируй и вызывай следующий при необходимости
$planModeSection

Доступные категории инструментов:
$categoriesList""" else null

        // ═══════════════════════════════════════════
        // SCENARIO 1: MCP + RAG — intelligent routing
        // ═══════════════════════════════════════════
        if (hasTools && ragContext != null) {
            return """
Ты — точный и полезный AI-ассистент с доступом к базе документов И набору инструментов (tools/API).

У тебя есть ДВА источника информации:
1. 📄 ДОКУМЕНТЫ — релевантные фрагменты из базы знаний (в разделе <context> ниже)
2. 🔧 ИНСТРУМЕНТЫ — MCP tools для выполнения действий (чтение файлов, bash, git и т.д.)

═══════════════════════════════════════════
СТРАТЕГИЯ: КАК ВЫБРАТЬ ИСТОЧНИК ОТВЕТА
═══════════════════════════════════════════

ПЕРЕД формированием ответа ОБЯЗАТЕЛЬНО определи тип запроса:

▸ ТИП A — ИНФОРМАЦИОННЫЙ ЗАПРОС
  Признаки: «что такое...», «как работает...», «объясни...», «расскажи про...», «в чём разница между...»
  Действие:
  1. Проверь, есть ли ответ в <context>
  2. Если ДА → отвечай на основе документов С ЦИТАТАМИ [Источник N]
  3. Если НЕТ → используй инструменты или свои знания БЕЗ цитат.
     Скажи: «В предоставленных документах информация по этому вопросу не найдена» и ответь из своих знаний или с помощью инструментов.

▸ ТИП B — ЗАПРОС НА ДЕЙСТВИЕ
  Признаки: «создай...», «запусти...», «покажи файл...», «сделай коммит», «найди на диске...», «выполни...», «открой...», «удали...»
  Действие:
  1. Используй ИНСТРУМЕНТЫ (MCP tools)
  2. Цитаты [Источник N] НЕ нужны — ты выполняешь действие, а не цитируешь документы
  3. Раздел «📚 Источники:» НЕ добавляй

▸ ТИП C — СМЕШАННЫЙ ЗАПРОС
  Признаки: содержит И информационную часть, И запрос на действие
  Примеры: «расскажи об архитектуре и покажи файл конфигурации», «что написано в доке о деплое, и запусти тесты»
  Действие:
  1. Информацию из документов сопровождай цитатами [Источник N]
  2. Результаты инструментов приводи БЕЗ цитат
  3. Раздел «📚 Источники:» добавляй ТОЛЬКО если хотя бы одна цитата [Источник N] была использована

═══════════════════════════════════════════
ПРАВИЛА ЦИТИРОВАНИЯ (только при использовании документов)
═══════════════════════════════════════════

Цитаты нужны ИСКЛЮЧИТЕЛЬНО когда ты берёшь информацию из раздела <context>:
• Каждое утверждение из документов → сопровождай ссылкой [Источник N]
• Точные выдержки из документов → приводи в кавычках «...»
• Информация из нескольких источников → указывай все: [Источник 1][Источник 3]
• Раздел «📚 Источники:» — добавляй В КОНЦЕ ответа ТОЛЬКО если есть хотя бы одна цитата:
  📚 Источники:
  [Источник 1] — имя файла, краткое описание использованной информации
  [Источник 2] — имя файла, краткое описание использованной информации

⚠️ ЗАПРЕЩЕНО добавлять раздел «📚 Источники:» если ни одна цитата [Источник N] в тексте ответа не использована.

═══════════════════════════════════════════
ПРАВИЛА РАБОТЫ С ИНСТРУМЕНТАМИ
═══════════════════════════════════════════

${toolInstructionsBlock!!.trim()}

═══════════════════════════════════════════
КОНТЕКСТ ДОКУМЕНТОВ
═══════════════════════════════════════════

<context>
$ragContext
</context>
${if (commandContext != null) """

═══════════════════════════════════════════
КОНТЕКСТ КОМАНДЫ
═══════════════════════════════════════════

$commandContext
""" else ""}
Вывод: вызов инструмента в JSON-формате ИЛИ текстовый ответ (с цитатами — если использованы документы, без цитат — если использованы только инструменты).
            """.trimIndent()
        }

        // ═══════════════════════════════════════════
        // SCENARIO 2: RAG only — document-focused
        // ═══════════════════════════════════════════
        if (ragContext != null) {
            return """
Ты — точный AI-ассистент с доступом к базе документов. Отвечай на вопросы, опираясь на предоставленный контекст.

Инструменты (tools) тебе НЕ доступны. Отвечай только на основе документов и своих знаний.

ОБЯЗАТЕЛЬНЫЕ ПРАВИЛА ЦИТИРОВАНИЯ:
1. Каждое утверждение, основанное на документах, ДОЛЖНО сопровождаться ссылкой на источник в формате [Источник N].
2. Приводи ТОЧНЫЕ ЦИТАТЫ из документов в кавычках «...», когда это уместно.
3. Если используешь информацию из нескольких источников, указывай все релевантные ссылки.
4. В конце ответа ОБЯЗАТЕЛЬНО добавь раздел «📚 Источники:» со списком всех использованных источников:
   📚 Источники:
   [Источник 1] — имя файла, краткое описание использованной информации
   [Источник 2] — имя файла, краткое описание использованной информации
5. Если контекст НЕ содержит ответа на вопрос, ЯВНО скажи: «В предоставленных документах информация по этому вопросу не найдена» и ответь на основе своих знаний, пометив это.

ФОРМАТ ОТВЕТА:
- Основной ответ с цитатами [Источник N] и выдержками «...»
- Раздел «📚 Источники:» в конце

<context>
$ragContext
</context>
            """.trimIndent()
        }

        // ═══════════════════════════════════════════
        // SCENARIO 3: MCP only — tool-focused
        // ═══════════════════════════════════════════
        val commandContextSection = if (commandContext != null) {
            """

═══════════════════════════════════════════
КОНТЕКСТ КОМАНДЫ
═══════════════════════════════════════════

$commandContext

"""
        } else ""

        return """
Ты — точный и полезный AI-ассистент. Твоя задача — решать проблемы пользователя, используя доступные инструменты (API).

${toolInstructionsBlock!!.trim()}
$commandContextSection
Твой вывод ДОЛЖЕН быть либо вызовом инструмента в указанном JSON-формате, либо финальным ответом пользователю.
        """.trimIndent()
    }

    /**
     * Send a message and get AI response with metadata.
     * Supports chained function calls (multiple tools in sequence).
     * Returns a list of messages: tool calls, tool results (including screenshots), and final AI response.
     */
    suspend fun sendMessage(userMessage: String, ragContext: String? = null, ragCitations: Boolean = true, excludeTools: List<String>? = null, includeTools: List<String>? = null, commandContext: String? = null): List<AgentMessage> {
        val settings = settingsRepository.settings.value
        val resultMessages = mutableListOf<AgentMessage>()

        // Add user message to history
        historyMutex.withLock {
            conversationHistory.add(
                Message(
                    role = "user",
                    content = userMessage
                )
            )
        }

        // Simple chat mode: no tools, no system prompt
        if (settings.simpleChatMode) {
            return sendSimpleChat(userMessage)
        }

        // Get connected servers
        val connectedServers = mcpRepository.servers.value
            .filter { it.status == ConnectionStatus.CONNECTED }
        val serverCategories = connectedServers.map { it.name }

        // Get available tools from MCP servers and local tools, applying filter
        val allTools = mcpRepository.getAllTools()
        val availableTools = when {
            includeTools != null -> {
                val filtered = allTools.filter { it.name in includeTools }
                if (filtered.isEmpty() && includeTools.isNotEmpty()) {
                    println("[AgentRepository] WARNING: includeTools=$includeTools resulted in empty tool list")
                }
                filtered
            }
            excludeTools != null -> allTools.filter { it.name !in excludeTools }
            else -> allTools
        }
        val hasLocalTools = availableTools.any { it.serverId == "local" }

        // Check if in plan mode
        val inPlanMode = planModeRepository.planModeState.value.isActive

        // Build unified system prompt (handles all scenarios: MCP+RAG, MCP only, RAG only, /help, neither)
        val systemPrompt = buildSystemPrompt(
            serverCategories = serverCategories,
            hasLocalTools = hasLocalTools,
            inPlanMode = inPlanMode,
            ragContext = ragContext,
            ragCitations = ragCitations,
            commandContext = commandContext
        )

        // Don't send tool definitions when processing plain context (e.g. /help, pre-fetched review)
        val functions = if (!ragCitations && (ragContext != null || commandContext != null)) {
            emptyList()
        } else {
            availableTools.map { tool ->
                // Include negative examples in description to help model avoid wrong tool selection
                val enhancedDescription = if (tool.negativeFewShotExamples.isNotEmpty()) {
                    val negativeExamplesText = tool.negativeFewShotExamples.joinToString("\n") {
                        "- \"${it.request}\" → ${it.reason}"
                    }
                    "${tool.description}\n\n⛔ НЕ ИСПОЛЬЗУЙ этот инструмент для:\n$negativeExamplesText"
                } else {
                    tool.description
                }

                GigaChatFunction(
                    name = tool.name,
                    description = enhancedDescription,
                    parameters = tool.inputSchema,
                    fewShotExamples = tool.fewShotExamples.map { example ->
                        FewShotExample(
                            request = example.request,
                            params = example.params
                        )
                    }
                )
            }
        }

        // Track total execution time and token usage across all calls
        var totalExecutionTimeMs = 0L
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokens = 0

        // Loop to handle chained function calls
        val maxIterations = AgentConfig.MAX_ITERATIONS
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            // Measure execution time for this call (with retry on transient errors)
            val (response, duration) = measureTimedValue {
                RetryPolicy.withRetry {
                    when (settings.model.api) {
                        Model.Api.GIGACHAT -> sendToGigaChat(settings, functions, systemPrompt)
                        Model.Api.OLLAMA -> sendToOllama(settings, functions, systemPrompt)
                    }
                }
            }

            totalExecutionTimeMs += duration.inWholeMilliseconds
            totalPromptTokens += response.usage?.promptTokens ?: 0
            totalCompletionTokens += response.usage?.completionTokens ?: 0
            totalTokens += response.usage?.totalTokens ?: 0

            // Collect all function calls from all choices (supports parallel tool calls)
            val functionCallChoices = response.choices.filter {
                it.finishReason == "function_call" && it.message.functionCall != null
            }

            if (functionCallChoices.isNotEmpty()) {
                // Execute function calls — in parallel if multiple, sequentially if one
                val functionCalls = functionCallChoices.map { it.message.functionCall!! }

                // Add all assistant function call messages to history
                functionCalls.forEach { functionCall ->
                    historyMutex.withLock {
                        conversationHistory.add(
                            Message(role = "assistant", content = null, functionCall = functionCall)
                        )
                    }
                    resultMessages.add(
                        AgentMessage(
                            content = "${functionCall.name}(${functionCall.arguments})",
                            type = MessageType.TOOL_CALL
                        )
                    )
                }

                // Execute tools — parallel if multiple, direct if single
                println("[AgentRepository] Executing ${functionCalls.size} tool(s)")
                functionCalls.forEach { fc ->
                    println("[AgentRepository] Tool: ${fc.name}, Arguments: ${fc.arguments}")
                }

                val toolResults = if (functionCalls.size == 1) {
                    listOf(functionCalls[0] to toolExecutor.executeTool(functionCalls[0]))
                } else {
                    coroutineScope {
                        functionCalls.map { fc ->
                            async { fc to toolExecutor.executeTool(fc) }
                        }.awaitAll()
                    }
                }

                // Process results and add to history
                for ((functionCall, toolResult) in toolResults) {
                    val screenshotMessage = imageProcessor.extractScreenshotFromResult(functionCall.name, toolResult.content)

                    val contentForHistory = if (screenshotMessage != null) {
                        "[Screenshot image captured successfully. The image is displayed to the user.]"
                    } else {
                        imageProcessor.createPlaceholderForImageContent(toolResult.content) ?: toolResult.content
                    }

                    val functionResultJson = buildJsonObject {
                        put("result", contentForHistory)
                        put("is_error", toolResult.isError)
                    }.toString()

                    historyMutex.withLock {
                        conversationHistory.add(
                            Message(role = "function", name = functionCall.name, content = functionResultJson)
                        )
                    }

                    if (screenshotMessage != null) {
                        resultMessages.add(screenshotMessage)
                    } else {
                        resultMessages.add(
                            AgentMessage(content = toolResult.content, type = MessageType.TOOL_RESULT)
                        )
                    }
                }

                // Continue loop to get next response
                continue
            }

            // Regular response (no function calls) — we're done
            val choice = response.choices.firstOrNull()
                ?: error("No response choice from AI")

            val aiMessage = choice.message.content ?: ""
            historyMutex.withLock {
                conversationHistory.add(
                    Message(role = "assistant", content = aiMessage)
                )
            }

            resultMessages.add(
                AgentMessage(
                    content = aiMessage.trim(),
                    type = MessageType.AI,
                    executionTimeMs = totalExecutionTimeMs,
                    promptTokens = totalPromptTokens,
                    completionTokens = totalCompletionTokens,
                    totalTokens = totalTokens
                )
            )

            return resultMessages
        }

        // Safety: if we hit max iterations, return error
        resultMessages.add(
            AgentMessage(
                content = "Error: Too many function calls in chain (max $maxIterations)",
                type = MessageType.ERROR,
                executionTimeMs = totalExecutionTimeMs,
                promptTokens = totalPromptTokens,
                completionTokens = totalCompletionTokens,
                totalTokens = totalTokens
            )
        )
        return resultMessages
    }

    /**
     * Simple chat: no tools, no system prompt, single API call, no agentic loop.
     */
    private suspend fun sendSimpleChat(userMessage: String): List<AgentMessage> {
        val settings = settingsRepository.settings.value
        val startTime = System.currentTimeMillis()

        val response = RetryPolicy.withRetry {
            when (settings.model.api) {
                Model.Api.GIGACHAT -> sendToGigaChat(settings, emptyList(), null)
                Model.Api.OLLAMA -> sendToOllama(settings, emptyList(), null)
            }
        }

        val choice = response.choices.firstOrNull()
        val content = choice?.message?.content ?: ""
        val elapsed = System.currentTimeMillis() - startTime

        val aiMessage = AgentMessage(
            content = content,
            type = MessageType.AI,
            executionTimeMs = elapsed,
            promptTokens = response.usage?.promptTokens,
            completionTokens = response.usage?.completionTokens,
            totalTokens = response.usage?.totalTokens
        )

        historyMutex.withLock {
            conversationHistory.add(Message(role = "assistant", content = content))
        }

        return listOf(aiMessage)
    }

    private suspend fun sendToGigaChat(
        settings: AiSettings,
        functions: List<GigaChatFunction>,
        systemPrompt: String?
    ): ChatResponse {
        // Check token expiry and re-authenticate if needed
        val now = Clock.System.now().toEpochMilliseconds()
        if (gigaChatAccessToken == null || gigaChatTokenExpiry == null || now >= gigaChatTokenExpiry!!) {
            val authResponse = gigaChatApi.authenticate(
                clientId = BuildKonfig.GIGACHAT_CLIENT_ID,
                clientSecret = BuildKonfig.GIGACHAT_CLIENT_SECRET
            )
            gigaChatAccessToken = authResponse.accessToken
            gigaChatTokenExpiry = authResponse.expiresAt
        }

        // Log original history size and sanitize messages
        val sanitizedHistory = historyMutex.withLock {
            val originalSize = conversationHistory.sumOf { (it.content?.length ?: 0) }
            println("[AgentRepository] Original history: ${conversationHistory.size} messages, $originalSize chars")

            // Sanitize messages to remove any large base64 image data
            conversationHistory.map { imageProcessor.sanitizeMessageForApi(it) }
        }

        // Log sanitized size
        val sanitizedSize = sanitizedHistory.sumOf { (it.content?.length ?: 0) }
        println("[AgentRepository] Sanitized history: ${sanitizedHistory.size} messages, $sanitizedSize chars")

        // Apply sliding window with summarization instead of hard truncation
        val windowResult = contextManager.applyWindow(sanitizedHistory)

        if (windowResult.summarizedCount > 0) {
            println("[AgentRepository] Summarized ${windowResult.summarizedCount} old messages via sliding window")
        }

        // Build messages with optional system prompt.
        // GigaChat requires the system message to be the FIRST and ONLY system message.
        // ContextManager.applyWindow may insert a summary as role="system" at the start of
        // windowResult.messages when history is truncated — merging it into the main system
        // prompt prevents the 422 "system message must be the first message" error.
        val summarySystemMessage = windowResult.messages.firstOrNull()?.takeIf { it.role == "system" }
        val nonSummaryMessages = if (summarySystemMessage != null) windowResult.messages.drop(1) else windowResult.messages
        val effectiveSystemPrompt = when {
            systemPrompt != null && summarySystemMessage != null ->
                "${systemPrompt}\n\n${summarySystemMessage.content}"
            systemPrompt != null -> systemPrompt
            summarySystemMessage != null -> summarySystemMessage.content
            else -> null
        }

        val messages = if (effectiveSystemPrompt != null) {
            listOf(Message(role = "system", content = effectiveSystemPrompt)) + nonSummaryMessages
        } else {
            nonSummaryMessages
        }

        val finalTokens = messages.sumOf { ContextManager.estimateTokens(it) }
        println("[AgentRepository] Final: ${messages.size} messages, ~$finalTokens tokens")

        return gigaChatApi.sendMessage(
            accessToken = gigaChatAccessToken!!,
            messages = messages,
            model = settings.model.id,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            repetitionPenalty = settings.repetitionPenalty,
            functions = functions.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Execute a subagent with isolated context.
     * Creates a separate conversation history, uses filtered tools, and respects maxTurns.
     * Returns the final text output from the subagent.
     */
    suspend fun executeIsolated(
        systemPrompt: String,
        userMessage: String,
        allowedTools: List<String>?,
        maxTurns: Int
    ): String {
        val settings = settingsRepository.settings.value

        // Isolated conversation history — subagent has no access to parent's history
        val isolatedHistory = mutableListOf<Message>()

        // Add user message
        isolatedHistory.add(Message(role = "user", content = userMessage))

        // Get filtered tools
        val availableTools = mcpRepository.getFilteredTools(allowedTools)

        val functions = availableTools.map { tool ->
            val enhancedDescription = if (tool.negativeFewShotExamples.isNotEmpty()) {
                val negativeExamplesText = tool.negativeFewShotExamples.joinToString("\n") {
                    "- \"${it.request}\" → ${it.reason}"
                }
                "${tool.description}\n\n⛔ НЕ ИСПОЛЬЗУЙ этот инструмент для:\n$negativeExamplesText"
            } else {
                tool.description
            }

            GigaChatFunction(
                name = tool.name,
                description = enhancedDescription,
                parameters = tool.inputSchema,
                fewShotExamples = tool.fewShotExamples.map { example ->
                    FewShotExample(
                        request = example.request,
                        params = example.params
                    )
                }
            )
        }

        var iterations = 0

        while (iterations < maxTurns) {
            iterations++

            val response = RetryPolicy.withRetry {
                when (settings.model.api) {
                    Model.Api.GIGACHAT -> sendToGigaChatIsolated(settings, functions, systemPrompt, isolatedHistory)
                    Model.Api.OLLAMA -> sendToOllamaIsolated(settings, functions, systemPrompt, isolatedHistory)
                }
            }

            val choice = response.choices.firstOrNull()
                ?: return "Error: No response choice from AI"

            if (choice.finishReason == "function_call") {
                val functionCall = choice.message.functionCall
                    ?: return "Error: Function call expected but not found"

                // Add assistant's function call to isolated history
                isolatedHistory.add(
                    Message(role = "assistant", content = null, functionCall = functionCall)
                )

                // Execute the tool
                println("[AgentRepository] Executing tool: ${functionCall.name}, Arguments: ${functionCall.arguments}")
                val toolResult = toolExecutor.executeTool(functionCall)

                // For history: replace image data with placeholder
                val contentForHistory = imageProcessor.createPlaceholderForImageContent(toolResult.content) ?: toolResult.content

                val functionResultJson = buildJsonObject {
                    put("result", contentForHistory)
                    put("is_error", toolResult.isError)
                }.toString()

                isolatedHistory.add(
                    Message(role = "function", name = functionCall.name, content = functionResultJson)
                )

                continue
            }

            // Regular response — subagent is done
            return choice.message.content?.trim() ?: ""
        }

        return "Error: Subagent reached maximum iterations ($maxTurns)"
    }

    /**
     * Send to GigaChat with an isolated conversation history (for subagents).
     */
    private suspend fun sendToGigaChatIsolated(
        settings: AiSettings,
        functions: List<GigaChatFunction>,
        systemPrompt: String,
        isolatedHistory: List<Message>
    ): ChatResponse {
        // Ensure token is valid
        val now = Clock.System.now().toEpochMilliseconds()
        if (gigaChatAccessToken == null || gigaChatTokenExpiry == null || now >= gigaChatTokenExpiry!!) {
            val authResponse = gigaChatApi.authenticate(
                clientId = BuildKonfig.GIGACHAT_CLIENT_ID,
                clientSecret = BuildKonfig.GIGACHAT_CLIENT_SECRET
            )
            gigaChatAccessToken = authResponse.accessToken
            gigaChatTokenExpiry = authResponse.expiresAt
        }

        val sanitizedHistory = isolatedHistory.map { imageProcessor.sanitizeMessageForApi(it) }

        // Apply sliding window with summarization
        val windowResult = contextManager.applyWindow(sanitizedHistory)

        // Merge any summary system message into systemPrompt to avoid duplicate system messages
        val summarySystemMessage = windowResult.messages.firstOrNull()?.takeIf { it.role == "system" }
        val nonSummaryMessages = if (summarySystemMessage != null) windowResult.messages.drop(1) else windowResult.messages
        val effectiveSystemPrompt = if (summarySystemMessage != null) {
            "${systemPrompt}\n\n${summarySystemMessage.content}"
        } else {
            systemPrompt
        }

        val messages = listOf(Message(role = "system", content = effectiveSystemPrompt)) + nonSummaryMessages

        return gigaChatApi.sendMessage(
            accessToken = gigaChatAccessToken!!,
            messages = messages,
            model = settings.model.id,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            repetitionPenalty = settings.repetitionPenalty,
            functions = functions.takeIf { it.isNotEmpty() }
        )
    }

    private suspend fun sendToOllama(
        settings: AiSettings,
        functions: List<GigaChatFunction>,
        systemPrompt: String?
    ): ChatResponse {
        if (!ollamaApi.isAvailable()) {
            throw OllamaNotAvailableException(settings.model.id)
        }

        val sanitizedHistory = historyMutex.withLock {
            conversationHistory.map { imageProcessor.sanitizeMessageForApi(it) }
        }

        val windowResult = contextManager.applyWindow(sanitizedHistory)

        return ollamaApi.sendMessage(
            messages = windowResult.messages,
            model = settings.model.id,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            repetitionPenalty = settings.repetitionPenalty,
            functions = functions.takeIf { it.isNotEmpty() },
            systemPrompt = systemPrompt
        )
    }

    private suspend fun sendToOllamaIsolated(
        settings: AiSettings,
        functions: List<GigaChatFunction>,
        systemPrompt: String,
        isolatedHistory: List<Message>
    ): ChatResponse {
        if (!ollamaApi.isAvailable()) {
            throw OllamaNotAvailableException(settings.model.id)
        }

        val sanitizedHistory = isolatedHistory.map { imageProcessor.sanitizeMessageForApi(it) }

        val windowResult = contextManager.applyWindow(sanitizedHistory)

        return ollamaApi.sendMessage(
            messages = windowResult.messages,
            model = settings.model.id,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            repetitionPenalty = settings.repetitionPenalty,
            functions = functions.takeIf { it.isNotEmpty() },
            systemPrompt = systemPrompt
        )
    }

    suspend fun clearHistory() {
        historyMutex.withLock {
            conversationHistory.clear()
        }
    }

    /**
     * Restore conversation history from persisted messages.
     * Restores USER, AI, TOOL_CALL, and TOOL_RESULT messages to preserve
     * the full conversation context including tool interactions.
     */
    suspend fun restoreHistory(messages: List<AgentMessage>) {
        historyMutex.withLock {
            conversationHistory.clear()
        }
        messages.forEach { msg ->
            when (msg.type) {
                MessageType.USER -> historyMutex.withLock {
                    conversationHistory.add(
                        Message(role = "user", content = msg.content)
                    )
                }
                MessageType.AI -> historyMutex.withLock {
                    conversationHistory.add(
                        Message(role = "assistant", content = msg.content)
                    )
                }
                MessageType.TOOL_CALL -> {
                    // Reconstruct function call from persisted format "name(args)"
                    val fcMatch = Regex("""^(\w+)\((.+)\)$""", RegexOption.DOT_MATCHES_ALL)
                        .find(msg.content)
                    if (fcMatch != null) {
                        val name = fcMatch.groupValues[1]
                        val argsStr = fcMatch.groupValues[2]
                        try {
                            val argsJson = kotlinx.serialization.json.Json.parseToJsonElement(argsStr)
                            historyMutex.withLock {
                                conversationHistory.add(
                                    Message(
                                        role = "assistant",
                                        content = null,
                                        functionCall = FunctionCall(name = name, arguments = argsJson)
                                    )
                                )
                            }
                        } catch (_: Exception) {
                            // Can't parse args — skip this tool call
                        }
                    }
                }
                MessageType.TOOL_RESULT -> {
                    // Restore as function result message
                    // We need the tool name from the preceding TOOL_CALL
                    historyMutex.withLock {
                        val prevToolCall = conversationHistory.lastOrNull()?.functionCall
                        val toolName = prevToolCall?.name ?: "unknown"
                        val resultJson = kotlinx.serialization.json.buildJsonObject {
                            put("result", msg.content.take(AgentConfig.MAX_MESSAGE_LENGTH))
                            put("is_error", false)
                        }.toString()
                        conversationHistory.add(
                            Message(role = "function", name = toolName, content = resultJson)
                        )
                    }
                }
                else -> { /* skip SCREENSHOT, SYSTEM, ERROR, RAG_CONTEXT, COMMAND */ }
            }
        }
    }
}
