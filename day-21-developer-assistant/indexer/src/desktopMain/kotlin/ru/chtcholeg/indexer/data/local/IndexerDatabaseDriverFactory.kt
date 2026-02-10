package ru.chtcholeg.indexer.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager

/**
 * Factory for creating SQLite database driver on Desktop
 */
class IndexerDatabaseDriverFactory {

    fun createDriver(): SqlDriver {
        // Create database in user's home directory
        val dbDir = File(System.getProperty("user.home"), ".indexer")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }

        val dbFile = File(dbDir, "index.db")
        val isNewDb = !dbFile.exists()
        val dbPath = dbFile.absolutePath

        // Check if schema needs to be recreated (for existing databases missing tables)
        if (!isNewDb && !tableExists(dbPath, "IndexedChunkEntity")) {
            println("[IndexerDatabaseDriverFactory] IndexedChunkEntity table missing, deleting database to recreate schema...")
            dbFile.delete()
        }

        val needsSchema = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")

        // Enable foreign keys
        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)

        if (needsSchema) {
            IndexerDatabase.Schema.create(driver)
        }

        return driver
    }

    private fun tableExists(dbPath: String, tableName: String): Boolean {
        return try {
            DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'"
                    )
                    rs.next()
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
