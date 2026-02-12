package ru.chtcholeg.agent.config

/**
 * Centralized configuration constants for the AI Agent module.
 * All magic numbers should be defined here for easy tuning and maintenance.
 */
object AgentConfig {
    /** Maximum tool-call iterations before stopping the agentic loop */
    const val MAX_ITERATIONS = 50

    /** Maximum context window size in tokens for sliding-window summarization */
    const val MAX_CONTEXT_TOKENS = 12_000

    /** Maximum character budget for command context (system prompt + code) */
    const val MAX_CONTEXT_CHARS = 16_000

    /** Hard limit for any message content sent to the AI model */
    const val MAX_MESSAGE_LENGTH = 5_000

    /** Minimum string length to suspect base64-encoded image data */
    const val MIN_BASE64_LENGTH = 1_000

    /** Default max turns for sub-agent execution */
    const val DEFAULT_MAX_TURNS = 10

    /** Maximum concurrent background agents */
    const val MAX_CONCURRENT_BACKGROUND = 5

    /** Maximum characters for diff content in pre-fetched review data */
    const val MAX_REVIEW_DIFF_CHARS = 12_000

    /** Maximum characters per file content in pre-fetched review data */
    const val MAX_REVIEW_FILE_CHARS = 3_000

    /** Maximum total characters for all file contents combined in pre-fetched review data */
    const val MAX_REVIEW_TOTAL_FILES_CHARS = 20_000
}
