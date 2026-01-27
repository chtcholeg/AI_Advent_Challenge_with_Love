package com.example.mcp.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

const val API_KEY_AUTH = "api-key-auth"

/**
 * Principal representing an authenticated API client
 */
data class ApiKeyPrincipal(val apiKey: String) : Principal

/**
 * Configure API key authentication for Ktor
 */
fun AuthenticationConfig.apiKeyAuth(apiKey: String) {
    bearer(API_KEY_AUTH) {
        realm = "MCP Server"
        authenticate { tokenCredential ->
            if (tokenCredential.token == apiKey) {
                ApiKeyPrincipal(tokenCredential.token)
            } else {
                null
            }
        }
    }
}

/**
 * Plugin to handle authentication failures with proper JSON response
 */
val AuthenticationInterceptor = createApplicationPlugin(name = "AuthenticationInterceptor") {
    on(AuthenticationChecked) { call ->
        if (call.authentication.principal<ApiKeyPrincipal>() == null &&
            call.request.headers["Authorization"] != null) {
            // Invalid token provided
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Invalid API key")
            )
        }
    }
}
