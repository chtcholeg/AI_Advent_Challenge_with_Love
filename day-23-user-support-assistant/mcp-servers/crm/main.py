#!/usr/bin/env python3
"""
CRM MCP Server
Provides access to user and ticket data for support assistant
"""

import json
import os
from pathlib import Path
from typing import Any, Sequence
from datetime import datetime

from mcp.server import Server
from mcp.types import Tool, TextContent
import mcp.server.stdio


# Paths to data files
DATA_DIR = Path(__file__).parent / "data"
USERS_FILE = DATA_DIR / "users.json"
TICKETS_FILE = DATA_DIR / "tickets.json"


def load_users() -> list[dict[str, Any]]:
    """Load users from JSON file"""
    if not USERS_FILE.exists():
        return []
    with open(USERS_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def load_tickets() -> list[dict[str, Any]]:
    """Load tickets from JSON file"""
    if not TICKETS_FILE.exists():
        return []
    with open(TICKETS_FILE, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_tickets(tickets: list[dict[str, Any]]) -> None:
    """Save tickets to JSON file"""
    with open(TICKETS_FILE, 'w', encoding='utf-8') as f:
        json.dump(tickets, f, ensure_ascii=False, indent=2)


# Create MCP server
app = Server("crm-server")


@app.list_tools()
async def list_tools() -> list[Tool]:
    """List available CRM tools"""
    return [
        Tool(
            name="get_user",
            description="Get user information by ID",
            inputSchema={
                "type": "object",
                "properties": {
                    "user_id": {
                        "type": "string",
                        "description": "User ID (e.g., 'user_001')"
                    }
                },
                "required": ["user_id"]
            }
        ),
        Tool(
            name="list_users",
            description="List all users with optional filtering",
            inputSchema={
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
        ),
        Tool(
            name="get_ticket",
            description="Get ticket information by ID",
            inputSchema={
                "type": "object",
                "properties": {
                    "ticket_id": {
                        "type": "string",
                        "description": "Ticket ID (e.g., 'ticket_001')"
                    }
                },
                "required": ["ticket_id"]
            }
        ),
        Tool(
            name="list_tickets",
            description="List tickets with optional filtering",
            inputSchema={
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
                        "description": "Filter by category: 'authentication', 'indexing', 'mcp', 'features', 'performance', 'build'"
                    }
                }
            }
        ),
        Tool(
            name="update_ticket_status",
            description="Update ticket status and add notes",
            inputSchema={
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
        ),
        Tool(
            name="search_tickets",
            description="Search tickets by keyword in subject or description",
            inputSchema={
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search query"
                    }
                },
                "required": ["query"]
            }
        ),
        Tool(
            name="get_user_tickets",
            description="Get all tickets for a specific user with full context",
            inputSchema={
                "type": "object",
                "properties": {
                    "user_id": {
                        "type": "string",
                        "description": "User ID"
                    }
                },
                "required": ["user_id"]
            }
        )
    ]


@app.call_tool()
async def call_tool(name: str, arguments: Any) -> Sequence[TextContent]:
    """Handle tool calls"""

    if name == "get_user":
        users = load_users()
        user_id = arguments["user_id"]
        user = next((u for u in users if u["id"] == user_id), None)

        if user:
            result = f"""Пользователь найден:
ID: {user['id']}
Имя: {user['name']}
Email: {user['email']}
План подписки: {user['subscriptionPlan']}
Дата регистрации: {user['registrationDate']}
Последняя активность: {user['lastActive']}
Статус: {user['status']}"""
        else:
            result = f"Пользователь с ID '{user_id}' не найден"

        return [TextContent(type="text", text=result)]

    elif name == "list_users":
        users = load_users()

        # Apply filters
        if "status" in arguments:
            users = [u for u in users if u["status"] == arguments["status"]]
        if "subscription_plan" in arguments:
            users = [u for u in users if u["subscriptionPlan"] == arguments["subscription_plan"]]

        if not users:
            result = "Пользователи не найдены"
        else:
            result = f"Найдено пользователей: {len(users)}\n\n"
            for user in users:
                result += f"- {user['id']}: {user['name']} ({user['email']}) - {user['subscriptionPlan']} - {user['status']}\n"

        return [TextContent(type="text", text=result)]

    elif name == "get_ticket":
        tickets = load_tickets()
        ticket_id = arguments["ticket_id"]
        ticket = next((t for t in tickets if t["id"] == ticket_id), None)

        if ticket:
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
        else:
            result = f"Тикет с ID '{ticket_id}' не найден"

        return [TextContent(type="text", text=result)]

    elif name == "list_tickets":
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
            result = "Тикеты не найдены"
        else:
            result = f"Найдено тикетов: {len(tickets)}\n\n"
            for ticket in tickets:
                result += f"- {ticket['id']}: {ticket['subject']} [{ticket['status']}] ({ticket['priority']} priority)\n"

        return [TextContent(type="text", text=result)]

    elif name == "update_ticket_status":
        tickets = load_tickets()
        ticket_id = arguments["ticket_id"]
        new_status = arguments["status"]

        ticket = next((t for t in tickets if t["id"] == ticket_id), None)

        if not ticket:
            result = f"Тикет с ID '{ticket_id}' не найден"
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
                    result = f"Для статуса '{new_status}' требуется указать resolution"
                    return [TextContent(type="text", text=result)]

            save_tickets(tickets)

            result = f"""Тикет обновлен:
ID: {ticket_id}
Статус изменен: {old_status} → {new_status}
Обновлен: {ticket['updated']}"""

            if "notes" in arguments:
                result += f"\nДобавлены заметки: {arguments['notes']}"
            if "resolution" in ticket and new_status in ["resolved", "closed"]:
                result += f"\nРешение: {ticket['resolution']}"

        return [TextContent(type="text", text=result)]

    elif name == "search_tickets":
        tickets = load_tickets()
        query = arguments["query"].lower()

        # Search in subject and description
        found_tickets = [
            t for t in tickets
            if query in t["subject"].lower() or query in t["description"].lower()
        ]

        if not found_tickets:
            result = f"Тикеты по запросу '{arguments['query']}' не найдены"
        else:
            result = f"Найдено тикетов: {len(found_tickets)} по запросу '{arguments['query']}'\n\n"
            for ticket in found_tickets:
                result += f"- {ticket['id']}: {ticket['subject']} [{ticket['status']}]\n"
                result += f"  Описание: {ticket['description'][:100]}...\n\n"

        return [TextContent(type="text", text=result)]

    elif name == "get_user_tickets":
        user_id = arguments["user_id"]

        # Get user info
        users = load_users()
        user = next((u for u in users if u["id"] == user_id), None)

        if not user:
            result = f"Пользователь с ID '{user_id}' не найден"
            return [TextContent(type="text", text=result)]

        # Get user's tickets
        tickets = load_tickets()
        user_tickets = [t for t in tickets if t["userId"] == user_id]

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

        return [TextContent(type="text", text=result)]

    else:
        return [TextContent(type="text", text=f"Неизвестный инструмент: {name}")]


async def main():
    """Run the CRM MCP server"""
    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        await app.run(
            read_stream,
            write_stream,
            app.create_initialization_options()
        )


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
