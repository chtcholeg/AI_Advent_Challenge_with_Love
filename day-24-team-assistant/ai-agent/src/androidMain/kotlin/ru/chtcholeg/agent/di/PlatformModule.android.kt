package ru.chtcholeg.agent.di

import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.chtcholeg.agent.data.local.McpDatabaseDriverFactory
import ru.chtcholeg.shared.domain.service.RagDatabase
import ru.chtcholeg.shared.domain.service.VectorStore
import ru.chtcholeg.shared.domain.service.VectorStoreSqliteImpl

actual fun platformModule(): Module = module {
    // Context is already provided by androidContext() in AgentApplication
    single { McpDatabaseDriverFactory(get()) }

    single<VectorStore> {
        val context = get<android.content.Context>()
        VectorStoreSqliteImpl { path ->
            // On Android, path is treated as an absolute file path
            AndroidSqliteDriver(RagDatabase.Schema, context, path)
        }
    }
}
