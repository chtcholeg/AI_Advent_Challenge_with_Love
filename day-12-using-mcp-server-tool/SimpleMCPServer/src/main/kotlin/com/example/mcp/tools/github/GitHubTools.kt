package com.example.mcp.tools.github

import com.example.mcp.plugins.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

// Helper to build few-shot examples
private fun example(request: String, vararg params: Pair<String, Any>) = FewShotExample(
    request = request,
    params = buildJsonObject {
        params.forEach { (key, value) ->
            when (value) {
                is String -> put(key, JsonPrimitive(value))
                is Int -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }
)

/**
 * MCP Tool for getting GitHub repository information
 */
class GitHubRepoInfoTool(
    private val client: GitHubClient = GitHubClient()
) : McpTool {

    private val logger = LoggerFactory.getLogger(GitHubRepoInfoTool::class.java)

    override val name: String = "github_repo_info"

    override val description: String = """
        Получить информацию о GitHub репозитории: описание, звёзды, форки, язык программирования, лицензию и топики.
        Работает только с публичными репозиториями.
    """.trimIndent()

    override val fewShotExamples: List<FewShotExample> = listOf(
        example("Покажи информацию о репозитории vscode от Microsoft", "owner" to "microsoft", "repo" to "vscode"),
        example("Что за проект kotlin у JetBrains?", "owner" to "JetBrains", "repo" to "kotlin"),
        example("Расскажи про репозиторий react от facebook", "owner" to "facebook", "repo" to "react"),
        example("Информация о next.js", "owner" to "vercel", "repo" to "next.js")
    )

    override val inputSchema: JsonSchema = JsonSchema(
        type = "object",
        properties = mapOf(
            "owner" to PropertySchema(
                type = "string",
                description = "Repository owner (username or organization name)"
            ),
            "repo" to PropertySchema(
                type = "string",
                description = "Repository name"
            )
        ),
        required = listOf("owner", "repo"),
        description = "Both 'owner' and 'repo' are required"
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        logger.info("Executing github_repo_info with arguments: $arguments")

        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("Missing required parameter: repo")

        return try {
            val repository = client.getRepository(owner, repo)
                ?: return ToolResult.Error("Repository not found: $owner/$repo")

            val result = buildString {
                appendLine("Repository: ${repository.full_name}")
                appendLine("=".repeat(50))
                appendLine()
                repository.description?.let { appendLine("Description: $it") }
                appendLine("URL: ${repository.html_url}")
                appendLine()
                appendLine("Statistics:")
                appendLine("  Stars: ${repository.stargazers_count}")
                appendLine("  Forks: ${repository.forks_count}")
                appendLine("  Watchers: ${repository.watchers_count}")
                appendLine("  Open Issues: ${repository.open_issues_count}")
                appendLine()
                repository.language?.let { appendLine("Primary Language: $it") }
                repository.license?.name?.let { appendLine("License: $it") }
                repository.default_branch?.let { appendLine("Default Branch: $it") }
                appendLine()
                appendLine("Status:")
                appendLine("  Fork: ${if (repository.fork) "Yes" else "No"}")
                appendLine("  Archived: ${if (repository.archived) "Yes" else "No"}")
                repository.visibility?.let { appendLine("  Visibility: $it") }
                appendLine()
                repository.topics?.takeIf { it.isNotEmpty() }?.let {
                    appendLine("Topics: ${it.joinToString(", ")}")
                }
                appendLine()
                appendLine("Dates:")
                repository.created_at?.let { appendLine("  Created: $it") }
                repository.updated_at?.let { appendLine("  Updated: $it") }
                repository.pushed_at?.let { appendLine("  Last Push: $it") }
            }

            ToolResult.Success(listOf(ToolContent.Text(result)))
        } catch (e: Exception) {
            logger.error("GitHub repo info error: ${e.message}", e)
            ToolResult.Error("Failed to get repository info: ${e.message}")
        }
    }
}

/**
 * MCP Tool for getting GitHub user/organization information
 */
class GitHubUserInfoTool(
    private val client: GitHubClient = GitHubClient()
) : McpTool {

    private val logger = LoggerFactory.getLogger(GitHubUserInfoTool::class.java)

    override val name: String = "github_user_info"

    override val description: String = """
        Получить информацию о пользователе или организации на GitHub: профиль, био, локация, количество репозиториев и подписчиков.
        Работает с публичными профилями.
    """.trimIndent()

    override val fewShotExamples: List<FewShotExample> = listOf(
        example("Кто такой torvalds на GitHub?", "username" to "torvalds"),
        example("Покажи профиль Google на GitHub", "username" to "google"),
        example("Информация о JetBrains", "username" to "JetBrains"),
        example("Расскажи про пользователя gvanrossum", "username" to "gvanrossum")
    )

    override val inputSchema: JsonSchema = JsonSchema(
        type = "object",
        properties = mapOf(
            "username" to PropertySchema(
                type = "string",
                description = "GitHub username or organization name"
            )
        ),
        required = listOf("username"),
        description = "Username is required"
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        logger.info("Executing github_user_info with arguments: $arguments")

        val username = arguments["username"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("Missing required parameter: username")

        return try {
            val user = client.getUser(username)
                ?: return ToolResult.Error("User not found: $username")

            val result = buildString {
                appendLine("${user.type}: ${user.login}")
                appendLine("=".repeat(50))
                appendLine()
                user.name?.let { appendLine("Name: $it") }
                appendLine("URL: ${user.html_url}")
                appendLine()
                user.bio?.let { appendLine("Bio: $it") }
                appendLine()
                user.company?.let { appendLine("Company: $it") }
                user.location?.let { appendLine("Location: $it") }
                user.blog?.takeIf { it.isNotBlank() }?.let { appendLine("Website: $it") }
                user.email?.let { appendLine("Email: $it") }
                user.twitter_username?.let { appendLine("Twitter: @$it") }
                appendLine()
                appendLine("Statistics:")
                appendLine("  Public Repos: ${user.public_repos}")
                appendLine("  Public Gists: ${user.public_gists}")
                appendLine("  Followers: ${user.followers}")
                appendLine("  Following: ${user.following}")
                appendLine()
                user.created_at?.let { appendLine("Member Since: $it") }
            }

            ToolResult.Success(listOf(ToolContent.Text(result)))
        } catch (e: Exception) {
            logger.error("GitHub user info error: ${e.message}", e)
            ToolResult.Error("Failed to get user info: ${e.message}")
        }
    }
}

/**
 * MCP Tool for searching GitHub repositories
 */
class GitHubSearchReposTool(
    private val client: GitHubClient = GitHubClient()
) : McpTool {

    private val logger = LoggerFactory.getLogger(GitHubSearchReposTool::class.java)

    override val name: String = "github_search_repos"

    override val description: String = """
        Поиск репозиториев на GitHub по ключевым словам, языку программирования и другим критериям.
        Можно сортировать по звёздам, форкам или дате обновления.
    """.trimIndent()

    override val fewShotExamples: List<FewShotExample> = listOf(
        example("Найди библиотеки для MCP на Kotlin", "query" to "mcp", "language" to "Kotlin", "per_page" to 5),
        example("Популярные репозитории по машинному обучению", "query" to "machine learning", "sort" to "stars", "per_page" to 10),
        example("Поищи React компоненты на TypeScript", "query" to "react component", "language" to "TypeScript"),
        example("Найди awesome списки", "query" to "awesome", "sort" to "stars", "per_page" to 5),
        example("Репозитории про нейросети", "query" to "neural network deep learning", "sort" to "stars")
    )

    override val inputSchema: JsonSchema = JsonSchema(
        type = "object",
        properties = mapOf(
            "query" to PropertySchema(
                type = "string",
                description = "Search query (keywords, topics, etc.)"
            ),
            "language" to PropertySchema(
                type = "string",
                description = "Filter by programming language (e.g., 'Kotlin', 'Python', 'JavaScript')"
            ),
            "sort" to PropertySchema(
                type = "string",
                description = "Sort results by",
                enum = listOf("stars", "forks", "help-wanted-issues", "updated"),
                default = JsonPrimitive("best-match")
            ),
            "order" to PropertySchema(
                type = "string",
                description = "Sort order",
                enum = listOf("asc", "desc"),
                default = JsonPrimitive("desc")
            ),
            "per_page" to PropertySchema(
                type = "integer",
                description = "Number of results to return (1-100, default: 10)"
            )
        ),
        required = listOf("query"),
        description = "Query is required, other parameters are optional"
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        logger.info("Executing github_search_repos with arguments: $arguments")

        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("Missing required parameter: query")
        val language = arguments["language"]?.jsonPrimitive?.contentOrNull
        val sort = arguments["sort"]?.jsonPrimitive?.contentOrNull
        val order = arguments["order"]?.jsonPrimitive?.contentOrNull
        val perPage = arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 10

        return try {
            val searchResult = client.searchRepositories(
                query = query,
                language = language,
                sort = sort,
                order = order,
                perPage = perPage
            ) ?: return ToolResult.Error("Search failed")

            val result = buildString {
                appendLine("Search Results for: $query")
                language?.let { appendLine("Language filter: $it") }
                appendLine("=".repeat(50))
                appendLine("Found ${searchResult.total_count} repositories (showing ${searchResult.items.size})")
                appendLine()

                searchResult.items.forEachIndexed { index, repo ->
                    appendLine("${index + 1}. ${repo.full_name}")
                    repo.description?.let { appendLine("   $it") }
                    appendLine("   Stars: ${repo.stargazers_count} | Forks: ${repo.forks_count} | Language: ${repo.language ?: "N/A"}")
                    appendLine("   URL: ${repo.html_url}")
                    appendLine()
                }
            }

            ToolResult.Success(listOf(ToolContent.Text(result)))
        } catch (e: Exception) {
            logger.error("GitHub search error: ${e.message}", e)
            ToolResult.Error("Failed to search repositories: ${e.message}")
        }
    }
}

/**
 * MCP Tool for listing GitHub issues and pull requests
 */
class GitHubListIssuesTool(
    private val client: GitHubClient = GitHubClient()
) : McpTool {

    private val logger = LoggerFactory.getLogger(GitHubListIssuesTool::class.java)

    override val name: String = "github_list_issues"

    override val description: String = """
        Получить список issues и pull requests для GitHub репозитория.
        Можно фильтровать по статусу (open/closed/all) и меткам (labels).
        Работает с публичными репозиториями.
    """.trimIndent()

    override val fewShotExamples: List<FewShotExample> = listOf(
        example("Покажи открытые issues в vscode", "owner" to "microsoft", "repo" to "vscode", "state" to "open", "per_page" to 10),
        example("Закрытые issues в kotlin", "owner" to "JetBrains", "repo" to "kotlin", "state" to "closed", "per_page" to 5),
        example("Баги в react", "owner" to "facebook", "repo" to "react", "labels" to "bug"),
        example("Issues для новичков в next.js", "owner" to "vercel", "repo" to "next.js", "labels" to "good first issue"),
        example("Все проблемы в tensorflow", "owner" to "tensorflow", "repo" to "tensorflow", "state" to "all", "per_page" to 10)
    )

    override val inputSchema: JsonSchema = JsonSchema(
        type = "object",
        properties = mapOf(
            "owner" to PropertySchema(
                type = "string",
                description = "Repository owner (username or organization name)"
            ),
            "repo" to PropertySchema(
                type = "string",
                description = "Repository name"
            ),
            "state" to PropertySchema(
                type = "string",
                description = "Filter by issue state",
                enum = listOf("open", "closed", "all"),
                default = JsonPrimitive("open")
            ),
            "per_page" to PropertySchema(
                type = "integer",
                description = "Number of issues to return (1-100, default: 10)"
            ),
            "labels" to PropertySchema(
                type = "string",
                description = "Comma-separated list of label names (e.g., 'bug,enhancement')"
            )
        ),
        required = listOf("owner", "repo"),
        description = "Both 'owner' and 'repo' are required"
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        logger.info("Executing github_list_issues with arguments: $arguments")

        val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("Missing required parameter: owner")
        val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("Missing required parameter: repo")
        val state = arguments["state"]?.jsonPrimitive?.contentOrNull ?: "open"
        val perPage = arguments["per_page"]?.jsonPrimitive?.intOrNull ?: 10
        val labels = arguments["labels"]?.jsonPrimitive?.contentOrNull

        return try {
            val issues = client.listIssues(
                owner = owner,
                repo = repo,
                state = state,
                perPage = perPage,
                labels = labels
            ) ?: return ToolResult.Error("Failed to get issues for $owner/$repo")

            val result = buildString {
                appendLine("Issues for: $owner/$repo")
                appendLine("State: $state")
                labels?.let { appendLine("Labels: $it") }
                appendLine("=".repeat(50))
                appendLine("Showing ${issues.size} issues/PRs")
                appendLine()

                if (issues.isEmpty()) {
                    appendLine("No issues found matching the criteria.")
                } else {
                    issues.forEach { issue ->
                        val typeIndicator = if (issue.pull_request != null) "[PR]" else "[Issue]"
                        val stateEmoji = when (issue.state) {
                            "open" -> "OPEN"
                            "closed" -> "CLOSED"
                            else -> issue.state.uppercase()
                        }

                        appendLine("$typeIndicator #${issue.number}: ${issue.title}")
                        appendLine("   Status: $stateEmoji | Comments: ${issue.comments}")
                        issue.user?.let { appendLine("   Author: ${it.login}") }
                        if (issue.labels.isNotEmpty()) {
                            appendLine("   Labels: ${issue.labels.joinToString(", ") { it.name }}")
                        }
                        issue.created_at?.let { appendLine("   Created: $it") }
                        appendLine("   URL: ${issue.html_url}")
                        appendLine()
                    }
                }
            }

            ToolResult.Success(listOf(ToolContent.Text(result)))
        } catch (e: Exception) {
            logger.error("GitHub list issues error: ${e.message}", e)
            ToolResult.Error("Failed to list issues: ${e.message}")
        }
    }
}

/**
 * Built-in GitHub Plugin that provides GitHub tools
 */
class GitHubPlugin : McpPlugin {
    private val client = GitHubClient()

    override val name: String = "github"
    override val version: String = "1.0.0"
    override val description: String = "GitHub public API tools for repository, user, search, and issues"

    override fun getTools(): List<McpTool> = listOf(
        GitHubRepoInfoTool(client),
        GitHubUserInfoTool(client),
        GitHubSearchReposTool(client),
        GitHubListIssuesTool(client)
    )

    override fun shutdown() {
        client.close()
    }
}
