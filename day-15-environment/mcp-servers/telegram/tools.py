"""Telegram MCP tool definitions.

Each tool interacts with Telegram via Telethon:
- name: unique identifier used in tools/call
- description: shown to AI for function selection
- input_schema: JSON Schema describing accepted parameters
- execute(): async handler returning ToolResult

Read tools (get_channel_messages, get_channel_info) work with any public channel.
Write tools (send_message) require appropriate permissions in the target chat.
"""

import logging

from shared import ToolResult, BaseTool
from .telegram_client import TelegramChannelClient

logger = logging.getLogger(__name__)


class TelegramTool(BaseTool):
    """Base class for all Telegram MCP tools."""

    def __init__(self, client: TelegramChannelClient):
        self.client = client

    async def execute(self, arguments: dict) -> ToolResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Tool: get_channel_messages
# ---------------------------------------------------------------------------

class GetChannelMessagesTool(TelegramTool):
    name = "get_channel_messages"
    description = (
        "Get the latest messages from a public Telegram channel. "
        "Returns message text, date, view count, and ID. "
        "Channel must be public (have a @username). "
        "By default returns the 5 most recent messages."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "channel": {
                "type": "string",
                "description": "Telegram channel username (e.g. 'durov' or '@durov')",
            },
            "count": {
                "type": "integer",
                "description": "Number of messages to retrieve (1-100, default: 5)",
            },
        },
        "required": ["channel"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        channel = arguments.get("channel", "").strip()
        if not channel:
            return ToolResult("Missing required parameter: channel", is_error=True)

        count = arguments.get("count", 5)
        count = max(1, min(100, int(count)))

        try:
            messages = await self.client.get_messages(channel, limit=count)
            channel_clean = channel.lstrip("@")

            if not messages:
                return ToolResult(f"No text messages found in @{channel_clean}.")

            lines = [
                f"Latest {len(messages)} messages from @{channel_clean}:",
                "=" * 50,
                "",
            ]

            for i, msg in enumerate(messages, 1):
                lines.append(f"[{i}] ID: {msg['id']}  |  {msg['date']}")
                if msg.get("views"):
                    lines.append(f"    Views: {msg['views']}")
                if msg.get("has_media"):
                    lines.append(f"    [has media attachment]")
                if msg.get("reply_to_id"):
                    lines.append(f"    Reply to: #{msg['reply_to_id']}")
                text = msg["text"]
                if len(text) > 500:
                    text = text[:500] + "\n    ... [truncated]"
                lines.append(f"    {text}")
                lines.append("")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GetChannelMessagesTool error: {e}")
            return ToolResult(
                f"Failed to get messages from @{channel.lstrip('@')}: {e}",
                is_error=True,
            )


# ---------------------------------------------------------------------------
# Tool: get_channel_info
# ---------------------------------------------------------------------------

class GetChannelInfoTool(TelegramTool):
    name = "get_channel_info"
    description = (
        "Get information about a public Telegram channel: "
        "title, description, member count, creation date, and channel type. "
        "Channel must be public (have a @username)."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "channel": {
                "type": "string",
                "description": "Telegram channel username (e.g. 'durov' or '@durov')",
            },
        },
        "required": ["channel"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        channel = arguments.get("channel", "").strip()
        if not channel:
            return ToolResult("Missing required parameter: channel", is_error=True)

        try:
            info = await self.client.get_channel_info(channel)

            channel_type = "Supergroup" if info.get("is_supergroup") else "Channel"

            lines = [
                f"{channel_type}: @{info['username']}",
                "=" * 50,
                "",
                f"Title: {info['title']}",
            ]
            if info.get("description"):
                lines.append(f"Description: {info['description']}")
            lines.append("")

            if info.get("members_count") is not None:
                lines.append(f"Members: {info['members_count']:,}")
            if info.get("verified"):
                lines.append("Verified: Yes")
            lines.append(
                "Type: Broadcast (read-only)"
                if info.get("is_broadcast")
                else "Type: Interactive (members can post)"
            )
            if info.get("created"):
                lines.append(f"Created: {info['created']}")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"GetChannelInfoTool error: {e}")
            return ToolResult(
                f"Failed to get channel info for @{channel.lstrip('@')}: {e}",
                is_error=True,
            )


# ---------------------------------------------------------------------------
# Tool: send_message
# ---------------------------------------------------------------------------

class SendMessageTool(TelegramTool):
    name = "send_message"
    description = (
        "Send a text message to a Telegram chat (channel, group, or user). "
        "For channels: the authenticated user must be an admin with posting rights. "
        "For groups: the user must be a member. "
        "For users: any user with a public @username can receive messages. "
        "Returns the sent message ID and timestamp on success."
    )
    input_schema = {
        "type": "object",
        "properties": {
            "chat": {
                "type": "string",
                "description": "Telegram chat username (e.g. 'durov' or '@durov'), group, or channel",
            },
            "text": {
                "type": "string",
                "description": "Message text to send (supports Telegram markdown)",
            },
            "reply_to": {
                "type": "integer",
                "description": "Optional message ID to reply to",
            },
        },
        "required": ["chat", "text"],
    }

    async def execute(self, arguments: dict) -> ToolResult:
        chat = arguments.get("chat", "").strip()
        text = arguments.get("text", "").strip()
        reply_to = arguments.get("reply_to")

        if not chat:
            return ToolResult("Missing required parameter: chat", is_error=True)
        if not text:
            return ToolResult("Missing required parameter: text", is_error=True)

        # Validate reply_to if provided
        if reply_to is not None:
            try:
                reply_to = int(reply_to)
            except (ValueError, TypeError):
                return ToolResult("Invalid reply_to: must be an integer message ID", is_error=True)

        try:
            result = await self.client.send_message(chat, text, reply_to=reply_to)
            chat_clean = chat.lstrip("@")

            lines = [
                f"Message sent successfully to @{chat_clean}",
                "=" * 50,
                "",
                f"Message ID: {result['id']}",
                f"Sent at: {result['date']}",
                f"Preview: {result['text_preview']}",
            ]
            if reply_to:
                lines.append(f"Reply to: #{reply_to}")

            return ToolResult("\n".join(lines))
        except Exception as e:
            logger.error(f"SendMessageTool error: {e}")
            return ToolResult(
                f"Failed to send message to @{chat.lstrip('@')}: {e}",
                is_error=True,
            )


# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------

def get_all_tools(client: TelegramChannelClient) -> list[TelegramTool]:
    """Return all registered Telegram MCP tools."""
    return [
        GetChannelMessagesTool(client),
        GetChannelInfoTool(client),
        SendMessageTool(client),
    ]
