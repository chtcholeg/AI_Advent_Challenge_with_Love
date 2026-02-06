package ru.chtcholeg.app.util

import ru.chtcholeg.app.BuildKonfig

/**
 * Get configuration value by key
 * Uses BuildKonfig to access build-time configuration
 */
fun getEnvVariable(key: String): String {
    return when (key) {
        "GIGACHAT_CLIENT_ID" -> BuildKonfig.GIGACHAT_CLIENT_ID
        "GIGACHAT_CLIENT_SECRET" -> BuildKonfig.GIGACHAT_CLIENT_SECRET
        "HUGGINGFACE_API_TOKEN" -> BuildKonfig.HUGGINGFACE_API_TOKEN
        else -> throw IllegalArgumentException("Unknown configuration key: $key")
    }
}
