package ru.chtcholeg.app.presentation.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.chtcholeg.app.data.local.ChatLocalRepository

class SessionListStore(
    private val chatLocalRepository: ChatLocalRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(SessionListState())
    val state: StateFlow<SessionListState> = _state.asStateFlow()

    init {
        // Observe active sessions
        coroutineScope.launch {
            chatLocalRepository.getActiveSessions().collect { sessions ->
                _state.update { it.copy(activeSessions = sessions, isLoading = false) }
            }
        }

        // Observe archived sessions
        coroutineScope.launch {
            chatLocalRepository.getArchivedSessions().collect { sessions ->
                _state.update { it.copy(archivedSessions = sessions) }
            }
        }
    }

    fun dispatch(intent: SessionListIntent) {
        when (intent) {
            is SessionListIntent.Search -> search(intent.query)
            is SessionListIntent.ClearSearch -> clearSearch()
            is SessionListIntent.ToggleShowArchived -> toggleShowArchived()
            is SessionListIntent.ArchiveSession -> archiveSession(intent.sessionId)
            is SessionListIntent.UnarchiveSession -> unarchiveSession(intent.sessionId)
            is SessionListIntent.DeleteSession -> deleteSession(intent.sessionId)
            is SessionListIntent.DeleteAllArchived -> deleteAllArchived()
            is SessionListIntent.RefreshSessions -> refresh()
        }
    }

    private fun search(query: String) {
        _state.update { it.copy(searchQuery = query, isSearching = true) }

        if (query.isBlank()) {
            clearSearch()
            return
        }

        coroutineScope.launch {
            val results = chatLocalRepository.searchSessions(query)
            _state.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    private fun clearSearch() {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    private fun toggleShowArchived() {
        _state.update { it.copy(showArchived = !it.showArchived) }
    }

    private fun archiveSession(sessionId: String) {
        coroutineScope.launch {
            chatLocalRepository.archiveSession(sessionId)
        }
    }

    private fun unarchiveSession(sessionId: String) {
        coroutineScope.launch {
            chatLocalRepository.unarchiveSession(sessionId)
        }
    }

    private fun deleteSession(sessionId: String) {
        coroutineScope.launch {
            chatLocalRepository.deleteSession(sessionId)
        }
    }

    private fun deleteAllArchived() {
        coroutineScope.launch {
            chatLocalRepository.deleteAllArchivedSessions()
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true) }
        // Flow will automatically update the state
    }
}
