package ru.chtcholeg.app.data.local

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
            name = "mcp.db"
        )
    }
}
