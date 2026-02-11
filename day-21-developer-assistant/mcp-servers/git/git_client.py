"""Git client for executing git commands."""

import asyncio
import logging
import os
import stat
import tempfile
from pathlib import Path
from typing import Optional

import httpx

logger = logging.getLogger(__name__)


class GitClient:
    """Client for executing git commands."""

    def __init__(self, repo_path: Optional[str] = None, github_token: Optional[str] = None):
        """Initialize git client.

        Args:
            repo_path: Path to git repository. Defaults to current directory.
            github_token: GitHub Personal Access Token for HTTPS auth.
        """
        self.repo_path = Path(repo_path or os.getcwd()).resolve()
        self.github_token = github_token or ""
        self._askpass_path: Optional[str] = None

        if self.github_token:
            self._setup_askpass()

        logger.info(f"Git client initialized for repository: {self.repo_path}")
        logger.info(f"GitHub auth: {'CONFIGURED' if self.github_token else 'NOT CONFIGURED'}")

    def _setup_askpass(self) -> None:
        """Create a temporary GIT_ASKPASS helper script that echoes the token."""
        fd, path = tempfile.mkstemp(prefix="git_askpass_", suffix=".sh")
        with os.fdopen(fd, "w") as f:
            f.write(f"#!/bin/sh\necho \"{self.github_token}\"\n")
        os.chmod(path, stat.S_IRWXU)
        self._askpass_path = path
        logger.debug(f"Created askpass helper at {path}")

    def cleanup(self) -> None:
        """Remove temporary askpass helper script."""
        if self._askpass_path and os.path.exists(self._askpass_path):
            os.unlink(self._askpass_path)
            logger.debug(f"Removed askpass helper at {self._askpass_path}")
            self._askpass_path = None

    async def _run_command(self, *args: str, check_repo: bool = True) -> tuple[str, str, int]:
        """Run a git command asynchronously.

        Args:
            *args: Git command arguments
            check_repo: Whether to check if directory is a git repository

        Returns:
            Tuple of (stdout, stderr, return_code)
        """
        if check_repo and not self._is_git_repo():
            raise ValueError(f"Not a git repository: {self.repo_path}")

        cmd = ["git", "-C", str(self.repo_path)] + list(args)
        logger.debug(f"Running command: {' '.join(cmd)}")

        env = None
        if self._askpass_path:
            env = os.environ.copy()
            env["GIT_ASKPASS"] = self._askpass_path
            env["GIT_TERMINAL_PROMPT"] = "0"

        try:
            process = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
                env=env,
            )

            stdout, stderr = await process.communicate()
            return_code = process.returncode or 0

            stdout_str = stdout.decode("utf-8", errors="replace")
            stderr_str = stderr.decode("utf-8", errors="replace")

            if return_code != 0:
                logger.warning(f"Command failed with code {return_code}: {stderr_str}")

            return stdout_str, stderr_str, return_code

        except Exception as e:
            logger.error(f"Failed to run git command: {e}")
            raise

    def _is_git_repo(self) -> bool:
        """Check if the path is a git repository."""
        git_dir = self.repo_path / ".git"
        return git_dir.exists() and git_dir.is_dir()

    async def status(self, short: bool = False) -> dict:
        """Get repository status.

        Args:
            short: Use short format

        Returns:
            Dictionary with status information
        """
        args = ["status"]
        if short:
            args.append("--short")

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git status failed: {stderr}")

        return {
            "output": stdout,
            "clean": "nothing to commit, working tree clean" in stdout,
        }

    async def log(
        self,
        max_count: int = 10,
        branch: Optional[str] = None,
        oneline: bool = False,
    ) -> dict:
        """Get commit history.

        Args:
            max_count: Maximum number of commits to return
            branch: Branch name (None for current branch)
            oneline: Use oneline format

        Returns:
            Dictionary with log information
        """
        args = ["log", f"--max-count={max_count}"]

        if oneline:
            args.append("--oneline")
        else:
            args.extend(["--format=%H%n%an%n%ae%n%ad%n%s%n%b%n---"])

        if branch:
            args.append(branch)

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git log failed: {stderr}")

        return {
            "output": stdout,
            "branch": branch or await self._get_current_branch(),
        }

    async def diff(
        self,
        cached: bool = False,
        file_path: Optional[str] = None,
        commit: Optional[str] = None,
    ) -> dict:
        """Get diff of changes.

        Args:
            cached: Show staged changes
            file_path: Specific file to diff
            commit: Compare with specific commit

        Returns:
            Dictionary with diff information
        """
        args = ["diff"]

        if cached:
            args.append("--cached")

        if commit:
            args.append(commit)

        if file_path:
            args.extend(["--", file_path])

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git diff failed: {stderr}")

        return {
            "output": stdout,
            "has_changes": bool(stdout.strip()),
        }

    async def branch_list(self, all_branches: bool = False) -> dict:
        """List branches.

        Args:
            all_branches: Include remote branches

        Returns:
            Dictionary with branch information
        """
        args = ["branch"]
        if all_branches:
            args.append("-a")

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git branch failed: {stderr}")

        branches = []
        current = None

        for line in stdout.split("\n"):
            line = line.strip()
            if not line:
                continue

            is_current = line.startswith("* ")
            branch_name = line[2:] if is_current else line

            branches.append(branch_name)
            if is_current:
                current = branch_name

        return {
            "branches": branches,
            "current": current,
            "output": stdout,
        }

    async def show_commit(self, commit: str = "HEAD") -> dict:
        """Show commit details.

        Args:
            commit: Commit hash or reference (default: HEAD)

        Returns:
            Dictionary with commit information
        """
        args = ["show", "--format=%H%n%an%n%ae%n%ad%n%s%n%b", commit]

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git show failed: {stderr}")

        return {"output": stdout}

    async def blame(self, file_path: str) -> dict:
        """Show file blame information.

        Args:
            file_path: Path to file

        Returns:
            Dictionary with blame information
        """
        args = ["blame", file_path]

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git blame failed: {stderr}")

        return {"output": stdout, "file": file_path}

    async def add(self, *paths: str) -> dict:
        """Add files to staging area.

        Args:
            *paths: Paths to add

        Returns:
            Dictionary with operation result
        """
        if not paths:
            raise ValueError("At least one path required")

        args = ["add"] + list(paths)

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git add failed: {stderr}")

        return {
            "success": True,
            "paths": list(paths),
            "message": "Files added to staging area",
        }

    async def commit(self, message: str, amend: bool = False) -> dict:
        """Create a commit.

        Args:
            message: Commit message
            amend: Amend previous commit

        Returns:
            Dictionary with commit result
        """
        if not message:
            raise ValueError("Commit message required")

        args = ["commit", "-m", message]
        if amend:
            args.append("--amend")

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git commit failed: {stderr}")

        return {
            "success": True,
            "message": message,
            "output": stdout,
        }

    async def checkout(self, branch: str, create: bool = False) -> dict:
        """Checkout a branch.

        Args:
            branch: Branch name
            create: Create new branch

        Returns:
            Dictionary with checkout result
        """
        args = ["checkout"]
        if create:
            args.append("-b")
        args.append(branch)

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git checkout failed: {stderr}")

        return {
            "success": True,
            "branch": branch,
            "output": stdout,
        }

    async def pull(self, remote: str = "origin", branch: Optional[str] = None) -> dict:
        """Pull changes from remote.

        Args:
            remote: Remote name
            branch: Branch name (None for current)

        Returns:
            Dictionary with pull result
        """
        args = ["pull", remote]
        if branch:
            args.append(branch)

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git pull failed: {stderr}")

        return {
            "success": True,
            "remote": remote,
            "branch": branch or await self._get_current_branch(),
            "output": stdout,
        }

    async def push(
        self,
        remote: str = "origin",
        branch: Optional[str] = None,
        set_upstream: bool = False,
    ) -> dict:
        """Push changes to remote.

        Args:
            remote: Remote name
            branch: Branch name (None for current)
            set_upstream: Set upstream branch

        Returns:
            Dictionary with push result
        """
        args = ["push"]
        if set_upstream:
            args.append("--set-upstream")
        args.append(remote)
        if branch:
            args.append(branch)

        stdout, stderr, code = await self._run_command(*args)

        if code != 0:
            raise RuntimeError(f"git push failed: {stderr}")

        return {
            "success": True,
            "remote": remote,
            "branch": branch or await self._get_current_branch(),
            "output": stdout + stderr,  # Git outputs to stderr for push
        }

    async def _get_github_owner_repo(self) -> tuple[str, str]:
        """Parse owner/repo from git remote origin URL.

        Returns:
            Tuple of (owner, repo).

        Raises:
            RuntimeError: If unable to parse remote URL.
        """
        stdout, stderr, code = await self._run_command("remote", "get-url", "origin")
        if code != 0:
            raise RuntimeError(f"Failed to get remote URL: {stderr}")

        url = stdout.strip()
        # Handle SSH: git@github.com:owner/repo.git
        if url.startswith("git@"):
            path = url.split(":", 1)[1]
        # Handle HTTPS: https://github.com/owner/repo.git
        elif "github.com" in url:
            path = url.split("github.com/", 1)[1]
        else:
            raise RuntimeError(f"Cannot parse GitHub owner/repo from remote URL: {url}")

        path = path.removesuffix(".git")
        parts = path.split("/")
        if len(parts) < 2:
            raise RuntimeError(f"Cannot parse GitHub owner/repo from path: {path}")

        return parts[0], parts[1]

    async def github_pr_list(self, state: str = "open", limit: int = 10) -> dict:
        """List pull requests for the repository.

        Args:
            state: Filter by state (open, closed, all).
            limit: Maximum number of PRs to return.

        Returns:
            Dictionary with PR list.
        """
        if not self.github_token:
            raise ValueError("GitHub token is not configured")

        owner, repo = await self._get_github_owner_repo()

        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"https://api.github.com/repos/{owner}/{repo}/pulls",
                params={"state": state, "per_page": min(limit, 100)},
                headers={
                    "Authorization": f"Bearer {self.github_token}",
                    "Accept": "application/vnd.github+json",
                },
                timeout=15.0,
            )

        if resp.status_code != 200:
            raise RuntimeError(f"GitHub API returned {resp.status_code}: {resp.text}")

        prs = resp.json()
        return {
            "pull_requests": [
                {
                    "number": pr["number"],
                    "title": pr["title"],
                    "state": pr["state"],
                    "author": pr["user"]["login"],
                    "created_at": pr["created_at"],
                    "updated_at": pr["updated_at"],
                    "head": pr["head"]["ref"],
                    "base": pr["base"]["ref"],
                }
                for pr in prs
            ],
            "total": len(prs),
        }

    async def github_pr_get(self, pr_number: int) -> dict:
        """Get details of a specific pull request.

        Args:
            pr_number: PR number.

        Returns:
            Dictionary with PR details.
        """
        if not self.github_token:
            raise ValueError("GitHub token is not configured")

        owner, repo = await self._get_github_owner_repo()

        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"https://api.github.com/repos/{owner}/{repo}/pulls/{pr_number}",
                headers={
                    "Authorization": f"Bearer {self.github_token}",
                    "Accept": "application/vnd.github+json",
                },
                timeout=15.0,
            )

        if resp.status_code != 200:
            raise RuntimeError(f"GitHub API returned {resp.status_code}: {resp.text}")

        pr = resp.json()
        return {
            "number": pr["number"],
            "title": pr["title"],
            "body": pr.get("body") or "",
            "state": pr["state"],
            "author": pr["user"]["login"],
            "labels": [l["name"] for l in pr.get("labels", [])],
            "created_at": pr["created_at"],
            "updated_at": pr["updated_at"],
            "merged": pr.get("merged", False),
            "mergeable": pr.get("mergeable"),
            "head": pr["head"]["ref"],
            "base": pr["base"]["ref"],
            "additions": pr.get("additions", 0),
            "deletions": pr.get("deletions", 0),
            "changed_files": pr.get("changed_files", 0),
        }

    async def github_pr_diff(self, pr_number: int) -> dict:
        """Get the diff of a pull request.

        Args:
            pr_number: PR number.

        Returns:
            Dictionary with PR diff text.
        """
        if not self.github_token:
            raise ValueError("GitHub token is not configured")

        owner, repo = await self._get_github_owner_repo()

        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"https://api.github.com/repos/{owner}/{repo}/pulls/{pr_number}",
                headers={
                    "Authorization": f"Bearer {self.github_token}",
                    "Accept": "application/vnd.github.v3.diff",
                },
                timeout=30.0,
            )

        if resp.status_code != 200:
            raise RuntimeError(f"GitHub API returned {resp.status_code}: {resp.text}")

        return {
            "diff": resp.text,
            "pr_number": pr_number,
        }

    async def github_pr_files(self, pr_number: int) -> dict:
        """List files changed in a pull request.

        Args:
            pr_number: PR number.

        Returns:
            Dictionary with changed files list.
        """
        if not self.github_token:
            raise ValueError("GitHub token is not configured")

        owner, repo = await self._get_github_owner_repo()

        async with httpx.AsyncClient() as client:
            resp = await client.get(
                f"https://api.github.com/repos/{owner}/{repo}/pulls/{pr_number}/files",
                params={"per_page": 100},
                headers={
                    "Authorization": f"Bearer {self.github_token}",
                    "Accept": "application/vnd.github+json",
                },
                timeout=15.0,
            )

        if resp.status_code != 200:
            raise RuntimeError(f"GitHub API returned {resp.status_code}: {resp.text}")

        files = resp.json()
        return {
            "files": [
                {
                    "filename": f["filename"],
                    "status": f["status"],
                    "additions": f["additions"],
                    "deletions": f["deletions"],
                    "changes": f["changes"],
                }
                for f in files
            ],
            "total": len(files),
        }

    async def github_user_info(self) -> dict:
        """Fetch authenticated GitHub user info using the configured token.

        Returns:
            Dictionary with login, name, email, public_repos.

        Raises:
            ValueError: If no GitHub token is configured.
            RuntimeError: If the API call fails.
        """
        if not self.github_token:
            raise ValueError("GitHub token is not configured")

        async with httpx.AsyncClient() as client:
            resp = await client.get(
                "https://api.github.com/user",
                headers={
                    "Authorization": f"Bearer {self.github_token}",
                    "Accept": "application/vnd.github+json",
                },
                timeout=10.0,
            )

        if resp.status_code != 200:
            raise RuntimeError(f"GitHub API returned {resp.status_code}: {resp.text}")

        data = resp.json()
        return {
            "login": data.get("login"),
            "name": data.get("name"),
            "email": data.get("email"),
            "public_repos": data.get("public_repos"),
        }

    async def _get_current_branch(self) -> str:
        """Get current branch name."""
        stdout, _, _ = await self._run_command("branch", "--show-current")
        return stdout.strip()
