#!/usr/bin/env python3
"""
CRM MCP Server - HTTP/SSE transport
Provides access to user and ticket data for support assistant
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
from .search_service import get_search_service

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

# Global state
sessions: dict[str, dict] = {}

# Authentication flag
AUTH_ENABLED = not config.NO_AUTH and bool(config.CRM_API_KEY)


def load_users() -> list[dict[str, Any]]:
    """Load users from JSON file"""
    if not config.USERS_FILE.exists():
        return []
    with open(config.USERS_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def load_tickets() -> list[dict[str, Any]]:
    """Load tickets from JSON file"""
    if not config.TICKETS_FILE.exists():
        return []
    with open(config.TICKETS_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_tickets(tickets: list[dict[str, Any]]) -> None:
    """Save tickets to JSON file"""
    with open(config.TICKETS_FILE, 'w', encoding='utf-8') as f:
        json.dump(tickets, f, ensure_ascii=False, indent=2)


def get_tools_list() -> list[dict]:
    """Get list of available CRM tools"""
    return [
        {
            "name": "get_user",
            "description": "Get user information by ID",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "user_id": {
                        "type": "string",
                        "description": "User ID (e.g., 'user_001')"
                    }
                },
                "required": ["user_id"]
            }
        },
        {
            "name": "list_users",
            "description": "List all users with optional filtering",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "status": {
                        "type": "string",
                        "description": "Filter by status: 'active' or 'inactive'",
                        "enum": ["active", "inactive"]
                    },
                    "subscription_plan": {
                        "type": "string",
                        "description": "Filter by subscription plan: 'Basic', 'Pro', or 'Enterprise'",
                        "enum": ["Basic", "Pro", "Enterprise"]
                    }
                }
            }
        },
        {
            "name": "get_ticket",
            "description": "Get ticket information by ID",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "ticket_id": {
                        "type": "string",
                        "description": "Ticket ID (e.g., 'ticket_001')"
                    }
                },
                "required": ["ticket_id"]
            }
        },
        {
            "name": "list_tickets",
            "description": "List tickets with optional filtering",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "user_id": {
                        "type": "string",
                        "description": "Filter by user ID"
                    },
                    "status": {
                        "type": "string",
                        "description": "Filter by status: 'open', 'in_progress', or 'resolved'",
                        "enum": ["open", "in_progress", "resolved"]
                    },
                    "priority": {
                        "type": "string",
                        "description": "Filter by priority: 'low', 'medium', or 'high'",
                        "enum": ["low", "medium", "high"]
                    },
                    "category": {
                        "type": "string",
                        "description": "Filter by category"
                    }
                }
            }
        },
        {
            "name": "update_ticket_status",
            "description": "Update ticket status and add notes",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "ticket_id": {
                        "type": "string",
                        "description": "Ticket ID to update"
                    },
                    "status": {
                        "type": "string",
                        "description": "New status",
                        "enum": ["open", "in_progress", "resolved", "closed"]
                    },
                    "notes": {
                        "type": "string",
                        "description": "Additional notes or comments"
                    },
                    "resolution": {
                        "type": "string",
                        "description": "Resolution description (required when status is 'resolved' or 'closed')"
                    }
                },
                "required": ["ticket_id", "status"]
            }
        },
        {
            "name": "search_tickets",
            "description": "Smart search tickets using LLM query expansion and relevance scoring. Automatically expands query with synonyms and technical terms for better results.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search query in natural language (e.g., 'authorization not working', 'pdf indexing error')"
                    }
                },
                "required": ["query"]
            }
        },
        {
            "name": "get_user_tickets",
            "description": "Get all tickets for a specific user with full context",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "user_id": {
                        "type": "string",
                        "description": "User ID"
                    }
                },
                "required": ["user_id"]
            }
        }
    ]


async def execute_tool(name: str, arguments: dict) -> str:
    """Execute a CRM tool and return result"""
    logger.info(f"[execute_tool] Tool: {name}, Arguments: {arguments}")

    if name == "get_user":
        users = load_users()
        user_id = arguments["user_id"]
        logger.info(f"[get_user] Looking for user_id: {user_id}")
        user = next((u for u in users if u["id"] == user_id), None)

        if user:
            logger.info(f"[get_user] Found user: {user['name']}")
            return f"""Пользователь найден:
ID: {user['id']}
Имя: {user['name']}
Email: {user['email']}
План подписки: {user['subscriptionPlan']}
Дата регистрации: {user['registrationDate']}
Последняя активность: {user['lastActive']}
Статус: {user['status']}"""
        else:
            logger.warning(f"[get_user] User not found: {user_id}")
            return f"Пользователь с ID '{user_id}' не найден"

    elif name == "list_users":
        logger.info(f"[list_users] Filters: {arguments}")
        users = load_users()

        # Apply filters
        if "status" in arguments:
            users = [u for u in users if u["status"] == arguments["status"]]
        if "subscription_plan" in arguments:
            users = [u for u in users if u["subscriptionPlan"] == arguments["subscription_plan"]]

        if not users:
            return "Пользователи не найдены"
        else:
            result = f"Найдено пользователей: {len(users)}\n\n"
            for user in users:
                result += f"- {user['id']}: {user['name']} ({user['email']}) - {user['subscriptionPlan']} - {user['status']}\n"
            return result

    elif name == "get_ticket":
        tickets = load_tickets()
        ticket_id = arguments["ticket_id"]
        logger.info(f"[get_ticket] Looking for ticket_id: {ticket_id}")
        ticket = next((t for t in tickets if t["id"] == ticket_id), None)

        if ticket:
            logger.info(f"[get_ticket] Found ticket: {ticket['subject']}")
            # Get user info
            users = load_users()
            user = next((u for u in users if u["id"] == ticket["userId"]), None)
            user_name = user["name"] if user else "Неизвестно"
            user_email = user["email"] if user else "Неизвестно"
            user_plan = user["subscriptionPlan"] if user else "Неизвестно"

            result = f"""Тикет найден:
ID: {ticket['id']}
Пользователь: {user_name} ({user_email})
План подписки: {user_plan}
Тема: {ticket['subject']}
Описание: {ticket['description']}
Статус: {ticket['status']}
Приоритет: {ticket['priority']}
Категория: {ticket['category']}
Создан: {ticket['created']}
Обновлен: {ticket['updated']}
Назначен на: {ticket.get('assignedTo', 'Не назначен')}"""

            if 'notes' in ticket:
                result += f"\nЗаметки: {ticket['notes']}"
            if 'resolution' in ticket:
                result += f"\nРешение: {ticket['resolution']}"
            if 'resolvedAt' in ticket:
                result += f"\nРешен: {ticket['resolvedAt']}"
            return result
        else:
            logger.warning(f"[get_ticket] Ticket not found: {ticket_id}")
            return f"Тикет с ID '{ticket_id}' не найден"

    elif name == "list_tickets":
        logger.info(f"[list_tickets] Filters: {arguments}")
        tickets = load_tickets()

        # Apply filters
        if "user_id" in arguments:
            tickets = [t for t in tickets if t["userId"] == arguments["user_id"]]
        if "status" in arguments:
            tickets = [t for t in tickets if t["status"] == arguments["status"]]
        if "priority" in arguments:
            tickets = [t for t in tickets if t["priority"] == arguments["priority"]]
        if "category" in arguments:
            tickets = [t for t in tickets if t["category"] == arguments["category"]]

        if not tickets:
            logger.info(f"[list_tickets] No tickets found with filters: {arguments}")
            return "Тикеты не найдены"
        else:
            logger.info(f"[list_tickets] Found {len(tickets)} tickets")
            result = f"Найдено тикетов: {len(tickets)}\n\n"
            for ticket in tickets:
                result += f"- {ticket['id']}: {ticket['subject']} [{ticket['status']}] ({ticket['priority']} priority)\n"
            return result

    elif name == "update_ticket_status":
        logger.info(f"[update_ticket_status] Updating ticket {arguments.get('ticket_id')} to status {arguments.get('status')}")
        tickets = load_tickets()
        ticket_id = arguments["ticket_id"]
        new_status = arguments["status"]

        ticket = next((t for t in tickets if t["id"] == ticket_id), None)

        if not ticket:
            return f"Тикет с ID '{ticket_id}' не найден"
        else:
            old_status = ticket["status"]
            ticket["status"] = new_status
            ticket["updated"] = datetime.utcnow().isoformat() + "Z"

            if "notes" in arguments:
                ticket["notes"] = arguments["notes"]

            if new_status in ["resolved", "closed"]:
                if "resolution" in arguments:
                    ticket["resolution"] = arguments["resolution"]
                    ticket["resolvedAt"] = ticket["updated"]
                else:
                    return f"Для статуса '{new_status}' требуется указать resolution"

            save_tickets(tickets)
            logger.info(f"[update_ticket_status] Ticket {ticket_id} updated successfully")

            result = f"""Тикет обновлен:
ID: {ticket_id}
Статус изменен: {old_status} → {new_status}
Обновлен: {ticket['updated']}"""

            if "notes" in arguments:
                result += f"\nДобавлены заметки: {arguments['notes']}"
            if "resolution" in ticket and new_status in ["resolved", "closed"]:
                result += f"\nРешение: {ticket['resolution']}"

            return result

    elif name == "search_tickets":
        tickets = load_tickets()
        query = arguments["query"]

        logger.info(f"[search_tickets] Searching with query: '{query}'")

        # Use advanced search service
        search_service = get_search_service()
        search_results = await search_service.search_tickets(
            tickets=tickets,
            query=query,
            use_llm_expansion=config.USE_LLM_SEARCH
        )

        logger.info(f"[search_tickets] Found {len(search_results)} tickets")

        if not search_results:
            return f"Тикеты по запросу '{query}' не найдены.\n\nПопробуйте:\n- Использовать другие ключевые слова\n- list_tickets для просмотра всех тикетов\n- list_tickets с фильтрами (status, priority, category)"

        # Format results with relevance scores
        result = f"Найдено тикетов: {len(search_results)} по запросу '{query}'\n"
        result += "(Отсортировано по релевантности)\n\n"

        for i, search_result in enumerate(search_results[:10], 1):  # Top 10
            ticket = search_result.ticket
            result += f"{i}. {ticket['id']}: {ticket['subject']} [{ticket['status']}]\n"
            result += f"   Приоритет: {ticket['priority']} | Категория: {ticket['category']}\n"
            result += f"   Описание: {ticket['description'][:150]}...\n"
            result += f"   📊 Релевантность: {search_result.score:.1f} ({search_result.match_reason})\n\n"

        if len(search_results) > 10:
            result += f"... и ещё {len(search_results) - 10} тикетов\n"

        return result

    elif name == "get_user_tickets":
        user_id = arguments["user_id"]
        logger.info(f"[get_user_tickets] Getting tickets for user: {user_id}")

        # Get user info
        users = load_users()
        user = next((u for u in users if u["id"] == user_id), None)

        if not user:
            logger.warning(f"[get_user_tickets] User not found: {user_id}")
            return f"Пользователь с ID '{user_id}' не найден"

        # Get user's tickets
        tickets = load_tickets()
        user_tickets = [t for t in tickets if t["userId"] == user_id]
        logger.info(f"[get_user_tickets] Found {len(user_tickets)} tickets for user {user_id}")

        result = f"""Информация о пользователе:
Имя: {user['name']}
Email: {user['email']}
План подписки: {user['subscriptionPlan']}
Дата регистрации: {user['registrationDate']}
Последняя активность: {user['lastActive']}
Статус: {user['status']}

Тикеты пользователя ({len(user_tickets)}):
"""

        if not user_tickets:
            result += "Нет тикетов"
        else:
            for ticket in user_tickets:
                result += f"""
- {ticket['id']}: {ticket['subject']}
  Статус: {ticket['status']} | Приоритет: {ticket['priority']} | Категория: {ticket['category']}
  Создан: {ticket['created']}
  Описание: {ticket['description']}
"""
                if 'resolution' in ticket:
                    result += f"  Решение: {ticket['resolution']}\n"

        return result

    else:
        return f"Неизвестный инструмент: {name}"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup/shutdown."""
    logger.info("=" * 60)
    logger.info("CRM MCP Server Starting")
    logger.info("=" * 60)
    logger.info(f"Data directory: {config.DATA_DIR}")
    logger.info(f"Host: {config.HOST}")
    logger.info(f"Port: {config.PORT}")
    logger.info(f"Authentication: {'ENABLED' if AUTH_ENABLED else 'DISABLED'}")

    # Initialize search service and check LLM availability
    search_service = get_search_service()
    if search_service.use_llm and config.USE_LLM_SEARCH:
        logger.info(f"Smart Search: ENABLED (LLM query expansion via GigaChat)")
    elif not search_service.use_llm:
        logger.info(f"Smart Search: DISABLED (no GigaChat credentials)")
    else:
        logger.info(f"Smart Search: DISABLED (CRM_USE_LLM_SEARCH=false)")

    logger.info("=" * 60)

    tools = get_tools_list()
    logger.info(f"Registered {len(tools)} CRM tools")
    for tool in tools:
        logger.info(f"  - {tool['name']}: {tool['description']}")

    yield

    logger.info("CRM MCP Server shutting down")


# Create FastAPI app
app = FastAPI(
    title="CRM MCP Server",
    description="User and ticket management via Model Context Protocol (MCP)",
    version="1.0.0",
    lifespan=lifespan,
)


def check_auth(authorization: Optional[str] = None) -> bool:
    """Check if request is authorized."""
    if not AUTH_ENABLED:
        return True

    if not authorization:
        return False

    # Support both "Bearer <token>" and plain token
    token = authorization.replace("Bearer ", "").strip()
    return token == config.CRM_API_KEY


@app.get("/")
async def root():
    """Server info endpoint."""
    return {
        "name": "CRM MCP Server",
        "version": "1.0.0",
        "description": "User and ticket management via MCP",
        "protocol": "mcp/1.0",
        "capabilities": {
            "tools": len(get_tools_list()),
        },
        "data_dir": str(config.DATA_DIR),
    }


@app.get("/health")
async def health():
    """Health check endpoint."""
    try:
        users = load_users()
        tickets = load_tickets()
        return {
            "status": "healthy",
            "users_count": len(users),
            "tickets_count": len(tickets),
        }
    except Exception as e:
        return {"status": "unhealthy", "error": str(e)}


@app.get("/tools")
async def list_tools(authorization: Optional[str] = Header(None)):
    """List available MCP tools."""
    if not check_auth(authorization):
        raise HTTPException(status_code=401, detail="Unauthorized")

    return {
        "tools": get_tools_list(),
    }


@app.get("/sse")
async def sse_endpoint(
    request: Request,
    authorization: Optional[str] = Header(None),
):
    """SSE endpoint for MCP protocol communication."""
    if not check_auth(authorization):
        raise HTTPException(status_code=401, detail="Unauthorized")

    session_id = str(uuid.uuid4())
    logger.info(f"New SSE connection: {session_id}")

    async def event_generator():
        try:
            # Store session
            sessions[session_id] = {
                "id": session_id,
                "connected": True,
            }

            # Send endpoint URL as first message
            endpoint_url = f"http://{config.HOST}:{config.PORT}/message?sessionId={session_id}"
            yield f"event: endpoint\ndata: {endpoint_url}\n\n"

            # Keep connection alive
            while sessions.get(session_id, {}).get("connected", False):
                yield f"event: ping\ndata: {{}}\n\n"
                await asyncio.sleep(30)

        except asyncio.CancelledError:
            logger.info(f"SSE connection cancelled: {session_id}")
        finally:
            # Clean up session
            if session_id in sessions:
                del sessions[session_id]
            logger.info(f"SSE connection closed: {session_id}")

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post("/message")
async def handle_message(
    request: Request,
    sessionId: str,
    authorization: Optional[str] = Header(None),
):
    """Handle MCP protocol messages."""
    if not check_auth(authorization):
        raise HTTPException(status_code=401, detail="Unauthorized")

    try:
        message = await request.json()
        logger.debug(f"Received message for session {sessionId}: {message}")

        method = message.get("method")
        msg_id = message.get("id")

        if method == "initialize":
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "protocolVersion": "1.0",
                    "serverInfo": {
                        "name": "crm-mcp-server",
                        "version": "1.0.0",
                    },
                    "capabilities": {
                        "tools": {},
                    },
                },
            }

        elif method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "tools": get_tools_list(),
                },
            }

        elif method == "tools/call":
            params = message.get("params", {})
            tool_name = params.get("name")
            arguments = params.get("arguments", {})

            logger.info(f"Executing tool: {tool_name}")

            # Execute tool
            try:
                result = await execute_tool(tool_name, arguments)

                # Log response preview
                preview_lines = result.splitlines()
                if len(preview_lines) <= 6:
                    preview = "\n".join(preview_lines)
                else:
                    skipped = len(preview_lines) - 6
                    preview = "\n".join(preview_lines[:3])
                    preview += f"\n... ({skipped} more lines) ..."
                    preview += "\n" + "\n".join(preview_lines[-3:])
                logger.info(f"Tool {tool_name} [OK]:\n{preview}")

                return {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "result": {
                        "content": [
                            {
                                "type": "text",
                                "text": result
                            }
                        ]
                    },
                }
            except Exception as e:
                logger.error(f"Tool execution error: {e}")
                return {
                    "jsonrpc": "2.0",
                    "id": msg_id,
                    "error": {
                        "code": -32603,
                        "message": f"Tool execution failed: {str(e)}",
                    },
                }

        else:
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "error": {
                    "code": -32601,
                    "message": f"Method not found: {method}",
                },
            }

    except Exception as e:
        logger.error(f"Message handling error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="CRM MCP Server")
    parser.add_argument("--host", default=config.HOST, help="Host to bind to")
    parser.add_argument("--port", type=int, default=config.PORT, help="Port to bind to")
    parser.add_argument("--no-auth", action="store_true", help="Disable authentication")
    parser.add_argument("--data-dir", default=None, help="Path to data directory")
    args = parser.parse_args()

    # Update config
    config.HOST = args.host
    config.PORT = args.port

    if args.no_auth:
        config.NO_AUTH = True
        global AUTH_ENABLED
        AUTH_ENABLED = False

    # Set data directory
    if args.data_dir:
        config.DATA_DIR = Path(args.data_dir)
    else:
        config.DATA_DIR = Path(__file__).parent / "data"

    config.USERS_FILE = config.DATA_DIR / "users.json"
    config.TICKETS_FILE = config.DATA_DIR / "tickets.json"

    # Check data files exist
    if not config.USERS_FILE.exists():
        logger.error(f"Users file not found: {config.USERS_FILE}")
        sys.exit(1)
    if not config.TICKETS_FILE.exists():
        logger.error(f"Tickets file not found: {config.TICKETS_FILE}")
        sys.exit(1)

    # Run server
    import uvicorn

    uvicorn.run(
        app,
        host=config.HOST,
        port=config.PORT,
        log_level="info",
    )


if __name__ == "__main__":
    main()
