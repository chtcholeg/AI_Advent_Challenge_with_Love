package ru.chtcholeg.shared.domain.model

sealed interface Model {
    val api: Api
    val id: String
    val displayName: String

    data object GigaChat : Model {
        override val id: String = "GigaChat"
        override val displayName: String = "GigaChat"
        override val api: Api = Api.GIGACHAT
    }

    data object GigaChatPro : Model {
        override val id: String = "GigaChat-Pro"
        override val displayName: String = "GigaChat Pro"
        override val api: Api = Api.GIGACHAT
    }

    data object GigaChatMax : Model {
        override val id: String = "GigaChat-Max"
        override val displayName: String = "GigaChat Max"
        override val api: Api = Api.GIGACHAT
    }

    // Локальные модели через Ollama (localhost:11434)
    data object OllamaQwen2_5_0_5B : Model {
        override val id: String = "qwen2.5:0.5b"
        override val displayName: String = "Qwen2.5 0.5B (Local)"
        override val api: Api = Api.OLLAMA
    }

    data object OllamaQwen2_5_32B : Model {
        override val id: String = "qwen2.5:32b"
        override val displayName: String = "Qwen2.5 32B (Local)"
        override val api: Api = Api.OLLAMA
    }

    enum class Api {
        GIGACHAT,
        OLLAMA
    }

    companion object {
        val ALL_MODELS = listOf(
            // GigaChat (Sberbank)
            GigaChat,
            GigaChatPro,
            GigaChatMax,
            // Ollama (локальные модели)
            OllamaQwen2_5_0_5B,
            OllamaQwen2_5_32B
        )

        fun fromId(id: String): Model? = ALL_MODELS.firstOrNull { it.id == id }
    }
}
