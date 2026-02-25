package ru.chtcholeg.godagent.data.tools

import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.chtcholeg.godagent.domain.model.AppSettings

class ToolExecutor(
    private val httpClient: HttpClient,
    private val settingsProvider: () -> AppSettings,
    private val extraTools: List<AgentTool> = emptyList()
) {
    private val _toolStatuses = MutableStateFlow<List<ToolStatus>>(emptyList())
    val toolStatuses: StateFlow<List<ToolStatus>> = _toolStatuses.asStateFlow()

    private val tools: List<AgentTool> by lazy { buildTools() }

    private fun buildTools(): List<AgentTool> {
        val baseTools = listOf(
            // Git
            GitLogTool { settingsProvider().gitRepoPath },
            GitDiffTool { settingsProvider().gitRepoPath },
            GitStatusTool { settingsProvider().gitRepoPath },
            GitBranchesTool { settingsProvider().gitRepoPath },
            GitCommitTool { settingsProvider().gitRepoPath },
            GitPushTool { settingsProvider().gitRepoPath },
            GitPullTool { settingsProvider().gitRepoPath },
            GitBlameTool { settingsProvider().gitRepoPath },
            // GitHub
            GitHubListPRsTool(httpClient) { settingsProvider().githubToken },
            GitHubListIssuesTool(httpClient) { settingsProvider().githubToken },
            // Telegram
            TelegramSendTool(
                httpClient,
                { settingsProvider().telegramBotToken },
                { settingsProvider().telegramChatId }
            ),
            // FileOps
            FileReadTool(),
            FileWriteTool(),
            FileListTool(),
            // Utility
            GetTimeTool(),
            GetWeatherTool(httpClient),
            GetCurrencyTool(httpClient)
        )
        return baseTools + extraTools
    }

    fun getToolsPromptDescription(): String {
        return tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}. Args: ${tool.parametersDescription}"
        }
    }

    fun getToolNames(): List<String> = tools.map { it.name }

    suspend fun execute(toolName: String, argsJson: String): String {
        val tool = tools.firstOrNull { it.name == toolName }
            ?: return "Error: unknown tool '$toolName'. Available tools: ${getToolNames().joinToString(", ")}"
        return try {
            tool.execute(argsJson)
        } catch (e: Exception) {
            "Error executing $toolName: ${e.message}"
        }
    }

    fun checkStatuses() {
        val statuses = mutableListOf<ToolStatus>()
        val settings = settingsProvider()

        // Git status
        val gitOk = try {
            val result = runGit(settings.gitRepoPath, "status", "--short")
            !result.startsWith("git error")
        } catch (e: Exception) {
            false
        }
        statuses.add(
            ToolStatus(
                "git", "Git", gitOk,
                if (!gitOk) "Git не найден или репозиторий не инициализирован" else null
            )
        )

        // Telegram status
        val tgOk = settings.telegramBotToken.isNotBlank() && settings.telegramChatId.isNotBlank()
        statuses.add(
            ToolStatus(
                "telegram", "Telegram", tgOk,
                if (!tgOk) "Настройте Bot Token и Chat ID" else null
            )
        )

        // GitHub status — always available, token is optional
        statuses.add(ToolStatus("github", "GitHub", true))

        // FileOps status
        statuses.add(ToolStatus("fileops", "File Operations", true))

        // Utility tools status
        statuses.add(ToolStatus("utility", "Weather/Currency/Time", true))

        // RAG status
        val ragAvailable = settings.ragFolderPath.isNotBlank()
        statuses.add(
            ToolStatus(
                "rag", "RAG Knowledge Base", ragAvailable,
                if (!ragAvailable) "Укажите папку для RAG-индексации в настройках" else null
            )
        )

        _toolStatuses.value = statuses
    }
}
