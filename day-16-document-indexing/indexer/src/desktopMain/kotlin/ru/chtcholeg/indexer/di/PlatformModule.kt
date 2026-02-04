package ru.chtcholeg.indexer.di

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
}
