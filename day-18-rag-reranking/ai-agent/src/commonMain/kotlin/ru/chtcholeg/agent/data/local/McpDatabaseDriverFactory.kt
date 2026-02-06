package ru.chtcholeg.agent.data.local

import app.cash.sqldelight.db.SqlDriver

expect class McpDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
