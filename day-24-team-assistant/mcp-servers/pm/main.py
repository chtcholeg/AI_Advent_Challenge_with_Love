#!/usr/bin/env python3
"""
Project Management MCP Server - HTTP/SSE transport
Provides task management, priority analysis, and project dashboard
"""

import argparse
import asyncio
import json
import logging
import uuid
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

import sys
sys.path.append(str(Path(__file__).parent.parent))

from fastapi import FastAPI, Request, HTTPException, Header
from fastapi.responses import StreamingResponse

from . import config
from .ai_service import get_ai_service
from .dashboard_service import DashboardService

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

# Global state
sessions: dict[str, dict] = {}

# Authentication flag
AUTH_ENABLED = not config.NO_AUTH and bool(config.PM_API_KEY)


def load_tasks() -> list[dict[str, Any]]:
    """Load tasks from JSON file"""
    if not config.TASKS_FILE.exists():
        return []
    with open(config.TASKS_FILE, 'r', encoding='utf-8') as f:
        data = json.load(f)
        # Support both old format (list) and new format (dict with metadata)
        if isinstance(data, list):
            return data
        return data.get("tasks", [])


def save_tasks(tasks: list[dict[str, Any]]) -> None:
    """Save tasks to JSON file with metadata"""
    config.DATA_DIR.mkdir(parents=True, exist_ok=True)

    # Load existing metadata or create new
    metadata = {"last_task_id": 0}
    if config.TASKS_FILE.exists():
        with open(config.TASKS_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
            if isinstance(data, dict) and "metadata" in data:
                metadata = data["metadata"]

    # Save with metadata
    data = {
        "metadata": metadata,
        "tasks": tasks
    }
    with open(config.TASKS_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def load_metadata() -> dict[str, Any]:
    """Load metadata from tasks file"""
    if not config.TASKS_FILE.exists():
        return {"last_task_id": 0}
    with open(config.TASKS_FILE, 'r', encoding='utf-8') as f:
        data = json.load(f)
        if isinstance(data, dict) and "metadata" in data:
            return data["metadata"]
        return {"last_task_id": 0}


def save_metadata(metadata: dict[str, Any]) -> None:
    """Save metadata to tasks file"""
    tasks = load_tasks()
    data = {
        "metadata": metadata,
        "tasks": tasks
    }
    config.DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(config.TASKS_FILE, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def load_projects() -> list[dict[str, Any]]:
    """Load projects from JSON file"""
    if not config.PROJECTS_FILE.exists():
        return []
    with open(config.PROJECTS_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_projects(projects: list[dict[str, Any]]) -> None:
    """Save projects to JSON file"""
    config.DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(config.PROJECTS_FILE, 'w', encoding='utf-8') as f:
        json.dump(projects, f, indent=2, ensure_ascii=False)


def load_sprints() -> list[dict[str, Any]]:
    """Load sprints from JSON file"""
    if not config.SPRINTS_FILE.exists():
        return []
    with open(config.SPRINTS_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_sprints(sprints: list[dict[str, Any]]) -> None:
    """Save sprints to JSON file"""
    config.DATA_DIR.mkdir(parents=True, exist_ok=True)
    with open(config.SPRINTS_FILE, 'w', encoding='utf-8') as f:
        json.dump(sprints, f, indent=2, ensure_ascii=False)


def find_task(task_id: str) -> Optional[dict[str, Any]]:
    """Find task by ID"""
    tasks = load_tasks()
    for task in tasks:
        if task["id"] == task_id:
            return task
    return None


def generate_task_id(labels: list[str] = None) -> str:
    """Generate unique task ID with type-based prefix and sequential counter"""
    metadata = load_metadata()

    # Determine task type from labels
    task_type = "feature"  # default
    if labels:
        if "bug" in labels:
            task_type = "bug"
        elif "documentation" in labels:
            task_type = "doc"
        elif "optimization" in labels:
            task_type = "opt"
        elif "testing" in labels or "quality" in labels:
            task_type = "test"
        # else keep "feature" for feature, command, mcp, ai, etc.

    # Initialize counters if not present
    if "task_counters" not in metadata:
        metadata["task_counters"] = {
            "feature": 0,
            "bug": 0,
            "doc": 0,
            "opt": 0,
            "test": 0
        }

    # Increment counter for this type
    counter = metadata["task_counters"].get(task_type, 0) + 1
    task_id = f"{task_type}_{counter:03d}"

    # Update metadata
    metadata["task_counters"][task_type] = counter
    save_metadata(metadata)

    return task_id


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup/shutdown."""
    logger.info("=" * 60)
    logger.info("Project Management MCP Server Starting")
    logger.info("=" * 60)
    logger.info(f"Data directory: {config.DATA_DIR}")
    logger.info(f"Host: {config.HOST}")
    logger.info(f"Port: {config.PORT}")
    logger.info(f"Authentication: {'ENABLED' if AUTH_ENABLED else 'DISABLED'}")
    logger.info(f"AI Analysis: {'ENABLED' if config.USE_AI_ANALYSIS else 'DISABLED'}")
    logger.info("=" * 60)

    # Ensure data directory exists
    config.DATA_DIR.mkdir(parents=True, exist_ok=True)

    yield

    logger.info("Project Management MCP Server Shutting Down")


app = FastAPI(lifespan=lifespan, title="Project Management MCP Server")


def verify_auth(authorization: Optional[str] = None) -> None:
    """Verify API key if authentication is enabled"""
    if not AUTH_ENABLED:
        return

    if not authorization:
        raise HTTPException(status_code=401, detail="Missing Authorization header")

    try:
        scheme, token = authorization.split()
        if scheme.lower() != "bearer" or token != config.PM_API_KEY:
            raise HTTPException(status_code=401, detail="Invalid API key")
    except ValueError:
        raise HTTPException(status_code=401, detail="Invalid Authorization header format")


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "name": "Project Management MCP Server",
        "version": "1.0.0",
        "transport": "SSE"
    }


@app.get("/sse")
async def sse_handler(
    request: Request,
    authorization: Optional[str] = Header(None)
):
    """Handle SSE connection for MCP protocol"""
    verify_auth(authorization)

    session_id = str(uuid.uuid4())
    logger.info(f"New SSE connection: {session_id}")

    async def event_generator():
        try:
            # Send endpoint event
            yield f"event: endpoint\ndata: /message\n\n"

            # Keep connection alive
            while True:
                if await request.is_disconnected():
                    break
                await asyncio.sleep(1)
        except Exception as e:
            logger.error(f"Error in SSE stream: {e}")
        finally:
            if session_id in sessions:
                del sessions[session_id]
            logger.info(f"SSE connection closed: {session_id}")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )


@app.post("/message")
async def message_handler(
    request: Request,
    authorization: Optional[str] = Header(None)
):
    """Handle MCP protocol messages"""
    verify_auth(authorization)

    try:
        message = await request.json()
        logger.info(f"Received message: {message.get('method', 'unknown')}")

        jsonrpc = message.get("jsonrpc", "2.0")
        msg_id = message.get("id")
        method = message.get("method")
        params = message.get("params", {})

        # Handle initialization
        if method == "initialize":
            return {
                "jsonrpc": jsonrpc,
                "id": msg_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {
                        "tools": {}
                    },
                    "serverInfo": {
                        "name": "pm-server",
                        "version": "1.0.0"
                    }
                }
            }

        # Handle tools list
        if method == "tools/list":
            tools = [
                {
                    "name": "create_task",
                    "description": "Create a new task in the project",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "title": {"type": "string", "description": "Task title"},
                            "description": {"type": "string", "description": "Task description"},
                            "priority": {"type": "string", "enum": ["low", "medium", "high", "critical"], "default": "medium"},
                            "assignee": {"type": "string", "description": "User assigned to the task"},
                            "labels": {"type": "array", "items": {"type": "string"}, "description": "Task labels"},
                            "due_date": {"type": "string", "description": "Due date (YYYY-MM-DD)"},
                            "estimated_hours": {"type": "number", "description": "Estimated hours"}
                        },
                        "required": ["title"]
                    }
                },
                {
                    "name": "list_tasks",
                    "description": "List tasks with optional filters",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "status": {"type": "string", "enum": ["open", "in_progress", "review", "done", "blocked", "cancelled"]},
                            "priority": {"type": "string", "enum": ["low", "medium", "high", "critical"]},
                            "assignee": {"type": "string", "description": "Filter by assignee"},
                            "label": {"type": "string", "description": "Filter by label"},
                            "limit": {"type": "number", "default": 50, "description": "Maximum number of tasks"}
                        }
                    }
                },
                {
                    "name": "get_task",
                    "description": "Get task details by ID",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "task_id": {"type": "string", "description": "Task ID"}
                        },
                        "required": ["task_id"]
                    }
                },
                {
                    "name": "update_task",
                    "description": "Update task fields",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "task_id": {"type": "string", "description": "Task ID"},
                            "title": {"type": "string"},
                            "description": {"type": "string"},
                            "status": {"type": "string", "enum": ["open", "in_progress", "review", "done", "blocked", "cancelled"]},
                            "priority": {"type": "string", "enum": ["low", "medium", "high", "critical"]},
                            "assignee": {"type": "string"},
                            "labels": {"type": "array", "items": {"type": "string"}},
                            "due_date": {"type": "string"},
                            "estimated_hours": {"type": "number"},
                            "spent_hours": {"type": "number"},
                            "git_branch": {"type": "string"},
                            "git_commits": {"type": "array", "items": {"type": "string"}}
                        },
                        "required": ["task_id"]
                    }
                },
                {
                    "name": "delete_task",
                    "description": "Delete a task",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "task_id": {"type": "string", "description": "Task ID"}
                        },
                        "required": ["task_id"]
                    }
                },
                {
                    "name": "get_project_dashboard",
                    "description": "Get project dashboard with statistics and metrics",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "project_id": {"type": "string", "default": "proj_001"}
                        }
                    }
                },
                {
                    "name": "get_team_workload",
                    "description": "Get team workload by assignee",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "project_id": {"type": "string", "default": "proj_001"}
                        }
                    }
                },
                {
                    "name": "analyze_priorities",
                    "description": "AI-powered analysis of task priorities with recommendations",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "project_id": {"type": "string", "default": "proj_001"}
                        }
                    }
                },
                {
                    "name": "suggest_priority",
                    "description": "AI-powered priority suggestion for a new task",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "title": {"type": "string", "description": "Task title"},
                            "description": {"type": "string", "description": "Task description"}
                        },
                        "required": ["title", "description"]
                    }
                }
            ]

            return {
                "jsonrpc": jsonrpc,
                "id": msg_id,
                "result": {
                    "tools": tools
                }
            }

        # Handle tool calls
        if method == "tools/call":
            tool_name = params.get("name")
            arguments = params.get("arguments", {})

            result = await handle_tool_call(tool_name, arguments)

            return {
                "jsonrpc": jsonrpc,
                "id": msg_id,
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, ensure_ascii=False, indent=2)
                        }
                    ]
                }
            }

        # Unknown method
        return {
            "jsonrpc": jsonrpc,
            "id": msg_id,
            "error": {
                "code": -32601,
                "message": f"Method not found: {method}"
            }
        }

    except Exception as e:
        logger.error(f"Error handling message: {e}", exc_info=True)
        return {
            "jsonrpc": "2.0",
            "id": message.get("id") if "message" in locals() else None,
            "error": {
                "code": -32603,
                "message": f"Internal error: {str(e)}"
            }
        }


async def handle_tool_call(tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    """Handle tool call"""

    if tool_name == "create_task":
        return create_task(arguments)
    elif tool_name == "list_tasks":
        return list_tasks(arguments)
    elif tool_name == "get_task":
        return get_task(arguments)
    elif tool_name == "update_task":
        return update_task(arguments)
    elif tool_name == "delete_task":
        return delete_task(arguments)
    elif tool_name == "get_project_dashboard":
        return get_project_dashboard(arguments)
    elif tool_name == "get_team_workload":
        return get_team_workload(arguments)
    elif tool_name == "analyze_priorities":
        return await analyze_priorities(arguments)
    elif tool_name == "suggest_priority":
        return await suggest_priority(arguments)
    else:
        raise ValueError(f"Unknown tool: {tool_name}")


def create_task(args: dict[str, Any]) -> dict[str, Any]:
    """Create a new task"""
    tasks = load_tasks()

    labels = args.get("labels", [])
    task_id = generate_task_id(labels)
    now = datetime.now().isoformat()

    task = {
        "id": task_id,
        "project_id": config.DEFAULT_PROJECT_ID,
        "title": args["title"],
        "description": args.get("description", ""),
        "status": "open",
        "priority": args.get("priority", "medium"),
        "assignee": args.get("assignee", ""),
        "labels": labels,
        "git_branch": "",
        "git_commits": [],
        "created_at": now,
        "updated_at": now,
        "due_date": args.get("due_date", ""),
        "estimated_hours": args.get("estimated_hours", 0),
        "spent_hours": 0
    }

    tasks.append(task)
    save_tasks(tasks)

    logger.info(f"Created task: {task_id}")

    return {
        "success": True,
        "task": task,
        "message": f"Task {task_id} created successfully"
    }


def list_tasks(args: dict[str, Any]) -> dict[str, Any]:
    """List tasks with filters"""
    tasks = load_tasks()

    # Apply filters
    if "status" in args:
        tasks = [t for t in tasks if t["status"] == args["status"]]
    if "priority" in args:
        tasks = [t for t in tasks if t["priority"] == args["priority"]]
    if "assignee" in args:
        tasks = [t for t in tasks if t["assignee"] == args["assignee"]]
    if "label" in args:
        tasks = [t for t in tasks if args["label"] in t.get("labels", [])]

    # Apply limit
    limit = args.get("limit", 50)
    tasks = tasks[:limit]

    logger.info(f"Listed {len(tasks)} tasks")

    return {
        "success": True,
        "tasks": tasks,
        "count": len(tasks)
    }


def get_task(args: dict[str, Any]) -> dict[str, Any]:
    """Get task details"""
    task_id = args["task_id"]
    task = find_task(task_id)

    if not task:
        return {
            "success": False,
            "error": f"Task {task_id} not found"
        }

    logger.info(f"Retrieved task: {task_id}")

    return {
        "success": True,
        "task": task
    }


def update_task(args: dict[str, Any]) -> dict[str, Any]:
    """Update task"""
    task_id = args["task_id"]
    tasks = load_tasks()

    task = None
    for i, t in enumerate(tasks):
        if t["id"] == task_id:
            task = t
            break

    if not task:
        return {
            "success": False,
            "error": f"Task {task_id} not found"
        }

    # Update fields
    for key in ["title", "description", "status", "priority", "assignee",
                "labels", "due_date", "estimated_hours", "spent_hours",
                "git_branch", "git_commits"]:
        if key in args:
            task[key] = args[key]

    task["updated_at"] = datetime.now().isoformat()

    save_tasks(tasks)

    logger.info(f"Updated task: {task_id}")

    return {
        "success": True,
        "task": task,
        "message": f"Task {task_id} updated successfully"
    }


def delete_task(args: dict[str, Any]) -> dict[str, Any]:
    """Delete task"""
    task_id = args["task_id"]
    tasks = load_tasks()

    original_count = len(tasks)
    tasks = [t for t in tasks if t["id"] != task_id]

    if len(tasks) == original_count:
        return {
            "success": False,
            "error": f"Task {task_id} not found"
        }

    save_tasks(tasks)

    logger.info(f"Deleted task: {task_id}")

    return {
        "success": True,
        "message": f"Task {task_id} deleted successfully"
    }


def get_project_dashboard(args: dict[str, Any]) -> dict[str, Any]:
    """Get project dashboard"""
    project_id = args.get("project_id", config.DEFAULT_PROJECT_ID)
    dashboard_service = DashboardService()
    return dashboard_service.get_dashboard(project_id)


def get_team_workload(args: dict[str, Any]) -> dict[str, Any]:
    """Get team workload"""
    project_id = args.get("project_id", config.DEFAULT_PROJECT_ID)
    dashboard_service = DashboardService()
    return dashboard_service.get_team_workload(project_id)


async def analyze_priorities(args: dict[str, Any]) -> dict[str, Any]:
    """AI-powered priority analysis"""
    if not config.USE_AI_ANALYSIS:
        return {
            "success": False,
            "error": "AI analysis is disabled"
        }

    project_id = args.get("project_id", config.DEFAULT_PROJECT_ID)
    ai_service = get_ai_service()
    return await ai_service.analyze_priorities(project_id)


async def suggest_priority(args: dict[str, Any]) -> dict[str, Any]:
    """AI-powered priority suggestion"""
    if not config.USE_AI_ANALYSIS:
        return {
            "success": False,
            "error": "AI analysis is disabled"
        }

    ai_service = get_ai_service()
    return await ai_service.suggest_priority(args["title"], args["description"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Project Management MCP Server")
    parser.add_argument("--host", default=config.HOST, help="Host to bind to")
    parser.add_argument("--port", type=int, default=config.PORT, help="Port to bind to")
    parser.add_argument("--no-auth", action="store_true", help="Disable authentication")
    args = parser.parse_args()

    if args.no_auth:
        config.NO_AUTH = True

    import uvicorn
    uvicorn.run(app, host=args.host, port=args.port)
