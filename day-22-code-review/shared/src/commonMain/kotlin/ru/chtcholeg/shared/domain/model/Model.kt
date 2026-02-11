package ru.chtcholeg.shared.domain.model

sealed interface Model {
    val api: Api
        get() = Api.HUGGINGFACE
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

    // Сентябрь 2023 - Mistral AI, первая открытая модель, превзошедшая Llama 2 13B
    data object Mistral7BInstruct : Model {
        override val id: String = "mistralai/Mistral-7B-Instruct-v0.1"
        override val displayName: String = "Mistral 7B Instruct"
    }

    // Июнь 2024 - Google, лёгкая модель с хорошим балансом размера и качества
    data object Gemma29BIt : Model {
        override val id: String = "google/gemma-2-9b-it"
        override val displayName: String = "Gemma 2 9B IT"
    }

    // Сентябрь 2024 - Meta, компактная модель для edge/mobile устройств
    data object Llama323BInstruct : Model {
        override val id: String = "meta-llama/Llama-3.2-3B-Instruct"
        override val displayName: String = "Llama 3.2 3B Instruct"
    }

    // Январь 2025 - DeepSeek, модель для сложных рассуждений
    data object DeepSeekR1 : Model {
        override val id: String = "deepseek-ai/DeepSeek-R1"
        override val displayName: String = "DeepSeek R1"
    }

    // Апрель 2025 - Alibaba Qwen, новейшая модель с поддержкой 119 языков
    data object Qwen3_8B : Model {
        override val id: String = "Qwen/Qwen3-8B"
        override val displayName: String = "Qwen3 8B"
    }

    enum class Api {
        GIGACHAT,
        HUGGINGFACE
    }

    companion object {
        val ALL_MODELS = listOf(
            // GigaChat (Sberbank)
            GigaChat,
            GigaChatPro,
            GigaChatMax,
            // Hugging Face (хронологический порядок)
            Mistral7BInstruct, // Сентябрь 2023 - самая старая
            Gemma29BIt,        // Июнь 2024
            Llama323BInstruct, // Сентябрь 2024
            DeepSeekR1,        // Январь 2025
            Qwen3_8B           // Апрель 2025 - самая новая
        )

        fun fromId(id: String): Model? = ALL_MODELS.firstOrNull { it.id == id }
    }
}
