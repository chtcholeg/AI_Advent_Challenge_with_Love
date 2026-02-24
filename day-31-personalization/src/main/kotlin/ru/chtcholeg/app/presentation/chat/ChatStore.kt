package ru.chtcholeg.app.presentation.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.chtcholeg.app.data.api.ApiMessage
import ru.chtcholeg.app.data.api.OllamaApi
import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.data.repository.SessionRepository
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.ModelInfo

class ChatStore(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val ollamaApi: OllamaApi,
    private val coroutineScope: CoroutineScope
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var lastUserMessage: String? = null

    init {
        coroutineScope.launch {
            sessionRepository.sessions.collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
        coroutineScope.launch {
            sessionRepository.currentSession.collect { session ->
                _state.update { it.copy(currentSession = session) }
                if (session != null) {
                    syncHistoryFromSession(session)
                }
            }
        }
        refreshOllamaModels()
    }

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.RetryLastMessage -> lastUserMessage?.let { sendMessage(it) }
            is ChatIntent.NewSession -> newSession()
            is ChatIntent.SelectSession -> selectSession(intent.sessionId)
            is ChatIntent.DeleteSession -> deleteSession(intent.sessionId)
            is ChatIntent.RefreshOllamaModels -> refreshOllamaModels()
        }
    }

    private fun sendMessage(text: String) {
        if (text.isBlank() || _state.value.isLoading) return
        lastUserMessage = text

        coroutineScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val userMessage = ChatMessage(content = text, isFromUser = true)
            val session = _state.value.currentSession ?: sessionRepository.createNewSession()
            val updatedSession = session.copy(messages = session.messages + userMessage)
            sessionRepository.updateCurrentSession(updatedSession)

            try {
                val settings = settingsRepository.settings.value
                val response = chatRepository.sendMessage(text, settings)

                val aiMessage = ChatMessage(
                    content = response.content,
                    isFromUser = false,
                    executionTimeMs = response.executionTimeMs
                )
                val finalSession = updatedSession.copy(messages = updatedSession.messages + aiMessage)
                sessionRepository.updateCurrentSession(finalSession)

                _state.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun newSession() {
        chatRepository.resetHistory()
        sessionRepository.createNewSession()
        lastUserMessage = null
        _state.update { it.copy(error = null) }
    }

    private fun selectSession(sessionId: String) {
        sessionRepository.selectSession(sessionId)
        chatRepository.resetHistory()
        lastUserMessage = null
        _state.update { it.copy(error = null) }
    }

    private fun deleteSession(sessionId: String) {
        sessionRepository.deleteSession(sessionId)
        chatRepository.resetHistory()
        lastUserMessage = null
    }

    private fun syncHistoryFromSession(session: ru.chtcholeg.app.domain.model.ChatSession) {
        val apiMessages = session.messages.map { msg ->
            ApiMessage(
                role = if (msg.isFromUser) "user" else "assistant",
                content = msg.content
            )
        }
        chatRepository.loadHistory(apiMessages)
    }

    fun refreshOllamaModels() {
        coroutineScope.launch {
            try {
                val models = ollamaApi.getAvailableModels()
                _state.update {
                    it.copy(
                        ollamaModels = models.map { name -> ModelInfo.OllamaModel(name) },
                        isOllamaAvailable = models.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(ollamaModels = emptyList(), isOllamaAvailable = false) }
            }
        }
    }
}
