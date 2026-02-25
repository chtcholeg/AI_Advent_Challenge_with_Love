package ru.chtcholeg.godagent.data.tools

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class GitHubListPRsTool(
    private val httpClient: HttpClient,
    private val tokenProvider: () -> String
) : AgentTool {
    override val name = "github_list_prs"
    override val description = "List open pull requests for a GitHub repository"
    override val parametersDescription = """{"owner": string, "repo": string, "state": string (optional: open/closed/all, default: open)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val owner = args["owner"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: owner required"
        val repo = args["repo"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: repo required"
        val state = args["state"]?.jsonPrimitive?.contentOrNull ?: "open"
        try {
            val token = tokenProvider()
            val response: String = httpClient.get("https://api.github.com/repos/$owner/$repo/pulls?state=$state") {
                if (token.isNotBlank()) header("Authorization", "token $token")
                header("Accept", "application/vnd.github.v3+json")
            }.body()
            val prs = Json.parseToJsonElement(response).jsonArray
            if (prs.isEmpty()) return@withContext "No $state PRs found"
            prs.joinToString("\n") { pr ->
                val obj = pr.jsonObject
                "#${obj["number"]?.jsonPrimitive?.int} ${obj["title"]?.jsonPrimitive?.content} — ${obj["user"]?.jsonObject?.get("login")?.jsonPrimitive?.content}"
            }
        } catch (e: Exception) {
            "Error fetching PRs: ${e.message}"
        }
    }
}

class GitHubListIssuesTool(
    private val httpClient: HttpClient,
    private val tokenProvider: () -> String
) : AgentTool {
    override val name = "github_list_issues"
    override val description = "List open issues for a GitHub repository"
    override val parametersDescription = """{"owner": string, "repo": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            return@withContext "Error: invalid args"
        }
        val owner = args["owner"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: owner required"
        val repo = args["repo"]?.jsonPrimitive?.contentOrNull ?: return@withContext "Error: repo required"
        try {
            val token = tokenProvider()
            val response: String = httpClient.get("https://api.github.com/repos/$owner/$repo/issues?state=open") {
                if (token.isNotBlank()) header("Authorization", "token $token")
                header("Accept", "application/vnd.github.v3+json")
            }.body()
            val issues = Json.parseToJsonElement(response).jsonArray
                .filter { it.jsonObject["pull_request"] == null }
            if (issues.isEmpty()) return@withContext "No open issues found"
            issues.take(10).joinToString("\n") { issue ->
                val obj = issue.jsonObject
                "#${obj["number"]?.jsonPrimitive?.int} ${obj["title"]?.jsonPrimitive?.content}"
            }
        } catch (e: Exception) {
            "Error fetching issues: ${e.message}"
        }
    }
}
