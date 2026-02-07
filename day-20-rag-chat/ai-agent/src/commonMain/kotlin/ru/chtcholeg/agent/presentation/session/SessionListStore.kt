package ru.chtcholeg.agent.presentation.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.chtcholeg.agent.data.local.ChatHistoryRepository
import ru.chtcholeg.agent.domain.model.AgentSession

data class SessionListState(
    val sessions: List<AgentSession> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface SessionListIntent {
    data class DeleteSession(val id: String) : SessionListIntent
    data class RenameSession(val id: String, val title: String) : SessionListIntent
    data object Refresh : SessionListIntent
}

class SessionListStore(
    private val chatHistoryRepository: ChatHistoryRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(SessionListState())
    val state: StateFlow<SessionListState> = _state.asStateFlow()

    fun dispatch(intent: SessionListIntent) {
        when (intent) {
            is SessionListIntent.DeleteSession -> deleteSession(intent.id)
            is SessionListIntent.RenameSession -> renameSession(intent.id, intent.title)
            is SessionListIntent.Refresh -> loadSessions()
        }
    }

    fun loadSessions() {
        _state.update { it.copy(isLoading = true) }
        coroutineScope.launch {
            try {
                chatHistoryRepository.getSessions().collect { sessions ->
                    _state.update { it.copy(sessions = sessions, isLoading = false) }
                }
            } catch (e: Exception) {
                println("[SessionListStore] Failed to load sessions: ${e.message}")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        coroutineScope.launch {
            try {
                chatHistoryRepository.deleteSession(sessionId)
                _state.update { current ->
                    current.copy(sessions = current.sessions.filter { it.id != sessionId })
                }
            } catch (e: Exception) {
                println("[SessionListStore] Failed to delete session: ${e.message}")
            }
        }
    }

    private fun renameSession(sessionId: String, title: String) {
        coroutineScope.launch {
            try {
                chatHistoryRepository.updateSessionTitle(sessionId, title)
                _state.update { current ->
                    current.copy(sessions = current.sessions.map {
                        if (it.id == sessionId) it.copy(title = title) else it
                    })
                }
            } catch (e: Exception) {
                println("[SessionListStore] Failed to rename session: ${e.message}")
            }
        }
    }
}
