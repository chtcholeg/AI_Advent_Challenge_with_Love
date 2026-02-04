package ru.chtcholeg.app.presentation.session

sealed interface SessionListIntent {
    data class Search(val query: String) : SessionListIntent
    data object ClearSearch : SessionListIntent
    data object ToggleShowArchived : SessionListIntent
    data class ArchiveSession(val sessionId: String) : SessionListIntent
    data class UnarchiveSession(val sessionId: String) : SessionListIntent
    data class DeleteSession(val sessionId: String) : SessionListIntent
    data object DeleteAllArchived : SessionListIntent
    data object RefreshSessions : SessionListIntent
}
