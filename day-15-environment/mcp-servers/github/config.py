"""Server configuration loaded from environment variables and CLI flags."""

import os
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ServerConfig:
    host: str = "0.0.0.0"
    port: int = 8000


@dataclass
class AuthConfig:
    enabled: bool = True
    api_key: Optional[str] = None


@dataclass
class GitHubConfig:
    token: Optional[str] = None


@dataclass
class AppConfig:
    server: ServerConfig = field(default_factory=ServerConfig)
    auth: AuthConfig = field(default_factory=AuthConfig)
    github: GitHubConfig = field(default_factory=GitHubConfig)


def load_config(disable_auth: bool = False) -> AppConfig:
    config = AppConfig()

    # Server
    config.server.host = os.getenv("HOST", "0.0.0.0")
    config.server.port = int(os.getenv("PORT", "8000"))

    # Auth
    if disable_auth:
        config.auth.enabled = False
    else:
        config.auth.enabled = True
        config.auth.api_key = os.getenv("MCP_API_KEY")
        if not config.auth.api_key:
            raise ValueError(
                "MCP_API_KEY environment variable must be set. "
                "Use --no-auth flag to run without authentication."
            )

    # GitHub token (optional, increases rate limits)
    config.github.token = os.getenv("GITHUB_TOKEN")

    return config
