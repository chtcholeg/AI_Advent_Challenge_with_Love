"""GitHub MCP tool definitions.

Each tool maps to a GitHub REST API endpoint and follows the MCP tool interface:
- name: unique identifier used in tools/call
- description: shown to AI for function selection
- input_schema: JSON Schema describing accepted parameters
- execute(): async handler returning ToolResult
"""

import base64
import logging

from shared import ToolResult, BaseTool
from .github_client import GitHubClient

logger = logging.getLogger(__name__)


class GitHubTool(BaseTool):
    """Base class for all GitHub MCP tools."""

    def __init__(self, client: GitHubClient):
        self.client = client

    @staticmethod
    def _parse_owner_repo(arguments: dict) -> tuple:
        """Extract owner and repo, handling 'owner/repo' passed as single value in owner field."""
        owner = arguments.get("owner", "")
        repo = arguments.get("repo", "")

        # AI sometimes passes "owner/repo" as the owner value
        if "/" in owner:
            parts = owner.split("/", 1)
            owner = parts[0]
            if not repo:
                repo = parts[1]

        return owner or None, repo or None

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Tool: get_repo_info
# ---------------------------------------------------------------------------

class RepoInfoTool(GitHubTool):
    name = "get_repo_info"
    description = (
        "Get information about a GitHub repository: description, stars, forks, "
        "language, license, topics, creation and update dates. "
        "Works with public repositories."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "owner": {"type": "string", "description": "Repository owner (username or organization)"},
            "repo": {"type": "string", "description": "Repository name"},
        },
        "required": ["owner", "repo"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        owner, repo = self._parse_owner_repo(arguments)
        if not owner or not repo:
            return ToolResult("Missing required parameters: owner and repo", is_error=True)

        try:
            data = await self.client.get_repo(owner, repo)

            lines = [
                f"Repository: {data['full_name']}",
                "=" * 50,
                "",
            ]
            if data.get("description"):
                lines.append(f"Description: {data['description']}")
            lines.append(f"URL: {data['html_url']}")
            lines.append("")
            lines.append("Statistics:")
            lines.append(f"  Stars:        {data.get('stargazers_count', 0)}")
            lines.append(f"  Forks:        {data.get('forks_count', 0)}")
            lines.append(f"  Watchers:     {data.get('watchers_count', 0)}")
            lines.append(f"  Open Issues:  {data.get('open_issues_count', 0)}")
            lines.append("")
            if data.get("language"):
                lines.append(f"Primary Language: {data['language']}")
            if data.get("license"):
                lines.append(f"License: {data['license'].get('name', 'N/A')}")
            if data.get("default_branch"):
                lines.append(f"Default Branch: {data['default_branch']}")
            lines.append("")
            lines.append(f"Fork:     {'Yes' if data.get('fork') else 'No'}")
            lines.append(f"Archived: {'Yes' if data.get('archived') else 'No'}")
            if data.get("visibility"):
                lines.append(f"Visibility: {data['visibility']}")
            lines.append("")
            topics = data.get("topics", [])
            if topics:
                lines.append(f"Topics: {', '.join(topics)}")
                lines.append("")
            lines.append("Dates:")
            if data.get("created_at"):
                lines.append(f"  Created:    {data['created_at']}")
            if data.get("updated_at"):
                lines.append(f"  Updated:    {data['updated_at']}")
            if data.get("pushed_at"):
                lines.append(f"  Last Push:  {data['pushed_at']}")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"RepoInfoTool error: {e}")
            return ToolResult(f"Failed to get repository info: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_repo_branches
# ---------------------------------------------------------------------------

class BranchesTool(GitHubTool):
    name = "get_repo_branches"
    description = (
        "List branches of a GitHub repository. "
        "Returns branch names, latest commit SHA, and protection status."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "owner": {"type": "string", "description": "Repository owner"},
            "repo": {"type": "string", "description": "Repository name"},
            "per_page": {"type": "integer", "description": "Number of branches to return (1-100, default: 30)"},
        },
        "required": ["owner", "repo"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        owner, repo = self._parse_owner_repo(arguments)
        per_page = arguments.get("per_page", 30)
        if not owner or not repo:
            return ToolResult("Missing required parameters: owner and repo", is_error=True)

        try:
            branches = await self.client.get_branches(owner, repo, per_page)
            lines = [
                f"Branches for {owner}/{repo}:",
                "=" * 50,
                f"Shown: {len(branches)}",
                "",
            ]
            if not branches:
                lines.append("No branches found.")
            else:
                for b in branches:
                    protected = " [protected]" if b.get("protected") else ""
                    lines.append(f"  {b['name']}{protected}")
                    lines.append(f"    Latest commit: {b['commit']['sha'][:8]}")
                    lines.append("")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"BranchesTool error: {e}")
            return ToolResult(f"Failed to get branches: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_repo_tags
# ---------------------------------------------------------------------------

class TagsTool(GitHubTool):
    name = "get_repo_tags"
    description = (
        "List tags (releases) of a GitHub repository. "
        "Shows tag name and associated commit SHA."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "owner": {"type": "string", "description": "Repository owner"},
            "repo": {"type": "string", "description": "Repository name"},
            "per_page": {"type": "integer", "description": "Number of tags to return (1-100, default: 30)"},
        },
        "required": ["owner", "repo"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        owner, repo = self._parse_owner_repo(arguments)
        per_page = arguments.get("per_page", 30)
        if not owner or not repo:
            return ToolResult("Missing required parameters: owner and repo", is_error=True)

        try:
            tags = await self.client.get_tags(owner, repo, per_page)
            lines = [
                f"Tags for {owner}/{repo}:",
                "=" * 50,
                f"Shown: {len(tags)}",
                "",
            ]
            if not tags:
                lines.append("No tags found.")
            else:
                for t in tags:
                    lines.append(f"  {t['name']}")
                    lines.append(f"    Commit: {t['commit']['sha'][:8]}")
                    lines.append("")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"TagsTool error: {e}")
            return ToolResult(f"Failed to get tags: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_readme
# ---------------------------------------------------------------------------

class ReadmeTool(GitHubTool):
    name = "get_readme"
    description = (
        "Fetch the README content of a GitHub repository. "
        "Returns the decoded text. Optionally specify a branch or tag."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "owner": {"type": "string", "description": "Repository owner"},
            "repo": {"type": "string", "description": "Repository name"},
            "ref": {"type": "string", "description": "Branch, tag, or commit SHA (default: default branch)"},
        },
        "required": ["owner", "repo"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        owner, repo = self._parse_owner_repo(arguments)
        ref = arguments.get("ref")
        if not owner or not repo:
            return ToolResult("Missing required parameters: owner and repo", is_error=True)

        try:
            data = await self.client.get_readme(owner, repo, ref)
            content_b64 = data.get("content", "")
            content = base64.b64decode(content_b64).decode("utf-8")

            ref_info = f" (ref: {ref})" if ref else ""
            lines = [
                f"README for {owner}/{repo}{ref_info}:",
                "=" * 50,
                "",
                content,
            ]
            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"ReadmeTool error: {e}")
            return ToolResult(f"Failed to get README: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_repo_contributors
# ---------------------------------------------------------------------------

class ContributorsTool(GitHubTool):
    name = "get_repo_contributors"
    description = (
        "Get the top contributors to a GitHub repository. "
        "Shows username, number of contributions, and profile URL."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "owner": {"type": "string", "description": "Repository owner"},
            "repo": {"type": "string", "description": "Repository name"},
            "per_page": {"type": "integer", "description": "Number of contributors to return (1-100, default: 10)"},
        },
        "required": ["owner", "repo"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        owner, repo = self._parse_owner_repo(arguments)
        per_page = arguments.get("per_page", 10)
        if not owner or not repo:
            return ToolResult("Missing required parameters: owner and repo", is_error=True)

        try:
            contributors = await self.client.get_contributors(owner, repo, per_page)
            lines = [
                f"Top contributors for {owner}/{repo}:",
                "=" * 50,
                "",
            ]
            if not contributors:
                lines.append("No contributor data available.")
            else:
                for i, c in enumerate(contributors, 1):
                    lines.append(f"  {i}. {c['login']}")
                    lines.append(f"     Contributions: {c.get('contributions', 'N/A')}")
                    lines.append(f"     Profile: {c.get('html_url', 'N/A')}")
                    lines.append("")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"ContributorsTool error: {e}")
            return ToolResult(f"Failed to get contributors: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_repo_commits
# ---------------------------------------------------------------------------

class CommitsTool(GitHubTool):
    name = "get_repo_commits"
    description = (
        "Get recent commits for a GitHub repository. "
        "Optionally filter by branch name. Shows commit SHA, message, author, and date."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "owner": {"type": "string", "description": "Repository owner"},
            "repo": {"type": "string", "description": "Repository name"},
            "branch": {"type": "string", "description": "Branch name or SHA (default: default branch)"},
            "per_page": {"type": "integer", "description": "Number of commits to return (1-100, default: 10)"},
        },
        "required": ["owner", "repo"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        owner, repo = self._parse_owner_repo(arguments)
        branch = arguments.get("branch")
        per_page = arguments.get("per_page", 10)
        if not owner or not repo:
            return ToolResult("Missing required parameters: owner and repo", is_error=True)

        try:
            commits = await self.client.get_commits(owner, repo, sha=branch, per_page=per_page)
            branch_info = f" (branch: {branch})" if branch else ""
            lines = [
                f"Recent commits for {owner}/{repo}{branch_info}:",
                "=" * 50,
                "",
            ]
            if not commits:
                lines.append("No commits found.")
            else:
                for i, c in enumerate(commits, 1):
                    sha = c["sha"][:8]
                    message = c["commit"]["message"].split("\n")[0]
                    author = c["commit"]["author"].get("name", "Unknown")
                    date = c["commit"]["author"].get("date", "")
                    lines.append(f"  {i}. [{sha}] {message}")
                    lines.append(f"     Author: {author}  Date: {date}")
                    lines.append("")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"CommitsTool error: {e}")
            return ToolResult(f"Failed to get commits: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Tool: get_user_info
# ---------------------------------------------------------------------------

class UserInfoTool(GitHubTool):
    name = "get_user_info"
    description = (
        "Get information about a GitHub user or organization: "
        "name, bio, location, number of public repositories, followers, following. "
        "Works with public profiles."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "username": {"type": "string", "description": "GitHub username or organization name"},
        },
        "required": ["username"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        username = arguments.get("username")
        if not username:
            return ToolResult("Missing required parameter: username", is_error=True)

        try:
            data = await self.client.get_user(username)

            user_type = data.get("type", "User")
            lines = [
                f"{user_type}: {data['login']}",
                "=" * 50,
                "",
            ]
            if data.get("name"):
                lines.append(f"Name: {data['name']}")
            lines.append(f"URL: {data['html_url']}")
            lines.append("")
            if data.get("bio"):
                lines.append(f"Bio: {data['bio']}")
                lines.append("")
            if data.get("company"):
                lines.append(f"Company: {data['company']}")
            if data.get("location"):
                lines.append(f"Location: {data['location']}")
            if data.get("blog"):
                lines.append(f"Website: {data['blog']}")
            if data.get("twitter_username"):
                lines.append(f"Twitter: @{data['twitter_username']}")
            lines.append("")
            lines.append("Statistics:")
            lines.append(f"  Public Repos:  {data.get('public_repos', 0)}")
            lines.append(f"  Public Gists:  {data.get('public_gists', 0)}")
            lines.append(f"  Followers:     {data.get('followers', 0)}")
            lines.append(f"  Following:     {data.get('following', 0)}")
            lines.append("")
            if data.get("created_at"):
                lines.append(f"Member Since: {data['created_at']}")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"UserInfoTool error: {e}")
            return ToolResult(f"Failed to get user info: {e}", is_error=True)


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: GitHubClient) -> list[GitHubTool]:
    """Return all registered GitHub MCP tools."""
    return [
        RepoInfoTool(client),
        BranchesTool(client),
        TagsTool(client),
        ReadmeTool(client),
        ContributorsTool(client),
        CommitsTool(client),
        UserInfoTool(client),
    ]
