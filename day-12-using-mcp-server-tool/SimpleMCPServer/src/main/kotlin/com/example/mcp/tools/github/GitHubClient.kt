package com.example.mcp.tools.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * GitHub Repository response
 */
@Serializable
data class GitHubRepository(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String? = null,
    val html_url: String,
    val language: String? = null,
    val stargazers_count: Int = 0,
    val forks_count: Int = 0,
    val watchers_count: Int = 0,
    val open_issues_count: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
    val pushed_at: String? = null,
    val default_branch: String? = null,
    val license: GitHubLicense? = null,
    val topics: List<String>? = null,
    val visibility: String? = null,
    val fork: Boolean = false,
    val archived: Boolean = false
)

@Serializable
data class GitHubLicense(
    val key: String? = null,
    val name: String? = null,
    val spdx_id: String? = null
)

/**
 * GitHub User response
 */
@Serializable
data class GitHubUser(
    val login: String,
    val id: Long,
    val type: String,
    val name: String? = null,
    val company: String? = null,
    val blog: String? = null,
    val location: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val twitter_username: String? = null,
    val public_repos: Int = 0,
    val public_gists: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
    val html_url: String
)

/**
 * GitHub Search response
 */
@Serializable
data class GitHubSearchResponse(
    val total_count: Int,
    val incomplete_results: Boolean,
    val items: List<GitHubRepository>
)

/**
 * GitHub Issue response
 */
@Serializable
data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val html_url: String,
    val user: GitHubIssueUser? = null,
    val labels: List<GitHubLabel> = emptyList(),
    val created_at: String? = null,
    val updated_at: String? = null,
    val closed_at: String? = null,
    val body: String? = null,
    val comments: Int = 0,
    val pull_request: GitHubPullRequestRef? = null
)

@Serializable
data class GitHubIssueUser(
    val login: String,
    val id: Long
)

@Serializable
data class GitHubLabel(
    val name: String,
    val color: String? = null
)

@Serializable
data class GitHubPullRequestRef(
    val url: String? = null,
    val html_url: String? = null
)

/**
 * Client for GitHub API (public, no authentication)
 */
class GitHubClient {
    private val logger = LoggerFactory.getLogger(GitHubClient::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    companion object {
        private const val BASE_URL = "https://api.github.com"
    }

    /**
     * Get repository information
     */
    suspend fun getRepository(owner: String, repo: String): GitHubRepository? {
        logger.debug("Getting repository: $owner/$repo")
        return try {
            httpClient.get("$BASE_URL/repos/$owner/$repo") {
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                header(HttpHeaders.UserAgent, "MCP-Server")
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to get repository $owner/$repo: ${e.message}")
            null
        }
    }

    /**
     * Get user or organization information
     */
    suspend fun getUser(username: String): GitHubUser? {
        logger.debug("Getting user: $username")
        return try {
            httpClient.get("$BASE_URL/users/$username") {
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                header(HttpHeaders.UserAgent, "MCP-Server")
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to get user $username: ${e.message}")
            null
        }
    }

    /**
     * Search repositories
     */
    suspend fun searchRepositories(
        query: String,
        language: String? = null,
        sort: String? = null,
        order: String? = null,
        perPage: Int = 10
    ): GitHubSearchResponse? {
        logger.debug("Searching repositories: $query")
        return try {
            val fullQuery = buildString {
                append(query)
                language?.let { append(" language:$it") }
            }

            httpClient.get("$BASE_URL/search/repositories") {
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                header(HttpHeaders.UserAgent, "MCP-Server")
                parameter("q", fullQuery)
                sort?.let { parameter("sort", it) }
                order?.let { parameter("order", it) }
                parameter("per_page", perPage.coerceIn(1, 100))
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to search repositories: ${e.message}")
            null
        }
    }

    /**
     * List issues for a repository
     */
    suspend fun listIssues(
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 10,
        labels: String? = null
    ): List<GitHubIssue>? {
        logger.debug("Listing issues for: $owner/$repo")
        return try {
            httpClient.get("$BASE_URL/repos/$owner/$repo/issues") {
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                header(HttpHeaders.UserAgent, "MCP-Server")
                parameter("state", state)
                parameter("per_page", perPage.coerceIn(1, 100))
                labels?.let { parameter("labels", it) }
            }.body()
        } catch (e: Exception) {
            logger.error("Failed to list issues for $owner/$repo: ${e.message}")
            null
        }
    }

    fun close() {
        httpClient.close()
    }
}
