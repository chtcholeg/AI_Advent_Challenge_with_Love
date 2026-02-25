package ru.chtcholeg.godagent.data.rag

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.sqrt

data class DocumentChunk(
    val id: Int = 0,
    val filePath: String,
    val content: String,
    val embedding: List<Float>
)

class VectorStore {
    private val dbPath = File(System.getProperty("user.home"), ".god-agent/rag.db").also {
        it.parentFile.mkdirs()
    }
    private var connection: Connection? = null

    private fun getConnection(): Connection {
        return connection?.takeUnless { it.isClosed } ?: run {
            Class.forName("org.sqlite.JDBC")
            DriverManager.getConnection("jdbc:sqlite:${dbPath.absolutePath}").also {
                connection = it
                it.createStatement().execute(
                    """
                    CREATE TABLE IF NOT EXISTS chunks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_path TEXT NOT NULL,
                        content TEXT NOT NULL,
                        embedding BLOB NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        getConnection().createStatement().execute("DELETE FROM chunks")
    }

    suspend fun saveChunk(filePath: String, content: String, embedding: List<Float>) = withContext(Dispatchers.IO) {
        val conn = getConnection()
        val stmt = conn.prepareStatement("INSERT INTO chunks (file_path, content, embedding) VALUES (?, ?, ?)")
        stmt.setString(1, filePath)
        stmt.setString(2, content)
        stmt.setBytes(3, embeddingToBytes(embedding))
        stmt.executeUpdate()
    }

    suspend fun search(queryEmbedding: List<Float>, topK: Int = 5): List<Pair<DocumentChunk, Float>> =
        withContext(Dispatchers.IO) {
            val conn = getConnection()
            val rs = conn.createStatement().executeQuery("SELECT id, file_path, content, embedding FROM chunks")
            val results = mutableListOf<Pair<DocumentChunk, Float>>()
            while (rs.next()) {
                val embedding = bytesToEmbedding(rs.getBytes("embedding"))
                val similarity = cosineSimilarity(queryEmbedding, embedding)
                results.add(
                    DocumentChunk(
                        rs.getInt("id"),
                        rs.getString("file_path"),
                        rs.getString("content"),
                        embedding
                    ) to similarity
                )
            }
            results.sortedByDescending { it.second }.take(topK)
        }

    suspend fun getChunkCount(): Int = withContext(Dispatchers.IO) {
        val rs = getConnection().createStatement().executeQuery("SELECT COUNT(*) FROM chunks")
        if (rs.next()) rs.getInt(1) else 0
    }

    private fun embeddingToBytes(embedding: List<Float>): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(embedding.size * 4)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun bytesToEmbedding(bytes: ByteArray): List<Float> {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val result = mutableListOf<Float>()
        while (buffer.hasRemaining()) result.add(buffer.float)
        return result
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i]
        }
        val denom = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    fun close() {
        connection?.close()
    }
}
