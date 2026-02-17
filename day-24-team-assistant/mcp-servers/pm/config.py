"""Configuration for Project Management MCP Server."""

import os
from pathlib import Path

# Server configuration
HOST = os.getenv("PM_HOST", "0.0.0.0")
PORT = int(os.getenv("PM_PORT", "8012"))
NO_AUTH = os.getenv("PM_NO_AUTH", "true").lower() == "true"
PM_API_KEY = os.getenv("MCP_API_KEY", "")

# Data storage
DATA_DIR = Path(os.getenv("PM_DATA_DIR", Path(__file__).parent / "data"))
TASKS_FILE = DATA_DIR / "tasks.json"
PROJECTS_FILE = DATA_DIR / "projects.json"
SPRINTS_FILE = DATA_DIR / "sprints.json"

# AI integration
USE_AI_ANALYSIS = os.getenv("PM_USE_AI", "true").lower() == "true"
GIGACHAT_CLIENT_ID = os.getenv("GIGACHAT_CLIENT_ID", "")
GIGACHAT_CLIENT_SECRET = os.getenv("GIGACHAT_CLIENT_SECRET", "")

# Default project ID
DEFAULT_PROJECT_ID = "proj_001"

# Task status transitions
STATUS_TRANSITIONS = {
    "open": ["in_progress", "blocked", "cancelled"],
    "in_progress": ["review", "blocked", "cancelled"],
    "review": ["done", "in_progress"],
    "blocked": ["open", "in_progress"],
    "done": [],
    "cancelled": []
}

# Priority levels (lowest to highest)
PRIORITY_LEVELS = ["low", "medium", "high", "critical"]

# Valid task statuses
TASK_STATUSES = ["open", "in_progress", "review", "done", "blocked", "cancelled"]
