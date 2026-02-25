package ru.chtcholeg.godagent.di

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.dsl.module
import ru.chtcholeg.godagent.data.api.GigaChatApi
import ru.chtcholeg.godagent.data.api.OllamaApi
import ru.chtcholeg.godagent.data.audio.AudioRecorder
import ru.chtcholeg.godagent.data.audio.VoskSpeechRecognitionService
import ru.chtcholeg.godagent.data.rag.DocumentIndexer
import ru.chtcholeg.godagent.data.rag.RagTool
import ru.chtcholeg.godagent.data.rag.VectorStore
import ru.chtcholeg.godagent.data.repository.ChatRepository
import ru.chtcholeg.godagent.data.repository.SessionRepository
import ru.chtcholeg.godagent.data.repository.SettingsRepository
import ru.chtcholeg.godagent.data.tools.ToolExecutor
import ru.chtcholeg.godagent.presentation.chat.ChatStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

val appModule = module {
    single {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ssl = SSLContext.getInstance("TLS").also { it.init(null, trustAll, SecureRandom()) }
        HttpClient(OkHttp) {
            engine {
                config {
                    sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
                    hostnameVerifier { _, _ -> true }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 180_000
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            expectSuccess = false  // Don't throw on 4xx/5xx
        }
    }

    single { SettingsRepository() }
    single { SessionRepository() }
    single { GigaChatApi(get()) }
    single { OllamaApi(get()) }
    single { VectorStore() }
    single { AudioRecorder() }
    single { VoskSpeechRecognitionService() }

    single {
        val settingsRepo: SettingsRepository = get()
        DocumentIndexer(get(), get(), get()) { settingsRepo.settings.value }
    }

    single { RagTool(get()) }

    single {
        val settingsRepo: SettingsRepository = get()
        val ragTool: RagTool = get()
        ToolExecutor(get(), { settingsRepo.settings.value }, listOf(ragTool))
    }

    single {
        ChatRepository(get(), get(), get(), get())
    }

    single {
        ChatStore(
            chatRepository = get(),
            settingsRepository = get(),
            sessionRepository = get(),
            ollamaApi = get(),
            toolExecutor = get(),
            documentIndexer = get(),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            audioRecorder = get(),
            speechRecognitionService = get()
        )
    }
}

fun initKoin() {
    startKoin { modules(appModule) }
}
