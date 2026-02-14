"""Configuration for CRM MCP Server."""

import os

# Server settings
HOST = os.getenv("CRM_HOST", "0.0.0.0")
PORT = int(os.getenv("CRM_PORT", "8011"))

# Authentication
CRM_API_KEY = os.getenv("CRM_API_KEY", "")
NO_AUTH = os.getenv("CRM_NO_AUTH", "").lower() in ("true", "1", "yes")

# Data paths (set by main.py based on script location)
DATA_DIR = None
USERS_FILE = None
TICKETS_FILE = None
