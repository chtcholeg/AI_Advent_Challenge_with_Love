package ru.chtcholeg.godagent.presentation.chat

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
import ru.chtcholeg.godagent.data.api.ApiMessage
import ru.chtcholeg.godagent.data.api.OllamaApi
import ru.chtcholeg.godagent.data.audio.AudioRecorder
import ru.chtcholeg.godagent.data.audio.VoskSpeechRecognitionService
import ru.chtcholeg.godagent.data.rag.DocumentIndexer
import ru.chtcholeg.godagent.data.repository.ChatRepository
import ru.chtcholeg.godagent.data.repository.SessionRepository
import ru.chtcholeg.godagent.data.repository.SettingsRepository
import ru.chtcholeg.godagent.data.tools.ToolExecutor
import ru.chtcholeg.godagent.domain.model.AgentStep
import ru.chtcholeg.godagent.domain.model.ChatMessage
import ru.chtcholeg.godagent.domain.model.ChatSession
import ru.chtcholeg.godagent.domain.model.MessageRole
import ru.chtcholeg.godagent.domain.model.ModelInfo

private enum class VoiceStopReason { MANUAL, SEND, CANCEL }

class ChatStore(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val ollamaApi: OllamaApi,
    private val toolExecutor: ToolExecutor,
    private val documentIndexer: DocumentIndexer,
    private val coroutineScope: CoroutineScope,
    private val audioRecorder: AudioRecorder,
    private val speechRecognitionService: VoskSpeechRecognitionService
) {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var lastUserMessage: String? = null
    private var agentJob: Job? = null
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
        toolExecutor.checkStatuses()
    }

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.RetryLastMessage -> lastUserMessage?.let { sendMessage(it) }
            is ChatIntent.StopAgent -> stopAgent()
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
        if (text.isBlank() || _state.value.isAgentRunning) return
        lastUserMessage = text

        agentJob = coroutineScope.launch {
            _state.update {
                it.copy(
                    isAgentRunning = true,
                    isLoading = true,
                    error = null,
                    agentSteps = emptyList(),
                    currentStatusMessage = ""
                )
            }

            val userMessage = ChatMessage(content = text, role = MessageRole.USER)
            val session = _state.value.currentSession ?: sessionRepository.createNewSession()
            val updatedSession = session.copy(messages = session.messages + userMessage)
            sessionRepository.updateCurrentSession(updatedSession)

            val settings = settingsRepository.settings.value
            var finalSession = updatedSession

            try {
                chatRepository.sendMessage(text, settings).collect { step ->
                    when (step) {
                        is AgentStep.ToolCall -> {
                            _state.update {
                                it.copy(
                                    agentSteps = it.agentSteps + step,
                                    currentStatusMessage = "Вызов ${step.toolName}..."
                                )
                            }
                        }
                        is AgentStep.ToolResult -> {
                            _state.update { it.copy(agentSteps = it.agentSteps + step) }
                        }
                        is AgentStep.StatusUpdate -> {
                            _state.update { it.copy(currentStatusMessage = step.message) }
                        }
                        is AgentStep.FinalAnswer -> {
                            val aiMsg = ChatMessage(content = step.content, role = MessageRole.ASSISTANT)
                            finalSession = updatedSession.copy(messages = updatedSession.messages + aiMsg)
                            sessionRepository.updateCurrentSession(finalSession)
                        }
                        is AgentStep.Error -> {
                            _state.update { it.copy(error = step.message) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Agent was stopped intentionally — do nothing
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Unknown error") }
            }

            _state.update {
                it.copy(
                    isAgentRunning = false,
                    isLoading = false,
                    currentStatusMessage = ""
                )
            }
        }
    }

    private fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
        _state.update {
            it.copy(
                isAgentRunning = false,
                isLoading = false,
                currentStatusMessage = ""
            )
        }
    }

    private fun newSession() {
        chatRepository.resetHistory()
        sessionRepository.createNewSession()
        lastUserMessage = null
        _state.update { it.copy(error = null, agentSteps = emptyList(), currentStatusMessage = "") }
    }

    private fun selectSession(sessionId: String) {
        sessionRepository.selectSession(sessionId)
        chatRepository.resetHistory()
        lastUserMessage = null
        _state.update { it.copy(error = null, agentSteps = emptyList(), currentStatusMessage = "") }
    }

    private fun deleteSession(sessionId: String) {
        sessionRepository.deleteSession(sessionId)
        chatRepository.resetHistory()
        lastUserMessage = null
    }

    private fun syncHistoryFromSession(session: ChatSession) {
        val apiMessages = session.messages
            .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
            .map { msg ->
                ApiMessage(
                    role = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content = msg.content
                )
            }
        chatRepository.loadHistory(apiMessages)
    }

    // -- Active voice recording -----------------------------------------------

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
                _state.update {
                    it.copy(isRecording = false, partialRecognizedText = "", sttError = e.message)
                }
                return@launch
            }

            // Flow completed — handle result based on what triggered the stop
            val rawFinal = speechRecognitionService.getFinalResult()
            when (voiceStopReason) {
                VoiceStopReason.SEND -> {
                    val trimmedText = rawFinal.stripWord(keywords.stopSendWord)
                    _state.update {
                        it.copy(
                            isRecording = false,
                            partialRecognizedText = "",
                            clearInputTrigger = it.clearInputTrigger + 1
                        )
                    }
                    if (trimmedText.isNotBlank()) sendMessage(trimmedText)
                }
                VoiceStopReason.CANCEL -> {
                    _state.update {
                        it.copy(
                            isRecording = false,
                            partialRecognizedText = "",
                            clearInputTrigger = it.clearInputTrigger + 1
                        )
                    }
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

    // -- Passive listening (wake-word mode) ------------------------------------

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

    // -- Helpers ---------------------------------------------------------------

    private fun String.stripWord(word: String): String {
        if (word.isBlank()) return this
        return replace(word.lowercase(), "", ignoreCase = true)
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    fun refreshOllamaModels() {
        coroutineScope.launch {
            try {
                val baseUrl = settingsRepository.settings.value.ollamaBaseUrl
                val models = ollamaApi.getAvailableModels(baseUrl)
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

    fun indexRagFolder(folderPath: String, onProgress: (String) -> Unit, onDone: (Int) -> Unit) {
        coroutineScope.launch {
            try {
                val count = documentIndexer.indexFolder(folderPath, onProgress)
                toolExecutor.checkStatuses()
                onDone(count)
            } catch (e: Exception) {
                onDone(-1)
            }
        }
    }
}
