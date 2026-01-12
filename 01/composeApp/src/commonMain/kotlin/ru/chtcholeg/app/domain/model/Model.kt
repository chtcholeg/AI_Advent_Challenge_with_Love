package ru.chtcholeg.app.domain.model

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

    data object Llama323BInstruct : Model {
        override val id: String = "meta-llama/Llama-3.2-3B-Instruct"
        override val displayName: String = "Llama 3.2 3B Instruct"
    }

    data object MetaLlama370BInstruct : Model {
        override val id: String = "meta-llama/Meta-Llama-3-70B-Instruct"
        override val displayName: String = "Meta Llama 3 70B Instruct"
    }

    data object DeepSeekV3 : Model {
        override val id: String = "deepseek-ai/DeepSeek-V3"
        override val displayName: String = "DeepSeek V3"
    }

    enum class Api {
        GIGACHAT,
        HUGGINGFACE
    }

    companion object {
        val ALL_MODELS = listOf(
            GigaChat,
            Llama323BInstruct,
            MetaLlama370BInstruct,
            DeepSeekV3
        )

        fun fromId(id: String): Model? = ALL_MODELS.firstOrNull { it.id == id }
    }
}
