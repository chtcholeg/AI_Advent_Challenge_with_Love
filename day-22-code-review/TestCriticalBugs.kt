package ru.chtcholeg.test

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Test cases for critical bugs that should be detected by code review.
 * These bugs are NOT currently detected by /review-pr command.
 */
class TestCriticalBugs {

    // ❌ BUG #1: Division by zero (ImageProcessor.kt:33)
    fun calculateCompressionRatio(original: String, compressed: String): Double {
        // ⚠️ If compressed.length == 0, ArithmeticException
        val ratio = compressed.length / original.length
        return ratio.toDouble()
    }

    // ❌ BUG #2: Removed safety check (RagRepository.kt:54)
    private var indexLoaded = false

    suspend fun getRelevantChunks(query: String): List<String> {
        // ⚠️ DELETED: if (!indexLoaded) throw IllegalStateException(...)
        // This can lead to NPE or incorrect behavior if index is not loaded
        val embedding = generateEmbedding(query)
        return searchVector(embedding)
    }

    // ❌ BUG #3: Memory leak via GlobalScope (AgentStore.kt:419)
    fun loadTools() {
        // ⚠️ GlobalScope not tied to component lifecycle
        // Coroutine will continue even after AgentStore is destroyed
        kotlinx.coroutines.GlobalScope.launch {
            loadToolsInternal()
        }
    }

    // ❌ BUG #4: Removed permission denied handling (ToolExecutor.kt:27)
    suspend fun executeTool(toolName: String): String {
        val permission = checkPermission(toolName)

        when (permission) {
            // ⚠️ DELETED: is PermissionResult.Denied -> return error
            // Tool will execute even if permission is denied!
            is PermissionResult.AllowedWithWarning -> {
                println("Warning: ${permission.warning}")
            }
            else -> { /* proceed */ }
        }

        return callTool(toolName)
    }

    // ❌ BUG #5: Removed retry delay (RetryPolicy.kt:31-36)
    suspend fun <T> withRetry(maxRetries: Int = 3, block: suspend () -> T): T {
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    // ⚠️ DELETED: delay(delayMs)
                    // No exponential backoff between retries
                    // Can lead to rate limit exhaustion
                    println("Retrying after error (attempt ${attempt + 1}/$maxRetries)")
                }
            }
        }

        throw lastException ?: IllegalStateException("Retry exhausted")
    }

    // ❌ BUG #6: Integer overflow not handled
    fun calculateTotalSize(sizes: List<Int>): Int {
        var total = 0
        for (size in sizes) {
            total += size  // ⚠️ Can overflow if sum > Int.MAX_VALUE
        }
        return total
    }

    // ❌ BUG #7: Removed null check in data processing
    fun processUser(userId: String): String {
        val user = findUser(userId)  // returns User?
        // ⚠️ DELETED: ?: throw UserNotFoundException()
        // Will NPE if user is null
        return user.name
    }

    // Stub methods
    private suspend fun generateEmbedding(query: String): FloatArray = floatArrayOf()
    private suspend fun searchVector(embedding: FloatArray): List<String> = emptyList()
    private suspend fun loadToolsInternal() {}
    private fun checkPermission(toolName: String): PermissionResult = PermissionResult.Allowed
    private suspend fun callTool(toolName: String): String = ""
    private fun findUser(id: String): User? = null

    sealed class PermissionResult {
        object Allowed : PermissionResult()
        data class AllowedWithWarning(val warning: String) : PermissionResult()
        data class Denied(val reason: String) : PermissionResult()
    }

    data class User(val name: String)
}
