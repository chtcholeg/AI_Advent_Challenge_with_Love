package ru.chtcholeg.app.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import ru.chtcholeg.app.domain.model.ChatMessage
import ru.chtcholeg.app.domain.model.ChatSession
import ru.chtcholeg.app.domain.model.MessageType

class ChatLocalRepositoryImpl(
    private val database: ChatDatabase
) : ChatLocalRepository {

    private val sessionQueries = database.chatSessionQueries
    private val messageQueries = database.chatMessageQueries

    // =============== SESSION OPERATIONS ===============

    override fun getActiveSessions(): Flow<List<ChatSession>> {
        return sessionQueries.selectAllActive()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getArchivedSessions(): Flow<List<ChatSession>> {
        return sessionQueries.selectAllArchived()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getSessionById(id: String): ChatSession? = withContext(Dispatchers.IO) {
        sessionQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun searchSessions(query: String): List<ChatSession> = withContext(Dispatchers.IO) {
        val byTitle = sessionQueries.searchByTitle(query).executeAsList()
        val byContent = messageQueries.searchByContent(query).executeAsList()

        (byTitle + byContent)
            .distinctBy { it.id }
            .map { it.toDomain() }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun createSession(title: String, modelName: String, systemPrompt: String?): ChatSession = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val session = ChatSession(
            title = title,
            createdAt = now,
            updatedAt = now,
            modelName = modelName,
            systemPrompt = systemPrompt
        )

        sessionQueries.insert(
            id = session.id,
            title = session.title,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            modelName = session.modelName,
            systemPrompt = session.systemPrompt,
            isArchived = if (session.isArchived) 1L else 0L,
            isCompressed = if (session.isCompressed) 1L else 0L,
            originalSessionId = session.originalSessionId,
            compressionPoint = session.compressionPoint
        )

        session
    }

    override suspend fun updateSessionTitle(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.updateTitle(title, now, sessionId)
    }

    override suspend fun updateSessionTimestamp(sessionId: String) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.updateTimestamp(now, sessionId)
    }

    override suspend fun archiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.archive(now, sessionId)
    }

    override suspend fun unarchiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.unarchive(now, sessionId)
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        // Messages will be deleted automatically due to ON DELETE CASCADE
        sessionQueries.deleteById(sessionId)
    }

    override suspend fun deleteAllArchivedSessions() = withContext(Dispatchers.IO) {
        sessionQueries.deleteAllArchived()
    }

    // =============== MESSAGE OPERATIONS ===============

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return messageQueries.selectBySessionId(sessionId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun saveMessage(sessionId: String, message: ChatMessage) = withContext(Dispatchers.IO) {
        messageQueries.insert(
            id = message.id,
            sessionId = sessionId,
            content = message.content,
            isFromUser = if (message.isFromUser) 1L else 0L,
            timestamp = message.timestamp,
            messageType = message.messageType.name,
            executionTimeMs = message.executionTimeMs,
            promptTokens = message.promptTokens?.toLong(),
            completionTokens = message.completionTokens?.toLong(),
            totalTokens = message.totalTokens?.toLong(),
            isSummary = if (message.isSummary) 1L else 0L,
            compressedMessageCount = message.compressedMessageCount?.toLong()
        )

        // Update session timestamp
        updateSessionTimestamp(sessionId)
    }

    override suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageQueries.deleteById(messageId)
    }

    override suspend fun deleteAllMessagesInSession(sessionId: String) = withContext(Dispatchers.IO) {
        messageQueries.deleteBySessionId(sessionId)
    }

    // =============== MAPPERS ===============

    private fun ChatSessionEntity.toDomain(): ChatSession {
        val lastMsg = messageQueries.selectLastBySessionId(id).executeAsOneOrNull()?.toDomain()
        val count = messageQueries.countBySessionId(id).executeAsOne().toInt()

        return ChatSession(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            modelName = modelName,
            systemPrompt = systemPrompt,
            isArchived = isArchived == 1L,
            lastMessage = lastMsg,
            messageCount = count,
            isCompressed = isCompressed == 1L,
            originalSessionId = originalSessionId,
            compressionPoint = compressionPoint
        )
    }

    private fun ChatMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            content = content,
            isFromUser = isFromUser == 1L,
            timestamp = timestamp,
            messageType = MessageType.valueOf(messageType),
            executionTimeMs = executionTimeMs,
            promptTokens = promptTokens?.toInt(),
            completionTokens = completionTokens?.toInt(),
            totalTokens = totalTokens?.toInt(),
            isSummary = isSummary == 1L,
            compressedMessageCount = compressedMessageCount?.toInt()
        )
    }

    // =============== COMPRESSION OPERATIONS ===============

    override suspend fun getOriginalSession(originalSessionId: String): ChatSession? = withContext(Dispatchers.IO) {
        sessionQueries.selectOriginalSession(originalSessionId).executeAsOneOrNull()?.toDomain()
    }

    override fun getMessagesBeforeCompression(sessionId: String, compressionPoint: Long): Flow<List<ChatMessage>> {
        return messageQueries.selectBeforeCompressionPoint(sessionId, compressionPoint)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getMessagesAfterCompression(sessionId: String, compressionPoint: Long): Flow<List<ChatMessage>> {
        return messageQueries.selectAfterCompressionPoint(sessionId, compressionPoint)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun updateSessionCompressionInfo(
        sessionId: String,
        isCompressed: Boolean,
        originalSessionId: String?,
        compressionPoint: Long?
    ) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.updateCompressionInfo(
            isCompressed = if (isCompressed) 1L else 0L,
            originalSessionId = originalSessionId,
            compressionPoint = compressionPoint,
            updatedAt = now,
            id = sessionId
        )
    }
}
