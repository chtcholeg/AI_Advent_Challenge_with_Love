package ru.chtcholeg.app.presentation.chat

import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.ReminderConfig

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentModelName: String = "",

    // Session management
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "New Chat",
    val hasUnsavedChanges: Boolean = false,

    // Compression state
    val compressionPoint: Long? = null,
    val originalSessionId: String? = null,
    val compressedMessagesCount: Int = 0,
    val showCompressedHistory: Boolean = false,

    // Reminder state
    val activeReminder: ReminderConfig? = null
)
