package ru.chtcholeg.app.presentation.chat

import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.ChatSession
import ru.chtcholeg.app.domain.model.ModelInfo

data class ChatState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val ollamaModels: List<ModelInfo.OllamaModel> = emptyList(),
    val isOllamaAvailable: Boolean = false,
    val isRecording: Boolean = false,
    val isPassiveListening: Boolean = false,
    val isInitializingSTT: Boolean = false,
    val recognizedText: String? = null,
    val partialRecognizedText: String = "",
    val sttError: String? = null,
    val clearInputTrigger: Int = 0
) {
    val currentMessages: List<ChatMessage>
        get() = currentSession?.messages ?: emptyList()
}
