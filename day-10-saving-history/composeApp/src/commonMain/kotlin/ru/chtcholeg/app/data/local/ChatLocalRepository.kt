package ru.chtcholeg.app.data.local

import kotlinx.coroutines.flow.Flow
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.ChatSession

interface ChatLocalRepository {
    // Session operations
    fun getActiveSessions(): Flow<List<ChatSession>>
    fun getArchivedSessions(): Flow<List<ChatSession>>
    suspend fun getSessionById(id: String): ChatSession?
    suspend fun searchSessions(query: String): List<ChatSession>
    suspend fun createSession(title: String, modelName: String, systemPrompt: String? = null): ChatSession
    suspend fun updateSessionTitle(sessionId: String, title: String)
    suspend fun updateSessionTimestamp(sessionId: String)
    suspend fun archiveSession(sessionId: String)
    suspend fun unarchiveSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)
    suspend fun deleteAllArchivedSessions()

    // Message operations
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun saveMessage(sessionId: String, message: ChatMessage)
    suspend fun deleteMessage(messageId: String)
    suspend fun deleteAllMessagesInSession(sessionId: String)

    // Compression operations
    suspend fun getOriginalSession(originalSessionId: String): ChatSession?
    fun getMessagesBeforeCompression(sessionId: String, compressionPoint: Long): Flow<List<ChatMessage>>
    fun getMessagesAfterCompression(sessionId: String, compressionPoint: Long): Flow<List<ChatMessage>>
    suspend fun updateSessionCompressionInfo(
        sessionId: String,
        isCompressed: Boolean,
        originalSessionId: String?,
        compressionPoint: Long?
    )
}
