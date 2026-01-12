package ru.chtcholeg.app.data.repository

import ru.chtcholeg.app.domain.model.AiSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepositoryImpl : SettingsRepository {
    private val _settings = MutableStateFlow(AiSettings.DEFAULT)
    override val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    override fun updateSettings(settings: AiSettings) {
        _settings.update { settings.validated() }
    }

    override fun resetToDefaults() {
        _settings.update { AiSettings.DEFAULT }
    }
}
