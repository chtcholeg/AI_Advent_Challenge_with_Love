package ru.chtcholeg.shared

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ru.chtcholeg.shared.data.api.GigaChatApiImpl
import ru.chtcholeg.shared.domain.service.*
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Main entry point for document indexing CLI
 *
 * Usage:
 * - Index: ./gradlew :shared:runIndexing --args="index <directory> <output_index_path>"
 * - Search: ./gradlew :shared:runIndexing --args="search <index_path> <query>"
 * - Stats: ./gradlew :shared:runIndexing --args="stats <index_path>"
 */
fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        printUsage()
        return@runBlocking
    }

    val command = args[0]

    // Read credentials from environment or local.properties
    val clientId = System.getenv("GIGACHAT_CLIENT_ID")
        ?: throw IllegalStateException("GIGACHAT_CLIENT_ID not set")
    val clientSecret = System.getenv("GIGACHAT_CLIENT_SECRET")
        ?: throw IllegalStateException("GIGACHAT_CLIENT_SECRET not set")

    // Setup SSL trust manager for GigaChat (Sberbank certificate)
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    // Setup dependencies
    val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                hostnameVerifier { _, _ -> true }
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    val gigaChatApi = GigaChatApiImpl(httpClient)
    val fileSystem = FileSystem()
    val documentLoader = DocumentLoaderImpl(fileSystem)
    val webPageLoader = WebPageLoaderImpl(httpClient)
    val textChunker = TextChunkerImpl(
        ChunkConfig(
            strategy = ChunkStrategy.BY_CHARACTERS,
            chunkSize = 500,
            overlapSize = 50
        )
    )
    val embeddingService = EmbeddingServiceImpl(
        gigaChatApi = gigaChatApi,
        clientId = clientId,
        clientSecret = clientSecret,
        batchSize = 10
    )
    val vectorStore = VectorStoreSqliteImpl { path ->
        val resolved = if (path.startsWith("~"))
            path.replaceFirst("~", System.getProperty("user.home"))
        else path
        val isNew = !File(resolved).exists()
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:$resolved")
        if (isNew) {
            RagDatabase.Schema.create(driver)
        }
        driver
    }
    val documentIndexer = DocumentIndexerImpl(
        documentLoader = documentLoader,
        textChunker = textChunker,
        embeddingService = embeddingService,
        vectorStore = vectorStore,
        webPageLoader = webPageLoader
    )
    val cli = IndexingCliImpl(documentIndexer, vectorStore)

    try {
        when (command) {
            "index" -> {
                if (args.size < 3) {
                    println("❌ Error: Missing arguments for index command")
                    println("Usage: index <directory> <output_index_path> [extensions...]")
                    return@runBlocking
                }
                val directory = args[1]
                val outputPath = args[2]
                val extensions = if (args.size > 3) args.drop(3) else listOf("md", "txt")

                cli.indexDocuments(directory, outputPath, extensions)
            }
            "search" -> {
                if (args.size < 3) {
                    println("❌ Error: Missing arguments for search command")
                    println("Usage: search <index_path> <query> [top_k]")
                    return@runBlocking
                }
                val indexPath = args[1]
                val query = args[2]
                val topK = if (args.size > 3) args[3].toIntOrNull() ?: 5 else 5

                cli.searchDocuments(indexPath, query, topK)
            }
            "stats" -> {
                if (args.size < 2) {
                    println("❌ Error: Missing arguments for stats command")
                    println("Usage: stats <index_path>")
                    return@runBlocking
                }
                val indexPath = args[1]

                cli.showStats(indexPath)
            }
            "index-url" -> {
                if (args.size < 3) {
                    println("❌ Error: Missing arguments for index-url command")
                    println("Usage: index-url <url> <output_index_path>")
                    return@runBlocking
                }
                val url = args[1]
                val outputPath = args[2]

                cli.indexUrl(url, outputPath)
            }
            "index-urls" -> {
                if (args.size < 3) {
                    println("❌ Error: Missing arguments for index-urls command")
                    println("Usage: index-urls <url1,url2,...> <output_index_path>")
                    return@runBlocking
                }
                val urlsString = args[1]
                val outputPath = args[2]
                val urls = urlsString.split(",").map { it.trim() }

                cli.indexUrls(urls, outputPath)
            }
            else -> {
                println("❌ Error: Unknown command: $command")
                printUsage()
            }
        }
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    } finally {
        httpClient.close()
    }
}

private fun printUsage() {
    println("""
        📚 Document Indexing CLI

        Usage:
          index <directory> <output_index_path> [extensions...]
            Index all documents in directory with given extensions (default: md, txt)
            Example: index ./docs ./index.db md txt

          index-url <url> <output_index_path>
            Index a single web page from URL (extracts plain text without HTML tags)
            Example: index-url https://example.com/article ./index.db

          index-urls <url1,url2,...> <output_index_path>
            Index multiple web pages from URLs (comma-separated)
            Example: index-urls https://example.com/page1,https://example.com/page2 ./index.db

          search <index_path> <query> [top_k]
            Search indexed documents and web pages for similar content
            Example: search ./index.db "GigaChat API" 5

          stats <index_path>
            Show statistics about the index
            Example: stats ./index.db

        Environment Variables:
          GIGACHAT_CLIENT_ID     - GigaChat API client ID
          GIGACHAT_CLIENT_SECRET - GigaChat API client secret
    """.trimIndent())
}
