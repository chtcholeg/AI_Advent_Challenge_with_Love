package ru.chtcholeg.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.chtcholeg.app.domain.model.ReminderConfig
import ru.chtcholeg.app.domain.model.ReminderInterval

class ReminderLocalRepositoryImpl(
    private val database: ChatDatabase
) : ReminderLocalRepository {

    private val queries = database.reminderQueries

    override suspend fun saveOrUpdate(config: ReminderConfig) = withContext(Dispatchers.IO) {
        queries.upsert(
            id = config.id,
            channel = config.channel,
            intervalSeconds = config.interval.seconds.toLong(),
            messageCount = config.messageCount.toLong(),
            enabled = if (config.enabled) 1L else 0L,
            sessionId = config.sessionId,
            createdAt = config.createdAt,
            lastTriggeredAt = config.lastTriggeredAt,
            lastSeenMessageId = config.lastSeenMessageId
        )
    }

    override suspend fun getActive(): ReminderConfig? = withContext(Dispatchers.IO) {
        queries.selectActive().executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getBySessionId(sessionId: String): ReminderConfig? = withContext(Dispatchers.IO) {
        queries.selectBySessionId(sessionId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun updateLastSeen(id: String, messageId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        queries.updateLastSeen(messageId, timestamp, id)
    }

    override suspend fun disable(id: String) = withContext(Dispatchers.IO) {
        queries.disable(id)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.deleteById(id)
    }

    private fun ReminderEntity.toDomain(): ReminderConfig = ReminderConfig(
        id = id,
        channel = channel,
        interval = ReminderInterval.fromSeconds(intervalSeconds.toInt()),
        messageCount = messageCount.toInt(),
        enabled = enabled == 1L,
        sessionId = sessionId,
        lastSeenMessageId = lastSeenMessageId,
        lastTriggeredAt = lastTriggeredAt,
        createdAt = createdAt
    )
}
