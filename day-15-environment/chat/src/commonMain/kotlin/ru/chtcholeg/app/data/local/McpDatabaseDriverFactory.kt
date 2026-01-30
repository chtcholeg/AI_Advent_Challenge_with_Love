package ru.chtcholeg.app.data.local

import app.cash.sqldelight.db.SqlDriver

expect class McpDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
