package ru.chtcholeg.agent.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.chtcholeg.agent.data.local.McpDatabaseDriverFactory
import ru.chtcholeg.shared.domain.service.RagDatabase
import ru.chtcholeg.shared.domain.service.VectorStore
import ru.chtcholeg.shared.domain.service.VectorStoreSqliteImpl

actual fun platformModule(): Module = module {
    single { McpDatabaseDriverFactory() }

    single<VectorStore> {
        VectorStoreSqliteImpl { path ->
            val resolved = if (path.startsWith("~"))
                path.replaceFirst("~", System.getProperty("user.home"))
            else path
            val isNew = !File(resolved).exists()
            val driver = JdbcSqliteDriver("jdbc:sqlite:$resolved")
            if (isNew) {
                RagDatabase.Schema.create(driver)
            }
            driver
        }
    }
}
