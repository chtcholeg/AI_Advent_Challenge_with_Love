package ru.chtcholeg.indexer.di

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.chtcholeg.indexer.data.api.OllamaApi
import ru.chtcholeg.indexer.data.api.OllamaApiImpl
import ru.chtcholeg.indexer.data.local.IndexerLocalRepository
import ru.chtcholeg.indexer.data.local.IndexerLocalRepositoryImpl
import ru.chtcholeg.indexer.domain.service.DocumentIndexerService
import ru.chtcholeg.indexer.domain.service.OllamaEmbeddingService
import ru.chtcholeg.indexer.presentation.IndexerStore
import ru.chtcholeg.shared.data.api.GigaChatApi
import ru.chtcholeg.shared.data.api.GigaChatApiImpl
import ru.chtcholeg.shared.domain.service.*

/**
 * Common Koin module for indexer
 */
val indexerCommonModule = module {
    // JSON
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    // HTTP Client
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(get())
            }
            install(Logging) {
                level = LogLevel.BODY
                logger = object : Logger {
                    override fun log(message: String) {
                        println("HTTP: $message")
                    }
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000 // 2 minutes for embedding generation
                connectTimeoutMillis = 10_000
            }
        }
    }

    // Shared services
    single<FileSystem> { FileSystem() }
    single<TextChunker> { TextChunkerImpl(ChunkConfig()) }
    single<DocumentLoader> { DocumentLoaderImpl(get()) }
    single<WebPageLoader> { WebPageLoaderImpl(get()) }

    // Ollama API
    single<OllamaApi> { OllamaApiImpl(get()) }

    // Embedding services
    single { OllamaEmbeddingService(get()) }

    // GigaChat API (HttpClient with SSL registered in platform module under "gigachat" qualifier)
    single<GigaChatApi> { GigaChatApiImpl(get(named("gigachat"))) }

    // Local repository (database injected from platform module)
    single<IndexerLocalRepository> { IndexerLocalRepositoryImpl(get()) }

    // Document indexer service — default embedding service is Ollama
    single {
        val ollama: OllamaEmbeddingService = get()
        DocumentIndexerService(get(), get(), ollama, get(), get())
    }

    // MVI Store
    singleOf(::IndexerStore)
}
