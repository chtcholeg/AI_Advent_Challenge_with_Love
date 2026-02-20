package ru.chtcholeg.agent.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class McpDatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = getDatabasePath()
        val databaseFile = File(databasePath)
        val databaseExists = databaseFile.exists()

        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")

        if (!databaseExists) {
            McpDatabase.Schema.create(driver)
        } else {
            // Migrate: add SettingsEntity table if missing
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS SettingsEntity (
                    key TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """.trimIndent(), 0)

            // Migrate: add ChatMessageEntity table if missing
            driver.execute(null, """
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
            """.trimIndent(), 0)
            driver.execute(null, """
                CREATE INDEX IF NOT EXISTS idx_chat_message_timestamp ON ChatMessageEntity(timestamp)
            """.trimIndent(), 0)

            // Migrate: add sessionId column to ChatMessageEntity if missing
            try {
                driver.execute(null, """
                    ALTER TABLE ChatMessageEntity ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''
                """.trimIndent(), 0)
            } catch (_: Exception) {
                // Column already exists
            }
            driver.execute(null, """
                CREATE INDEX IF NOT EXISTS idx_chat_message_session ON ChatMessageEntity(sessionId)
            """.trimIndent(), 0)

            // Migrate: add AgentSessionEntity table if missing
            driver.execute(null, """
                CREATE TABLE IF NOT EXISTS AgentSessionEntity (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent(), 0)
            driver.execute(null, """
                CREATE INDEX IF NOT EXISTS idx_agent_session_updated ON AgentSessionEntity(updatedAt)
            """.trimIndent(), 0)
        }

        return driver
    }

    private fun getDatabasePath(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-agent")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return File(appDir, "mcp.db").absolutePath
    }
}
