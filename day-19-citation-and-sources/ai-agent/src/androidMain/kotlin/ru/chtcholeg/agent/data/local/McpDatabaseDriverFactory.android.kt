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
                }
            }
        )
    }
}
