#!/usr/bin/env python3
"""Test that our OAuth fix works"""

import asyncio
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.dirname(__file__))

from crm.search_service import SearchService
from pm.ai_service import AiService


async def test_crm_oauth():
    """Test CRM OAuth"""
    print("=== Testing CRM OAuth Fix ===")
    service = SearchService()

    if not service.use_llm:
        print("❌ LLM not enabled (credentials not set)")
        return False

    token = await service.get_gigachat_token()
    if token:
        print(f"✓ CRM OAuth successful!")
        print(f"  Token: {token[:20]}...")
        return True
    else:
        print("❌ CRM OAuth failed")
        return False


async def test_pm_oauth():
    """Test PM OAuth"""
    print("\n=== Testing PM OAuth Fix ===")
    service = AiService()

    if not service.client_id or not service.client_secret:
        print("❌ Credentials not set")
        return False

    try:
        token = await service.get_access_token()
        print(f"✓ PM OAuth successful!")
        print(f"  Token: {token[:20]}...")
        return True
    except Exception as e:
        print(f"❌ PM OAuth failed: {e}")
        return False


async def main():
    print("Testing OAuth fixes for MCP servers\n")

    crm_ok = await test_crm_oauth()
    pm_ok = await test_pm_oauth()

    print("\n=== Results ===")
    print(f"CRM OAuth: {'✓ PASS' if crm_ok else '✗ FAIL'}")
    print(f"PM OAuth:  {'✓ PASS' if pm_ok else '✗ FAIL'}")

    if crm_ok and pm_ok:
        print("\n✓ All OAuth fixes working!")
        return 0
    else:
        print("\n✗ Some OAuth fixes failed")
        return 1


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    sys.exit(exit_code)
