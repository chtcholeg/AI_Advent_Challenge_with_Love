package ru.chtcholeg.indexer.di

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.chtcholeg.indexer.data.local.IndexerDatabase
import ru.chtcholeg.indexer.data.local.IndexerDatabaseDriverFactory

/**
 * Desktop-specific Koin module
 */
val indexerPlatformModule = module {
    // Database driver factory
    single { IndexerDatabaseDriverFactory() }

    // Database
    single {
        val factory: IndexerDatabaseDriverFactory = get()
        IndexerDatabase(factory.createDriver())
    }

    // GigaChat HTTP client — OkHttp with permissive SSL for Sberbank certificates
    single(named("gigachat")) {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
        HttpClient(OkHttp) {
            engine {
                config {
                    sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
                    hostnameVerifier { _, _ -> true }
                }
            }
            install(ContentNegotiation) { json() }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 10_000
            }
        }
    }
}
