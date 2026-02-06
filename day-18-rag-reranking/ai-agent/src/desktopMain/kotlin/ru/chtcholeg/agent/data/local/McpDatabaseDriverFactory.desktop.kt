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
