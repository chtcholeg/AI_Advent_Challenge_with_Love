package com.example.localllmchat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localllmchat.data.Message
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

sealed class UiState {
    data object Loading : UiState()
    data object ModelNotFound : UiState()
    data object Ready : UiState()
    data object Generating : UiState()
    data class Error(val message: String) : UiState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var llmInference: LlmInference? = null
    private var onPartialResult: ((String, Boolean) -> Unit)? = null

    companion object {
        // Push model with ADB:
        // adb push model.bin /sdcard/Android/data/com.example.localllmchat/files/model.bin
        const val MODEL_FILENAME = "model.bin"
        const val MAX_TOKENS = 1024
    }

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelFile = File(
                getApplication<Application>().getExternalFilesDir(null),
                MODEL_FILENAME
            )

            if (!modelFile.exists()) {
                _uiState.value = UiState.ModelNotFound
                return@launch
            }

            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(40)
                    .setTemperature(0.8f)
                    .setRandomSeed(101)
                    .setResultListener { partialResult, done ->
                        onPartialResult?.invoke(partialResult ?: "", done)
                    }
                    .build()

                llmInference = LlmInference.createFromOptions(getApplication(), options)
                _uiState.value = UiState.Ready
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load model")
            }
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _uiState.value != UiState.Ready) return

        // Block further input immediately
        _uiState.value = UiState.Generating

        // Add user message and build prompt before adding the model placeholder
        val userMsg = Message(text = userInput, isUser = true)
        _messages.update { it + userMsg }

        val prompt = buildGemmaPrompt(_messages.value)

        // Add empty model message — will be filled token by token
        val modelMsgPlaceholder = Message(text = "", isUser = false)
        _messages.update { it + modelMsgPlaceholder }
        val modelIndex = _messages.value.lastIndex

        viewModelScope.launch {
            try {
                var accumulated = ""

                suspendCancellableCoroutine { cont ->
                    onPartialResult = { partialResult, done ->
                        if (partialResult.isNotEmpty()) {
                            accumulated += partialResult
                            _messages.update { list ->
                                list.toMutableList().also {
                                    it[modelIndex] = modelMsgPlaceholder.copy(text = accumulated)
                                }
                            }
                        }
                        if (done && cont.isActive) {
                            onPartialResult = null
                            cont.resume(Unit)
                        }
                    }
                    if (llmInference != null) {
                        llmInference!!.generateResponseAsync(prompt)
                    } else {
                        onPartialResult = null
                        cont.resume(Unit)
                    }
                }
            } catch (e: Exception) {
                _messages.update { list ->
                    list.toMutableList().also {
                        it[modelIndex] = modelMsgPlaceholder.copy(text = "Error: ${e.message}")
                    }
                }
            } finally {
                _uiState.value = UiState.Ready
            }
        }
    }

    /**
     * Builds the Gemma 2 instruction-tuned prompt format.
     * https://ai.google.dev/gemma/docs/formatting
     */
    private fun buildGemmaPrompt(messages: List<Message>): String {
        val sb = StringBuilder()
        for (msg in messages) {
            if (msg.isUser) {
                sb.append("<start_of_turn>user\n${msg.text}<end_of_turn>\n")
            } else if (msg.text.isNotEmpty()) {
                sb.append("<start_of_turn>model\n${msg.text}<end_of_turn>\n")
            }
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        llmInference?.close()
    }
}
