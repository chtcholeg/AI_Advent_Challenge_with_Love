package ru.chtcholeg.godagent.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import ru.chtcholeg.godagent.data.api.ApiMessage
import ru.chtcholeg.godagent.data.api.GigaChatApi
import ru.chtcholeg.godagent.data.api.OllamaApi
import ru.chtcholeg.godagent.data.rag.DocumentIndexer
import ru.chtcholeg.godagent.data.tools.ToolExecutor
import ru.chtcholeg.godagent.domain.model.*

class ChatRepository(
    private val gigaChatApi: GigaChatApi,
    private val ollamaApi: OllamaApi,
    private val toolExecutor: ToolExecutor,
    private val documentIndexer: DocumentIndexer
) {
    private val history = mutableListOf<ApiMessage>()
    private var currentSystemPrompt: String? = null

    fun sendMessage(userMessage: String, settings: AppSettings): Flow<AgentStep> = flow {
        val systemPrompt = buildSystemPrompt(settings)

        // Rebuild history if system prompt changed
        if (systemPrompt != currentSystemPrompt) {
            val userMessages = history.filter { it.role != "system" }
            history.clear()
            history.add(ApiMessage("system", systemPrompt))
            history.addAll(userMessages)
            currentSystemPrompt = systemPrompt
        }

        // Compress history if too large (rough estimate)
        val totalChars = history.sumOf { it.content.length }
        if (totalChars > 16000) {
            compressHistory(settings)
        }

        history.add(ApiMessage("user", userMessage))

        val maxIterations = settings.maxReactIterations
        var iterations = 0

        emit(AgentStep.StatusUpdate("Анализирую запрос..."))

        while (iterations < maxIterations) {
            val responseContent = callLlm(settings, history.toList())

            // Try to parse as JSON (ReAct step)
            val jsonResponse = extractJson(responseContent)

            if (jsonResponse == null) {
                // Plain text response — treat as final answer
                history.add(ApiMessage("assistant", responseContent))
                emit(AgentStep.FinalAnswer(responseContent))
                break
            }

            val isDone = jsonResponse["done"]?.jsonPrimitive?.booleanOrNull ?: false
            if (isDone) {
                val answer = jsonResponse["answer"]?.jsonPrimitive?.contentOrNull ?: responseContent
                history.add(ApiMessage("assistant", answer))
                emit(AgentStep.FinalAnswer(answer))
                break
            }

            val toolName = jsonResponse["tool"]?.jsonPrimitive?.contentOrNull
            if (toolName != null) {
                val argsElement = jsonResponse["args"]
                val argsJson = when {
                    argsElement == null -> "{}"
                    argsElement is JsonObject -> argsElement.toString()
                    argsElement is JsonPrimitive -> argsElement.content
                    else -> "{}"
                }

                emit(AgentStep.ToolCall(toolName, argsJson))

                // Add assistant's tool call to history
                history.add(ApiMessage("assistant", responseContent))

                // Execute tool
                val result = toolExecutor.execute(toolName, argsJson)
                val isError = result.startsWith("Error")

                emit(AgentStep.ToolResult(toolName, result, isError))

                // Add tool result to history as user message (observation)
                history.add(ApiMessage("user", "Tool result for $toolName:\n$result"))

                iterations++
                continue
            }

            // Unexpected format — treat as final answer
            history.add(ApiMessage("assistant", responseContent))
            emit(AgentStep.FinalAnswer(responseContent))
            break
        }

        if (iterations >= maxIterations) {
            val msg = "Достигнут лимит итераций ($maxIterations). Последний контекст обработан."
            history.add(ApiMessage("assistant", msg))
            emit(AgentStep.Error(msg))
        }
    }

    private suspend fun callLlm(settings: AppSettings, messages: List<ApiMessage>): String {
        val modelId = settings.selectedModelId
        val params = settings.modelParameters
        val isGigaChat = modelId.startsWith("GigaChat")

        return if (isGigaChat) {
            gigaChatApi.ensureAuthenticated(settings.gigachatClientId, settings.gigachatClientSecret)
            gigaChatApi.sendMessage(
                messages = messages,
                modelId = modelId,
                temperature = params.temperature,
                topP = params.topP,
                maxTokens = params.maxTokens,
                repetitionPenalty = params.repetitionPenalty
            ).content
        } else {
            ollamaApi.sendMessage(
                messages = messages,
                modelId = modelId,
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                maxTokens = params.maxTokens,
                repetitionPenalty = params.repetitionPenalty,
                baseUrl = settings.ollamaBaseUrl
            ).content
        }
    }

    private fun extractJson(text: String): JsonObject? {
        // Strip markdown code blocks if present
        val stripped = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return try {
            val jsonStr = stripped.substring(start, end + 1)
            Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonStr).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun buildSystemPrompt(settings: AppSettings): String {
        val toolsDescription = toolExecutor.getToolsPromptDescription()
        val personalization = settings.userProfile.buildPersonalizationPrompt()

        return buildString {
            appendLine("Ты — God Agent, персональный AI-ассистент.")
            if (personalization.isNotBlank()) {
                appendLine(personalization)
            }
            appendLine()
            appendLine("ТВОИ ОГРАНИЧЕНИЯ (критически важно):")
            appendLine("- У тебя НЕТ встроенных часов. Ты не знаешь текущее время или дату.")
            appendLine("- У тебя НЕТ данных о текущей погоде.")
            appendLine("- У тебя НЕТ актуальных курсов валют.")
            appendLine("- Ты не имеешь доступа к файлам, git-репозиторию или внешним системам.")
            appendLine("Всё это доступно ТОЛЬКО через инструменты.")
            appendLine()
            appendLine("ДОСТУПНЫЕ ИНСТРУМЕНТЫ:")
            appendLine(toolsDescription)
            appendLine()
            appendLine("ФОРМАТ ОТВЕТА — строго один из двух:")
            appendLine()
            appendLine("Вызов инструмента:")
            appendLine("{\"tool\": \"название\", \"args\": {параметры}}")
            appendLine()
            appendLine("Финальный ответ:")
            appendLine("{\"done\": true, \"answer\": \"текст\"}")
            appendLine()
            appendLine("Отвечай ТОЛЬКО JSON, никакого текста за пределами JSON-объекта.")
            appendLine("После получения результата инструмента: отвечай финальным JSON или вызывай следующий инструмент.")
            appendLine()
            appendLine("ПРИМЕРЫ:")
            appendLine()
            appendLine("User: который час?")
            appendLine("Неверно: {\"done\": true, \"answer\": \"Сейчас 14:00\"} — ты не знаешь время!")
            appendLine("Верно:   {\"tool\": \"get_time\", \"args\": {}}")
            appendLine()
            appendLine("User: какая погода в Москве?")
            appendLine("{\"tool\": \"get_weather\", \"args\": {\"city\": \"Москва\"}}")
            appendLine()
            appendLine("User: курс доллара к рублю")
            appendLine("{\"tool\": \"get_currency\", \"args\": {\"from\": \"USD\", \"to\": \"RUB\"}}")
            appendLine()
            appendLine("User: 5 * 7?")
            appendLine("{\"done\": true, \"answer\": \"35\"}")
            if (settings.gitRepoPath.isNotBlank()) {
                appendLine()
                appendLine("Git репозиторий: ${settings.gitRepoPath}")
            }
        }.trim()
    }

    private suspend fun compressHistory(settings: AppSettings) {
        val nonSystem = history.filter { it.role != "system" }
        if (nonSystem.size < 10) return

        val toCompress = nonSystem.dropLast(6) // Keep last 6 messages fresh
        val summaryRequest = listOf(
            ApiMessage(
                "system",
                "Сожми следующую историю диалога в краткое резюме на русском языке (3-5 предложений):"
            ),
            ApiMessage(
                "user",
                toCompress.joinToString("\n") { "${it.role}: ${it.content.take(200)}" }
            )
        )

        try {
            val summary = callLlm(settings, summaryRequest)
            val systemMsg = history.firstOrNull { it.role == "system" }
            history.clear()
            if (systemMsg != null) history.add(systemMsg)
            history.add(ApiMessage("assistant", "[История сжата]: $summary"))
            history.addAll(nonSystem.takeLast(6))
        } catch (e: Exception) {
            // If compression fails, just trim old messages
            val systemMsg = history.firstOrNull { it.role == "system" }
            history.clear()
            if (systemMsg != null) history.add(systemMsg)
            history.addAll(nonSystem.takeLast(10))
        }
    }

    fun resetHistory() {
        history.clear()
        currentSystemPrompt = null
    }

    fun loadHistory(messages: List<ApiMessage>) {
        history.clear()
        currentSystemPrompt = null
        history.addAll(messages)
    }
}
