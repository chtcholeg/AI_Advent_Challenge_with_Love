"""Git MCP server configuration."""

import os


# Server settings
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8010"))
GIT_API_KEY = os.getenv("GIT_API_KEY", "")
NO_AUTH = os.getenv("NO_AUTH", "false").lower() == "true"

# GitHub authentication
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "")

# Git settings
DEFAULT_REPO_PATH = os.getenv("GIT_REPO_PATH", os.getcwd())
MAX_LOG_ENTRIES = int(os.getenv("GIT_MAX_LOG_ENTRIES", "100"))
MAX_DIFF_LINES = int(os.getenv("GIT_MAX_DIFF_LINES", "1000"))
