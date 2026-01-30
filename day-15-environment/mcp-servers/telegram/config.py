"""Server configuration loaded from .env file with fallback to environment variables."""

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

# Загрузить .env из директории этого файла; override=True означает:
# значения из .env имеют приоритет, env vars — только как запасной вариант.
load_dotenv(Path(__file__).parent / ".env", override=True)


@dataclass
class ServerConfig:
    host: str = "0.0.0.0"
    port: int = 8001


@dataclass
class AuthConfig:
    enabled: bool = True
    api_key: Optional[str] = None


@dataclass
class TelegramConfig:
    api_id: Optional[int] = None
    api_hash: Optional[str] = None
    session_file: str = "telegram_session"


@dataclass
class AppConfig:
    server: ServerConfig = field(default_factory=ServerConfig)
    auth: AuthConfig = field(default_factory=AuthConfig)
    telegram: TelegramConfig = field(default_factory=TelegramConfig)


def load_config(disable_auth: bool = False) -> AppConfig:
    config = AppConfig()

    # Server
    config.server.host = os.getenv("HOST", "0.0.0.0")
    config.server.port = int(os.getenv("PORT", "8001"))

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

    # Telegram credentials (required)
    api_id = os.getenv("TELEGRAM_API_ID")
    api_hash = os.getenv("TELEGRAM_API_HASH")

    if not api_id or not api_hash:
        raise ValueError(
            "TELEGRAM_API_ID and TELEGRAM_API_HASH must be set. "
            "Get them at https://my.telegram.org"
        )

    config.telegram.api_id = int(api_id)
    config.telegram.api_hash = api_hash
    config.telegram.session_file = os.getenv("TELEGRAM_SESSION_FILE", "telegram_session")

    return config
