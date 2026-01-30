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

        // Create tables only if the database is new
        if (!databaseExists) {
            McpDatabase.Schema.create(driver)
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
