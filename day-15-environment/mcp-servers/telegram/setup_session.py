"""One-time interactive setup to authenticate and create a Telegram session file.

Run this script once before starting the MCP server:
    python setup_session.py

You will need:
    1. TELEGRAM_API_ID   (from https://my.telegram.org)
    2. TELEGRAM_API_HASH (from https://my.telegram.org)
    3. Your phone number for OTP verification

After successful authentication a session file is created. The MCP server
uses it for all subsequent requests — no re-authentication needed.
"""

import asyncio
import os
import sys

from telethon import TelegramClient


async def main():
    print("Telegram Session Setup")
    print("=" * 40)

    api_id = os.getenv("TELEGRAM_API_ID") or input("Enter TELEGRAM_API_ID: ").strip()
    api_hash = os.getenv("TELEGRAM_API_HASH") or input("Enter TELEGRAM_API_HASH: ").strip()
    session_file = os.getenv("TELEGRAM_SESSION_FILE", "telegram_session")

    if not api_id or not api_hash:
        print("ERROR: Both TELEGRAM_API_ID and TELEGRAM_API_HASH are required.")
        print("Get them at https://my.telegram.org")
        sys.exit(1)

    print(f"\nSession file: {session_file}.session")
    print("Connecting to Telegram (phone/OTP prompt may follow)...")
    print()

    client = TelegramClient(session_file, int(api_id), api_hash)
    # start() handles interactive phone number + OTP auth automatically
    await client.start()

    me = await client.get_me()
    name = me.username or me.first_name or "Unknown"

    print(f"\nAuthenticated as: {name}")
    print(f"Session saved to: {session_file}.session")
    print("\nStart the MCP server:")
    print("    python main.py --no-auth")

    await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
