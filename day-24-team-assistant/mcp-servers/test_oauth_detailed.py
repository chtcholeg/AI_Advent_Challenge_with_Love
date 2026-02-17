#!/usr/bin/env python3
"""Detailed OAuth test with multiple approaches"""

import asyncio
import base64
import os
import httpx
import uuid


async def test_with_uuid():
    """Test with real UUID in RqUID"""
    client_id = os.getenv("GIGACHAT_CLIENT_ID")
    client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")

    if not client_id or not client_secret:
        print("❌ Credentials not set")
        return

    try:
        print("Test 1: Using proper UUID for RqUID...")
        auth = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()

        async with httpx.AsyncClient(verify=False) as client:
            response = await client.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={
                    "Authorization": f"Basic {auth}",
                    "Accept": "application/json",
                    "RqUID": str(uuid.uuid4()),
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                data={"scope": "GIGACHAT_API_PERS"},
                timeout=30.0,
            )
            print(f"  Status: {response.status_code}")
            if response.status_code == 200:
                print(f"  ✓ Success!")
                data = response.json()
                print(f"  Token: {data.get('access_token', '')[:20]}...")
            else:
                print(f"  Body: {response.text}")
    except Exception as e:
        print(f"  ❌ Failed: {e}")


async def test_without_rquid():
    """Test without RqUID header"""
    client_id = os.getenv("GIGACHAT_CLIENT_ID")
    client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")

    try:
        print("\nTest 2: Without RqUID...")
        auth = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()

        async with httpx.AsyncClient(verify=False) as client:
            response = await client.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={
                    "Authorization": f"Basic {auth}",
                    "Accept": "application/json",
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                data={"scope": "GIGACHAT_API_PERS"},
                timeout=30.0,
            )
            print(f"  Status: {response.status_code}")
            if response.status_code == 200:
                print(f"  ✓ Success!")
                data = response.json()
                print(f"  Token: {data.get('access_token', '')[:20]}...")
            else:
                print(f"  Body: {response.text}")
    except Exception as e:
        print(f"  ❌ Failed: {e}")


async def test_different_scope():
    """Test with different scope"""
    client_id = os.getenv("GIGACHAT_CLIENT_ID")
    client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")

    try:
        print("\nTest 3: Different scope (GIGACHAT_API_CORP)...")
        auth = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()

        async with httpx.AsyncClient(verify=False) as client:
            response = await client.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={
                    "Authorization": f"Basic {auth}",
                    "Accept": "application/json",
                    "RqUID": str(uuid.uuid4()),
                    "Content-Type": "application/x-www-form-urlencoded"
                },
                data={"scope": "GIGACHAT_API_CORP"},
                timeout=30.0,
            )
            print(f"  Status: {response.status_code}")
            if response.status_code == 200:
                print(f"  ✓ Success!")
                data = response.json()
                print(f"  Token: {data.get('access_token', '')[:20]}...")
            else:
                print(f"  Body: {response.text}")
    except Exception as e:
        print(f"  ❌ Failed: {e}")


async def test_credentials_format():
    """Check if credentials have proper format"""
    client_id = os.getenv("GIGACHAT_CLIENT_ID")
    client_secret = os.getenv("GIGACHAT_CLIENT_SECRET")

    print("\n=== Credentials Check ===")
    print(f"Client ID: '{client_id}'")
    print(f"  Length: {len(client_id)}")
    print(f"  Has whitespace: {' ' in client_id or '\t' in client_id or '\n' in client_id}")
    print(f"Client Secret: '{client_secret}'")
    print(f"  Length: {len(client_secret)}")
    print(f"  Has whitespace: {' ' in client_secret or '\t' in client_secret or '\n' in client_secret}")


async def main():
    print("=== Detailed GigaChat OAuth Test ===\n")
    await test_credentials_format()
    await test_with_uuid()
    await test_without_rquid()
    await test_different_scope()

    print("\n=== Recommendation ===")
    print("If all tests fail with 400, the credentials may be:")
    print("1. Invalid or expired")
    print("2. For a different API version")
    print("3. Not activated for API access")
    print("\nPlease verify credentials at: https://developers.sber.ru/portal/products/gigachat-api")


if __name__ == "__main__":
    asyncio.run(main())
