package ru.chtcholeg.app.presentation.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.chtcholeg.app.data.api.ApiMessage
import ru.chtcholeg.app.data.api.OllamaApi
import ru.chtcholeg.app.data.audio.AudioRecorder
import ru.chtcholeg.app.data.audio.VoskSpeechRecognitionService
import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.data.repository.SessionRepository
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.ModelInfo

private enum class VoiceStopReason { MANUAL, SEND, CANCEL }

class ChatStore(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val ollamaApi: OllamaApi,
    private val coroutineScope: CoroutineScope,
    private val audioRecorder: AudioRecorder,
    private val speechRecognitionService: VoskSpeechRecognitionService
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var lastUserMessage: String? = null
    private var recordingJob: Job? = null
    private var passiveListeningJob: Job? = null

    // Written by both UI thread (stopVoiceRecording) and IO coroutine (keyword detection)
    @Volatile private var voiceStopReason = VoiceStopReason.MANUAL

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
        // Auto-start/stop passive listening when voice keywords enabled state changes
        coroutineScope.launch {
            settingsRepository.settings
                .map { it.voiceKeywords.enabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        val s = _state.value
                        if (!s.isPassiveListening && !s.isRecording && !s.isInitializingSTT) {
                            startPassiveListening()
                        }
                    } else {
                        stopPassiveListening()
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
            is ChatIntent.StartVoiceRecording -> startVoiceRecording()
            is ChatIntent.StopVoiceRecording -> stopVoiceRecording()
            is ChatIntent.ConsumeRecognizedText -> _state.update { it.copy(recognizedText = null) }
            is ChatIntent.StartPassiveListening -> startPassiveListening()
            is ChatIntent.StopPassiveListening -> stopPassiveListening()
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

    // ── Active voice recording ────────────────────────────────────────────────

    private fun startVoiceRecording() {
        if (_state.value.isRecording || _state.value.isInitializingSTT) return

        voiceStopReason = VoiceStopReason.MANUAL
        recordingJob = coroutineScope.launch {
            _state.update { it.copy(sttError = null) }

            if (!speechRecognitionService.isReady) {
                _state.update { it.copy(isInitializingSTT = true) }
                try {
                    speechRecognitionService.initialize()
                } catch (e: Exception) {
                    _state.update {
                        it.copy(isInitializingSTT = false, sttError = "Ошибка инициализации STT: ${e.message}")
                    }
                    return@launch
                }
                _state.update { it.copy(isInitializingSTT = false) }
            }

            speechRecognitionService.reset()
            _state.update { it.copy(isRecording = true) }

            val keywords = settingsRepository.settings.value.voiceKeywords

            try {
                audioRecorder.startRecording().collect { chunk ->
                    val partial = speechRecognitionService.acceptAudioChunk(chunk) ?: return@collect
                    val lower = partial.lowercase()

                    if (keywords.enabled) {
                        when {
                            keywords.stopSendWord.isNotBlank() &&
                                    lower.contains(keywords.stopSendWord.lowercase()) -> {
                                voiceStopReason = VoiceStopReason.SEND
                                audioRecorder.stopRecording()
                            }
                            keywords.stopCancelWord.isNotBlank() &&
                                    lower.contains(keywords.stopCancelWord.lowercase()) -> {
                                voiceStopReason = VoiceStopReason.CANCEL
                                audioRecorder.stopRecording()
                            }
                            else -> _state.update { it.copy(partialRecognizedText = partial) }
                        }
                    } else {
                        _state.update { it.copy(partialRecognizedText = partial) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isRecording = false, partialRecognizedText = "", sttError = e.message) }
                return@launch
            }

            // Flow completed — handle result based on what triggered the stop
            val rawFinal = speechRecognitionService.getFinalResult()
            when (voiceStopReason) {
                VoiceStopReason.SEND -> {
                    val text = rawFinal.stripWord(keywords.stopSendWord)
                    _state.update { it.copy(isRecording = false, partialRecognizedText = "", clearInputTrigger = it.clearInputTrigger + 1) }
                    if (text.isNotBlank()) sendMessage(text)
                }
                VoiceStopReason.CANCEL -> {
                    _state.update { it.copy(isRecording = false, partialRecognizedText = "", clearInputTrigger = it.clearInputTrigger + 1) }
                }
                VoiceStopReason.MANUAL -> {
                    _state.update {
                        it.copy(
                            isRecording = false,
                            partialRecognizedText = "",
                            recognizedText = rawFinal.takeIf { t -> t.isNotBlank() }
                        )
                    }
                }
            }
        }
    }

    private fun stopVoiceRecording() {
        voiceStopReason = VoiceStopReason.MANUAL
        audioRecorder.stopRecording()
        // State update (isRecording = false, recognizedText) happens inside recordingJob
        // after the flow completes naturally. Update immediately for responsive UI.
        _state.update { it.copy(isRecording = false) }
    }

    // ── Passive listening (wake-word mode) ────────────────────────────────────

    private fun startPassiveListening() {
        if (_state.value.isPassiveListening || _state.value.isRecording || _state.value.isInitializingSTT) return

        passiveListeningJob = coroutineScope.launch {
            _state.update { it.copy(sttError = null) }

            if (!speechRecognitionService.isReady) {
                _state.update { it.copy(isInitializingSTT = true) }
                try {
                    speechRecognitionService.initialize()
                } catch (e: Exception) {
                    _state.update {
                        it.copy(isInitializingSTT = false, sttError = "Ошибка инициализации STT: ${e.message}")
                    }
                    return@launch
                }
                _state.update { it.copy(isInitializingSTT = false) }
            }

            speechRecognitionService.reset()
            _state.update { it.copy(isPassiveListening = true) }

            val wakeWord = settingsRepository.settings.value.voiceKeywords.wakeWord.trim().lowercase()
            var wakeDetected = false

            try {
                audioRecorder.startRecording().collect { chunk ->
                    val partial = speechRecognitionService.acceptAudioChunk(chunk) ?: return@collect
                    if (wakeWord.isNotBlank() && partial.lowercase().contains(wakeWord)) {
                        wakeDetected = true
                        audioRecorder.stopRecording()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isPassiveListening = false, sttError = e.message) }
                return@launch
            }

            _state.update { it.copy(isPassiveListening = false) }
            passiveListeningJob = null

            if (wakeDetected) {
                speechRecognitionService.reset()
                startVoiceRecording()
                recordingJob?.join()
                // Auto-restart passive listening loop after recording completes
                if (settingsRepository.settings.value.voiceKeywords.enabled) {
                    startPassiveListening()
                }
            }
        }
    }

    private fun stopPassiveListening() {
        audioRecorder.stopRecording()
        passiveListeningJob?.cancel()
        passiveListeningJob = null
        _state.update { it.copy(isPassiveListening = false) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.stripWord(word: String): String {
        if (word.isBlank()) return this
        return replace(word.lowercase(), "", ignoreCase = true)
            .replace(Regex("\\s{2,}"), " ")
            .trim()
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
