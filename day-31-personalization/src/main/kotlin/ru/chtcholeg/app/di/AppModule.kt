package ru.chtcholeg.app.di

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
import ru.chtcholeg.app.data.api.GigaChatApi
import ru.chtcholeg.app.data.api.OllamaApi
import ru.chtcholeg.app.data.repository.ChatRepository
import ru.chtcholeg.app.data.repository.SessionRepository
import ru.chtcholeg.app.data.repository.SettingsRepository
import ru.chtcholeg.app.presentation.chat.ChatStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

val appModule = module {

    single {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        HttpClient(OkHttp) {
            engine {
                config {
                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    hostnameVerifier { _, _ -> true }
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 120_000
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }

            expectSuccess = true
        }
    }

    single { GigaChatApi(get()) }
    single { OllamaApi(get()) }
    single { SettingsRepository() }
    single { SessionRepository() }
    single { ChatRepository(get(), get()) }

    single {
        ChatStore(
            chatRepository = get(),
            settingsRepository = get(),
            sessionRepository = get(),
            ollamaApi = get(),
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
    }
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
