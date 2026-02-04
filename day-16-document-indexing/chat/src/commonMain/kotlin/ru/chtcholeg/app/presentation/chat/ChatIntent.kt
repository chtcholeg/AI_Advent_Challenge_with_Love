package ru.chtcholeg.app.presentation.chat

sealed interface ChatIntent {
    // Existing intents
    data class SendMessage(val text: String) : ChatIntent
    data object RetryLastMessage : ChatIntent
    data object ClearChat : ChatIntent
    data class CopyMessage(val messageId: String) : ChatIntent
    data object CopyAllMessages : ChatIntent
    data object SummarizeChat : ChatIntent
    data object SummarizeAndReplaceChat : ChatIntent

    // Session intents
    data object CreateNewSession : ChatIntent
    data class LoadSession(val sessionId: String) : ChatIntent
    data class UpdateSessionTitle(val title: String) : ChatIntent

    // Compression intents
    data object UndoCompression : ChatIntent
    data object ToggleCompressedHistory : ChatIntent
}
