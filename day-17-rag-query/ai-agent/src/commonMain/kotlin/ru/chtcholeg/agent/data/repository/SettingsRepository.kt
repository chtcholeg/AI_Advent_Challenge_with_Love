package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.chtcholeg.agent.data.local.McpDatabase
import ru.chtcholeg.agent.domain.model.AiSettings
import ru.chtcholeg.agent.domain.model.RagMode
import ru.chtcholeg.shared.domain.model.Model

class SettingsRepository(
    private val database: McpDatabase
) {
    private val queries = database.settingsQueries

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    fun updateSettings(newSettings: AiSettings) {
        val validated = newSettings.validated()
        _settings.value = validated
        saveSettings(validated)
    }

    private fun loadSettings(): AiSettings {
        val rows = queries.selectAll().executeAsList()
        if (rows.isEmpty()) return AiSettings()

        val map = rows.associate { it.key to it.value_ }
        return AiSettings(
            model = map["model"]?.let { Model.fromId(it) } ?: AiSettings().model,
            temperature = map["temperature"]?.toFloatOrNull() ?: AiSettings().temperature,
            topP = map["topP"]?.toFloatOrNull() ?: AiSettings().topP,
            maxTokens = map["maxTokens"]?.toIntOrNull() ?: AiSettings().maxTokens,
            repetitionPenalty = map["repetitionPenalty"]?.toFloatOrNull() ?: AiSettings().repetitionPenalty,
            showSystemMessages = map["showSystemMessages"]?.toBooleanStrictOrNull() ?: AiSettings().showSystemMessages,
            ragMode = map["ragMode"]?.let { runCatching { RagMode.valueOf(it) }.getOrNull() } ?: AiSettings().ragMode,
            indexPath = map["indexPath"] ?: AiSettings().indexPath
        )
    }

    private fun saveSettings(settings: AiSettings) {
        queries.upsert("model", settings.model.id)
        queries.upsert("temperature", settings.temperature.toString())
        queries.upsert("topP", settings.topP.toString())
        queries.upsert("maxTokens", settings.maxTokens.toString())
        queries.upsert("repetitionPenalty", settings.repetitionPenalty.toString())
        queries.upsert("showSystemMessages", settings.showSystemMessages.toString())
        queries.upsert("ragMode", settings.ragMode.name)
        queries.upsert("indexPath", settings.indexPath)
    }
}
