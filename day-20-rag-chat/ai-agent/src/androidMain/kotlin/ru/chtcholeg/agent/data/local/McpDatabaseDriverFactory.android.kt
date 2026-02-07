package ru.chtcholeg.agent.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class McpDatabaseDriverFactory(
    private val context: Context
) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = McpDatabase.Schema,
            context = context,
            name = "mcp.db",
            callback = object : AndroidSqliteDriver.Callback(McpDatabase.Schema) {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Migrate: add SettingsEntity table if missing
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS SettingsEntity (
                            key TEXT NOT NULL PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                    """.trimIndent())
                    // Migrate: add ChatMessageEntity table if missing
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS ChatMessageEntity (
                            id TEXT NOT NULL PRIMARY KEY,
                            sessionId TEXT NOT NULL DEFAULT '',
                            content TEXT NOT NULL,
                            type TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            executionTimeMs INTEGER,
                            promptTokens INTEGER,
                            completionTokens INTEGER,
                            totalTokens INTEGER,
                            sourcesJson TEXT
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS idx_chat_message_timestamp ON ChatMessageEntity(timestamp)
                    """.trimIndent())

                    // Migrate: add sessionId column to ChatMessageEntity if missing
                    try {
                        db.execSQL("""
                            ALTER TABLE ChatMessageEntity ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''
                        """.trimIndent())
                    } catch (_: Exception) {
                        // Column already exists
                    }
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS idx_chat_message_session ON ChatMessageEntity(sessionId)
                    """.trimIndent())

                    // Migrate: add AgentSessionEntity table if missing
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS AgentSessionEntity (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS idx_agent_session_updated ON AgentSessionEntity(updatedAt)
                    """.trimIndent())
                }
            }
        )
    }
}
