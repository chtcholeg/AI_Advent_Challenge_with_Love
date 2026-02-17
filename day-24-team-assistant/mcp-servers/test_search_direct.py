#!/usr/bin/env python3
"""Direct test of search_tickets via API"""

import asyncio
import httpx
import json


async def test_search():
    """Test search_tickets tool via CRM MCP Server"""

    url = "http://localhost:8011/message?sessionId=test-123"

    # Prepare tool call message
    message = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "search_tickets",
            "arguments": {
                "query": "напоминания Telegram"
            }
        }
    }

    print("🔍 Testing search_tickets via API")
    print("=" * 60)
    print(f"Query: 'напоминания Telegram'")
    print()

    try:
        async with httpx.AsyncClient() as client:
            response = await client.post(url, json=message, timeout=30.0)
            response.raise_for_status()

            data = response.json()

            if "result" in data:
                result_text = data["result"]["content"][0]["text"]
                print("✅ Search result:")
                print("-" * 60)
                print(result_text)
                print("-" * 60)
            elif "error" in data:
                print(f"❌ Error: {data['error']}")
            else:
                print(f"❓ Unexpected response: {data}")

    except Exception as e:
        print(f"❌ Request failed: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    asyncio.run(test_search())
