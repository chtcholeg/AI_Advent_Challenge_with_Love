package ru.chtcholeg.agent.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.chtcholeg.agent.domain.model.AiSettings

class SettingsRepository {
    private val _settings = MutableStateFlow(AiSettings())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    fun updateSettings(newSettings: AiSettings) {
        _settings.value = newSettings.validated()
    }
}
