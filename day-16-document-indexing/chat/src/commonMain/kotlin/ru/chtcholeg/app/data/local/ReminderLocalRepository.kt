package ru.chtcholeg.app.data.local

import ru.chtcholeg.app.domain.model.ReminderConfig

interface ReminderLocalRepository {
    suspend fun saveOrUpdate(config: ReminderConfig)
    suspend fun getActive(): ReminderConfig?
    suspend fun getBySessionId(sessionId: String): ReminderConfig?
    suspend fun updateLastSeen(id: String, messageId: String, timestamp: Long)
    suspend fun disable(id: String)
    suspend fun delete(id: String)
}
