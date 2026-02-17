"""Configuration for CRM MCP Server."""

import os

# Server settings
HOST = os.getenv("CRM_HOST", "0.0.0.0")
PORT = int(os.getenv("CRM_PORT", "8011"))

# Authentication
CRM_API_KEY = os.getenv("CRM_API_KEY", "")
NO_AUTH = os.getenv("CRM_NO_AUTH", "").lower() in ("true", "1", "yes")

# GigaChat credentials (for LLM query expansion)
GIGACHAT_CLIENT_ID = os.getenv("GIGACHAT_CLIENT_ID", "")
GIGACHAT_CLIENT_SECRET = os.getenv("GIGACHAT_CLIENT_SECRET", "")

# Search settings
USE_LLM_SEARCH = os.getenv("CRM_USE_LLM_SEARCH", "true").lower() in ("true", "1", "yes")

# Data paths (set by main.py based on script location)
DATA_DIR = None
USERS_FILE = None
TICKETS_FILE = None
