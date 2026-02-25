package ru.chtcholeg.godagent.presentation.chat

import ru.chtcholeg.godagent.domain.model.AgentStep
import ru.chtcholeg.godagent.domain.model.ChatMessage
import ru.chtcholeg.godagent.domain.model.ChatSession
import ru.chtcholeg.godagent.domain.model.ModelInfo

data class ChatState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val isLoading: Boolean = false,
    val isAgentRunning: Boolean = false,
    val error: String? = null,
    val ollamaModels: List<ModelInfo.OllamaModel> = emptyList(),
    val isOllamaAvailable: Boolean = false,
    // Thinking block
    val agentSteps: List<AgentStep> = emptyList(),
    val currentStatusMessage: String = "",
    // Voice
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
