package ru.chtcholeg.app.presentation.chat

sealed interface ChatIntent {
    data class SendMessage(val text: String) : ChatIntent
    data object RetryLastMessage : ChatIntent
    data object ClearChat : ChatIntent
    data class CopyMessage(val messageId: String) : ChatIntent
    data object CopyAllMessages : ChatIntent
    data object SummarizeChat : ChatIntent
}
