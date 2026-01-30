package ru.chtcholeg.app.presentation.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.chtcholeg.app.data.local.ReminderLocalRepository
import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.data.repository.McpRepository
import ru.chtcholeg.app.domain.model.AiSettings
import ru.chtcholeg.app.domain.model.ReminderConfig
import ru.chtcholeg.app.domain.model.ReminderInterval

private const val REMINDER_SUMMARIZATION_PROMPT = """Ты получил список сообщений из Telegram-канала. Создай краткий саммари (3-5 предложений), выделив ключевые новости, события или идеи.

ПРАВИЛА:
1. Язык саммари — тот же, что у сообщений
2. Выдель самые важные моменты
3. Не добавляй информацию, которой нет в сообщениях
4. Формат: просто текст, без списков и заголовков
5. Начинай саммари с упоминания канала"""

class ReminderStore(
    private val mcpRepository: McpRepository,
    private val chatRepository: ChatRepository,
    private val reminderLocalRepository: ReminderLocalRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ReminderState())
    val state: StateFlow<ReminderState> = _state.asStateFlow()

    private var tickJob: Job? = null

    init {
        coroutineScope.launch {
            val saved = reminderLocalRepository.getActive()
            if (saved != null && saved.enabled && saved.channel.isNotBlank()) {
                _state.update { it.copy(activeConfig = saved) }
                startTickLoop(saved)
            }
        }
    }

    fun dispatch(intent: ReminderIntent) {
        when (intent) {
            is ReminderIntent.Start -> handleStart(intent.config)
            is ReminderIntent.Stop -> handleStop()
            is ReminderIntent.UpdateConfig -> handleUpdate(intent.field, intent.value)
            is ReminderIntent.Activate -> handleActivate(intent.config)
        }
    }

    private fun handleStart(config: ReminderConfig) {
        coroutineScope.launch {
            val validConfig = config.copy(
                channel = config.channel.removePrefix("@").trim(),
                messageCount = config.messageCount.coerceIn(1, 30)
            )
            reminderLocalRepository.saveOrUpdate(validConfig)
            _state.update { it.copy(activeConfig = validConfig, error = null) }
            startTickLoop(validConfig)
        }
    }

    private fun handleStop() {
        tickJob?.cancel()
        tickJob = null
        coroutineScope.launch {
            val current = _state.value.activeConfig
            if (current != null) {
                reminderLocalRepository.disable(current.id)
            }
            _state.update { it.copy(activeConfig = null, lastSummary = null, lastSummaryChannel = null) }
        }
    }

    private fun handleUpdate(field: String, value: String) {
        val current = _state.value.activeConfig ?: return

        val updated = when (field) {
            "channel" -> current.copy(channel = value.removePrefix("@").trim())
            "interval_seconds" -> current.copy(interval = ReminderInterval.fromSeconds(value.toIntOrNull() ?: current.interval.seconds))
            "message_count" -> current.copy(messageCount = (value.toIntOrNull() ?: current.messageCount).coerceIn(1, 30))
            else -> {
                _state.update { it.copy(error = "Unknown field: $field") }
                return
            }
        }

        coroutineScope.launch {
            reminderLocalRepository.saveOrUpdate(updated)
            _state.update { it.copy(activeConfig = updated, error = null) }
            startTickLoop(updated)
        }
    }

    private fun handleActivate(config: ReminderConfig) {
        if (config.enabled && config.channel.isNotBlank()) {
            coroutineScope.launch {
                reminderLocalRepository.saveOrUpdate(config)
                _state.update { it.copy(activeConfig = config, error = null) }
                startTickLoop(config)
            }
        }
    }

    private fun startTickLoop(config: ReminderConfig) {
        tickJob?.cancel()
        tickJob = coroutineScope.launch {
            while (isActive) {
                executeTick(config)
                delay(config.interval.seconds * 1_000L)
            }
        }
    }

    private suspend fun executeTick(config: ReminderConfig) {
        if (config.channel.isBlank()) return

        try {
            val params = buildJsonObject {
                put("channel", config.channel)
                put("count", config.messageCount)
            }

            val toolResult = mcpRepository.executeTool("get_channel_messages", params)
            if (toolResult.isError) {
                _state.update { it.copy(error = "MCP error: ${toolResult.content}") }
                return
            }

            val maxId = extractMaxMessageId(toolResult.content)
            val currentConfig = reminderLocalRepository.getActive() ?: config

            if (maxId != null && maxId == currentConfig.lastSeenMessageId) {
                return
            }

            val summaryRequest = "Вот последние сообщения из канала @${config.channel}:\n\n${toolResult.content}\n\nСделай саммари."
            val response = chatRepository.sendMessageWithCustomSystemPrompt(
                userMessage = summaryRequest,
                systemPrompt = REMINDER_SUMMARIZATION_PROMPT
            )

            val now = Clock.System.now().toEpochMilliseconds()
            if (maxId != null) {
                reminderLocalRepository.updateLastSeen(config.id, maxId, now)
            }

            _state.update {
                it.copy(
                    lastSummary = response.content,
                    lastSummaryChannel = config.channel,
                    error = null
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Tick error: ${e.message}") }
        }
    }

    private fun extractMaxMessageId(text: String): String? {
        val idPattern = Regex("""ID:\s*(\d+)""")
        return idPattern.findAll(text)
            .map { it.groupValues[1] }
            .maxByOrNull { it.toLongOrNull() ?: 0L }
    }
}
