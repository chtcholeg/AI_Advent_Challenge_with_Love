package ru.chtcholeg.godagent.domain.model

sealed class ModelInfo {
    abstract val id: String
    abstract val displayName: String
    abstract val provider: String

    data class GigaChatModel(
        override val id: String,
        override val displayName: String
    ) : ModelInfo() {
        override val provider = "GigaChat"
    }

    data class OllamaModel(override val id: String) : ModelInfo() {
        override val displayName = id
        override val provider = "Ollama (local)"
    }

    companion object {
        val GIGACHAT_MODELS: List<GigaChatModel> = listOf(
            GigaChatModel("GigaChat", "GigaChat"),
            GigaChatModel("GigaChat-Pro", "GigaChat Pro"),
            GigaChatModel("GigaChat-Max", "GigaChat Max")
        )
    }
}
