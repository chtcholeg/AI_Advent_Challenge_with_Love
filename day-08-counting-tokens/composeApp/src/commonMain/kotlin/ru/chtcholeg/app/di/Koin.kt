package ru.chtcholeg.app.di

import ru.chtcholeg.app.data.api.GigaChatApi
import ru.chtcholeg.app.data.api.GigaChatApiImpl
import ru.chtcholeg.app.data.api.HuggingFaceApi
import ru.chtcholeg.app.data.api.HuggingFaceApiImpl
import ru.chtcholeg.app.data.local.ChatDatabase
import ru.chtcholeg.app.data.local.ChatLocalRepository
import ru.chtcholeg.app.data.local.ChatLocalRepositoryImpl
import ru.chtcholeg.app.data.local.DatabaseDriverFactory
import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.data.repository.ChatRepositoryImpl
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.data.repository.SettingsRepositoryImpl
import ru.chtcholeg.app.domain.usecase.SendMessageUseCase
import ru.chtcholeg.app.presentation.chat.ChatStore
import ru.chtcholeg.app.presentation.session.SessionListStore
import ru.chtcholeg.app.util.getEnvVariable
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect fun platformModule(): Module

val appModule = module {
    // HTTP Client
    single {
        HttpClient(get()) {
            // JSON serialization
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                })
            }

            // Timeout configuration
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 30000
            }

            // Logging
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }

            // Request configuration
            expectSuccess = true
        }
    }

    // APIs
    single<GigaChatApi> { GigaChatApiImpl(get()) }
    single<HuggingFaceApi> { HuggingFaceApiImpl(get()) }

    // Database
    single { get<DatabaseDriverFactory>().createDriver() }
    single { ChatDatabase(get()) }
    single<ChatLocalRepository> { ChatLocalRepositoryImpl(get()) }

    // Repositories
    single<SettingsRepository> { SettingsRepositoryImpl() }

    single<ChatRepository> {
        ChatRepositoryImpl(
            gigaChatApi = get(),
            huggingFaceApi = get(),
            gigaChatClientId = getEnvVariable("GIGACHAT_CLIENT_ID"),
            gigaChatClientSecret = getEnvVariable("GIGACHAT_CLIENT_SECRET"),
            huggingFaceToken = getEnvVariable("HUGGINGFACE_API_TOKEN"),
            settingsRepository = get()
        )
    }

    // Use Case
    factory { SendMessageUseCase(get()) }

    // Stores
    factory {
        ChatStore(
            sendMessageUseCase = get(),
            chatRepository = get(),
            chatLocalRepository = get(),
            settingsRepository = get(),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
    }

    factory {
        SessionListStore(
            chatLocalRepository = get(),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule, platformModule())
    }
}
