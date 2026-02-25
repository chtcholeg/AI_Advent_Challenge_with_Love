package ru.chtcholeg.godagent.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.godagent.domain.model.AppSettings
import java.io.File

class SettingsRepository {

    private val settingsFile = File(
        System.getProperty("user.home"),
        ".god-agent/settings.json"
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString<AppSettings>(settingsFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun updateSettings(settings: AppSettings) {
        _settings.value = settings
        saveSettings(settings)
    }

    private fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.parentFile?.mkdirs()
            settingsFile.writeText(json.encodeToString(settings))
        } catch (e: Exception) {
            // Ignore save errors silently
        }
    }
}
