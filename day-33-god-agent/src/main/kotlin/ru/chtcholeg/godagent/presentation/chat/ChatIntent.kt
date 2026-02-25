package ru.chtcholeg.godagent.presentation.chat

sealed interface ChatIntent {
    data class SendMessage(val text: String) : ChatIntent
    data object RetryLastMessage : ChatIntent
    data object StopAgent : ChatIntent
    data object NewSession : ChatIntent
    data class SelectSession(val sessionId: String) : ChatIntent
    data class DeleteSession(val sessionId: String) : ChatIntent
    data object RefreshOllamaModels : ChatIntent
    data object StartVoiceRecording : ChatIntent
    data object StopVoiceRecording : ChatIntent
    data object ConsumeRecognizedText : ChatIntent
    data object StartPassiveListening : ChatIntent
    data object StopPassiveListening : ChatIntent
}
