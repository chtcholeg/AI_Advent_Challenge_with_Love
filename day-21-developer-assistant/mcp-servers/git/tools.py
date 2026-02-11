"""Git MCP tool definitions.

Each tool maps to a git command and follows the MCP tool interface:
- name: unique identifier used in tools/call
- description: shown to AI for function selection
- input_schema: JSON Schema describing accepted parameters
- execute(): async handler returning ToolResult
"""

import logging
from pathlib import Path

import sys
sys.path.append(str(Path(__file__).parent.parent))

from shared import ToolResult, BaseTool
from .git_client import GitClient

logger = logging.getLogger(__name__)


class GitTool(BaseTool):
    """Base class for all Git MCP tools."""

    def __init__(self, client: GitClient):
        self.client = client

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Tool: git_status
# ---------------------------------------------------------------------------

class GitStatusTool(GitTool):
    name = "git_status"
    description = (
        "Show the working tree status. "
        "Displays which files are modified, staged, or untracked."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "short": {
                "type": "boolean",
                "description": "Use short format output (default: false)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        short = arguments.get("short", False)

        try:
            result = await self.client.status(short=short)
            return ToolResult(result["output"])
        except Exception as e:
            logger.error(f"GitStatusTool error: {e}")
            return ToolResult(f"Failed to get git status: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_log
# ---------------------------------------------------------------------------

class GitLogTool(GitTool):
    name = "git_log"
    description = (
        "Show commit logs. "
        "Displays commit history with author, date, and message."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "max_count": {
                "type": "integer",
                "description": "Maximum number of commits to show (default: 10, max: 100)",
            },
            "branch": {
                "type": "string",
                "description": "Branch name to show log for (default: current branch)",
            },
            "oneline": {
                "type": "boolean",
                "description": "Use compact oneline format (default: false)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        max_count = min(arguments.get("max_count", 10), 100)
        branch = arguments.get("branch")
        oneline = arguments.get("oneline", False)

        try:
            result = await self.client.log(
                max_count=max_count,
                branch=branch,
                oneline=oneline,
            )

            lines = [
                f"Git Log (branch: {result['branch']})",
                "=" * 50,
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitLogTool error: {e}")
            return ToolResult(f"Failed to get git log: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_diff
# ---------------------------------------------------------------------------

class GitDiffTool(GitTool):
    name = "git_diff"
    description = (
        "Show changes between commits, commit and working tree, etc. "
        "Can show unstaged changes, staged changes, or diff of specific file."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "cached": {
                "type": "boolean",
                "description": "Show staged changes (default: false - shows unstaged)",
            },
            "file_path": {
                "type": "string",
                "description": "Show diff for specific file only",
            },
            "commit": {
                "type": "string",
                "description": "Compare with specific commit (hash or ref)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        cached = arguments.get("cached", False)
        file_path = arguments.get("file_path")
        commit = arguments.get("commit")

        try:
            result = await self.client.diff(
                cached=cached,
                file_path=file_path,
                commit=commit,
            )

            if not result["has_changes"]:
                return ToolResult("No changes to display")

            diff_type = "staged" if cached else "unstaged"
            lines = [
                f"Git Diff ({diff_type} changes)",
                "=" * 50,
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitDiffTool error: {e}")
            return ToolResult(f"Failed to get git diff: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_branch_list
# ---------------------------------------------------------------------------

class GitBranchListTool(GitTool):
    name = "git_branch_list"
    description = (
        "List all branches. "
        "Shows local branches and optionally remote-tracking branches."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "all": {
                "type": "boolean",
                "description": "Include remote-tracking branches (default: false)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        all_branches = arguments.get("all", False)

        try:
            result = await self.client.branch_list(all_branches=all_branches)

            lines = [
                "Git Branches",
                "=" * 50,
                f"Current: {result['current']}",
                "",
                "Branches:",
            ]

            for branch in result["branches"]:
                marker = "* " if branch == result["current"] else "  "
                lines.append(f"{marker}{branch}")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitBranchListTool error: {e}")
            return ToolResult(f"Failed to list branches: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_show_commit
# ---------------------------------------------------------------------------

class GitShowCommitTool(GitTool):
    name = "git_show_commit"
    description = (
        "Show information about a specific commit. "
        "Displays commit metadata and changes introduced."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "commit": {
                "type": "string",
                "description": "Commit hash or reference (default: HEAD)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        commit = arguments.get("commit", "HEAD")

        try:
            result = await self.client.show_commit(commit=commit)

            lines = [
                f"Git Show Commit: {commit}",
                "=" * 50,
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitShowCommitTool error: {e}")
            return ToolResult(f"Failed to show commit: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_blame
# ---------------------------------------------------------------------------

class GitBlameTool(GitTool):
    name = "git_blame"
    description = (
        "Show what revision and author last modified each line of a file. "
        "Useful for understanding code history and authorship."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "file_path": {
                "type": "string",
                "description": "Path to file to blame",
            },
        },
        "required": ["file_path"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        file_path = arguments.get("file_path", "").strip()

        if not file_path:
            return ToolResult("Missing required parameter: file_path", is_error=True)

        try:
            result = await self.client.blame(file_path=file_path)

            lines = [
                f"Git Blame: {file_path}",
                "=" * 50,
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitBlameTool error: {e}")
            return ToolResult(f"Failed to blame file: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_add
# ---------------------------------------------------------------------------

class GitAddTool(GitTool):
    name = "git_add"
    description = (
        "Add file contents to the staging area. "
        "Prepares files to be included in the next commit. "
        "Use '.' to add all changes."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "paths": {
                "type": "array",
                "items": {"type": "string"},
                "description": "Paths to add (files or directories). Use ['.'] for all changes.",
            },
        },
        "required": ["paths"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        paths = arguments.get("paths", [])

        if not paths:
            return ToolResult("Missing required parameter: paths", is_error=True)

        try:
            result = await self.client.add(*paths)

            lines = [
                "Git Add - Files staged successfully",
                "=" * 50,
                "",
                f"Staged paths: {', '.join(result['paths'])}",
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitAddTool error: {e}")
            return ToolResult(f"Failed to add files: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_commit
# ---------------------------------------------------------------------------

class GitCommitTool(GitTool):
    name = "git_commit"
    description = (
        "Record changes to the repository. "
        "Creates a new commit with staged changes."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "message": {
                "type": "string",
                "description": "Commit message describing the changes",
            },
            "amend": {
                "type": "boolean",
                "description": "Amend previous commit instead of creating new one (default: false)",
            },
        },
        "required": ["message"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        message = arguments.get("message", "").strip()
        amend = arguments.get("amend", False)

        if not message:
            return ToolResult("Missing required parameter: message", is_error=True)

        try:
            result = await self.client.commit(message=message, amend=amend)

            action = "amended" if amend else "created"
            lines = [
                f"Git Commit - Successfully {action}",
                "=" * 50,
                "",
                f"Message: {message}",
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitCommitTool error: {e}")
            return ToolResult(f"Failed to commit: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_checkout
# ---------------------------------------------------------------------------

class GitCheckoutTool(GitTool):
    name = "git_checkout"
    description = (
        "Switch branches or restore working tree files. "
        "Can create new branch with -b option."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "branch": {
                "type": "string",
                "description": "Branch name to checkout",
            },
            "create": {
                "type": "boolean",
                "description": "Create new branch before checkout (default: false)",
            },
        },
        "required": ["branch"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        branch = arguments.get("branch", "").strip()
        create = arguments.get("create", False)

        if not branch:
            return ToolResult("Missing required parameter: branch", is_error=True)

        try:
            result = await self.client.checkout(branch=branch, create=create)

            action = "created and checked out" if create else "checked out"
            lines = [
                f"Git Checkout - Successfully {action}",
                "=" * 50,
                "",
                f"Branch: {branch}",
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitCheckoutTool error: {e}")
            return ToolResult(f"Failed to checkout: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_pull
# ---------------------------------------------------------------------------

class GitPullTool(GitTool):
    name = "git_pull"
    description = (
        "Fetch from and integrate with another repository or local branch. "
        "Downloads changes from remote and merges them."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "remote": {
                "type": "string",
                "description": "Remote name (default: origin)",
            },
            "branch": {
                "type": "string",
                "description": "Branch name (default: current branch)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        remote = arguments.get("remote", "origin")
        branch = arguments.get("branch")

        try:
            result = await self.client.pull(remote=remote, branch=branch)

            lines = [
                "Git Pull - Successfully updated",
                "=" * 50,
                "",
                f"Remote: {remote}",
                f"Branch: {result['branch']}",
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitPullTool error: {e}")
            return ToolResult(f"Failed to pull: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: git_push
# ---------------------------------------------------------------------------

class GitPushTool(GitTool):
    name = "git_push"
    description = (
        "Update remote refs along with associated objects. "
        "Uploads local commits to remote repository."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "remote": {
                "type": "string",
                "description": "Remote name (default: origin)",
            },
            "branch": {
                "type": "string",
                "description": "Branch name (default: current branch)",
            },
            "set_upstream": {
                "type": "boolean",
                "description": "Set upstream branch for tracking (default: false)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        remote = arguments.get("remote", "origin")
        branch = arguments.get("branch")
        set_upstream = arguments.get("set_upstream", False)

        try:
            result = await self.client.push(
                remote=remote,
                branch=branch,
                set_upstream=set_upstream,
            )

            lines = [
                "Git Push - Successfully pushed",
                "=" * 50,
                "",
                f"Remote: {remote}",
                f"Branch: {result['branch']}",
                "",
                result["output"],
            ]

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GitPushTool error: {e}")
            return ToolResult(f"Failed to push: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: github_whoami
# ---------------------------------------------------------------------------

class GitHubWhoAmITool(GitTool):
    name = "github_whoami"
    description = (
        "Show authenticated GitHub user info. "
        "Verifies the GitHub Personal Access Token is valid and returns the user profile."
    )
    input_schema = {
        "type": "object",
        "properties": {},
    }

    async def execute(self, arguments: dict) -> ToolResult:
        try:
            info = await self.client.github_user_info()

            lines = [
                "GitHub User Info",
                "=" * 50,
                "",
                f"Login:        {info['login']}",
                f"Name:         {info['name'] or 'N/A'}",
                f"Email:        {info['email'] or 'N/A'}",
                f"Public repos: {info['public_repos']}",
            ]

            return ToolResult("\n".join(lines))
        except ValueError as e:
            return ToolResult(
                "GitHub token is not configured. "
                "Set GITHUB_TOKEN env var or pass --github-token to the server.",
                is_error=True,
            )
        except Exception as e:
            logger.error(f"GitHubWhoAmITool error: {e}")
            return ToolResult(f"Failed to get GitHub user info: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: github_pr_list
# ---------------------------------------------------------------------------

class GitHubPrListTool(GitTool):
    name = "github_pr_list"
    description = (
        "List pull requests for the GitHub repository. "
        "Can filter by state (open, closed, all)."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "state": {
                "type": "string",
                "enum": ["open", "closed", "all"],
                "description": "Filter by PR state (default: open)",
            },
            "limit": {
                "type": "integer",
                "description": "Maximum number of PRs to return (default: 10, max: 100)",
            },
        },
    }

    async def execute(self, arguments: dict) -> ToolResult:
        state = arguments.get("state", "open")
        limit = min(arguments.get("limit", 10), 100)

        try:
            result = await self.client.github_pr_list(state=state, limit=limit)

            lines = [
                f"GitHub Pull Requests ({state})",
                "=" * 50,
                "",
            ]

            for pr in result["pull_requests"]:
                lines.append(
                    f"#{pr['number']} [{pr['state']}] {pr['title']} "
                    f"(by {pr['author']}, {pr['head']} → {pr['base']})"
                )

            if not result["pull_requests"]:
                lines.append("No pull requests found.")

            return ToolResult("\n".join(lines))
        except ValueError as e:
            return ToolResult(
                "GitHub token is not configured. "
                "Set GITHUB_TOKEN env var or pass --github-token to the server.",
                is_error=True,
            )
        except Exception as e:
            logger.error(f"GitHubPrListTool error: {e}")
            return ToolResult(f"Failed to list pull requests: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: github_pr_get
# ---------------------------------------------------------------------------

class GitHubPrGetTool(GitTool):
    name = "github_pr_get"
    description = (
        "Get details of a specific GitHub pull request. "
        "Returns title, body, author, labels, head/base branches, and change stats."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "pr_number": {
                "type": "integer",
                "description": "Pull request number",
            },
        },
        "required": ["pr_number"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        pr_number = arguments.get("pr_number")

        if pr_number is None:
            return ToolResult("Missing required parameter: pr_number", is_error=True)

        try:
            pr = await self.client.github_pr_get(pr_number=pr_number)

            labels = ", ".join(pr["labels"]) if pr["labels"] else "none"
            lines = [
                f"GitHub PR #{pr['number']}: {pr['title']}",
                "=" * 50,
                "",
                f"State:         {pr['state']}",
                f"Author:        {pr['author']}",
                f"Branch:        {pr['head']} → {pr['base']}",
                f"Labels:        {labels}",
                f"Merged:        {pr['merged']}",
                f"Mergeable:     {pr['mergeable']}",
                f"Changes:       +{pr['additions']} -{pr['deletions']} in {pr['changed_files']} file(s)",
                f"Created:       {pr['created_at']}",
                f"Updated:       {pr['updated_at']}",
                "",
                "Description:",
                pr["body"] or "(no description)",
            ]

            return ToolResult("\n".join(lines))
        except ValueError as e:
            return ToolResult(
                "GitHub token is not configured. "
                "Set GITHUB_TOKEN env var or pass --github-token to the server.",
                is_error=True,
            )
        except Exception as e:
            logger.error(f"GitHubPrGetTool error: {e}")
            return ToolResult(f"Failed to get pull request: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: github_pr_diff
# ---------------------------------------------------------------------------

class GitHubPrDiffTool(GitTool):
    name = "github_pr_diff"
    description = (
        "Get the diff (patch) of a GitHub pull request. "
        "Returns the unified diff text. Large diffs are truncated to 5000 lines."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "pr_number": {
                "type": "integer",
                "description": "Pull request number",
            },
        },
        "required": ["pr_number"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        pr_number = arguments.get("pr_number")

        if pr_number is None:
            return ToolResult("Missing required parameter: pr_number", is_error=True)

        try:
            result = await self.client.github_pr_diff(pr_number=pr_number)

            diff_lines = result["diff"].split("\n")
            truncated = len(diff_lines) > 5000
            if truncated:
                diff_lines = diff_lines[:5000]

            lines = [
                f"GitHub PR #{pr_number} Diff",
                "=" * 50,
                "",
            ]
            lines.extend(diff_lines)
            if truncated:
                lines.append("\n... (diff truncated at 5000 lines)")

            return ToolResult("\n".join(lines))
        except ValueError as e:
            return ToolResult(
                "GitHub token is not configured. "
                "Set GITHUB_TOKEN env var or pass --github-token to the server.",
                is_error=True,
            )
        except Exception as e:
            logger.error(f"GitHubPrDiffTool error: {e}")
            return ToolResult(f"Failed to get PR diff: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: github_pr_files
# ---------------------------------------------------------------------------

class GitHubPrFilesTool(GitTool):
    name = "github_pr_files"
    description = (
        "List files changed in a GitHub pull request. "
        "Returns filename, status (added/modified/removed), additions, and deletions for each file."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "pr_number": {
                "type": "integer",
                "description": "Pull request number",
            },
        },
        "required": ["pr_number"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        pr_number = arguments.get("pr_number")

        if pr_number is None:
            return ToolResult("Missing required parameter: pr_number", is_error=True)

        try:
            result = await self.client.github_pr_files(pr_number=pr_number)

            lines = [
                f"GitHub PR #{pr_number} Changed Files ({result['total']} files)",
                "=" * 50,
                "",
            ]

            for f in result["files"]:
                lines.append(
                    f"  [{f['status']}] {f['filename']} "
                    f"(+{f['additions']} -{f['deletions']})"
                )

            return ToolResult("\n".join(lines))
        except ValueError as e:
            return ToolResult(
                "GitHub token is not configured. "
                "Set GITHUB_TOKEN env var or pass --github-token to the server.",
                is_error=True,
            )
        except Exception as e:
            logger.error(f"GitHubPrFilesTool error: {e}")
            return ToolResult(f"Failed to get PR files: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: GitClient) -> list[GitTool]:
    """Return all registered Git MCP tools."""
    return [
        # Read-only operations
        GitStatusTool(client),
        GitLogTool(client),
        GitDiffTool(client),
        GitBranchListTool(client),
        GitShowCommitTool(client),
        GitBlameTool(client),
        # Write operations
        GitAddTool(client),
        GitCommitTool(client),
        GitCheckoutTool(client),
        GitPullTool(client),
        GitPushTool(client),
        # GitHub operations
        GitHubWhoAmITool(client),
        GitHubPrListTool(client),
        GitHubPrGetTool(client),
        GitHubPrDiffTool(client),
        GitHubPrFilesTool(client),
    ]
