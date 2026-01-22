package ru.chtcholeg.app.presentation.chat

import ru.chtcholeg.app.domain.model.ChatMessage

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentModelName: String = "",

    // Session management
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "New Chat",
    val hasUnsavedChanges: Boolean = false
)
