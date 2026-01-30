"""HTTP client for the GitHub REST API."""

import httpx
from typing import Optional


class GitHubApiError(Exception):
    """Raised when the GitHub API returns an error response."""
    pass


class GitHubClient:
    BASE_URL = "https://api.github.com"

    def __init__(self, token: Optional[str] = None):
        headers = {
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "GitHubMCPServer/1.0",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"
        self.client = httpx.AsyncClient(headers=headers, timeout=30.0)

    async def _handle_response(self, response: httpx.Response):
        if response.status_code == 404:
            raise GitHubApiError("Resource not found (404). Check owner/repo name.")
        if response.status_code == 403:
            raise GitHubApiError(
                "Access denied (403). Rate limit exceeded or insufficient permissions. "
                "Set GITHUB_TOKEN env var for 5000 req/hr instead of 60."
            )
        if response.status_code == 422:
            raise GitHubApiError(f"Validation failed (422): {response.text}")
        response.raise_for_status()
        return response.json()

    async def get_repo(self, owner: str, repo: str) -> dict:
        response = await self.client.get(f"{self.BASE_URL}/repos/{owner}/{repo}")
        return await self._handle_response(response)

    async def get_branches(self, owner: str, repo: str, per_page: int = 30) -> list:
        response = await self.client.get(
            f"{self.BASE_URL}/repos/{owner}/{repo}/branches",
            params={"per_page": min(per_page, 100)},
        )
        return await self._handle_response(response)

    async def get_tags(self, owner: str, repo: str, per_page: int = 30) -> list:
        response = await self.client.get(
            f"{self.BASE_URL}/repos/{owner}/{repo}/tags",
            params={"per_page": min(per_page, 100)},
        )
        return await self._handle_response(response)

    async def get_readme(self, owner: str, repo: str, ref: Optional[str] = None) -> dict:
        params = {}
        if ref:
            params["ref"] = ref
        response = await self.client.get(
            f"{self.BASE_URL}/repos/{owner}/{repo}/readme",
            params=params,
        )
        return await self._handle_response(response)

    async def get_contributors(self, owner: str, repo: str, per_page: int = 10) -> list:
        response = await self.client.get(
            f"{self.BASE_URL}/repos/{owner}/{repo}/contributors",
            params={"per_page": min(per_page, 100)},
        )
        return await self._handle_response(response)

    async def get_commits(self, owner: str, repo: str, sha: Optional[str] = None, per_page: int = 10) -> list:
        params = {"per_page": min(per_page, 100)}
        if sha:
            params["sha"] = sha
        response = await self.client.get(
            f"{self.BASE_URL}/repos/{owner}/{repo}/commits",
            params=params,
        )
        return await self._handle_response(response)

    async def get_user(self, username: str) -> dict:
        response = await self.client.get(f"{self.BASE_URL}/users/{username}")
        return await self._handle_response(response)

    async def close(self):
        await self.client.aclose()
