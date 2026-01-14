package ru.chtcholeg.app.domain.usecase

import ru.chtcholeg.app.data.repository.ChatRepository

class SendMessageUseCase(
    private val repository: ChatRepository
) {
    suspend operator fun invoke(message: String): String {
        require(message.isNotBlank()) { "Message cannot be empty" }
        return repository.sendMessage(message.trim())
    }
}
