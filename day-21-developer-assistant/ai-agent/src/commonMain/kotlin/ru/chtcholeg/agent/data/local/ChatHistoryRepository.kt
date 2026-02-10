package ru.chtcholeg.agent.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.chtcholeg.agent.domain.model.AgentMessage
import ru.chtcholeg.agent.domain.model.AgentSession
import ru.chtcholeg.agent.domain.model.MessageType
import ru.chtcholeg.agent.domain.model.SourceReference
import kotlin.random.Random

class ChatHistoryRepository(
    private val database: McpDatabase
) {
    private val messageQueries = database.chatMessageQueries
    private val sessionQueries = database.agentSessionQueries
    private val json = Json { ignoreUnknownKeys = true }

    // --- Session methods ---

    fun getSessions(): Flow<List<AgentSession>> = flow {
        val sessions = withContext(Dispatchers.IO) {
            sessionQueries.selectAll().executeAsList().map { entity ->
                val lastMsg = messageQueries.selectLastBySessionId(entity.id).executeAsOneOrNull()
                val count = messageQueries.countBySessionId(entity.id).executeAsOne()
                AgentSession(
                    id = entity.id,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    lastMessage = lastMsg?.let { mapEntityToMessage(it) },
                    messageCount = count.toInt()
                )
            }
        }
        emit(sessions)
    }

    suspend fun createSession(title: String): AgentSession = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "${now}_${Random.nextInt()}"
        sessionQueries.insert(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now
        )
        AgentSession(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        messageQueries.deleteBySessionId(sessionId)
        sessionQueries.deleteById(sessionId)
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.updateTitle(title = title, updatedAt = now, id = sessionId)
    }

    suspend fun updateSessionTimestamp(sessionId: String) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        sessionQueries.updateTimestamp(updatedAt = now, id = sessionId)
    }

    // --- Message methods (session-aware) ---

    suspend fun loadMessages(sessionId: String): List<AgentMessage> = withContext(Dispatchers.IO) {
        messageQueries.selectBySessionId(sessionId).executeAsList().map { mapEntityToMessage(it) }
    }

    suspend fun saveMessage(sessionId: String, message: AgentMessage) = withContext(Dispatchers.IO) {
        if (message.type == MessageType.SCREENSHOT) return@withContext
        messageQueries.insert(
            id = message.id,
            sessionId = sessionId,
            content = message.content,
            type = message.type.name,
            timestamp = message.timestamp,
            executionTimeMs = message.executionTimeMs,
            promptTokens = message.promptTokens?.toLong(),
            completionTokens = message.completionTokens?.toLong(),
            totalTokens = message.totalTokens?.toLong(),
            sourcesJson = message.sources?.let { serializeSources(it) }
        )
    }

    suspend fun saveMessages(sessionId: String, messages: List<AgentMessage>) = withContext(Dispatchers.IO) {
        database.transaction {
            messages.forEach { message ->
                if (message.type == MessageType.SCREENSHOT) return@forEach
                messageQueries.insert(
                    id = message.id,
                    sessionId = sessionId,
                    content = message.content,
                    type = message.type.name,
                    timestamp = message.timestamp,
                    executionTimeMs = message.executionTimeMs,
                    promptTokens = message.promptTokens?.toLong(),
                    completionTokens = message.completionTokens?.toLong(),
                    totalTokens = message.totalTokens?.toLong(),
                    sourcesJson = message.sources?.let { serializeSources(it) }
                )
            }
        }
    }

    suspend fun clearMessages(sessionId: String) = withContext(Dispatchers.IO) {
        messageQueries.deleteBySessionId(sessionId)
    }

    // --- Legacy methods (no session) for backward compatibility during migration ---

    suspend fun loadAllMessages(): List<AgentMessage> = withContext(Dispatchers.IO) {
        messageQueries.selectAll().executeAsList().map { mapEntityToMessage(it) }
    }

    suspend fun getLatestSessionId(): String? = withContext(Dispatchers.IO) {
        sessionQueries.selectAll().executeAsList().firstOrNull()?.id
    }

    // --- Private helpers ---

    private fun mapEntityToMessage(entity: ChatMessageEntity): AgentMessage {
        return AgentMessage(
            id = entity.id,
            content = entity.content,
            type = MessageType.valueOf(entity.type),
            timestamp = entity.timestamp,
            executionTimeMs = entity.executionTimeMs,
            promptTokens = entity.promptTokens?.toInt(),
            completionTokens = entity.completionTokens?.toInt(),
            totalTokens = entity.totalTokens?.toInt(),
            imageBase64 = null,
            sources = entity.sourcesJson?.let { deserializeSources(it) }
        )
    }

    private fun serializeSources(sources: Map<Int, SourceReference>): String {
        val list = sources.map { (key, ref) ->
            SourceEntry(
                key = key,
                filePath = ref.filePath,
                chunkIndex = ref.chunkIndex,
                totalChunks = ref.totalChunks,
                similarity = ref.similarity,
                isUrl = ref.isUrl,
                text = ref.text
            )
        }
        return json.encodeToString(list)
    }

    private fun deserializeSources(jsonStr: String): Map<Int, SourceReference>? {
        return try {
            val list = json.decodeFromString<List<SourceEntry>>(jsonStr)
            list.associate { entry ->
                entry.key to SourceReference(
                    filePath = entry.filePath,
                    chunkIndex = entry.chunkIndex,
                    totalChunks = entry.totalChunks,
                    similarity = entry.similarity,
                    isUrl = entry.isUrl,
                    text = entry.text
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    @kotlinx.serialization.Serializable
    private data class SourceEntry(
        val key: Int,
        val filePath: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val similarity: Float,
        val isUrl: Boolean = false,
        val text: String = ""
    )
}
