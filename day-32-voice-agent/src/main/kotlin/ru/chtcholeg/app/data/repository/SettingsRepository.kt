package ru.chtcholeg.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.app.domain.model.AppSettings
import java.io.File

class SettingsRepository {

    private val settingsFile = File(
        System.getProperty("user.home"),
        ".ai-chat-day31/settings.json"
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val base = try {
            if (settingsFile.exists()) {
                json.decodeFromString<AppSettings>(settingsFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            AppSettings()
        }
        return base.copy(
            gigachatClientId = base.gigachatClientId.ifEmpty {
                System.getenv("GIGACHAT_CLIENT_ID") ?: ""
            },
            gigachatClientSecret = base.gigachatClientSecret.ifEmpty {
                System.getenv("GIGACHAT_CLIENT_SECRET") ?: ""
            }
        )
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
