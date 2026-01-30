"""Async Telegram client wrapper using Telethon.

Reads messages and channel info from public Telegram channels.
Sends messages to channels/groups where the authenticated user has permission.
Requires a pre-authenticated session file (run setup_session.py first).
"""

import logging
from typing import Optional

from telethon import TelegramClient
from telethon.errors import (
    ChannelInvalidError,
    NotFoundError,
    UsernameInvalidError,
    FloodWaitError,
    ChatWriteForbiddenError,
    UserBannedInChannelError,
)

logger = logging.getLogger(__name__)


class TelegramChannelError(Exception):
    """Raised when a Telegram channel operation fails."""
    pass


class TelegramChannelClient:
    """Async client for reading public Telegram channels."""

    def __init__(self, api_id: int, api_hash: str, session_file: str = "telegram_session"):
        self.client = TelegramClient(session_file, api_id, api_hash)
        self._connected = False

    async def _ensure_connected(self):
        if not self._connected:
            await self.client.start()
            self._connected = True
            me = await self.client.get_me()
            logger.info(f"Telegram client connected as: {me.username or me.first_name or 'anonymous'}")

    async def get_channel_info(self, channel: str) -> dict:
        """Get metadata about a public Telegram channel.

        Args:
            channel: Channel username (with or without @ prefix)

        Returns:
            Dict with channel metadata

        Raises:
            TelegramChannelError: Channel not found or invalid
        """
        await self._ensure_connected()
        channel = channel.lstrip("@")

        try:
            entity = await self.client.get_entity(channel)
        except (ChannelInvalidError, NotFoundError, UsernameInvalidError) as e:
            raise TelegramChannelError(f"Channel not found: @{channel}") from e

        info = {
            "username": getattr(entity, "username", None) or channel,
            "title": getattr(entity, "title", "Unknown"),
            "description": getattr(entity, "description", None) or "",
            "members_count": getattr(entity, "members_count", None),
            "is_channel": hasattr(entity, "broadcast"),
            "is_broadcast": getattr(entity, "broadcast", False),
            "is_supergroup": getattr(entity, "megagroup", False),
            "verified": getattr(entity, "verified", False),
            "created": getattr(entity, "date", None),
        }

        if info["created"]:
            info["created"] = info["created"].isoformat()

        return info

    async def get_messages(self, channel: str, limit: int = 10, offset_id: Optional[int] = None) -> list[dict]:
        """Get recent messages from a public Telegram channel.

        Args:
            channel: Channel username (with or without @ prefix)
            limit: Number of messages to retrieve (1-100)
            offset_id: Start reading before this message ID

        Returns:
            List of message dicts, newest first

        Raises:
            TelegramChannelError: Channel not found, invalid, or rate limited
        """
        await self._ensure_connected()
        channel = channel.lstrip("@")
        limit = max(1, min(100, limit))

        try:
            entity = await self.client.get_entity(channel)
        except (ChannelInvalidError, NotFoundError, UsernameInvalidError) as e:
            raise TelegramChannelError(f"Channel not found: @{channel}") from e

        messages = []
        try:
            # Build kwargs for iter_messages, only include offset_id if not None
            iter_kwargs = {"limit": limit}
            if offset_id is not None:
                iter_kwargs["offset_id"] = offset_id

            async for msg in self.client.iter_messages(entity, **iter_kwargs):
                if msg.text:
                    entry = {
                        "id": msg.id,
                        "text": msg.text,
                        "date": msg.date.isoformat() if msg.date else None,
                        "views": msg.views,
                        "has_media": msg.media is not None,
                    }
                    if msg.reply_to_msg_id:
                        entry["reply_to_id"] = msg.reply_to_msg_id
                    messages.append(entry)
        except FloodWaitError as e:
            raise TelegramChannelError(f"Rate limited by Telegram. Wait {e.seconds} seconds.") from e

        return messages

    async def send_message(self, chat: str, text: str, reply_to: Optional[int] = None) -> dict:
        """Send a text message to a Telegram chat (channel, group, or user).

        Args:
            chat: Chat username (with or without @ prefix) or numeric ID
            text: Message text to send
            reply_to: Optional message ID to reply to

        Returns:
            Dict with sent message info (id, date, chat)

        Raises:
            TelegramChannelError: Permission denied, chat not found, or rate limited

        Note:
            For channels: the authenticated user must be an admin with posting rights.
            For groups: the user must be a member (and not restricted).
            For users: any user with a public username can receive messages.
        """
        await self._ensure_connected()
        chat = chat.lstrip("@")

        try:
            entity = await self.client.get_entity(chat)
        except (ChannelInvalidError, NotFoundError, UsernameInvalidError) as e:
            raise TelegramChannelError(f"Chat not found: @{chat}") from e

        try:
            msg = await self.client.send_message(entity, text, reply_to=reply_to)
            return {
                "id": msg.id,
                "date": msg.date.isoformat() if msg.date else None,
                "chat": chat,
                "text_preview": text[:100] + ("..." if len(text) > 100 else ""),
            }
        except ChatWriteForbiddenError as e:
            raise TelegramChannelError(
                f"Cannot send message to @{chat}: no permission (admin rights required for channels)"
            ) from e
        except UserBannedInChannelError as e:
            raise TelegramChannelError(
                f"Cannot send message to @{chat}: user is banned or restricted"
            ) from e
        except FloodWaitError as e:
            raise TelegramChannelError(f"Rate limited by Telegram. Wait {e.seconds} seconds.") from e

    async def disconnect(self):
        if self._connected:
            await self.client.disconnect()
            self._connected = False
