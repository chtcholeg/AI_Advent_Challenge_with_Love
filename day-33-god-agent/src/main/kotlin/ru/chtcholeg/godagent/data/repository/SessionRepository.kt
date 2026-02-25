package ru.chtcholeg.godagent.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.godagent.domain.model.ChatSession
import java.io.File
import java.util.UUID

class SessionRepository {

    private val sessionsDir = File(
        System.getProperty("user.home"),
        ".god-agent/sessions"
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    init {
        loadAllSessions()
    }

    private fun loadAllSessions() {
        sessionsDir.mkdirs()
        val loaded = sessionsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<ChatSession>(file.readText())
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()

        _sessions.value = loaded
        _currentSession.value = loaded.firstOrNull() ?: createNewSession()
    }

    fun createNewSession(): ChatSession {
        val session = ChatSession(id = UUID.randomUUID().toString())
        saveSession(session)
        val updated = listOf(session) + _sessions.value
        _sessions.value = updated
        _currentSession.value = session
        return session
    }

    fun selectSession(sessionId: String) {
        val session = _sessions.value.firstOrNull { it.id == sessionId }
        if (session != null) {
            _currentSession.value = session
        }
    }

    fun updateCurrentSession(session: ChatSession) {
        val updated = session.copy(updatedAt = System.currentTimeMillis())
        _currentSession.value = updated
        _sessions.value = _sessions.value.map { if (it.id == updated.id) updated else it }
            .sortedByDescending { it.updatedAt }
        saveSession(updated)
    }

    fun deleteSession(sessionId: String) {
        val file = File(sessionsDir, "$sessionId.json")
        file.delete()
        val remaining = _sessions.value.filter { it.id != sessionId }
        _sessions.value = remaining

        if (_currentSession.value?.id == sessionId) {
            _currentSession.value = remaining.firstOrNull() ?: createNewSession()
        }
    }

    private fun saveSession(session: ChatSession) {
        try {
            sessionsDir.mkdirs()
            val file = File(sessionsDir, "${session.id}.json")
            file.writeText(json.encodeToString(session))
        } catch (e: Exception) {
            // Ignore save errors
        }
    }
}
