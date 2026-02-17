#!/usr/bin/env python3
"""Test GigaChat OAuth authentication"""

import asyncio
import base64
import os
import httpx


async def test_oauth_basic_auth():
    """Test OAuth with BasicAuth (как в CRM)"""
    client_id = os.getenv("GIGACHAT_CLIENT_ID")
    client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")

    if not client_id or not client_secret:
        print("❌ Credentials not set")
        return

    print(f"✓ Client ID: {client_id[:10]}...")
    print(f"✓ Client Secret: {client_secret[:10]}...")
    print()

    try:
        print("Testing BasicAuth approach (CRM style)...")
        async with httpx.AsyncClient(verify=False) as client:
            auth = httpx.BasicAuth(client_id, client_secret)
            response = await client.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                auth=auth,
                headers={
                    "Content-Type": "application/x-www-form-urlencoded",
                    "Accept": "application/json",
                    "RqUID": "test-basic",
                },
                data={"scope": "GIGACHAT_API_PERS"},
                timeout=30.0,
            )
            print(f"Status: {response.status_code}")
            print(f"Headers: {dict(response.headers)}")
            print(f"Body: {response.text}")
            response.raise_for_status()
            data = response.json()
            print(f"✓ Token obtained: {data.get('access_token', '')[:20]}...")
    except Exception as e:
        print(f"❌ BasicAuth failed: {e}")
        print()


async def test_oauth_manual_base64():
    """Test OAuth with manual Base64 (как в PM)"""
    client_id = os.getenv("GIGACHAT_CLIENT_ID")
    client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")

    if not client_id or not client_secret:
        print("❌ Credentials not set")
        return

    try:
        print("Testing Manual Base64 approach (PM style)...")
        auth = base64.b64encode(
            f"{client_id}:{client_secret}".encode()
        ).decode()

        async with httpx.AsyncClient(verify=False) as client:
            response = await client.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={
                    "Authorization": f"Basic {auth}",
                    "Accept": "application/json",
                    "RqUID": "test-manual",
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                data={"scope": "GIGACHAT_API_PERS"},
                timeout=30.0,
            )
            print(f"Status: {response.status_code}")
            print(f"Headers: {dict(response.headers)}")
            print(f"Body: {response.text}")
            response.raise_for_status()
            data = response.json()
            print(f"✓ Token obtained: {data.get('access_token', '')[:20]}...")
    except Exception as e:
        print(f"❌ Manual Base64 failed: {e}")


async def main():
    print("=== GigaChat OAuth Test ===")
    print()
    await test_oauth_basic_auth()
    print()
    await test_oauth_manual_base64()


if __name__ == "__main__":
    asyncio.run(main())
