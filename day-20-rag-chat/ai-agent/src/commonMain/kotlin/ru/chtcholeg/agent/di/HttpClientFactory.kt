package ru.chtcholeg.agent.di

import io.ktor.client.*

/**
 * Platform-specific HttpClient factory.
 * Creates HttpClient with SSL certificate verification disabled.
 */
expect fun createHttpClient(): HttpClient
