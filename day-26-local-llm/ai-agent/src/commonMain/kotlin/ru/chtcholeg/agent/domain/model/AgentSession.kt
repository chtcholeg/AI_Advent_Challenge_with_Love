package ru.chtcholeg.agent.domain.model

data class AgentSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: AgentMessage? = null,
    val messageCount: Int = 0
)
