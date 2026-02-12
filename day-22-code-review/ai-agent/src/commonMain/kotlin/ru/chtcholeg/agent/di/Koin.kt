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
import ru.chtcholeg.agent.data.local.ChatHistoryRepository
import ru.chtcholeg.agent.data.local.McpLocalRepository
import ru.chtcholeg.agent.data.local.McpLocalRepositoryImpl
import ru.chtcholeg.agent.BuildKonfig
import ru.chtcholeg.agent.data.repository.*
import ru.chtcholeg.agent.data.tool.LocalToolsProvider
import ru.chtcholeg.agent.util.FileSystem
import ru.chtcholeg.agent.domain.service.CommandHandler
import ru.chtcholeg.agent.domain.service.ImageProcessor
import ru.chtcholeg.agent.domain.service.PermissionManager
import ru.chtcholeg.agent.domain.service.ProjectRootProvider
import ru.chtcholeg.agent.domain.service.ToolExecutor
import ru.chtcholeg.agent.domain.service.createProjectRootProvider
import ru.chtcholeg.agent.presentation.agent.AgentStore
import ru.chtcholeg.agent.presentation.session.SessionListStore

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
    single { ChatHistoryRepository(get()) }
    single<TaskRepository> { TaskRepositoryImpl(get()) }
    single { PlanModeRepository(get()) }

    // Agent Executor
    single { AgentExecutor(get()) }

    // Local Tools
    single { FileSystem() }
    single {
        LocalToolsProvider(
            fileSystem = get(),
            taskRepository = get(),
            planModeRepository = get(),
            agentExecutor = lazy { get<AgentExecutor>() },
            ragRepository = get(),
            settingsRepository = get()
        )
    }

    // Repositories
    single { SettingsRepository(get()) }

    single<McpRepository> {
        McpRepositoryImpl(
            mcpClientManager = get(),
            mcpLocalRepository = get(),
            localToolsProvider = get()
        )
    }

    // Command System
    single<ProjectRootProvider> { createProjectRootProvider() }
    single { CommandHandler(get(), get()) }

    // Image processing and tool execution
    single { ImageProcessor() }
    single { PermissionManager() }
    single {
        ToolExecutor(
            mcpRepository = get(),
            planModeRepository = get(),
            permissionManager = get(),
            hookManager = null
        )
    }

    single {
        AgentRepository(
            httpClient = get(),
            settingsRepository = get(),
            mcpRepository = get(),
            planModeRepository = get(),
            imageProcessor = get(),
            toolExecutor = get()
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
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        AgentStore(
            agentRepository = get(),
            mcpRepository = get(),
            ragRepository = get(),
            settingsRepository = get(),
            chatHistoryRepository = get(),
            commandHandler = get(),
            coroutineScope = scope
        )
    }
    single {
        SessionListStore(
            chatHistoryRepository = get(),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
    }
}

expect fun platformModule(): Module
