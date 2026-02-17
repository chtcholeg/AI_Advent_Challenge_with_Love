package ru.chtcholeg.agent.domain.service

import kotlinx.coroutines.delay
import ru.chtcholeg.shared.data.api.GigaChatApiException

/**
 * Retry policy with exponential backoff for transient errors.
 */
object RetryPolicy {

    /**
     * Execute a block with retry logic and exponential backoff.
     * Retries on transient HTTP errors (429, 500, 502, 503).
     *
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay before first retry
     * @param block The suspend function to execute
     * @return Result of the block execution
     */
    suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRIES,
        initialDelayMs: Long = INITIAL_DELAY_MS,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                return block()
            } catch (e: GigaChatApiException) {
                if (e.statusCode in RETRYABLE_STATUS_CODES) {
                    lastException = e
                    if (attempt < maxRetries) {
                        val delayMs = initialDelayMs * (1L shl attempt) // exponential: 1s, 2s, 4s
                        println("[RetryPolicy] Retrying after ${e.statusCode} error (attempt ${attempt + 1}/$maxRetries, delay ${delayMs}ms)")
                        delay(delayMs)
                    }
                } else {
                    throw e // non-retryable error
                }
            } catch (e: Exception) {
                // Network errors are retryable
                if (isNetworkError(e)) {
                    lastException = e
                    if (attempt < maxRetries) {
                        val delayMs = initialDelayMs * (1L shl attempt)
                        println("[RetryPolicy] Retrying after network error (attempt ${attempt + 1}/$maxRetries, delay ${delayMs}ms): ${e.message}")
                        delay(delayMs)
                    }
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IllegalStateException("Retry exhausted without exception")
    }

    /**
     * Execute a tool call with single retry on failure.
     * Returns the result or an error message for the AI.
     */
    suspend fun <T> withToolRetry(
        toolName: String,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            println("[RetryPolicy] Tool '$toolName' failed, retrying once: ${e.message}")
            try {
                delay(TOOL_RETRY_DELAY_MS)
                block()
            } catch (retryException: Exception) {
                println("[RetryPolicy] Tool '$toolName' retry also failed: ${retryException.message}")
                throw retryException
            }
        }
    }

    private fun isNetworkError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("connection") ||
               message.contains("timeout") ||
               message.contains("socket") ||
               message.contains("network") ||
               message.contains("eof") ||
               e is java.net.ConnectException ||
               e is java.net.SocketTimeoutException
    }

    private val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503)
    private const val MAX_RETRIES = 3
    private const val INITIAL_DELAY_MS = 1000L
    private const val TOOL_RETRY_DELAY_MS = 500L
}
