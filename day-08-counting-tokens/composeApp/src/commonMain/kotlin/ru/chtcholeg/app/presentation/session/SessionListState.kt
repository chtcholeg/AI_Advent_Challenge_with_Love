package ru.chtcholeg.app.presentation.session

import ru.chtcholeg.app.domain.model.ChatSession

data class SessionListState(
    val activeSessions: List<ChatSession> = emptyList(),
    val archivedSessions: List<ChatSession> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ChatSession> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val showArchived: Boolean = false
)
