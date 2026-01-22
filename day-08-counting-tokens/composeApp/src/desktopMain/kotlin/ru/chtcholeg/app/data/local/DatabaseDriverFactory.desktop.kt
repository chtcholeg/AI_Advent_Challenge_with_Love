package ru.chtcholeg.app.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = getDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")

        // Create tables if they don't exist
        ChatDatabase.Schema.create(driver)

        return driver
    }

    private fun getDatabasePath(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".ai-chat")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return File(appDir, "chat.db").absolutePath
    }
}
