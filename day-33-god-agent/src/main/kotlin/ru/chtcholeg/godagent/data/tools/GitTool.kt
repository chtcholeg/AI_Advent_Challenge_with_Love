package ru.chtcholeg.godagent.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class GitLogTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_log"
    override val description = "Get the last N git commits in the repository"
    override val parametersDescription = """{"n": number (optional, default 5)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        val n = args["n"]?.jsonPrimitive?.intOrNull ?: 5
        runGit(repoPathProvider(), "log", "--oneline", "--decorate", "-$n")
    }
}

class GitDiffTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_diff"
    override val description = "Get the diff for a specific commit SHA or the current working tree (unstaged changes)"
    override val parametersDescription = """{"sha": string (optional, if omitted shows unstaged diff)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        val sha = args["sha"]?.jsonPrimitive?.contentOrNull
        if (sha != null) runGit(repoPathProvider(), "show", "--stat", "-p", sha)
        else runGit(repoPathProvider(), "diff")
    }
}

class GitStatusTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_status"
    override val description = "Show the working tree status"
    override val parametersDescription = "{}"

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        runGit(repoPathProvider(), "status", "--short")
    }
}

class GitBranchesTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_branches"
    override val description = "List all branches"
    override val parametersDescription = "{}"

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        runGit(repoPathProvider(), "branch", "-a")
    }
}

class GitCommitTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_commit"
    override val description = "Stage all changes and create a git commit with the given message"
    override val parametersDescription = """{"message": string}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        val message = args["message"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext "Error: 'message' is required"
        val repoPath = repoPathProvider()
        val addResult = runGit(repoPath, "add", "-A")
        val commitResult = runGit(repoPath, "commit", "-m", message)
        buildString {
            if (addResult != "(no output)") appendLine("git add: $addResult")
            append("git commit: $commitResult")
        }
    }
}

class GitPushTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_push"
    override val description = "Push commits to the remote repository"
    override val parametersDescription = """{"remote": string (optional, default "origin"), "branch": string (optional, current branch by default)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        val branch = args["branch"]?.jsonPrimitive?.contentOrNull
        if (branch != null) runGit(repoPathProvider(), "push", remote, branch)
        else runGit(repoPathProvider(), "push", remote)
    }
}

class GitPullTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_pull"
    override val description = "Pull latest changes from the remote repository"
    override val parametersDescription = """{"remote": string (optional, default "origin"), "branch": string (optional)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        val remote = args["remote"]?.jsonPrimitive?.contentOrNull ?: "origin"
        val branch = args["branch"]?.jsonPrimitive?.contentOrNull
        if (branch != null) runGit(repoPathProvider(), "pull", remote, branch)
        else runGit(repoPathProvider(), "pull", remote)
    }
}

class GitBlameTool(private val repoPathProvider: () -> String) : AgentTool {
    override val name = "git_blame"
    override val description = "Show who last modified each line of a file"
    override val parametersDescription = """{"file": string, "start_line": number (optional), "end_line": number (optional)}"""

    override suspend fun execute(argsJson: String): String = withContext(Dispatchers.IO) {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
        val file = args["file"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext "Error: 'file' is required"
        val startLine = args["start_line"]?.jsonPrimitive?.intOrNull
        val endLine = args["end_line"]?.jsonPrimitive?.intOrNull
        if (startLine != null && endLine != null) {
            runGit(repoPathProvider(), "blame", "-L", "$startLine,$endLine", file)
        } else {
            runGit(repoPathProvider(), "blame", file)
        }
    }
}

fun runGit(repoPath: String, vararg args: String): String {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(java.io.File(repoPath))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.trim().ifEmpty { "(no output)" }
    } catch (e: Exception) {
        "git error: ${e.message}"
    }
}
