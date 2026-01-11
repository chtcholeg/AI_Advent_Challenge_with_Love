package ru.chtcholeg.app.presentation.chat

import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatStore(
    private val sendMessageUseCase: SendMessageUseCase,
    private val chatRepository: ChatRepository,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var lastUserMessage: String? = null

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.RetryLastMessage -> retryLastMessage()
            is ChatIntent.ClearChat -> clearChat()
        }
    }

    private fun sendMessage(text: String) {
        if (text.isBlank()) return

        lastUserMessage = text
        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Add user message to state immediately
                val userMessage = ChatMessage(
                    content = text,
                    isFromUser = true
                )
                _state.update { it.copy(messages = it.messages + userMessage) }

                // Get AI response
                val response = sendMessageUseCase(text)

                // Add AI response to state
                val aiMessage = ChatMessage(
                    content = response,
                    isFromUser = false
                )
                _state.update {
                    it.copy(
                        messages = it.messages + aiMessage,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    private fun retryLastMessage() {
        lastUserMessage?.let { message ->
            // Remove the last user message and any partial response
            val currentMessages = _state.value.messages
            val messagesToKeep = currentMessages.dropLast(1)
            _state.update { it.copy(messages = messagesToKeep, error = null) }

            // Resend the message
            sendMessage(message)
        }
    }

    private fun clearChat() {
        _state.update { ChatState() }
        chatRepository.clearHistory()
        lastUserMessage = null
    }
}
