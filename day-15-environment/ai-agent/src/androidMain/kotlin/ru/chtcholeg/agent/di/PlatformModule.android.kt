package ru.chtcholeg.agent.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ru.chtcholeg.agent.data.local.McpDatabaseDriverFactory

actual fun platformModule(): Module = module {
    // Context is already provided by androidContext() in AgentApplication
    single { McpDatabaseDriverFactory(get()) }
}
