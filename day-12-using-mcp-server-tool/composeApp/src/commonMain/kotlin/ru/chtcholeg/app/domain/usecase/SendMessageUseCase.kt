package ru.chtcholeg.app.domain.usecase

import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.domain.model.AiResponse

class SendMessageUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(message: String): AiResponse {
        require(message.isNotBlank()) { "Message cannot be empty" }
        return repository.sendMessage(message.trim())
    }
}
