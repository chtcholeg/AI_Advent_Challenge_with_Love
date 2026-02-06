package ru.chtcholeg.agent.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import ru.chtcholeg.shared.data.api.GigaChatApiImpl
import ru.chtcholeg.shared.data.mcp.McpClientManager
import ru.chtcholeg.shared.data.mcp.McpTransportFactory
import ru.chtcholeg.shared.domain.service.EmbeddingService
import ru.chtcholeg.shared.domain.service.EmbeddingServiceImpl
import ru.chtcholeg.agent.data.local.McpDatabase
import ru.chtcholeg.agent.data.local.McpDatabaseDriverFactory
import ru.chtcholeg.agent.data.local.McpLocalRepository
import ru.chtcholeg.agent.data.local.McpLocalRepositoryImpl
import ru.chtcholeg.agent.BuildKonfig
import ru.chtcholeg.agent.data.repository.*
import ru.chtcholeg.agent.presentation.agent.AgentStore

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(
        appModule(),
        platformModule()
    )
}

fun appModule() = module {
    // HttpClient with SSL verification disabled
    single { createHttpClient() }

    // Database
    single {
        val driver = get<McpDatabaseDriverFactory>().createDriver()
        McpDatabase(driver)
    }

    // Local Repositories
    single<McpLocalRepository> { McpLocalRepositoryImpl(get()) }

    // Repositories
    single { SettingsRepository(get()) }

    single<McpRepository> {
        McpRepositoryImpl(
            mcpClientManager = get(),
            mcpLocalRepository = get()
        )
    }

    single {
        AgentRepository(
            httpClient = get(),
            settingsRepository = get(),
            mcpRepository = get()
        )
    }

    // MCP
    single {
        McpClientManager(
            transportFactory = get()
        )
    }

    single {
        McpTransportFactory(httpClient = get())
    }

    // RAG
    single<EmbeddingService> {
        EmbeddingServiceImpl(
            gigaChatApi = GigaChatApiImpl(get()),
            clientId = BuildKonfig.GIGACHAT_CLIENT_ID,
            clientSecret = BuildKonfig.GIGACHAT_CLIENT_SECRET
        )
    }
    // VectorStore binding is in platformModule() (desktopMain / androidMain)
    single { RagRepository(get(), get()) }

    // Stores
    single {
        AgentStore(
            agentRepository = get(),
            mcpRepository = get(),
            ragRepository = get(),
            settingsRepository = get(),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
    }
}

expect fun platformModule(): Module
