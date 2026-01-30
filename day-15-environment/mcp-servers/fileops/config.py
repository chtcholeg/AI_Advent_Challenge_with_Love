"""Configuration for FileOps MCP Server."""

import os
from dataclasses import dataclass


@dataclass
class ServerConfig:
    """Server binding configuration."""
    host: str = "0.0.0.0"
    port: int = 8005


@dataclass
class AuthConfig:
    """Authentication configuration."""
    enabled: bool = True
    api_key: str | None = None


@dataclass
class FileOpsConfig:
    """FileOps-specific configuration."""
    # Root directory for file operations (default: current working directory)
    root_dir: str = os.getcwd()
    # Maximum file size in bytes (default: 10MB)
    max_file_size: int = 10 * 1024 * 1024
    # Maximum search results
    max_search_results: int = 100


@dataclass
class Config:
    """Overall configuration."""
    server: ServerConfig
    auth: AuthConfig
    fileops: FileOpsConfig


def load_config(disable_auth: bool = False) -> Config:
    """Load configuration from environment variables."""
    # Auth
    auth_enabled = not disable_auth
    api_key = os.getenv("MCP_API_KEY") if auth_enabled else None
    if auth_enabled and not api_key:
        raise ValueError(
            "MCP_API_KEY environment variable is required. "
            "Set it or use --no-auth flag."
        )

    # Server
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8005"))

    # FileOps-specific
    root_dir = os.getenv("FILEOPS_ROOT_DIR", os.getcwd())
    max_file_size = int(os.getenv("FILEOPS_MAX_FILE_SIZE", str(10 * 1024 * 1024)))
    max_search_results = int(os.getenv("FILEOPS_MAX_SEARCH_RESULTS", "100"))

    return Config(
        server=ServerConfig(host=host, port=port),
        auth=AuthConfig(enabled=auth_enabled, api_key=api_key),
        fileops=FileOpsConfig(
            root_dir=root_dir,
            max_file_size=max_file_size,
            max_search_results=max_search_results,
        ),
    )
