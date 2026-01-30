"""Configuration for TimeService MCP Server."""

import os
from dataclasses import dataclass


@dataclass
class ServerConfig:
    host: str = "0.0.0.0"
    port: int = 8003


@dataclass
class AuthConfig:
    enabled: bool = False
    api_key: str = ""


@dataclass
class Config:
    server: ServerConfig
    auth: AuthConfig


def load_config(disable_auth: bool = False) -> Config:
    """Load configuration from environment variables."""
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8003"))

    auth_enabled = not disable_auth and bool(os.getenv("MCP_API_KEY"))
    api_key = os.getenv("MCP_API_KEY", "")

    if auth_enabled and not api_key:
        raise ValueError("MCP_API_KEY environment variable required when auth is enabled")

    return Config(
        server=ServerConfig(host=host, port=port),
        auth=AuthConfig(enabled=auth_enabled, api_key=api_key),
    )
